package no.nav.foreldrepenger.mottak.tjeneste.dokumentforsendelse;

import no.nav.foreldrepenger.fordel.kodeverk.DokumentTypeId;

public class FilMetadata {
    private final String contentId;
    private final DokumentTypeId dokumentTypeId;

    public FilMetadata(String contentId, DokumentTypeId dokumentTypeId) {
        this.contentId = contentId;
        this.dokumentTypeId = dokumentTypeId;
    }

    public String getContentId() {
        return contentId;
    }

    public DokumentTypeId getDokumentTypeId() {
        return dokumentTypeId;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[contentId=" + contentId + ", dokumentTypeId=" + dokumentTypeId + "]";
    }
}