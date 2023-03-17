package no.nav.foreldrepenger.fordel.web.app.rest.journalføring;

import static no.nav.foreldrepenger.fordel.web.app.rest.journalføring.ManuellJournalføringMapper.mapYtelseTypeFraDto;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import no.nav.foreldrepenger.fordel.web.app.exceptions.FeilDto;
import no.nav.foreldrepenger.fordel.web.server.abac.AppAbacAttributtType;
import no.nav.foreldrepenger.mottak.klient.YtelseTypeDto;
import no.nav.foreldrepenger.typer.AktørId;
import no.nav.foreldrepenger.typer.JournalpostId;
import no.nav.vedtak.exception.TekniskException;
import no.nav.vedtak.sikkerhet.abac.AbacDataAttributter;
import no.nav.vedtak.sikkerhet.abac.BeskyttetRessurs;
import no.nav.vedtak.sikkerhet.abac.TilpassetAbacAttributt;
import no.nav.vedtak.sikkerhet.abac.beskyttet.ActionType;
import no.nav.vedtak.sikkerhet.abac.beskyttet.ResourceType;

/**
 * Enkelt REST tjeneste for å oppdatere og ferdigstille journalføring på dokumenter som kunne ikke
 * journalføres automatisk på fpsak saker. Brukes for å klargjøre og sende over saken til videre behandling i VL.
 * Gir mulighet å opprette saken i fpsak og så journalføre dokumentet på den nye saken.
 */
@Path("/sak")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@Transactional
public class FerdigstillJournalføringRestTjeneste {
    private FerdigstillJournalføringTjeneste journalføringTjeneste;


    protected FerdigstillJournalføringRestTjeneste() {
        // CDI proxy
    }

    @Inject
    public FerdigstillJournalføringRestTjeneste(FerdigstillJournalføringTjeneste journalføringTjeneste) {
        this.journalføringTjeneste = journalføringTjeneste;
    }


