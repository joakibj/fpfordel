package no.nav.foreldrepenger.mottak.journal.dokarkiv.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DokumentInfoResponse(String dokumentInfoId) {

}
