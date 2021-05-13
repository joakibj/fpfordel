package no.nav.foreldrepenger.mottak.person;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;

import com.github.benmanes.caffeine.cache.LoadingCache;

import no.nav.vedtak.felles.integrasjon.pdl.Pdl;
import no.nav.vedtak.felles.integrasjon.rest.jersey.Jersey;

@Dependent
public class PersonTjeneste extends AbstractPersonTjeneste {

    @Inject
    public PersonTjeneste(@Jersey Pdl pdl) {
        this(pdl, cache(tilFnr(pdl)), cache(tilAktørId(pdl)));
    }

    private PersonTjeneste(Pdl pdl, LoadingCache<String, String> tilFnr, LoadingCache<String, String> tilAktør) {
        super(pdl, tilFnr, tilAktør);
    }

}
