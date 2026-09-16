package kreyj.konfplan.persistence;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Nutzer mit rein lesendem Zugriff: sieht die Teilnehmer der ihm zugewiesenen
 * {@link #gruppenwerte}, deren Verfügbarkeiten, Prioritäten und Buchungen - hat aber keine
 * Schreibrechte. Anders als bei {@link Teilnehmer#addGruppenwert} gibt es hier keine
 * Einwertigkeits-Regel: ein Betrachter darf auch innerhalb derselben (nicht mehrwertigen)
 * Kategorie mehrere Werte gleichzeitig zugewiesen bekommen (z.B. zwei Klassen).
 */
@Entity
@DiscriminatorValue("BETRACHTER")
public class Betrachter extends Nutzer {

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "betrachter_gruppenwert",
        joinColumns = @JoinColumn(name = "betrachter_id"),
        inverseJoinColumns = @JoinColumn(name = "gruppenkategoriewert_id")
    )
    private Set<GruppenkategorieWert> gruppenwerte = new HashSet<>();


    public Betrachter() {
        this.setRole("BETRACHTER");
    }


    public Set<GruppenkategorieWert> getGruppenwerte() {
        return Collections.unmodifiableSet(gruppenwerte);
    }


    public void addGruppenwert(GruppenkategorieWert wert) {
        if (null == wert) {
            return;
        }
        gruppenwerte.add(wert);
        wert.nimmBetrachterAuf(this);
    }


    public void removeGruppenwert(GruppenkategorieWert wert) {
        if (null == wert) {
            return;
        }
        if (gruppenwerte.remove(wert)) {
            wert.entferneBetrachter(this);
        }
    }
}
