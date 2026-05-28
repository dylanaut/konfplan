package kreyj.konfplan.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.konfplan.persistence.Protokoll;
import kreyj.konfplan.persistence.ProtokollKategorie;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class ProtokollService {

    private final JsonWebToken jwt;

    public ProtokollService(JsonWebToken jwt) {
        this.jwt = jwt;
    }

    public List<Protokoll> listAll() {
        return Protokoll.list("order by zeitpunkt desc");
    }

    @Transactional
    public void log(ProtokollKategorie kategorie, String ereignis, String details, Long referenzId) {
        Protokoll p = new Protokoll();
        p.setZeitpunkt(LocalDateTime.now());
        p.setAkteur(getAkteur());
        p.setKategorie(kategorie);
        p.setEreignis(ereignis);
        p.setDetails(details);
        p.setReferenzId(referenzId);
        p.persist();
    }

    public void log(ProtokollKategorie kategorie, String ereignis, String details) {
        log(kategorie, ereignis, details, null);
    }

    public void log(ProtokollKategorie kategorie, String ereignis) {
        log(kategorie, ereignis, null, null);
    }

    private String getAkteur() {
        if (jwt != null && jwt.getName() != null) {
            return jwt.getName();
        }
        return "SYSTEM";
    }
}
