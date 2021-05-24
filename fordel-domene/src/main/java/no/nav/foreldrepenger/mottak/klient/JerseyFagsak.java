package no.nav.foreldrepenger.mottak.klient;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static javax.ws.rs.client.Entity.json;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;

import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import no.nav.foreldrepenger.fordel.kodeverdi.BehandlingTema;
import no.nav.foreldrepenger.fordel.kodeverdi.DokumentKategori;
import no.nav.foreldrepenger.fordel.kodeverdi.DokumentTypeId;
import no.nav.foreldrepenger.konfig.Environment;
import no.nav.foreldrepenger.konfig.KonfigVerdi;
import no.nav.foreldrepenger.kontrakter.fordel.BehandlendeFagsystemDto;
import no.nav.foreldrepenger.kontrakter.fordel.FagsakInfomasjonDto;
import no.nav.foreldrepenger.kontrakter.fordel.JournalpostKnyttningDto;
import no.nav.foreldrepenger.kontrakter.fordel.OpprettSakDto;
import no.nav.foreldrepenger.kontrakter.fordel.SaksnummerDto;
import no.nav.foreldrepenger.kontrakter.fordel.VurderFagsystemDto;
import no.nav.foreldrepenger.mottak.felles.MottakMeldingDataWrapper;
import no.nav.vedtak.exception.IntegrasjonException;
import no.nav.vedtak.felles.integrasjon.rest.jersey.AbstractJerseyOidcRestClient;

@Dependent
public class JerseyFagsak extends AbstractJerseyOidcRestClient implements Fagsak {
    private static final int DEFAULT_TIMEOUT = 60;
    private static final String TIMEOUT_KEY = "fordel.fagsak.timeout";
    private static final Environment ENV = Environment.current();
    private static final String DEFAULT_FPSAK_BASE_URI = "http://fpsak";
    private static final String JOURNALPOSTTILKNYTNING_PATH = "/fpsak/api/fordel/fagsak/knyttJournalpost";
    private static final String FAGSAKINFORMASJON_PATH = "/fpsak/api/fordel/fagsak/informasjon";
    private static final String FAGSAK_OPPRETT_PATH = "/fpsak/api/fordel/fagsak/opprett";
    private static final String VURDER_FAGSYSTEM_PATH = "/fpsak/api/fordel/vurderFagsystem";

    private static final Logger LOG = LoggerFactory.getLogger(JerseyFagsak.class);

    private final URI endpoint;

