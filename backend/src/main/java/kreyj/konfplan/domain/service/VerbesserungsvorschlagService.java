package kreyj.konfplan.domain.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Verbesserungsvorschlag;
import kreyj.konfplan.persistence.VorschlagStatus;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class VerbesserungsvorschlagService {

    @Transactional
    public Verbesserungsvorschlag create(String titel, String beschreibung, String erstellerLoginName) {
        Nutzer ersteller = Nutzer.findByLoginName(erstellerLoginName);
        if (null == ersteller) {
            throw new NotFoundException("Nutzer mit Login-Name '" + erstellerLoginName + "' nicht gefunden.");
        }

        Verbesserungsvorschlag vorschlag = new Verbesserungsvorschlag();
        vorschlag.setTitel(titel);
        vorschlag.setBeschreibung(beschreibung);
        vorschlag.setErsteller(ersteller);
        vorschlag.setErstelltAm(LocalDateTime.now());
        vorschlag.setStatus(VorschlagStatus.OFFEN);
        vorschlag.persist();
        return vorschlag;
    }

    public List<Verbesserungsvorschlag> listAll() {
        return Verbesserungsvorschlag.listAll();
    }

    @Transactional
    public Verbesserungsvorschlag updateStatus(Long id, VorschlagStatus status) {
        Verbesserungsvorschlag vorschlag = Verbesserungsvorschlag.findById(id);
        if (null == vorschlag) {
            throw new NotFoundException("Verbesserungsvorschlag mit ID " + id + " nicht gefunden.");
        }
        vorschlag.setStatus(status);
        return vorschlag;
    }

    @Transactional
    public void delete(Long id) {
        Verbesserungsvorschlag vorschlag = Verbesserungsvorschlag.findById(id);
        if (null == vorschlag) {
            throw new NotFoundException("Verbesserungsvorschlag mit ID " + id + " nicht gefunden.");
        }
        vorschlag.delete();
    }
}
