package kreyj.vortragsmanager.entity;

import com.opencsv.bean.CsvBindByName;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@DiscriminatorValue("REFERENT")
public class Referent extends User {

    @Column(name = "job_role")
    @CsvBindByName(column = "Position")
    public String jobRole;

    @Column(name = "organisation")
    @CsvBindByName(column = "Organisation")
    public String organisation;

    @Column(name = "slogan")
    @CsvBindByName(column = "Slogan")
    public String slogan;

    @Column(name = "biography", columnDefinition = "TEXT")
    @CsvBindByName(column = "Biografie")
    public String biography;

    @OneToMany(mappedBy = "referent", cascade = CascadeType.ALL)
    public List<Vortrag> vortraege = new ArrayList<>();

    public Referent() {
        this.role = "REFERENT";
    }
}
