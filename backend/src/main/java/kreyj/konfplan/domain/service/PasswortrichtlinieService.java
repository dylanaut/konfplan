package kreyj.konfplan.domain.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.dto.PasswortrichtlinieAnfrageDto;
import kreyj.konfplan.adapter.in.web.dto.PasswortrichtlinieDto;
import kreyj.konfplan.domain.exception.BusinessException;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Passwortrichtlinie;
import kreyj.konfplan.persistence.Veranstaltung;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Verwaltet je Veranstaltung und Nutzerrolle konfigurierbare Passwort-Syntax-Anforderungen
 * (siehe #741) - gilt für Passwörter, die ein Organisator/Administrator selbst setzt
 * (Einzel-Reset, ZIP-Bulk-Generierung), NICHT für Keycloaks eigenen Self-Service-Flow. Ist für
 * ein (Veranstaltung, Rolle)-Paar nichts konfiguriert, gilt {@link Passwortrichtlinie#STANDARD}.
 */
@ApplicationScoped
public class PasswortrichtlinieService {

    private static final Set<String> GUELTIGE_ROLLEN =
        Set.of("ORGANISATOR", "ADMINISTRATOR", "REFERENT", "TEILNEHMER", "BETRACHTER");

    /**
     * Muss mit dem {@code passwordPolicy}-Sockel in {@code konfplan-realm.json} bzw.
     * {@code ProdKeycloakRealmSyncService.PASSWORD_POLICY} übereinstimmen: Keycloak lehnt JEDES
     * neu gesetzte Passwort unterhalb dieser Länge ab, unabhängig davon, ob es per Selbst-Reset
     * oder admin-gesetzt (Einzel-Reset, ZIP-Bulk) zustande kommt - eine hier konfigurierte
     * Richtlinie mit kleinerer Mindestlänge würde also von der App akzeptiert, aber anschließend
     * von Keycloak zurückgewiesen.
     */
    private static final int KEYCLOAK_MINDESTLAENGE = 6;


    @Transactional
    public List<PasswortrichtlinieDto> getRichtlinien(Veranstaltung veranstaltung) {
        Map<String, Passwortrichtlinie> konfiguriert = Passwortrichtlinie.<Passwortrichtlinie>find("veranstaltung", veranstaltung)
            .list().stream()
            .collect(Collectors.toMap(Passwortrichtlinie::getRolle, Function.identity()));

        return GUELTIGE_ROLLEN.stream()
            .sorted()
            .map(rolle -> PasswortrichtlinieDto.from(rolle, konfiguriert.get(rolle)))
            .toList();
    }


    @Transactional
    public Passwortrichtlinie save(Veranstaltung veranstaltung, String rolle, PasswortrichtlinieAnfrageDto dto) {
        validiereRolle(rolle);
        if (dto.minLaenge < KEYCLOAK_MINDESTLAENGE) {
            throw new BusinessException("Die Mindestlänge muss mindestens " + KEYCLOAK_MINDESTLAENGE
                + " betragen (Keycloaks eigene Passwort-Policy erzwingt diesen Sockel für jedes neu gesetzte Passwort).");
        }
        if (null != dto.maxLaenge && dto.maxLaenge < dto.minLaenge) {
            throw new BusinessException("Die Maximallänge darf nicht kleiner als die Mindestlänge sein.");
        }

        Passwortrichtlinie richtlinie = Passwortrichtlinie.findByVeranstaltungUndRolle(veranstaltung, rolle);
        if (null == richtlinie) {
            richtlinie = new Passwortrichtlinie(veranstaltung, rolle, dto.minLaenge, dto.maxLaenge,
                dto.erfordertGrossbuchstabe, dto.erfordertKleinbuchstabe, dto.erfordertZiffer, dto.erfordertSonderzeichen, dto.nurZiffern);
            richtlinie.persist();
        } else {
            richtlinie.setMinLaenge(dto.minLaenge);
            richtlinie.setMaxLaenge(dto.maxLaenge);
            richtlinie.setErfordertGrossbuchstabe(dto.erfordertGrossbuchstabe);
            richtlinie.setErfordertKleinbuchstabe(dto.erfordertKleinbuchstabe);
            richtlinie.setErfordertZiffer(dto.erfordertZiffer);
            richtlinie.setErfordertSonderzeichen(dto.erfordertSonderzeichen);
            richtlinie.setNurZiffern(dto.nurZiffern);
        }
        return richtlinie;
    }


    @Transactional
    public void deleteRichtlinie(Veranstaltung veranstaltung, String rolle) {
        Passwortrichtlinie richtlinie = Passwortrichtlinie.findByVeranstaltungUndRolle(veranstaltung, rolle);
        if (null != richtlinie) {
            richtlinie.delete();
        }
    }


    @Transactional
    public Passwortrichtlinie resolve(Veranstaltung veranstaltung, String rolle) {
        Passwortrichtlinie richtlinie = Passwortrichtlinie.findByVeranstaltungUndRolle(veranstaltung, rolle);
        return null != richtlinie ? richtlinie : Passwortrichtlinie.STANDARD;
    }


    @Transactional
    public void validate(Veranstaltung veranstaltung, Nutzer nutzer, String neuesPasswort) {
        Passwortrichtlinie richtlinie = resolve(veranstaltung, nutzer.getRole());
        if (!richtlinie.erfuellt(neuesPasswort)) {
            throw new BusinessException(beschreibung(richtlinie));
        }
    }


    private void validiereRolle(String rolle) {
        if (!GUELTIGE_ROLLEN.contains(rolle)) {
            throw new BusinessException("Unbekannte Rolle: " + rolle);
        }
    }


    private String beschreibung(Passwortrichtlinie richtlinie) {
        if (richtlinie.isNurZiffern()) {
            if (null != richtlinie.getMaxLaenge() && !richtlinie.getMaxLaenge().equals(richtlinie.getMinLaenge())) {
                return "Das Passwort muss aus " + richtlinie.getMinLaenge() + " bis " + richtlinie.getMaxLaenge() + " Ziffern bestehen.";
            }
            return "Das Passwort muss aus genau " + richtlinie.getMinLaenge() + " Ziffern bestehen.";
        }

        List<String> anforderungen = new ArrayList<>();
        if (richtlinie.isErfordertGrossbuchstabe()) {
            anforderungen.add("ein Großbuchstabe");
        }
        if (richtlinie.isErfordertKleinbuchstabe()) {
            anforderungen.add("ein Kleinbuchstabe");
        }
        if (richtlinie.isErfordertZiffer()) {
            anforderungen.add("eine Ziffer");
        }
        if (richtlinie.isErfordertSonderzeichen()) {
            anforderungen.add("ein Sonderzeichen");
        }

        StringBuilder sb = new StringBuilder("Das Passwort muss mindestens " + richtlinie.getMinLaenge() + " Zeichen lang sein");
        if (!anforderungen.isEmpty()) {
            sb.append(" und mindestens je ").append(String.join(", ", anforderungen)).append(" enthalten");
        }
        return sb.append(".").toString();
    }
}
