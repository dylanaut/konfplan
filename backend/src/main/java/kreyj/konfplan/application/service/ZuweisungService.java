package kreyj.konfplan.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import kreyj.konfplan.persistence.Zuweisung;
import java.util.List;

@ApplicationScoped
public class ZuweisungService {

    public List<Zuweisung> findByVeranstaltung(Long vid) {
        return Zuweisung.find("vortrag.veranstaltung.id = ?1", vid).list();
    }

    public List<Zuweisung> findByTeilnehmer(String email) {
        return Zuweisung.find("teilnehmer.email = ?1", email).list();
    }

    public List<Zuweisung> findByReferent(String email) {
        return Zuweisung.find("vortrag.referent.email = ?1", email).list();
    }
}
