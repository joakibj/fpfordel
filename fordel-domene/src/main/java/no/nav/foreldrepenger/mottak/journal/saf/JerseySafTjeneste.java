package no.nav.foreldrepenger.mottak.journal.saf;

import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import no.nav.foreldrepenger.mottak.journal.saf.model.Journalpost;
import no.nav.foreldrepenger.mottak.journal.saf.model.VariantFormat;
import no.nav.saf.AvsenderMottakerResponseProjection;
import no.nav.saf.BrukerResponseProjection;
import no.nav.saf.DokumentInfoResponseProjection;
import no.nav.saf.DokumentvariantResponseProjection;
import no.nav.saf.JournalpostQueryRequest;
import no.nav.saf.JournalpostQueryResponse;
import no.nav.saf.JournalpostResponseProjection;
import no.nav.saf.LogiskVedleggResponseProjection;
import no.nav.saf.SakResponseProjection;
import no.nav.saf.Tilknytning;
import no.nav.saf.TilknyttedeJournalposterQueryRequest;
import no.nav.saf.TilknyttedeJournalposterQueryResponse;
import no.nav.vedtak.felles.integrasjon.rest.jersey.Jersey;
import no.nav.vedtak.felles.integrasjon.saf.HentDokumentQuery;
import no.nav.vedtak.felles.integrasjon.saf.Saf;

@ApplicationScoped
class JerseySafTjeneste implements SafTjeneste {

    private static final Logger LOG = LoggerFactory.getLogger(JerseySafTjeneste.class);

    private Saf saf;

    public JerseySafTjeneste() {
    }

    @Inject
    public JerseySafTjeneste(@Jersey Saf saf) {
        this.saf = saf;
    }

    @Override
    public Journalpost hentJournalpostInfo(String id) {
        var q = new JournalpostQueryRequest();
        q.setJournalpostId(id);
        var p = new JournalpostResponseProjection()
                .journalpostId()
                .journalposttype()
                .journalstatus()
                .datoOpprettet()
                .tittel()
                .kanal()
                .tema()
                .behandlingstema()
                .journalfoerendeEnhet()
                .eksternReferanseId()
                .bruker(new BrukerResponseProjection()
                        .id()
                        .type())
                .avsenderMottaker(new AvsenderMottakerResponseProjection()
                        .id()
                        .type()
                        .navn())
                .sak(new SakResponseProjection()
                        .arkivsaksnummer()
                        .fagsakId()
                        .fagsaksystem())
                .dokumenter(new DokumentInfoResponseProjection()
                        .dokumentInfoId()
                        .tittel()
                        .brevkode()
                        .logiskeVedlegg(new LogiskVedleggResponseProjection()
                                .tittel())
                        .dokumentvarianter(new DokumentvariantResponseProjection()
                                .variantformat()));
        LOG.info("Henter journalpost info");
        var res = SafMapper.map(saf.query(q, p, JournalpostQueryResponse.class).journalpost());
        LOG.info("Hentet journalpost info OK");
        return res;
    }

    @Override
    public String hentDokument(String journalpostId, String dokumentInfoId, VariantFormat variantFormat) {
        LOG.info("Henter dokument");
        var res = new String(saf.hentDokument(new HentDokumentQuery(journalpostId, dokumentInfoId, variantFormat.name())));
        LOG.info("Hentet dokument OK");
        return res;
    }

    @Override
    public List<Journalpost> hentEksternReferanseId(String dokumentInfoId) {
        var q = new TilknyttedeJournalposterQueryRequest();
        q.setDokumentInfoId(dokumentInfoId);
        q.setTilknytning(Tilknytning.GJENBRUK);
        var p = new JournalpostResponseProjection()
                .journalpostId()
                .eksternReferanseId();
        LOG.info("Henter ekstern id");
        var res = SafMapper.mapJP(saf.query(q, p, TilknyttedeJournalposterQueryResponse.class).tilknyttedeJournalposter());
        LOG.info("Hentet ekstern id OK");
        return res;
    }
}