    @Inject
    public JerseyFagsak(@KonfigVerdi(value = "fpsak.base.url", defaultVerdi = DEFAULT_FPSAK_BASE_URI) URI endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public Optional<FagsakInfomasjonDto> finnFagsakInfomasjon(SaksnummerDto saksnummerDto) {
        LOG.info("Finner fagsakinformasjon");
        var info = client.target(endpoint)
                .path(FAGSAKINFORMASJON_PATH)
                .request(APPLICATION_JSON_TYPE)
                .buildPost(json(saksnummerDto))
                .invoke(FagsakInfomasjonDto.class);
        LOG.info("Fant fagsakinformasjon OK");
        return Optional.ofNullable(info);
    }

    @Override
    public SaksnummerDto opprettSak(OpprettSakDto opprettSakDto) {
        LOG.info("Oppretter sak");
        var sak = client.target(endpoint)
                .path(FAGSAK_OPPRETT_PATH)
                .request(APPLICATION_JSON_TYPE)
                .buildPost(json(opprettSakDto))
                .invoke(SaksnummerDto.class);
        LOG.info("Opprettet sak OK");
        return sak;
    }

    @Override
    public void knyttSakOgJournalpost(JournalpostKnyttningDto dto) {
        LOG.info("Knytter sak og journalpost");
        var f = asyncClient
                .target(endpoint)
                .path(JOURNALPOSTTILKNYTNING_PATH)
                .request(APPLICATION_JSON_TYPE)
                .async()
                .post(json(dto));
        try {
            var timeout = ENV.getProperty(TIMEOUT_KEY, int.class, DEFAULT_TIMEOUT);
            LOG.info("Venter max {}s på svar for knytting sak og journalpost", timeout);
            var timer = new StopWatch();
            timer.start();
            f.get(timeout, SECONDS);
            timer.stop();
            LOG.info("Knyttet sak og journalpost OK etter {}ms", timer.getTime(MILLISECONDS));
        } catch (TimeoutException e) {
            LOG.warn("Timeout ved knytting sak og journalpost", e);
            throw new IntegrasjonException("F-999999", "Timeout", e);
        } catch (Exception e) {
            LOG.warn("Feil ved knytting sak og journalpost", e);
            throw new IntegrasjonException("F-42", "OOPS", e);
        }
    }

    @Override
    public VurderFagsystemResultat vurderFagsystem(MottakMeldingDataWrapper w) {
        String aktørId = w.getAktørId().get();
        boolean strukturertSøknad = w.erStrukturertDokument().orElse(Boolean.FALSE);
        var dokumentTypeId = w.getDokumentTypeId().orElse(DokumentTypeId.UDEFINERT);
        var dokumentKategori = w.getDokumentKategori().orElse(DokumentKategori.UDEFINERT);
        String behandlingTemaString = BehandlingTema.UDEFINERT.equals(w.getBehandlingTema())
                ? w.getBehandlingTema().getKode()
                : w.getBehandlingTema().getOffisiellKode();

        var dto = new VurderFagsystemDto(w.getArkivId(), strukturertSøknad, aktørId,
                behandlingTemaString);
        dto.setAdopsjonsBarnFodselsdatoer(w.getAdopsjonsbarnFodselsdatoer());
        w.getBarnTermindato().ifPresent(dto::setBarnTermindato);
        w.getBarnFodselsdato().ifPresent(dto::setBarnFodselsdato);
        w.getOmsorgsovertakelsedato().ifPresent(dto::setOmsorgsovertakelsedato);
        w.getÅrsakTilInnsending().ifPresent(dto::setÅrsakInnsendingInntektsmelding);
        w.getAnnenPartId().ifPresent(dto::setAnnenPart);
        w.getSaksnummer().ifPresent(dto::setSaksnummer);
        w.getVirksomhetsnummer().ifPresent(dto::setVirksomhetsnummer);
        w.getArbeidsgiverAktørId().ifPresent(dto::setArbeidsgiverAktørId);
        w.getArbeidsforholdsid().ifPresent(dto::setArbeidsforholdsid);
        w.getInntektsmeldingStartDato().ifPresent(dto::setStartDatoForeldrepengerInntektsmelding);
        w.getForsendelseMottattTidspunkt().ifPresent(dto::setForsendelseMottattTidspunkt);
        dto.setForsendelseMottatt(w.getForsendelseMottatt());
        dto.setDokumentTypeIdOffisiellKode(dokumentTypeId.getOffisiellKode());
        dto.setDokumentKategoriOffisiellKode(dokumentKategori.getOffisiellKode());

        // VurderFagsystemDto burde hatt et felt for første uttaksdag for søknad. For å
        // ikke kaste
        // mottatt søknad til manuell journalføring i fpsak, sender vi her første
        // uttaksdag i et
        // felt som brukes til det samme for inntektsmelding. Kontrakten bør endres
        if (DokumentKategori.SØKNAD.equals(w.getDokumentKategori().orElse(DokumentKategori.UDEFINERT))) {
            w.getFørsteUttaksdag().ifPresent(dto::setStartDatoForeldrepengerInntektsmelding);
        }
        LOG.info("Vurderer resultat");
        var res = client.target(endpoint)
                .path(VURDER_FAGSYSTEM_PATH)
                .request(APPLICATION_JSON_TYPE)
                .buildPost(json(dto))
                .invoke(BehandlendeFagsystemDto.class);
        LOG.info("Vurderert resultat OK");
        return new VurderFagsystemResultat(res);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [endpoint=" + endpoint + "]";
    }

}
