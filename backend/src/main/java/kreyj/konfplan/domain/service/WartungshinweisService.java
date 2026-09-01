package kreyj.konfplan.domain.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import kreyj.konfplan.persistence.Wartungshinweis;

import java.time.LocalDateTime;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;

@ApplicationScoped
public class WartungshinweisService {

    /**
     * Liefert die aktuell gueltige Ankuendigung, oder null, falls keine gesetzt ist oder das
     * angekuendigte Ende bereits in der Vergangenheit liegt (selbst-ablaufend, kein Aufraeum-Job
     * noetig).
     */
    public Wartungshinweis getAktuelle() {
        Wartungshinweis w = Wartungshinweis.aktuelles();
        if (null == w || null == w.getEndeZeitpunkt() || w.getEndeZeitpunkt().isBefore(LocalDateTime.now())) {
            return null;
        }
        return w;
    }

    @Transactional
    public Wartungshinweis setzen(LocalDateTime start, LocalDateTime ende) {
        if (null == start || null == ende) {
            throw new WebApplicationException("Start- und Endzeitpunkt sind erforderlich.", BAD_REQUEST.getStatusCode());
        }
        if (!ende.isAfter(start)) {
            throw new WebApplicationException("Der Endzeitpunkt muss nach dem Startzeitpunkt liegen.", BAD_REQUEST.getStatusCode());
        }

        Wartungshinweis w = Wartungshinweis.aktuelles();
        if (null == w) {
            w = new Wartungshinweis();
            w.persist();
        }
        w.setStartZeitpunkt(start);
        w.setEndeZeitpunkt(ende);
        return w;
    }

    @Transactional
    public void loeschen() {
        Wartungshinweis w = Wartungshinweis.aktuelles();
        if (null != w) {
            w.delete();
        }
    }
}
