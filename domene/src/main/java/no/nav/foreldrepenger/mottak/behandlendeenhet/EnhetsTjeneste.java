package no.nav.foreldrepenger.mottak.behandlendeenhet;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import no.nav.foreldrepenger.fordel.kodeverdi.BehandlingTema;
import no.nav.foreldrepenger.fordel.kodeverdi.Tema;
import no.nav.foreldrepenger.mottak.person.GeoTilknytning;
import no.nav.foreldrepenger.mottak.person.PersonInformasjon;
import no.nav.vedtak.feil.Feil;
import no.nav.vedtak.feil.FeilFactory;
import no.nav.vedtak.feil.LogLevel;
import no.nav.vedtak.feil.deklarasjon.DeklarerteFeil;
import no.nav.vedtak.feil.deklarasjon.ManglerTilgangFeil;
import no.nav.vedtak.feil.deklarasjon.TekniskFeil;
import no.nav.vedtak.felles.integrasjon.arbeidsfordeling.rest.Arbeidsfordeling;
import no.nav.vedtak.felles.integrasjon.arbeidsfordeling.rest.ArbeidsfordelingRequest;
import no.nav.vedtak.felles.integrasjon.arbeidsfordeling.rest.ArbeidsfordelingResponse;
import no.nav.vedtak.felles.integrasjon.rest.jersey.Jersey;

@ApplicationScoped
public class EnhetsTjeneste implements EnhetsInfo {
    private static final Logger LOG = LoggerFactory.getLogger(EnhetsTjeneste.class);
    private PersonInformasjon personTjeneste;
    private Arbeidsfordeling norgKlient;

    private List<String> alleJournalførendeEnheter = new ArrayList<>(); // Med klageinstans og kode6
    private List<String> nfpJournalførendeEnheter = new ArrayList<>(); // Kun NFP
    private LocalDate sisteInnhenting = LocalDate.MIN;

    public EnhetsTjeneste() {
    }

    @Inject
    public EnhetsTjeneste(
            PersonInformasjon personTjeneste,
            @Jersey Arbeidsfordeling norgKlient) {
        this.personTjeneste = personTjeneste;
        this.norgKlient = norgKlient;
    }

    @Override
    public String hentFordelingEnhetId(Tema tema, BehandlingTema behandlingTema, Optional<String> enhetInput, String aktørId) {
        LOG.info("Henter enhet id for {},{}", tema, behandlingTema);
        oppdaterEnhetCache();
        if (enhetInput.map(alleJournalførendeEnheter::contains).orElse(Boolean.FALSE)) {
            return enhetInput.get();
        }

        var id = Optional.ofNullable(aktørId)
                .map(a -> hentEnhetId(a, behandlingTema, tema))
                .orElseGet(this::tilfeldigNfpEnhet);
        LOG.info("returnerer enhet id  {}", id);
        return id;
    }

    private String hentEnhetId(String aktørId, BehandlingTema behandlingTema, Tema tema) {
        var gt = personTjeneste.hentPersonIdentForAktørId(aktørId)
                .map(this::hentGeografiskTilknytning)
                .orElse(new GeoTilknytning(null, null));

        if (gt.diskresjonskode() == null && gt.tilknytning() == null) {
            return tilfeldigNfpEnhet();
        }

        var request = ArbeidsfordelingRequest.ny()
                .medTemagruppe(TEMAGRUPPE)
                .medTema(tema.getOffisiellKode())
                .medBehandlingstema(behandlingTema.getOffisiellKode())
                .medBehandlingstype(BEHANDLINGTYPE)
                .medOppgavetype(OPPGAVETYPE_JFR)
                .medDiskresjonskode(gt.diskresjonskode())
                .medGeografiskOmraade(gt.tilknytning())
                .build();
        return validerOgVelgBehandlendeEnhet(norgKlient.finnEnhet(request), gt);
    }

    private static String validerOgVelgBehandlendeEnhet(List<ArbeidsfordelingResponse> response, GeoTilknytning gt) {
        // Vi forventer å få én behandlende enhet.
        if (response == null || response.size() != 1) {
            throw EnhetsTjeneste.EnhetsTjenesteFeil.FACTORY.finnerIkkeBehandlendeEnhet(gt.tilknytning(), gt.diskresjonskode())
                    .toException();
        }

        return response.get(0).getEnhetNr();
    }

    private String tilfeldigNfpEnhet() {
        return nfpJournalførendeEnheter.get(LocalDateTime.now().getSecond() % nfpJournalførendeEnheter.size());
    }

    private void oppdaterEnhetCache() {
        if (sisteInnhenting.isBefore(LocalDate.now())) {
            var request = ArbeidsfordelingRequest.ny()
                    .medTemagruppe(TEMAGRUPPE)
                    .medTema(TEMA)
                    .medBehandlingstype(BEHANDLINGTYPE)
                    .medOppgavetype(OPPGAVETYPE_JFR)
                    .build();
            var respons = norgKlient.hentAlleAktiveEnheter(request);
            alleJournalførendeEnheter.clear();
            nfpJournalførendeEnheter.clear();
            respons.stream().map(ArbeidsfordelingResponse::getEnhetNr).forEach(alleJournalførendeEnheter::add);
            respons.stream().filter(e -> ENHET_TYPE_NFP.equalsIgnoreCase(e.getEnhetType()))
                    .map(ArbeidsfordelingResponse::getEnhetNr).forEach(nfpJournalførendeEnheter::add);
            alleJournalførendeEnheter.add(NK_ENHET_ID);
            sisteInnhenting = LocalDate.now();
        }
    }

    private GeoTilknytning hentGeografiskTilknytning(String fnr) {
        return personTjeneste.hentGeografiskTilknytning(fnr);
    }

    private interface EnhetsTjenesteFeil extends DeklarerteFeil {

        EnhetsTjeneste.EnhetsTjenesteFeil FACTORY = FeilFactory.create(EnhetsTjeneste.EnhetsTjenesteFeil.class);

        @TekniskFeil(feilkode = "FP-669566", feilmelding = "Finner ikke behandlende enhet for geografisk tilknytning %s, diskresjonskode %s", logLevel = LogLevel.ERROR)
        Feil finnerIkkeBehandlendeEnhet(String geografiskTilknytning, String diskresjonskode);

        @TekniskFeil(feilkode = "FP-070668", feilmelding = "Person ikke funnet ved hentGeografiskTilknytning eller relasjoner", logLevel = LogLevel.ERROR)
        Feil enhetsTjenestePersonIkkeFunnet(Exception e);

        @ManglerTilgangFeil(feilkode = "FP-509290", feilmelding = "Mangler tilgang til å utføre hentGeografiskTilknytning eller hentrelasjoner", logLevel = LogLevel.ERROR)
        Feil enhetsTjenesteSikkerhetsbegrensing(Exception e);
    }
}
