package kreyj.vortragsmanager.entity;

import com.opencsv.bean.CsvBindByName;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@DiscriminatorValue("TEILNEHMER")
public class Teilnehmer extends User {

    @Column(name = "gruppe")
    @CsvBindByName(column = "Gruppe")
    public String gruppe;

    @OneToMany(mappedBy = "teilnehmer", cascade = CascadeType.ALL)
    public List<Prioritaet> prioritaeten = new ArrayList<>();

    @ManyToMany
    @JoinTable(
        name = "Teilnehmer_EventSlot",
        joinColumns = @JoinColumn(name = "teilnehmer_id", columnDefinition = "INTEGER"),
        inverseJoinColumns = @JoinColumn(name = "eventslot_id", columnDefinition = "INTEGER")
    )
    public List<EventSlot> verfuegbareSlots = new ArrayList<>();

    @ManyToOne(optional = false)
    @JoinColumn(name = "veranstaltung_id", columnDefinition = "INTEGER")
    public Veranstaltung veranstaltung;

    public Teilnehmer() {}
}
