package no.nav.foreldrepenger.mottak.infotrygd;

import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import no.nav.foreldrepenger.fordel.kodeverdi.BehandlingTema;

@ApplicationScoped
public class RelevantSakSjekker {

    private InfotrygdTjeneste fp;

    RelevantSakSjekker() {

    }

    @Inject
    public RelevantSakSjekker(InfotrygdTjeneste fp) {
        this.fp = fp;
    }

    public boolean skalMidlertidigJournalføre(String fnr, LocalDate fom, BehandlingTema behandlingTema) {
        return erITSakRelevant(fnr, fom, behandlingTema);
    }

    public boolean skalMidlertidigJournalføreIM(String fnr, LocalDate fom, BehandlingTema behandlingTema) {
        return erITSakRelevantForIM(fnr, fom, behandlingTema);
    }

    private boolean erITSakRelevant(String fnr, LocalDate fom, BehandlingTema tema) {

        if (BehandlingTema.gjelderForeldrepenger(tema)) {
            return erITSakRelevantForFP(fnr, fom);
        }
        return false;
    }

    private boolean erITSakRelevantForIM(String fnr, LocalDate fom, BehandlingTema tema) {
        if (BehandlingTema.gjelderForeldrepenger(tema)) {
            return erFpRelevantForIM(fnr, fom);
        }
        return false;
    }

    private boolean erITSakRelevantForFP(String fnr, LocalDate fom) {
        return restSaker(fp, fnr, fom)
                .anyMatch(svpFpRelevantTidFilter(fom));
    }

    private boolean erFpRelevantForIM(String fnr, LocalDate fom) {
        return restSaker(fp, fnr, fom)
                .map(InfotrygdSak::getIverksatt)
                .flatMap(Optional::stream)
                .anyMatch(fom::isBefore);
    }

    private static Stream<InfotrygdSak> restSaker(InfotrygdTjeneste t, String fnr, LocalDate fom) {
        return t.finnSakListe(fnr, fom).stream();
    }

    private static Predicate<? super InfotrygdSak> svpFpRelevantTidFilter(LocalDate fom) {
        // Intensjon med FALSE for å unngå treff pga praksis i enheter med
        // informasjonssaker
        return sak -> sak.getIverksatt().map(fom::isBefore).orElse(false)
                || ((sak.getRegistrert() != null) && fom.isBefore(sak.getRegistrert()));
    }

}