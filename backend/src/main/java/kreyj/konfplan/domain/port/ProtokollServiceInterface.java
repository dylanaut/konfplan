package kreyj.konfplan.domain.port;

import kreyj.konfplan.persistence.ProtokollKategorie;

public interface ProtokollServiceInterface {
    void log(ProtokollKategorie kategorie, String ereignis, String details, Long referenzId);
    void log(ProtokollKategorie kategorie, String ereignis, String details);
}