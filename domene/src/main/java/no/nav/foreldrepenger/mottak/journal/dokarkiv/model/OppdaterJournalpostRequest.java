package no.nav.foreldrepenger.mottak.journal.dokarkiv.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import no.nav.foreldrepenger.fordel.kodeverdi.Fagsystem;
import no.nav.foreldrepenger.mottak.journal.saf.model.BrukerIdType;

public class OppdaterJournalpostRequest {

    private String tittel;
    private String tema;
    private String behandlingstema;
    private Sak sak;
    private Bruker bruker;
    private AvsenderMottaker avsenderMottaker;
    private List<DokumentInfoOppdater> dokumenter;

    @JsonCreator
    public OppdaterJournalpostRequest(@JsonProperty("tittel") String tittel,
            @JsonProperty("tema") String tema,
            @JsonProperty("behandlingstema") String behandlingstema,
            @JsonProperty("bruker") Bruker bruker,
            @JsonProperty("avsenderMottaker") AvsenderMottaker avsenderMottaker,
            @JsonProperty("sak") Sak sak,
            @JsonProperty("dokumenter") List<DokumentInfoOppdater> dokumenter) {
        this.tittel = tittel;
        this.tema = tema;
        this.behandlingstema = behandlingstema;
        this.sak = sak;
        this.bruker = bruker;
        this.avsenderMottaker = avsenderMottaker;
        this.dokumenter = dokumenter;
    }

    private OppdaterJournalpostRequest() {

    }

    public String getTittel() {
        return tittel;
    }

    public String getTema() {
        return tema;
    }

    public String getBehandlingstema() {
        return behandlingstema;
    }

    public Sak getSak() {
        return sak;
    }

    public Bruker getBruker() {
        return bruker;
    }

    public AvsenderMottaker getAvsenderMottaker() {
        return avsenderMottaker;
    }

    public List<DokumentInfoOppdater> getDokumenter() {
        return dokumenter;
    }

    public static Builder ny() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "OppdaterJournalpostRequest{" +
                "tittel='" + tittel + '\'' +
                ", tema='" + tema + '\'' +
                ", behandlingstema='" + behandlingstema + '\'' +
                ", sak=" + sak +
                ", bruker=" + bruker +
                ", avsenderMottaker=" + avsenderMottaker +
                ", dokumenter=" + dokumenter +
                '}';
    }

    public static class Builder {
        private OppdaterJournalpostRequest request;
        private boolean harVerdier = false;

        Builder() {
            request = new OppdaterJournalpostRequest();
        }

        public Builder medTittel(String tittel) {
            this.request.tittel = tittel;
            this.harVerdier = true;
            return this;
        }

        public Builder medTema(String tema) {
            this.request.tema = tema;
            this.harVerdier = true;
            return this;
        }

        public Builder medBehandlingstema(String behandlingstema) {
            this.request.behandlingstema = behandlingstema;
            this.harVerdier = true;
            return this;
        }

        public Builder medBruker(String aktørId) {
            this.request.bruker = new Bruker(aktørId, BrukerIdType.AKTOERID);
            this.harVerdier = true;
            return this;
        }

        public Builder medAvsender(String fnr, String navn) {
            this.request.avsenderMottaker = new AvsenderMottaker(fnr, AvsenderMottakerIdType.FNR, navn);
            this.harVerdier = true;
            return this;
        }

        public Builder medSak(String fagsakId) {
            this.request.sak = new Sak(fagsakId, Fagsystem.FPSAK.getKode(), "FAGSAK", null, null);
            this.harVerdier = true;
            return this;
        }

        public Builder medArkivSak(String arkivSakID) {
            this.request.sak = new Sak(null, null, "ARKIVSAK", arkivSakID, "GSAK");
            this.harVerdier = true;
            return this;
        }

        public Builder leggTilDokument(DokumentInfoOppdater dokument) {
            if (this.request.dokumenter == null) {
                this.request.dokumenter = new ArrayList<>();
            }
            this.request.dokumenter.add(dokument);
            this.harVerdier = true;
            return this;
        }

        public boolean harVerdier() {
            return this.harVerdier;
        }

        public OppdaterJournalpostRequest build() {
            return this.request;
        }
    }
}
