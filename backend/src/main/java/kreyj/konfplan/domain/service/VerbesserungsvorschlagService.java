package kreyj.konfplan.domain.service;

import io.quarkus.info.BuildInfo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import kreyj.konfplan.persistence.Dringlichkeit;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Verbesserungsvorschlag;
import kreyj.konfplan.persistence.VorschlagStatus;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class VerbesserungsvorschlagService {

    @Inject
    Instance<BuildInfo> buildInfo;

    @Transactional
    public Verbesserungsvorschlag create(String titel, String beschreibung, Dringlichkeit dringlichkeit, String erstellerLoginName) {
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
        vorschlag.setDringlichkeit(null != dringlichkeit ? dringlichkeit : Dringlichkeit.MITTEL);
        vorschlag.setRelease(buildInfo.isResolvable() ? buildInfo.get().version() : "unbekannt");
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
