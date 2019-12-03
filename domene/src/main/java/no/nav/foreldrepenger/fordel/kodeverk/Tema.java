package no.nav.foreldrepenger.fordel.kodeverk;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity(name = "Tema")
@DiscriminatorValue(Tema.DISCRIMINATOR)
public class Tema extends Kodeliste {

    public static final String DISCRIMINATOR = "TEMA";

    /**
     * Konstanter for å skrive ned kodeverdi. For å hente ut andre data konfigurert,
     * må disse leses fra databasen (eks. for å hente offisiell kode for et Nav
     * kodeverk).
     */
    public static final Tema FORELDRE_OG_SVANGERSKAPSPENGER = new Tema("FOR_SVA");
    public static final Tema OMS = new Tema("OMS");

    /**
     * Alle kodeverk må ha en verdi, det kan ikke være null i databasen. Denne koden
     * gjør samme nytten.
     */
    public static final Tema UDEFINERT = new Tema("-");

    Tema() {
    }

    private Tema(String kode) {
        super(kode, DISCRIMINATOR);
    }

}
