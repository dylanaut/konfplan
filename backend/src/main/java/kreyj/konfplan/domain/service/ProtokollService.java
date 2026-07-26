package kreyj.konfplan.domain.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.konfplan.persistence.Protokoll;
import kreyj.konfplan.persistence.ProtokollKategorie;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ProtokollService {

    private final JsonWebToken jwt;

    @SuppressWarnings("CdiInjectionPointsInspection")
    public ProtokollService(JsonWebToken jwt) {
        this.jwt = jwt;
    }


    public List<Protokoll> listAll() {
        return Protokoll.list("order by zeitpunkt desc");
    }


    @Transactional
    public void log(ProtokollKategorie kategorie, String ereignis, String details, Long referenzId, Long veranstaltungId, String akteur) {
        Protokoll p = new Protokoll();
        p.setZeitpunkt(LocalDateTime.now());
        p.setAkteur(Optional.ofNullable(akteur).orElseGet(this::getAkteur));
        p.setKategorie(kategorie);
        p.setEreignis(ereignis);
        p.setDetails(details);
        p.setReferenzId(referenzId);
        p.setVeranstaltungId(veranstaltungId);
        p.persistAndFlush();
    }


    public void log(ProtokollKategorie kategorie, String ereignis, String details, Long referenzId, String akteur) {
        log(kategorie, ereignis, details, referenzId, null, akteur);
    }


    public void log(ProtokollKategorie kategorie, String ereignis, String details, Long referenzId, Long veranstaltungId) {
        log(kategorie, ereignis, details, referenzId, veranstaltungId, null);
    }


    public void log(ProtokollKategorie kategorie, String ereignis, String details, Long referenzId) {
        log(kategorie, ereignis, details, referenzId, null, null);
    }


    public void log(ProtokollKategorie kategorie, String ereignis, String details) {
        log(kategorie, ereignis, details, null, null, null);
    }


    public void log(ProtokollKategorie kategorie, String ereignis) {
        log(kategorie, ereignis, null, null, null, null);
    }


    private String getAkteur() {
        if (jwt != null && jwt.getName() != null) {
            return jwt.getName();
        }
        return "SYSTEM";
    }
}
