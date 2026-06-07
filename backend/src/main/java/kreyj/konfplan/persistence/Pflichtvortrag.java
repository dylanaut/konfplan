package kreyj.konfplan.persistence;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreRemove;
import kreyj.konfplan.domain.exception.UpdateVortragException;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static kreyj.konfplan.persistence.NutzerVerfuegbarkeit.alleNutzerVerfuegbar;
import static kreyj.konfplan.persistence.NutzerVerfuegbarkeitId.nvId;
import static kreyj.konfplan.persistence.RaumVerfuegbarkeitId.rvId;

@Getter
@Entity
@NoArgsConstructor
@DiscriminatorValue("PFLICHT")
public class Pflichtvortrag extends Vortrag {
    private String pflichtgruppe;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Raum pflichtraum;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Slot pflichtslot;

    public Pflichtvortrag(String titel, Referent referent, Veranstaltung veranstaltung,
                          String pflichtgruppe, Raum pflichtraum, Slot pflichtslot) {
        this(titel, "", referent, veranstaltung, pflichtgruppe, pflichtraum, pflichtslot);
    }


    public Pflichtvortrag(String titel, String inhalt, Referent referent, Veranstaltung veranstaltung,
                          String pflichtgruppe, Raum pflichtraum, Slot pflichtslot) {
        super(titel, inhalt, referent, veranstaltung);

        Objects.requireNonNull(pflichtgruppe);
        Objects.requireNonNull(pflichtraum);
        Objects.requireNonNull(pflichtslot);

        this.pflichtgruppe = pflichtgruppe;
        this.pflichtraum = pflichtraum;
        this.pflichtslot = pflichtslot;
        // constructor requires invocation of #updateVerfuegbarkeitenAfterPersist() _> refactor with factory pattern
    }

    /**
     * Aktualisiert die "Pflichtgruppe" für einen Pflichvortrag und stellt sicher,
     * dass die Teilnehmerverfügbarkeit entsprechend angepasst wird.
     * Die Methode stellt zunächst die Verfügbarkeit für Teilnehmer der alten Gruppe wieder her,
     * validiert die Verfügbarkeit von Teilnehmern in der neuen Gruppe für den "Pflichtslot",
     * und aktualisiert dann die "Pflichtgruppe", während die Verfügbarkeit für die Teilnehmer der neuen Gruppe entfernt wird.
     *
     * @param neuePflichtgruppe Die Kennung der neuen obligatorischen Gruppe, die zugewiesen werden soll. Darf nicht null oder leer sein.
     * @throws UpdateVortragException Wenn die neue obligatorische Gruppe null oder leer ist
     *                                  oder wenn nicht alle Teilnehmer der neuen Gruppe
     *                                  für den obligatorischen Slot verfügbar sind.
     */

    public void updatePflichtgruppe(String neuePflichtgruppe) {
        if (null == neuePflichtgruppe || neuePflichtgruppe.isBlank()) {
            throw new UpdateVortragException("Pflichtgruppe darf nicht leer sein.");
        }

        if (Objects.equals(pflichtgruppe, neuePflichtgruppe) || null == veranstaltung) {
            return;
        }

        Long veranstaltungId = veranstaltung.getId();

        // Verfuegbarkeit für Teilnehmenden der neuen Gruppe prüfen
        List<Teilnehmer> neueGruppenTeilnehmer =
                Teilnehmer.getGruppenTeilnehmer(neuePflichtgruppe, veranstaltungId);

        if (!alleNutzerVerfuegbar(neueGruppenTeilnehmer, pflichtslot.getId(), veranstaltungId)) {
            throw new UpdateVortragException("Nicht alle Teilnehmer der neuen Gruppe '" + neuePflichtgruppe
                    + "' sind im Slot '" + pflichtslot.getDescription() + "' verfügbar.");
        }

        // Verfuegbarkeit für Teilnehmenden der alten Gruppe wiederherstellen
        if (StringUtils.isNotBlank(pflichtgruppe)) {
            List<Teilnehmer> alteGruppenTeilnehmer =
                    Teilnehmer.getGruppenTeilnehmer(pflichtgruppe, veranstaltungId);
            for (Teilnehmer teilnehmer : alteGruppenTeilnehmer) {
                NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvId(teilnehmer, veranstaltung));
                if (null != nv) {
                    nv.addSlot(pflichtslot);
                }
            }
        }

