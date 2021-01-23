package no.nav.foreldrepenger.mottak.person;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;

import no.nav.pdl.Adressebeskyttelse;
import no.nav.pdl.AdressebeskyttelseGradering;
import no.nav.pdl.GeografiskTilknytning;
import no.nav.pdl.GtType;
import no.nav.pdl.Navn;
import no.nav.pdl.Person;
import no.nav.vedtak.felles.integrasjon.pdl.Pdl;

@ExtendWith(MockitoExtension.class)
public class PersonTjenesteTest {
    private static final String AKTØR_ID = "9999999999999";
    private static final String FNR = "99999999999";

    private static final Logger LOG = LoggerFactory.getLogger(PersonTjenesteTest.class);
    private PersonInformasjon personTjeneste;
    @Mock
    private Pdl pdl;
    private Cache<String, String> cache;

    @BeforeEach
    public void setup() {
        cache = cache(100, 10, TimeUnit.SECONDS);
        personTjeneste = new PersonTjeneste(pdl, cache);
    }

    @Test
    public void skal_returnere_fnr() {
        when(pdl.hentPersonIdentForAktørId(eq(AKTØR_ID))).thenReturn(Optional.of(FNR));
        var fnr = personTjeneste.hentPersonIdentForAktørId(AKTØR_ID);
        assertEquals(Optional.of(FNR), fnr);
        personTjeneste.hentPersonIdentForAktørId(AKTØR_ID);
        cache.invalidate(AKTØR_ID);
        personTjeneste.hentPersonIdentForAktørId(AKTØR_ID);
        verify(pdl, times(2)).hentPersonIdentForAktørId(eq(AKTØR_ID));
    }

    @Test
    public void skal_returnere_aktørid() {
        when(pdl.hentAktørIdForPersonIdent(eq(FNR))).thenReturn(Optional.of(AKTØR_ID));
        var aid = personTjeneste.hentAktørIdForPersonIdent(FNR);
        assertEquals(Optional.of(AKTØR_ID), aid);
        personTjeneste.hentAktørIdForPersonIdent(FNR);
        cache.invalidate(FNR);
        personTjeneste.hentAktørIdForPersonIdent(FNR);
        verify(pdl, times(2)).hentAktørIdForPersonIdent(eq(FNR));
    }

    @Test
    public void skal_returnere_empty_uten_match() {
        cache.invalidateAll();
        when(pdl.hentPersonIdentForAktørId(eq(AKTØR_ID))).thenReturn(Optional.empty());
        assertThat(personTjeneste.hentPersonIdentForAktørId(AKTØR_ID)).isEmpty();
    }

    @Test
    public void skal_returnere_forkortet_navn() {
        var responsenavn = new Navn("Ola", null, "Nordmann", "Nordmann Ola", null, null, null, null);
        var response = new Person();
        response.setNavn(List.of(responsenavn));
        when(pdl.hentPerson(argThat(a -> a.getInput().get("ident").equals(AKTØR_ID)), any())).thenReturn(response);
        String navn = personTjeneste.hentNavn(AKTØR_ID);
        assertThat(navn).isEqualTo("Nordmann Ola");
    }

    @Test
    public void skal_returnere_konstruert_navn() {
        var responsenavn = new Navn("Kari", "Mari", "Nordmann", null, null, null, null, null);
        var response = new Person();
        response.setNavn(List.of(responsenavn));
        when(pdl.hentPerson(argThat(a -> a.getInput().get("ident").equals(AKTØR_ID)), any())).thenReturn(response);
        String navn = personTjeneste.hentNavn(AKTØR_ID);
        assertThat(navn).isEqualTo("Nordmann Kari Mari");
    }

    @Test
    public void skal_returnere_tom_gt_hvisikkesatt() {
        var responsebeskyttelse = new Adressebeskyttelse(AdressebeskyttelseGradering.UGRADERT, null, null);
        var responsegt = new GeografiskTilknytning(null, null, null, null, null);
        var response = new Person();
        response.setAdressebeskyttelse(List.of(responsebeskyttelse));
        when(pdl.hentPerson(argThat(a -> a.getInput().get("ident").equals(AKTØR_ID)), any())).thenReturn(response);
        when(pdl.hentGT(argThat(a -> a.getInput().get("ident").equals(AKTØR_ID)), any())).thenReturn(responsegt);
        GeoTilknytning gt = personTjeneste.hentGeografiskTilknytning(AKTØR_ID);
        assertThat(gt.getTilknytning()).isNull();
        assertThat(gt.getDiskresjonskode()).isNull();
    }

    @Test
    public void skal_returnere_gt_bydel() {
        var responsebeskyttelse = new Adressebeskyttelse(AdressebeskyttelseGradering.FORTROLIG, null, null);
        var responsegt = new GeografiskTilknytning(GtType.BYDEL, "Oslo", "030110", "NOR", null);
        var response = new Person();
        response.setAdressebeskyttelse(List.of(responsebeskyttelse));
        when(pdl.hentPerson(argThat(a -> a.getInput().get("ident").equals(AKTØR_ID)), any())).thenReturn(response);
        when(pdl.hentGT(argThat(a -> a.getInput().get("ident").equals(AKTØR_ID)), any())).thenReturn(responsegt);
        GeoTilknytning gt = personTjeneste.hentGeografiskTilknytning(AKTØR_ID);
        assertThat(gt.getTilknytning()).isEqualTo("030110");
        assertThat(gt.getDiskresjonskode()).isEqualTo("SPFO");
    }

    @Test
    public void skal_returnere_gt_land() {
        var responsebeskyttelse = new Adressebeskyttelse(AdressebeskyttelseGradering.STRENGT_FORTROLIG, null, null);
        var responsegt = new GeografiskTilknytning(GtType.UTLAND, null, null, "POL", null);
        var response = new Person();
        response.setAdressebeskyttelse(List.of(responsebeskyttelse));
        when(pdl.hentPerson(argThat(a -> a.getInput().get("ident").equals(AKTØR_ID)), any())).thenReturn(response);
        when(pdl.hentGT(argThat(a -> a.getInput().get("ident").equals(AKTØR_ID)), any())).thenReturn(responsegt);

        GeoTilknytning gt = personTjeneste.hentGeografiskTilknytning(AKTØR_ID);

        assertThat(gt.getTilknytning()).isEqualTo("POL");
        assertThat(gt.getDiskresjonskode()).isEqualTo("SPSF");
    }

    private static Cache<String, String> cache(int size, long timeout, TimeUnit unit) {
        return Caffeine.newBuilder()
                .expireAfterWrite(timeout, unit)
                .maximumSize(size)
                .removalListener(new RemovalListener<String, String>() {
                    @Override
                    public void onRemoval(String key, String value, RemovalCause cause) {
                        LOG.info("Fjerner {} for {} grunnet {}", value, key, cause);
                    }
                })
                .build();
    }
}
