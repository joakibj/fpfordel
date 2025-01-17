package no.nav.foreldrepenger.mottak.hendelse;

import java.time.Duration;
import java.time.LocalDateTime;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.control.ActivateRequestContext;
import javax.inject.Inject;
import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import no.nav.foreldrepenger.fordel.kodeverdi.BehandlingTema;
import no.nav.foreldrepenger.fordel.kodeverdi.MottakKanal;
import no.nav.foreldrepenger.fordel.kodeverdi.Tema;
import no.nav.foreldrepenger.mottak.domene.dokument.DokumentRepository;
import no.nav.foreldrepenger.mottak.felles.MottakMeldingDataWrapper;
import no.nav.foreldrepenger.mottak.task.joark.HentDataFraJoarkTask;
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskTjeneste;
import no.nav.vedtak.log.mdc.MDCOperations;

/*
 * Dokumentasjon https://confluence.adeo.no/pages/viewpage.action?pageId=315215917
 */

@Transactional
@ActivateRequestContext
@ApplicationScoped
public class JournalføringHendelseHåndterer {

    private static final Logger LOG = LoggerFactory.getLogger(JournalføringHendelseHåndterer.class);
    private static final String HENDELSE_ENDRET = "TemaEndret";
    private static final String EESSI = MottakKanal.EESSI.getKode();

    private ProsessTaskTjeneste taskTjeneste;
    private DokumentRepository dokumentRepository;

    JournalføringHendelseHåndterer() {
        // CDI
    }

    @Inject
    public JournalføringHendelseHåndterer(ProsessTaskTjeneste taskTjeneste, DokumentRepository dokumentRepository) {
        this.taskTjeneste = taskTjeneste;
        this.dokumentRepository = dokumentRepository;
    }

    private static void setCallIdForHendelse(JournalfoeringHendelseRecord payload) {
        var hendelsesId = payload.getHendelsesId();
        if (hendelsesId == null || hendelsesId.isEmpty() || hendelsesId.isBlank()) {
            MDCOperations.putCallId();
        } else {
            MDCOperations.putCallId(hendelsesId);
        }
    }

    void handleMessage(JournalfoeringHendelseRecord payload) {
        setCallIdForHendelse(payload);

        var arkivId = String.valueOf(payload.getJournalpostId());
        var hendelseType = payload.getHendelsesType();
        var mottaksKanal = payload.getMottaksKanal();
        var eksternReferanseId =
            (payload.getKanalReferanseId() == null) || payload.getKanalReferanseId().isEmpty() ? null : payload.getKanalReferanseId();

        // De uten kanalreferanse er "klonet" av SBH og journalført fra Gosys.
        // Normalt blir de journalført, men det feiler av og til pga tilgang.
        // Håndterer disse journalpostene senere (18h) i tilfelle SBH skal ha klart å ordne ting selv
        var delay = eksternReferanseId == null && !mottaksKanal.equals(MottakKanal.SELVBETJENING.getKode()) ? Duration.ofHours(2) : Duration.ZERO;

        if (HENDELSE_ENDRET.equalsIgnoreCase(payload.getHendelsesType())) {
            // Hendelsen kan komme før arkivet er oppdatert .....
            delay = Duration.ofSeconds(30);
            var gammeltTema = payload.getTemaGammelt() != null ? payload.getTemaGammelt() : null;
            LOG.info("FPFORDEL Tema Endret fra {} journalpost {} kanal {} referanse {}", gammeltTema, arkivId, mottaksKanal, eksternReferanseId);
        }

        // EESSI har egen mottaksprosess m/BEH_SED-oppgaver.
        if (EESSI.equals(mottaksKanal)) {
            LOG.info("FPFORDEL Mottatt Journalføringhendelse ignorerer journalpost {} kanal {}", arkivId, mottaksKanal);
            return;
        }

        LOG.info("FPFORDEL Mottatt Journalføringhendelse type {} journalpost {} referanse {}", hendelseType, arkivId, eksternReferanseId);

        // All journalføring av innsendinger fra SB gir en Midlertidig-hendelse. De skal
        // vi ikke reagere på før evt full refaktorering
        if (eksternReferanseId != null && dokumentRepository.erLokalForsendelse(eksternReferanseId)) {
            LOG.info("FPFORDEL Mottatt Hendelse egen journalføring callid {}", arkivId);
            return;
        }
        if (!dokumentRepository.hentJournalposter(arkivId).isEmpty()) {
            LOG.info("FPFORDEL Mottatt Hendelse egen journalføring journalpost {}", arkivId);
            return;
        }

        lagreJoarkTask(payload, arkivId, eksternReferanseId, delay);
    }

    private void lagreJoarkTask(JournalfoeringHendelseRecord payload, String arkivId, String eksternReferanse, Duration delay) {
        var taskdata = ProsessTaskData.forProsessTask(HentDataFraJoarkTask.class);
        taskdata.setCallIdFraEksisterende();
        MottakMeldingDataWrapper melding = new MottakMeldingDataWrapper(taskdata);
        melding.setArkivId(arkivId);
        melding.setTema(Tema.fraOffisiellKode(payload.getTemaNytt()));
        melding.setBehandlingTema(BehandlingTema.fraOffisiellKode(payload.getBehandlingstema()));
        if (eksternReferanse != null) {
            melding.setEksternReferanseId(eksternReferanse);
        }
        var oppdatertTaskdata = melding.getProsessTaskData();
        oppdatertTaskdata.setNesteKjøringEtter(LocalDateTime.now().plus(delay));
        taskTjeneste.lagre(oppdatertTaskdata);
    }
}
