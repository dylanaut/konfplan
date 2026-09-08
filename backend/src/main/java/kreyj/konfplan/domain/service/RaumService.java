package kreyj.konfplan.domain.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.konfplan.domain.exception.BusinessException;
import kreyj.konfplan.persistence.Gebaeude;
import kreyj.konfplan.persistence.ProtokollKategorie;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Veranstaltung;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class RaumService {

    private static final Logger LOG = Logger.getLogger(RaumService.class);

    private final ProtokollService protokollService;


    public RaumService(ProtokollService protokollService) {
        this.protokollService = protokollService;
    }


    public List<Raum> listAll() {
        return Raum.listAll();
    }


    public List<Raum> listByGebaeude(Long gebaeudeId) {
        return Raum.list("gebaeude.id = ?1", gebaeudeId);
    }


    public Raum findById(Long id) {
        return Raum.findById(id);
    }


    @Transactional
    public Raum save(Raum r, Long gebaeudeId) {
        Gebaeude gebaeude = Gebaeude.findById(gebaeudeId);
        if (null == gebaeude) {
            protokollService.log(ProtokollKategorie.RAUM, "Raum-Speicherung fehlgeschlagen", "Gebäude mit ID " + gebaeudeId + " nicht gefunden.");
            throw new IllegalArgumentException("Gebäude mit ID " + gebaeudeId + " nicht gefunden.");
        }

        if (r.getId() == null) {
            ensureNameIstEindeutigImGebaeude(gebaeudeId, r.getName(), null);

            // gebaeude.addRaum(r) muss vor dem Persistieren laufen: r.gebaeude zeigt sonst noch auf
            // das aus dem Request-Body deserialisierte, detached Gebaeude-Stub-Objekt (nur id
            // gesetzt, version = null), was Hibernate beim Insert mit einer
            // PropertyValueException/"uninitialized version value" ablehnt.
            gebaeude.addRaum(r);
            r.persistAndFlush();
            gebaeude.persistAndFlush();

            // Gebaeude.addRaum() aktualisiert nur die Gebaeude<->Raum-Beziehung; für
            // Veranstaltungen, die dieses Gebäude bereits VOR dem neuen Raum aufgenommen hatten,
            // fehlt sonst die RaumVerfuegbarkeit für dessen Slots (führt sonst zu NPE bei der
            // Planerstellung, siehe appendRaumVerfuegbarkeiten in PlanErstellungService).
            for (Veranstaltung veranstaltung : gebaeude.getVeranstaltungen()) {
                for (Slot slot : veranstaltung.getSlots()) {
                    r.updateRaumVerfuegbarkeit(slot, veranstaltung, true, true);
                }
            }

            protokollService.log(ProtokollKategorie.RAUM, "Raum erstellt", "Raum '" + r.getName() + "' im Gebäude '" + gebaeude.getName() + "' erstellt.", r.getId());
            return r;
        } else {
            Raum raum = Raum.findById(r.getId());
            if (null == raum) {
                return null;
            }

            ensureNameIstEindeutigImGebaeude(raum.getGebaeude().getId(), r.getName(), raum.getId());

            raum.setName(r.getName());
            raum.setKapazitaet(r.getKapazitaet());
            raum.setEtage(r.getEtage());

            raum.persistAndFlush();
            protokollService.log(ProtokollKategorie.RAUM, "Raum aktualisiert", "Raum '" + raum.getName() + "' im Gebäude '" + gebaeude.getName() + "' aktualisiert.", raum.getId());
            return raum;
        }
    }


    private void ensureNameIstEindeutigImGebaeude(Long gebaeudeId, String name, Long ignoriertRaumId) {
        Raum vorhandenerRaum = Raum.find("gebaeude.id = ?1 and name = ?2", gebaeudeId, name).firstResult();
        if (null != vorhandenerRaum && !vorhandenerRaum.getId().equals(ignoriertRaumId)) {
            throw new BusinessException("Ein Raum mit dem Namen '" + name + "' existiert in diesem Gebäude bereits.");
        }
    }


    @Transactional
    public boolean delete(Long id) {
        Raum raum = Raum.findById(id);
        if (raum != null) {
            String name = raum.getName();
            String gName = raum.getGebaeude() != null ? raum.getGebaeude().getName() : "unbekannt";
            boolean deleted = Raum.deleteById(id);
            if (deleted) {
                protokollService.log(ProtokollKategorie.RAUM, "Raum gelöscht", "Raum '" + name + "' aus Gebäude '" + gName + "' gelöscht.", id);
            }
            return deleted;
        }
        return false;
    }
}
