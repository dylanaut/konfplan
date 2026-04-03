package kreyj.vortragsmanager.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.vortragsmanager.entity.Veranstaltung;
import kreyj.vortragsmanager.entity.User;
import java.util.List;

@ApplicationScoped
public class VeranstaltungService {

    public List<Veranstaltung> listAll() {
        return Veranstaltung.listAll();
    }

    public Veranstaltung findById(Long id) {
        return Veranstaltung.findById(id);
    }

    @Transactional
    public Veranstaltung save(Veranstaltung v) {
        // Validierung: Organisator muss ADMIN sein
        if (v.organisator == null || !"ADMIN".equals(v.organisator.role)) {
            throw new IllegalArgumentException("Der Organisator muss ein Benutzer mit der Rolle ADMIN sein.");
        }
        
        if (v.id == null) {
            v.persist();
            return v;
        } else {
            Veranstaltung entity = Veranstaltung.findById(v.id);
            if (entity == null) return null;
            
            entity.name = v.name;
            entity.beginntAm = v.beginntAm;
            entity.endetAm = v.endetAm;
            entity.ort = v.ort;
            entity.logo = v.logo;
            entity.logo_link = v.logo_link;
            entity.organisator = v.organisator;
            
            return entity;
        }
    }

    @Transactional
    public boolean delete(Long id) {
        return Veranstaltung.deleteById(id);
    }
}
