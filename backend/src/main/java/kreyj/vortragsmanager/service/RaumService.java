package kreyj.vortragsmanager.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.vortragsmanager.entity.Raum;
import kreyj.vortragsmanager.entity.EventSlot;
import java.util.List;

@ApplicationScoped
public class RaumService {

    public List<Raum> listAll() {
        return Raum.listAll();
    }

    public Raum findById(Long id) {
        return Raum.findById(id);
    }

    @Transactional
    public Raum save(Raum r) {
        if (r.id == null) {
            r.persist();
            return r;
        } else {
            Raum entity = Raum.findById(r.id);
            if (entity == null) return null;
            
            entity.name = r.name;
            entity.kapazitaet = r.kapazitaet;
            entity.etage = r.etage;
            entity.verfuegbareSlots = r.verfuegbareSlots;
            
            return entity;
        }
    }

    @Transactional
    public boolean delete(Long id) {
        return Raum.deleteById(id);
    }
}
