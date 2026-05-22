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

@Entity
@Getter
@Setter
@DiscriminatorValue("TEILNEHMER")
public class Teilnehmer extends Nutzer {

    @Column(name = "gruppe")
    @CsvBindByName(column = "Gruppe")
    private String gruppe;

    @OneToMany(mappedBy = "teilnehmer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Prioritaet> prioritaeten = new ArrayList<>();


    public Teilnehmer() {
        this.setRole("TEILNEHMER");
    }


    public void setGruppe(String neueGruppe) {
        if (Objects.equals(this.gruppe, neueGruppe)) {
            return; // Keine Änderung
        }

        // Pflichtvorträge der alten Gruppe entfernen und Verfügbarkeiten wiederherstellen
        if (this.gruppe != null && !this.gruppe.isEmpty()) {
            List<Pflichtvortrag> altePflichtvortraege = Pflichtvortrag.find("pflichtgruppe", this.gruppe).list();
            for (Pflichtvortrag pv : altePflichtvortraege) {
                if (pv.getPflichtslot() != null) {
                    updateVerfuegbarkeit(pv.getPflichtslot(), true);
                }
            }
        }

        this.gruppe = neueGruppe;

        // Pflichtvorträge der neuen Gruppe zuweisen und Verfügbarkeiten entfernen
        if (neueGruppe != null && !neueGruppe.isEmpty()) {
            List<Pflichtvortrag> neuePflichtvortraege = Pflichtvortrag.find("pflichtgruppe", neueGruppe).list();
            for (Pflichtvortrag pv : neuePflichtvortraege) {
                if (pv.getPflichtslot() != null) {
                    updateVerfuegbarkeit(pv.getPflichtslot(), false);
                }
            }
        }
    }

    private void updateVerfuegbarkeit(EventSlot slot, boolean isAvailable) {
        Verfuegbarkeit verfuegbarkeit = Verfuegbarkeit.find("nutzer = ?1 and slot = ?2", this, slot).firstResult();
        if (verfuegbarkeit != null) {
            verfuegbarkeit.setAvailable(isAvailable);
        } else if (!isAvailable) {
            // Erstelle eine neue Verfügbarkeit, falls keine existiert und der Teilnehmer nicht verfügbar sein soll
            new Verfuegbarkeit(this, slot, false).persist();
        }
    }
}