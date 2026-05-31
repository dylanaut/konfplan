package kreyj.konfplan.persistence;

import com.opencsv.bean.CsvBindByName;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static kreyj.konfplan.persistence.NutzerVerfuegbarkeitId.nvId;

@Entity
@Getter
@Setter
@DiscriminatorValue("TEILNEHMER")
public class Teilnehmer extends Nutzer {

    /**
     * Gruppenzugehörigkeit des Teilnehmers über Name der Gruppe
     */
    @Column(name = "gruppe")
    @CsvBindByName(column = "Gruppe")
    private String gruppe;

    @OneToMany(mappedBy = "teilnehmer", cascade = CascadeType.ALL, orphanRemoval = true)
    List<Prioritaet> prioritaeten = new ArrayList<>();


    // -------------------------------------------------------------------
    // Konstruktoren
    // -------------------------------------------------------------------

    public Teilnehmer() {
        this.setRole("TEILNEHMER");
    }


    // -------------------------------------------------------------------
    // public methods
    // -------------------------------------------------------------------

    public void setGruppe(String neueGruppe) {
        if (Objects.equals(this.gruppe, neueGruppe)) {
            return; // Keine Änderung
        }

        // Pflichtvorträge der alten Gruppe entfernen und Verfügbarkeiten wiederherstellen
        if (this.gruppe != null && !this.gruppe.isEmpty()) {
            List<Pflichtvortrag> altePflichtvortraege = Pflichtvortrag.find("pflichtgruppe", this.gruppe).list();
            for (Pflichtvortrag pv : altePflichtvortraege) {
                if (pv.getPflichtslot() != null) {
                    updateNutzerVerfuegbarkeit(pv.getVeranstaltung(), pv.getPflichtslot(), true);
                }
            }
        }

        this.gruppe = neueGruppe;

        // Pflichtvorträge der neuen Gruppe zuweisen und Verfügbarkeiten entfernen
        if (neueGruppe != null && !neueGruppe.isEmpty()) {
            List<Pflichtvortrag> neuePflichtvortraege = Pflichtvortrag.find("pflichtgruppe", neueGruppe).list();
            for (Pflichtvortrag pv : neuePflichtvortraege) {
                if (pv.getPflichtslot() != null) {
                    updateNutzerVerfuegbarkeit(pv.getVeranstaltung(), pv.getPflichtslot(), false);
                }
            }
        }
    }

    public List<Long> getVerfuegbareSlotIds(Veranstaltung veranstaltung) {
        NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvId(this, veranstaltung));

        return nv.getVerfuegbareSlotIds();
    }
}