    @POST
    @Path("/ferdigstill")
    @Operation(description = "For å ferdigstille journalføring. Det opprettes en ny fagsak om saksnummer ikke sendes.", tags = "Manuell journalføring", responses = {@ApiResponse(responseCode = "200", description = "Journalføring ferdigstillt"), @ApiResponse(responseCode = "500", description = "Feil i request", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FeilDto.class))),})
    @BeskyttetRessurs(actionType = ActionType.CREATE, resourceType = ResourceType.FAGSAK)
    public void oppdaterOgFerdigstillJournalfoering(@Parameter(description = "Trenger journalpostId, saksnummer og enhet til ferdigstille en journalføring. "
            + "Om saksnummer ikke foreligger må ytelse type og aktørId oppgis for å opprette en ny sak.") @NotNull @Valid
            @TilpassetAbacAttributt(supplierClass = AbacDataSupplier.class) FerdigstillJournalføringRestTjeneste.FerdigstillRequest request) {

        validerJournalpostId(request.journalpostId());
        validerEnhetId(request.enhetId());

        var opprettSak = Optional.ofNullable(request.opprettSak)
            .orElseThrow(() -> new TekniskException("FP-32354", "OpprettSakDto kan ikke være null ved opprettelse av en sak."));

        var journalpostId = new JournalpostId(request.journalpostId);
        var ytelseType = mapYtelseTypeFraDto(opprettSak.ytelseType());
        var aktørId = new AktørId(opprettSak.aktørId());
        var oppgaveId = request.oppgaveId();
        var saksnummer = request.saksnummer() != null ? request.saksnummer() : null;

        journalføringTjeneste.oppdaterJournalpostOgFerdigstill(request.enhetId, saksnummer, journalpostId, ytelseType, aktørId, oppgaveId.toString());
    }

    @POST
    @Path("/oppdaterJournalpostTittel")
    @Operation(description = "Opdaterer tittel på angitte dokumenter. Dersom valg av dokumentittel fører til ny journalposttittel returneres denne", tags = "Manuell journalføring", responses = {@ApiResponse(responseCode = "200", description = "Journalpost oppdatert"), @ApiResponse(responseCode = "500", description = "Feil i request", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FeilDto.class))),})
    @BeskyttetRessurs(actionType = ActionType.UPDATE, resourceType = ResourceType.FAGSAK)
    public JournalpostTittelDto oppdaterJournalpost(@Parameter(description = "Trenger journalpostId og en liste med dokumentId og tittel for de dokumentene som skal oppdateres.") @NotNull @Valid
                                                    @TilpassetAbacAttributt(supplierClass = AbacDataSupplier.class) OppdaterJournalpostMedTittelRequest journalpostRequest) {

        validerJournalpostId(journalpostRequest.journalpostId());
        var journalpostId = new JournalpostId(journalpostRequest.journalpostId());

        if (journalpostRequest.dokumenter().isEmpty() ) {
            throw new TekniskException("FpFordel: Ingen dokumenter å oppdatere for journalpostId {}",journalpostId.getVerdi() );
        }
        List<FerdigstillJournalføringTjeneste.DokumenterMedNyTittel> dokumenter = mapTilDokumenter(journalpostRequest.dokumenter());

        Optional<String> nyJournalpostTittel = journalføringTjeneste.utledJournalpostTittelOgOppdater(dokumenter, journalpostId);

        return new JournalpostTittelDto(nyJournalpostTittel.orElse(null));
    }

    private List<FerdigstillJournalføringTjeneste.DokumenterMedNyTittel> mapTilDokumenter(List<OppdaterJournalpostMedTittelRequest.OppdaterDokumentRequest> dokumenter) {
        return dokumenter.stream().map(d -> new FerdigstillJournalføringTjeneste.DokumenterMedNyTittel(d.dokumentIdDto().dokumentId(), d.tittel())).toList();
    }

    private static void validerEnhetId(String enhetId) {
        if (enhetId == null) {
            throw new TekniskException("FP-15679", lagUgyldigInputMelding("EnhetId", enhetId));
        }
    }

    private static void validerJournalpostId(String journalpostId) {
        if (erNullEllerTom(journalpostId)) {
            throw new TekniskException("FP-15688", lagUgyldigInputMelding("JournalpostId", journalpostId));
        }
    }

    private static boolean erNullEllerTom(String s) {
        return ((s == null) || s.isEmpty());
    }

    private static String lagUgyldigInputMelding(String feltnavn, String verdi) {
        return String.format("Ugyldig input: %s med verdi: %s er ugyldig input.", feltnavn, verdi);
    }
    public record JournalpostTittelDto(String journalpostTittel) {}

    public static class AbacDataSupplier implements Function<Object, AbacDataAttributter> {

        @Override
        public AbacDataAttributter apply(Object obj) {
            var req = (FerdigstillRequest) obj;
            var opprett = AbacDataAttributter.opprett();
            if (req.opprettSak() != null) {
                opprett.leggTil(AppAbacAttributtType.AKTØR_ID, req.opprettSak().aktørId());
            }
            return opprett;
        }
    }

    record OpprettSakDto(@NotNull YtelseTypeDto ytelseType,
                         @NotNull @Pattern(regexp = "^\\d{13}$", message = "aktørId ${validatedValue} har ikke gyldig verdi (pattern '{regexp}')") String aktørId) {
    }

    record FerdigstillRequest(
        @NotNull @Pattern(regexp = "^(-?[1-9]|[a-z0])[a-z0-9_:-]*$", message = "journalpostId ${validatedValue} har ikke gyldig verdi (pattern '{regexp}')") String journalpostId,
        @NotNull String enhetId,
        @Size(max = 11) @Pattern(regexp = "^[0-9_\\-]*$") String saksnummer,
        Long oppgaveId,
        @Valid OpprettSakDto opprettSak) {
    }
}
