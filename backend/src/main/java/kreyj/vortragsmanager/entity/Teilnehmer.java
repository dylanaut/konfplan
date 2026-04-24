package kreyj.vortragsmanager.entity;

import com.opencsv.bean.CsvBindByName;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@DiscriminatorValue("TEILNEHMER")
public class Teilnehmer extends Nutzer {

    @Column(name = "gruppe")
    @CsvBindByName(column = "Gruppe")
    public String gruppe;

    @OneToMany(mappedBy = "teilnehmer", cascade = CascadeType.ALL)
    public List<Prioritaet> prioritaeten = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "Teilnehmer_EventSlot",
            joinColumns = @JoinColumn(name = "teilnehmer_id"),
            inverseJoinColumns = @JoinColumn(name = "eventslot_id")
    )
    public List<EventSlot> verfuegbareSlots = new ArrayList<>();

    public Teilnehmer() {
        this.role = "TEILNEHMER";
    }
}