        pflichtgruppe = neuePflichtgruppe;

        // Verfuegbarkeit für neue Gruppe entfernen
        for (Teilnehmer teilnehmer : neueGruppenTeilnehmer) {
            NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvId(teilnehmer, veranstaltung));
            if (null != nv) {
                nv.removeSlot(pflichtslot);
            }
        }
    }

    public void updatePflichtraum(Raum neuerRaum) {
        if (Objects.equals(pflichtraum, neuerRaum) || getVeranstaltung() == null) {
            return;
        }

        // Restore availability for the old room
        if (pflichtraum != null) {
            // RaumVerfuegbarkeit für alten Raum laden
            RaumVerfuegbarkeit rvAlterRaum = RaumVerfuegbarkeit.findById(rvId(pflichtraum, veranstaltung));
            if (null != rvAlterRaum) {
                RaumVerfuegbarkeit rvNeuerRaum = RaumVerfuegbarkeit.findById(rvId(neuerRaum, veranstaltung));

                if (rvNeuerRaum == null) {
                    throw new UpdateVortragException("Keine RaumVerfuegbarkeit für neuen Raum '%s' in Slot '%s' (%s)"
                            .formatted(neuerRaum.getName(), pflichtslot.getDescription(), veranstaltung.getName()));
                } else {
                    if (rvNeuerRaum.getVerfuegbareSlotIds().contains(pflichtslot.getId())) {
                        rvNeuerRaum.removeSlot(pflichtslot);
                        rvAlterRaum.addSlot(pflichtslot); // wieder verfuegbar
                    } else {
                        throw new UpdateVortragException("Neuer Raum '%s' ist im Slot '%s' nicht verfügbar. (%s)"
                                .formatted(neuerRaum.getName(), pflichtslot.getDescription(), veranstaltung.getName()));
                    }
                }
            } else {
                throw new UpdateVortragException("Keine RaumVerfuegbarkeit für alten Raum '%s' in Slot '%s' (%s)"
                        .formatted(pflichtraum.getName(), pflichtslot.getDescription(), veranstaltung.getName()));
            }
        }


        pflichtraum = neuerRaum;
    }

    public void updatePflichtslot(Slot neuerSlot) {
        if (Objects.equals(pflichtslot, neuerSlot) || veranstaltung == null) {
            return;
        }

        // Restore availabilities for the old slot
        RaumVerfuegbarkeit rv = RaumVerfuegbarkeit.findById(rvId(pflichtraum, veranstaltung));

        if (null == rv) {
            throw new UpdateVortragException("RaumVerfuegbarkeit fehlt für Raum '%s' in Slot '%s'".formatted(
                    pflichtraum.getName(), pflichtslot.getDescription()));
        } else {
            if (rv.getVerfuegbareSlotIds().contains(neuerSlot.getId())) {
                rv.removeSlot(neuerSlot);
                rv.addSlot(pflichtslot);
            } else {
                throw new UpdateVortragException("Neuer Slot '%s' ist für Raum '%s' nicht verfügbar. (%s)"
                        .formatted(neuerSlot.getDescription(), pflichtraum.getName(), veranstaltung.getName()));
            }
        }

        List<Teilnehmer> teilnehmerDerGruppe = Teilnehmer.getGruppenTeilnehmer(pflichtgruppe, veranstaltung.getId());

        for (Teilnehmer teilnehmer : teilnehmerDerGruppe) {
            NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvId(teilnehmer, veranstaltung));
            if (null == nv) {
                throw new UpdateVortragException("NutzerVerfuegbarkeit fehlt für Teilnehmer '%s' in Slot '%s'".formatted(
                        teilnehmer.getEmail(), pflichtslot.getDescription()));
            } else {
                if (nv.getVerfuegbareSlotIds().contains(neuerSlot.getId())) {
                    nv.removeSlot(neuerSlot);
                    nv.addSlot(pflichtslot);
                } else {
                    throw new UpdateVortragException("Neuer Slot '%s' ist für Teilnehmer '%s' nicht verfügbar. (%s)"
                            .formatted(neuerSlot.getDescription(), teilnehmer.getEmail(), veranstaltung.getName()));
                }
            }
        }

        pflichtslot = neuerSlot;
    }

