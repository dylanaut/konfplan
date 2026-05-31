package kreyj.konfplan.persistence;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreRemove;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
        // constructor requires invocation of #updateVerfuegbarkeitenAfterPersist()
    }

    public void updatePflichtgruppe(String neuePflichtgruppe) {
        if (null == neuePflichtgruppe || neuePflichtgruppe.isBlank()) {
            throw new IllegalArgumentException("Pflichtgruppe darf nicht leer sein.");
        }

        if (Objects.equals(this.pflichtgruppe, neuePflichtgruppe) || this.getVeranstaltung() == null) {
            return;
        }

        // Restore availability for participants of the old group
        if (this.pflichtgruppe != null && !this.pflichtgruppe.isEmpty()) {
            List<Teilnehmer> alteTeilnehmer = Teilnehmer.find("gruppe", this.pflichtgruppe).list();
            for (Teilnehmer teilnehmer : alteTeilnehmer) {
                NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvId(teilnehmer, this.getVeranstaltung()));

                if (null != nv) {
                    nv.addSlot(this.pflichtslot);
                }
            }
        }

        this.pflichtgruppe = neuePflichtgruppe;

        initNutzerVerfuegbarkeitFuerGruppe();
    }

    public void updatePflichtraum(Raum neuerRaum) {
        if (Objects.equals(this.pflichtraum, neuerRaum) || this.getVeranstaltung() == null) {
            return;
        }

        // Restore availability for the old room
        if (this.pflichtraum != null) {
            RaumVerfuegbarkeit rv = RaumVerfuegbarkeit.findById(rvId(this.pflichtraum, this.getVeranstaltung()));
            if (null != rv) {
                rv.addSlot(this.pflichtslot);
            }
        }

        this.pflichtraum = neuerRaum;

        initRaumVerfuegbarkeiten();
    }

    public void updatePflichtslot(Slot neuerSlot) {
        if (Objects.equals(this.pflichtslot, neuerSlot) || this.getVeranstaltung() == null) {
            return;
        }

        Slot alterSlot = this.pflichtslot;

        // Restore availabilities for the old slot
        if (alterSlot != null) {
            if (this.pflichtraum != null) {
                RaumVerfuegbarkeit rv = RaumVerfuegbarkeit.findById(rvId(this.pflichtraum, this.getVeranstaltung()));
                if (null != rv) {
                    rv.addSlot(alterSlot);
                }
            }
            if (this.pflichtgruppe != null && !this.pflichtgruppe.isEmpty()) {
                List<Teilnehmer> teilnehmerDerGruppe = Teilnehmer.find("gruppe", this.pflichtgruppe).list();
                for (Teilnehmer teilnehmer : teilnehmerDerGruppe) {
                    NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvId(teilnehmer, this.getVeranstaltung()));
                    if (null != nv) {
                        nv.addSlot(alterSlot);
                    }
                }
            }
        }

        this.pflichtslot = neuerSlot;

        // Set unavailabilities for the new slot
        if (neuerSlot != null) {
            if (this.pflichtraum != null) {
                initRaumVerfuegbarkeiten();
            }
            if (this.pflichtgruppe != null && !this.pflichtgruppe.isEmpty()) {
                initNutzerVerfuegbarkeitFuerGruppe();
            }
        }
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
        if (this.pflichtraum != null) {
            RaumVerfuegbarkeit rv = RaumVerfuegbarkeit.findById(rvId(this.pflichtraum, this.getVeranstaltung()));
            if (null != rv) {
                rv.addSlot(this.pflichtslot);
            }
        }
        if (this.pflichtgruppe != null && !this.pflichtgruppe.isEmpty()) {
            List<Teilnehmer> teilnehmerDerGruppe = Teilnehmer.find("gruppe", this.pflichtgruppe).list();
            for (Teilnehmer teilnehmer : teilnehmerDerGruppe) {
                NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvId(teilnehmer, this.getVeranstaltung()));
                if (null != nv) {
                    nv.addSlot(this.pflichtslot);
                }
            }
        }
    }


    // -------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------

    /**
     * Set unavailability for the new room
     */
    private void initRaumVerfuegbarkeiten() {
        RaumVerfuegbarkeit rv = RaumVerfuegbarkeit.findById(rvId(pflichtraum, veranstaltung));
        if (null != rv) {
            rv.removeSlot(pflichtslot);
        } else {
            // wieso haben wir hier noch keine RaumVerfügbarkeiten??
            List<Long> verfuegbareIdsOhnePflicht = veranstaltung.getSlots().stream()
                    .filter(slot -> !Objects.equals(slot, pflichtslot))
                    .map(IdEntity::getId)
                    .toList();
            new RaumVerfuegbarkeit(pflichtraum, veranstaltung, verfuegbareIdsOhnePflicht).persistAndFlush();
        }
    }

    /**
     * Remove availability for participants of the new group - after persist()
     */
    private void initNutzerVerfuegbarkeitFuerGruppe() {
        List<Teilnehmer> gruppenTeilnehmer = Teilnehmer.find("gruppe", pflichtgruppe).list();
        for (Teilnehmer teilnehmer : gruppenTeilnehmer) {
            NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvId(teilnehmer, veranstaltung));

            if (null != nv) {
                nv.removeSlot(pflichtslot);
            } else {
                new NutzerVerfuegbarkeit(teilnehmer, veranstaltung, List.of(pflichtslot.getId())).persistAndFlush();
            }
        }
    }
}