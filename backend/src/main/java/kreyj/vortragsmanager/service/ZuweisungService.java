package kreyj.vortragsmanager.service;

import jakarta.enterprise.context.ApplicationScoped;
import kreyj.vortragsmanager.entity.Zuweisung;
import java.util.List;

@ApplicationScoped
public class ZuweisungService {

    public List<Zuweisung> findByVeranstaltung(Long vid) {
        return Zuweisung.find("vortrag.veranstaltung.id", vid).list();
    }

    public List<Zuweisung> findByTeilnehmer(String email) {
        return Zuweisung.find("teilnehmer.email", email).list();
    }

    public List<Zuweisung> findByReferent(String email) {
        return Zuweisung.find("vortrag.referent.email", email).list();
    }
}
