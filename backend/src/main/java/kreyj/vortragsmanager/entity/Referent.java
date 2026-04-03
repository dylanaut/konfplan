package kreyj.vortragsmanager.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@DiscriminatorValue("REFERENT")
public class Referent extends User {

    @Column(name = "biography", columnDefinition = "TEXT")
    public String biography;

    @OneToMany(mappedBy = "referent", cascade = CascadeType.ALL)
    public List<Vortrag> vortraege = new ArrayList<>();

    public Referent() {}
}
