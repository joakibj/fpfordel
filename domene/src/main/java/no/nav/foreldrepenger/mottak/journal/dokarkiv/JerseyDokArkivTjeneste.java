package no.nav.foreldrepenger.mottak.journal.dokarkiv;

import static javax.ws.rs.client.Entity.json;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static javax.ws.rs.core.UriBuilder.fromUri;

import java.net.URI;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.client.ClientRequestFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import no.nav.foreldrepenger.mottak.journal.dokarkiv.model.FerdigstillJournalpostRequest;
import no.nav.foreldrepenger.mottak.journal.dokarkiv.model.OppdaterJournalpostRequest;
import no.nav.foreldrepenger.mottak.journal.dokarkiv.model.OpprettJournalpostRequest;
import no.nav.foreldrepenger.mottak.journal.dokarkiv.model.OpprettJournalpostResponse;
import no.nav.vedtak.exception.TekniskException;
import no.nav.vedtak.felles.integrasjon.rest.jersey.AbstractJerseyOidcRestClient;
import no.nav.vedtak.konfig.KonfigVerdi;

@ApplicationScoped
public class JerseyDokArkivTjeneste extends AbstractJerseyOidcRestClient implements DokArkiv {

    private static final String FERDIGSTILL = "forsoekFerdigstill";
    private static final String DEFAULT_URI = "http://dokarkiv.default/rest/journalpostapi/v1/journalpost";
    private static final String OPPDATER_PATH = "/{journalpostId}";
    private static final String FERDIGSTILL_PATH = OPPDATER_PATH + "/ferdigstill";
    private static final Logger LOG = LoggerFactory.getLogger(JerseyDokArkivTjeneste.class);

    private URI endpoint;

    JerseyDokArkivTjeneste() {
    }

    @Inject
    public JerseyDokArkivTjeneste(@KonfigVerdi(value = "dokarkiv.base.url", defaultVerdi = DEFAULT_URI) URI endpoint) {
        this(endpoint, new ClientRequestFilter[0]);
    }

    JerseyDokArkivTjeneste(String base, ClientRequestFilter... filters) {
        this(URI.create(base), filters);
    }

    public JerseyDokArkivTjeneste(URI endpoint, ClientRequestFilter... filters) {
        super(filters);
        this.endpoint = endpoint;
    }

    @Override
    public OpprettJournalpostResponse opprettJournalpost(OpprettJournalpostRequest req, boolean ferdigstill) {
        try {
            LOG.info("Oppretter journalpost");
            var res = client.target(endpoint)
                    .queryParam(FERDIGSTILL, ferdigstill)
                    .request(APPLICATION_JSON_TYPE)
                    .buildPost(json(req))
                    .invoke(OpprettJournalpostResponse.class);
            LOG.info("Opprettet journalpost {} OK", res.journalpostId());
            return res;
        } catch (Exception e) {
            throw new TekniskException("F-999999", endpoint, e);
        }
    }

    @Override
    public boolean oppdaterJournalpost(String journalpostId, OppdaterJournalpostRequest req) {
        try {
            LOG.info("Oppdaterer journalpost {}", journalpostId);
            client.target(endpoint)
                    .path(OPPDATER_PATH)
                    .resolveTemplate("journalpostId", journalpostId)
                    .request(APPLICATION_JSON_TYPE)
                    .buildPut(json(req))
                    .invoke(Void.class);
            LOG.info("Oppdatert journalpost {} OK", journalpostId);
            return true;
        } catch (Exception e) {
            LOG.warn("Oppdatering journalpost {} feilet", journalpostId, e);
            return false;
        }
    }

    @Override
    public boolean ferdigstillJournalpost(String journalpostId, String enhet) {
        try {
            LOG.info("Ferdigstiller journalpost {}", journalpostId);
            patch(fromUri(endpoint).path(FERDIGSTILL_PATH).build(journalpostId), new FerdigstillJournalpostRequest(enhet));
            LOG.info("Ferdigstillt journalpost OK");
            return true;
        } catch (Exception e) {
            LOG.warn("Ferdigstilling journalpost {} feilet for {}", journalpostId, enhet, e);
            return false;
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [endpoint=" + endpoint + "]";
    }
}