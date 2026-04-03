package kreyj.vortragsmanager.entity;

import com.opencsv.bean.CsvBindByName;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@DiscriminatorValue("TEILNEHMER")
public class Teilnehmer extends User {

    @Column(name = "organization")
    @CsvBindByName(column = "organisation")
    public String organization;

    @Column(name = "job_role")
    @CsvBindByName(column = "job_role")
    public String jobRole;

    @OneToMany(mappedBy = "teilnehmer", cascade = CascadeType.ALL)
    public List<Prioritaet> prioritaeten = new ArrayList<>();

    public Teilnehmer() {}
}