// -------------------------------------------------------------------
// Overrides
// -------------------------------------------------------------------

    @Override
    public boolean istPflicht() {
        return true;
    }


    @Override
    public void afterPersistAndFlush() {
        initNutzerVerfuegbarkeitFuerGruppe();
        initRaumVerfuegbarkeiten();
    }


// -------------------------------------------------------------------
// Helper methods for Verfuegbarkeiten
// -------------------------------------------------------------------

    @PreRemove
    public void wiederherstelleVerfuegbarkeiten() {
        if (pflichtraum != null) {
            RaumVerfuegbarkeit rv = RaumVerfuegbarkeit.findById(rvId(this.pflichtraum, veranstaltung));
            if (null != rv) {
                rv.addSlot(pflichtslot);
            }
        }
        if (pflichtgruppe != null && !pflichtgruppe.isEmpty()) {
            List<Teilnehmer> teilnehmerDerGruppe = Teilnehmer.getGruppenTeilnehmer(pflichtgruppe, veranstaltung.getId());
            for (Teilnehmer teilnehmer : teilnehmerDerGruppe) {
                NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvId(teilnehmer, veranstaltung));
                if (null != nv) {
                    nv.addSlot(pflichtslot);
                }
            }
        }
    }


// -------------------------------------------------------------------
// Helper methods
// -------------------------------------------------------------------

    /**
     * Set unavailability for the new room
     *
     * @deprecated in factory integrieren
     */
    @Deprecated
    private void initRaumVerfuegbarkeiten() {
        RaumVerfuegbarkeit rv = RaumVerfuegbarkeit.findById(rvId(pflichtraum, veranstaltung));
        if (null != rv) {
            if (rv.getVerfuegbareSlotIds().contains(pflichtslot.getId())) {
                rv.removeSlot(pflichtslot);
            } else {
                // Raum war nicht verfügbar
                throw new UpdateVortragException("Neuer Raum '%s' ist im Slot '%s' nicht verfügbar. (%s)"
                        .formatted(this.pflichtraum.getName(), this.pflichtslot.getDescription(), veranstaltung.getName()));
            }
        } else {
            // wieso haben wir hier noch keine RaumVerfügbarkeiten??
            Set<Long> verfuegbareIdsOhnePflicht = veranstaltung.getSlots().stream()
                    .filter(slot -> !Objects.equals(slot, pflichtslot))
                    .map(IdEntity::getId)
                    .collect(Collectors.toSet());
            new RaumVerfuegbarkeit(pflichtraum, veranstaltung, verfuegbareIdsOhnePflicht).persistAndFlush();
        }
    }

    /**
     * Remove availability for participants of the new group - after persist()
     */
    private void initNutzerVerfuegbarkeitFuerGruppe() {
        List<Teilnehmer> gruppenTeilnehmer = Teilnehmer.getGruppenTeilnehmer(pflichtgruppe, veranstaltung.getId());
        for (Teilnehmer teilnehmer : gruppenTeilnehmer) {
            NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvId(teilnehmer, veranstaltung));

            if (null != nv) {
                nv.removeSlot(pflichtslot);
            }
        }
    }
}