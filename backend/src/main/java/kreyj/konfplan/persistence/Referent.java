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

@Entity
@Getter
@Setter
@DiscriminatorValue("REFERENT")
public class Referent extends Nutzer {

    @Column(name = "job_role")
    @CsvBindByName(column = "Position")
    private String jobRole;

    @Column(name = "organisation")
    @CsvBindByName(column = "Organisation")
    private String organisation;

    @Column(name = "slogan")
    @CsvBindByName(column = "Slogan")
    private String slogan;

    @Column(name = "biography", columnDefinition = "TEXT")
    @CsvBindByName(column = "Biografie")
    private String biography;

    @OneToMany(mappedBy = "referent", cascade = CascadeType.ALL)
    private List<Vortrag> vortraege = new ArrayList<>();

    public Referent() {
        this.setRole("REFERENT");
    }
}