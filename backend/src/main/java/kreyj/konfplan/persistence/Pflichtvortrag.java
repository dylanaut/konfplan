package kreyj.konfplan.persistence;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Objects;

@Getter
@Entity
@NoArgsConstructor
@DiscriminatorValue("PFLICHT")
public class Pflichtvortrag extends Vortrag {
    private String pflichtgruppe;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Raum pflichtraum;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private EventSlot pflichtslot;

    public Pflichtvortrag(String titel, Referent referent, String pflichtgruppe, Raum pflichtraum, EventSlot pflichtslot) {
        super(titel, referent);

        initPflichtFields(pflichtgruppe, pflichtraum, pflichtslot);
    }

    public Pflichtvortrag(String titel, Referent referent, Veranstaltung veranstaltung, String pflichtgruppe, Raum pflichtraum, EventSlot pflichtslot) {
        super(titel, referent, veranstaltung);

        initPflichtFields(pflichtgruppe, pflichtraum, pflichtslot);
    }

    private void initPflichtFields(String pflichtgruppe, Raum pflichtraum, EventSlot pflichtslot) {
        setPflichtgruppe(pflichtgruppe);
        setPflichtraum(pflichtraum);
        setPflichtslot(pflichtslot);
    }

    public void setPflichtgruppe(String neuePflichtgruppe) {
        if (Objects.equals(this.pflichtgruppe, neuePflichtgruppe)) {
            return; // No change
        }

        // Restore availability for participants of the old group
        if (this.pflichtgruppe != null && !this.pflichtgruppe.isEmpty()) {
            List<Teilnehmer> alteTeilnehmer = Teilnehmer.find("gruppe", this.pflichtgruppe).list();
            for (Teilnehmer teilnehmer : alteTeilnehmer) {
                updateVerfuegbarkeit(teilnehmer, this.pflichtslot, true);
            }
        }

        this.pflichtgruppe = neuePflichtgruppe;

        // Remove availability for participants of the new group
        if (neuePflichtgruppe != null && !neuePflichtgruppe.isEmpty()) {
            List<Teilnehmer> neueTeilnehmer = Teilnehmer.find("gruppe", neuePflichtgruppe).list();
            for (Teilnehmer teilnehmer : neueTeilnehmer) {
                updateVerfuegbarkeit(teilnehmer, this.pflichtslot, false);
            }
        }
    }

    public void setPflichtraum(Raum neuerRaum) {
        if (Objects.equals(this.pflichtraum, neuerRaum)) {
            return; // No change
        }

        // Restore availability for the old room
        if (this.pflichtraum != null) {
            updateRaumbelegbarkeit(this.pflichtraum, this.pflichtslot, false);
        }

        this.pflichtraum = neuerRaum;

        // Set unavailability for the new room
        if (neuerRaum != null) {
            updateRaumbelegbarkeit(neuerRaum, this.pflichtslot, true);
        }
    }

    public void setPflichtslot(EventSlot neuerSlot) {
        if (Objects.equals(this.pflichtslot, neuerSlot)) {
            return; // No change
        }

        EventSlot alterSlot = this.pflichtslot;

        // Restore availabilities for the old slot
        if (alterSlot != null) {
            if (this.pflichtraum != null) {
                updateRaumbelegbarkeit(this.pflichtraum, alterSlot, false);
            }
            if (this.pflichtgruppe != null && !this.pflichtgruppe.isEmpty()) {
                List<Teilnehmer> teilnehmerDerGruppe = Teilnehmer.find("gruppe", this.pflichtgruppe).list();
                for (Teilnehmer teilnehmer : teilnehmerDerGruppe) {
                    updateVerfuegbarkeit(teilnehmer, alterSlot, true);
                }
            }
        }

        this.pflichtslot = neuerSlot;

        // Set unavailabilities for the new slot
        if (neuerSlot != null) {
            if (this.pflichtraum != null) {
                updateRaumbelegbarkeit(this.pflichtraum, neuerSlot, true);
            }
            if (this.pflichtgruppe != null && !this.pflichtgruppe.isEmpty()) {
                List<Teilnehmer> teilnehmerDerGruppe = Teilnehmer.find("gruppe", this.pflichtgruppe).list();
                for (Teilnehmer teilnehmer : teilnehmerDerGruppe) {
                    updateVerfuegbarkeit(teilnehmer, neuerSlot, false);
                }
            }
        }
    }

    private void updateRaumbelegbarkeit(Raum raum, EventSlot slot, boolean isBelegt) {
        if (slot == null) {
            return;
        }

        RaumBelegbarkeit belegbarkeit = RaumBelegbarkeit.find("raum = ?1 and slot = ?2", raum, slot).firstResult();
        if (belegbarkeit != null) {
            belegbarkeit.setBelegt(isBelegt);
        } else if (isBelegt) {
            RaumBelegbarkeit neueBelegbarkeit = new RaumBelegbarkeit(raum, slot, true);
            neueBelegbarkeit.persist();
        }
    }

    private void updateVerfuegbarkeit(Teilnehmer teilnehmer, EventSlot slot, boolean isAvailable) {
        if (slot == null) {
            return;
        }

        Verfuegbarkeit verfuegbarkeit = Verfuegbarkeit.find("nutzer = ?1 and slot = ?2", teilnehmer, slot).firstResult();
        if (verfuegbarkeit != null) {
            verfuegbarkeit.setAvailable(isAvailable);
        } else if (!isAvailable) {
            new Verfuegbarkeit(teilnehmer, slot, false).persist();
        }
    }

    @Override
    public boolean istPflicht() {
        return true;
    }
}