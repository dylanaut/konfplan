package kreyj.konfplan.persistence;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Objects;

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

    public Pflichtvortrag(String titel, Referent referent, String pflichtgruppe, Raum pflichtraum, Slot pflichtslot) {
        super(titel, referent);
        initPflichtFields(pflichtgruppe, pflichtraum, pflichtslot);
    }

    public Pflichtvortrag(String titel, Referent referent, Veranstaltung veranstaltung, String pflichtgruppe, Raum pflichtraum, Slot pflichtslot) {
        super(titel, referent, veranstaltung);
        initPflichtFields(pflichtgruppe, pflichtraum, pflichtslot);
    }

    private void initPflichtFields(String pflichtgruppe, Raum pflichtraum, Slot pflichtslot) {
        setPflichtgruppe(pflichtgruppe);
        setPflichtraum(pflichtraum);
        setPflichtslot(pflichtslot);
    }

    public void setPflichtgruppe(String neuePflichtgruppe) {
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

        // Remove availability for participants of the new group
        if (neuePflichtgruppe != null && !neuePflichtgruppe.isEmpty()) {
            List<Teilnehmer> neueTeilnehmer = Teilnehmer.find("gruppe", neuePflichtgruppe).list();
            for (Teilnehmer teilnehmer : neueTeilnehmer) {
                NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvId(teilnehmer, this.getVeranstaltung()));

                if (null != nv) {
                    nv.removeSlot(this.pflichtslot);
                }
            }
        }
    }

    public void setPflichtraum(Raum neuerRaum) {
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

        // Set unavailability for the new room
        if (neuerRaum != null) {
            RaumVerfuegbarkeit rv = RaumVerfuegbarkeit.findById(rvId(neuerRaum, this.getVeranstaltung()));
            if (null != rv) {
                rv.removeSlot(this.pflichtslot);
            }
        }
    }

    public void setPflichtslot(Slot neuerSlot) {
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
                RaumVerfuegbarkeit rv = RaumVerfuegbarkeit.findById(rvId(this.pflichtraum, this.getVeranstaltung()));

                if (null != rv) {
                    rv.removeSlot(neuerSlot);
                }
            }
            if (this.pflichtgruppe != null && !this.pflichtgruppe.isEmpty()) {
                List<Teilnehmer> teilnehmerDerGruppe = Teilnehmer.find("gruppe", this.pflichtgruppe).list();
                for (Teilnehmer teilnehmer : teilnehmerDerGruppe) {
                    NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvId(teilnehmer, this.getVeranstaltung()));
                    if (null != nv) {
                        nv.removeSlot(neuerSlot);
                    }
                }
            }
        }
    }

    @Override
    public boolean istPflicht() {
        return true;
    }
}