package no.nav.foreldrepenger.mottak.task;

import java.util.Optional;
import java.util.UUID;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import no.nav.foreldrepenger.fordel.kodeverdi.DokumentTypeId;
import no.nav.foreldrepenger.mottak.behandlendeenhet.EnhetsTjeneste;
import no.nav.foreldrepenger.mottak.domene.dokument.DokumentRepository;
import no.nav.foreldrepenger.mottak.domene.oppgavebehandling.OpprettGSakOppgaveTask;
import no.nav.foreldrepenger.mottak.felles.MottakMeldingDataWrapper;
import no.nav.foreldrepenger.mottak.felles.MottakMeldingFeil;
import no.nav.foreldrepenger.mottak.felles.WrappedProsessTaskHandler;
import no.nav.foreldrepenger.mottak.felles.kafka.HendelseProdusent;
import no.nav.foreldrepenger.mottak.felles.kafka.SøknadFordeltOgJournalførtHendelse;
import no.nav.foreldrepenger.mottak.journal.dokumentforsendelse.DokumentforsendelseResponse;
import no.nav.foreldrepenger.mottak.journal.dokumentforsendelse.JournalTilstand;
import no.nav.foreldrepenger.mottak.tjeneste.TilJournalføringTjeneste;
import no.nav.foreldrepenger.mottak.tjeneste.dokumentforsendelse.dto.ForsendelseStatus;
import no.nav.vedtak.exception.IntegrasjonException;
import no.nav.vedtak.felles.integrasjon.aktør.klient.AktørConsumerMedCache;
import no.nav.vedtak.felles.prosesstask.api.ProsessTask;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskRepository;

/**
 * <p>
 * ProssessTask som utleder journalføringsbehov og forsøker rette opp disse.
 * </p>
 */
@Dependent
@ProsessTask(TilJournalføringTask.TASKNAME)
public class TilJournalføringTask extends WrappedProsessTaskHandler {

    public static final String TASKNAME = "fordeling.tilJournalforing";
    public static final String JOURNALMANGLER_EXCEPTION_KODE = "FP-453958";

    private static final Logger LOG = LoggerFactory.getLogger(TilJournalføringTask.class);

    private final TilJournalføringTjeneste journalføring;
    private final EnhetsTjeneste enhetsidTjeneste;
    private final DokumentRepository dokumentRepository;
    private final AktørConsumerMedCache aktør;
    private final HendelseProdusent hendelseProdusent;

    @Inject
    public TilJournalføringTask(ProsessTaskRepository prosessTaskRepository,
            TilJournalføringTjeneste journalføringTjeneste,
            EnhetsTjeneste enhetsidTjeneste, HendelseProdusent hendelseProdusent,
            DokumentRepository dokumentRepository,
            AktørConsumerMedCache aktørConsumer) {
        super(prosessTaskRepository);
        this.journalføring = journalføringTjeneste;
        this.enhetsidTjeneste = enhetsidTjeneste;
        this.dokumentRepository = dokumentRepository;
        this.aktør = aktørConsumer;
        this.hendelseProdusent = hendelseProdusent;
    }

    @Override
    public void precondition(MottakMeldingDataWrapper dataWrapper) {
        if (!dataWrapper.getAktørId().isPresent()) {
            throw MottakMeldingFeil.FACTORY.prosesstaskPreconditionManglerProperty(TASKNAME,
                    MottakMeldingDataWrapper.AKTØR_ID_KEY, dataWrapper.getId()).toException();
        }
        if (!dataWrapper.getSaksnummer().isPresent()) {
            throw MottakMeldingFeil.FACTORY.prosesstaskPreconditionManglerProperty(TASKNAME,
                    MottakMeldingDataWrapper.SAKSNUMMER_KEY, dataWrapper.getId()).toException();
        }
    }

    @Transactional
    @Override
    public MottakMeldingDataWrapper doTask(MottakMeldingDataWrapper w) {
        Optional<String> fnr = aktør.hentPersonIdentForAktørId(w.getAktørId().get());// NOSONAR
        if (fnr.isEmpty()) {
            throw MottakMeldingFeil.FACTORY.fantIkkePersonidentForAktørId(TASKNAME, w.getId()).toException();
        }
        String enhetsId = enhetsidTjeneste.hentFordelingEnhetId(w.getTema(), w.getBehandlingTema(),
                w.getJournalførendeEnhet(), fnr);
        if (w.getArkivId() == null) {
            UUID forsendelseId = w.getForsendelseId().orElseThrow(IllegalStateException::new);
            DokumentforsendelseResponse response = journalføring.journalførDokumentforsendelse(forsendelseId,
                    w.getSaksnummer(), w.getAvsenderId(), true,
                    w.getRetryingTask());
            w.setArkivId(response.getJournalpostId());
            // Hvis endelig journalføring feiler (fx pga doktype annet), send til manuell
            // journalføring (journalpost er opprettet).
            if (!JournalTilstand.ENDELIG_JOURNALFØRT.equals(response.getJournalTilstand())) {
                MottakMeldingFeil.FACTORY.feilJournalTilstandForventetTilstandEndelig(response.getJournalTilstand())
                        .log(LOG);
                dokumentRepository.oppdaterForseldelseMedArkivId(forsendelseId, w.getArkivId(),
                        ForsendelseStatus.GOSYS);
                return w.nesteSteg(OpprettGSakOppgaveTask.TASKNAME);
            }
        } else {
            String innhold = w.getDokumentTypeId().map(DokumentTypeId::getTermNavn).orElse("Ukjent innhold");
            try {
                if (!journalføring.tilJournalføring(w.getArkivId(), w.getSaksnummer().get(),
                        w.getAktørId().get(), enhetsId, innhold)) {
                    return w.nesteSteg(OpprettGSakOppgaveTask.TASKNAME);
                }
            } catch (IntegrasjonException e) {
                if (JOURNALMANGLER_EXCEPTION_KODE.equals(e.getFeil().getKode())) {
                    String logMessage = e.getFeil().getKode() + " " + e.getFeil().getFeilmelding();
                    LOG.info(logMessage);
                    return w.nesteSteg(OpprettGSakOppgaveTask.TASKNAME);
                } else {
                    throw e;
                }
            }
        }
        Optional<UUID> forsendelseId = w.getForsendelseId();
        if (forsendelseId.isPresent()) {
            dokumentRepository.oppdaterForsendelseMetadata(forsendelseId.get(), w.getArkivId(),
                    w.getSaksnummer().get(), ForsendelseStatus.PENDING);
            var hendelse = new SøknadFordeltOgJournalførtHendelse(w.getArkivId(), forsendelseId, fnr,
                    w.getSaksnummer());
            hendelseProdusent.send(hendelse, "TBD");
        }
        return w.nesteSteg(KlargjorForVLTask.TASKNAME);
    }
}
