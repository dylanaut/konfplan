package kreyj.konfplan.domain.service;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.dto.ImportResultDto;
import kreyj.konfplan.adapter.in.web.dto.NutzerDto;
import kreyj.konfplan.adapter.in.web.dto.RaumVerfuegbarkeitDto;
import kreyj.konfplan.adapter.in.web.dto.SlotDto;
import kreyj.konfplan.adapter.in.web.dto.VortragDto;
import kreyj.konfplan.adapter.in.web.dto.VortragStatDto;
import kreyj.konfplan.adapter.in.web.dto.csv.OrganisatorCsvDto;
import kreyj.konfplan.adapter.in.web.dto.csv.NutzerVerfuegbarkeitCsvDto;
import kreyj.konfplan.adapter.in.web.dto.csv.RaumVerfuegbarkeitCsvDto;
import kreyj.konfplan.adapter.in.web.dto.csv.SlotCsvDto;
import kreyj.konfplan.adapter.in.web.dto.csv.VortragCsvDto;
import kreyj.konfplan.application.port.in.OrganisatorServiceInterface;
import kreyj.konfplan.domain.exception.BusinessException;
import kreyj.konfplan.domain.exception.CreateSlotException;
import kreyj.konfplan.domain.exception.CreateVortragException;
import kreyj.konfplan.domain.exception.CsvImportException;
import kreyj.konfplan.domain.exception.DeleteVortragsgruppeException;
import kreyj.konfplan.domain.exception.EntityNotFoundException;
import kreyj.konfplan.domain.exception.UpdateNutzerException;
import kreyj.konfplan.domain.exception.UpdateVortragException;
import kreyj.konfplan.domain.exception.VeranstaltungException;
import kreyj.konfplan.persistence.AbschlussTyp;
import kreyj.konfplan.persistence.Gebaeude;
import kreyj.konfplan.persistence.IdEntity;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.NutzerVerfuegbarkeit;
import kreyj.konfplan.persistence.Pflichtvortrag;
import kreyj.konfplan.persistence.Prioritaet;
import kreyj.konfplan.persistence.ProtokollKategorie;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.RaumVerfuegbarkeit;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Neigung;
import kreyj.konfplan.persistence.Organisator;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Vortrag;
import kreyj.konfplan.persistence.VortragVerfuegbarkeit;
import kreyj.konfplan.persistence.Wahlvortrag;
import kreyj.konfplan.util.CsvHelper;
import kreyj.konfplan.util.StringHelper;
import kreyj.konfplan.util.TemplateExtensions;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;

import java.io.BufferedReader;
import java.io.Reader;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static kreyj.konfplan.persistence.NutzerVerfuegbarkeitId.nvId;
import static kreyj.konfplan.persistence.NutzerVerfuegbarkeitId.nvIdL;
import static kreyj.konfplan.persistence.RaumVerfuegbarkeitId.rvId;
import static kreyj.konfplan.persistence.Teilnehmer.getGruppenTeilnehmer;
import static kreyj.konfplan.persistence.VortragVerfuegbarkeitId.vvIdL;
import static kreyj.konfplan.util.DateHelper.DATE_FORMAT;
import static org.apache.commons.collections4.SetUtils.difference;

@ApplicationScoped
public class OrganisatorService implements OrganisatorServiceInterface {
    private static final Logger LOG = Logger.getLogger(OrganisatorService.class);
    public static final String CSV_PRIO_HEADER = "Teilnehmer LoginName;Prioritäten";
    public static final String PV_FAIL_MESSAGE = ". Pflichtvortrag kann nicht erstellt werden.";
    public static final String LEGENDE_TOKEN = "# Legende:";
    public static final String LEGENDEN_EINTRAG_SEP = "#";

    private final MailService mailService;
    private final ProtokollService protokollService;
    private final KeycloakUserProvisioningService keycloakUserProvisioningService;


    public OrganisatorService(MailService mailService, ProtokollService protokollService,
                        KeycloakUserProvisioningService keycloakUserProvisioningService) {
        this.mailService = mailService;
        this.protokollService = protokollService;
        this.keycloakUserProvisioningService = keycloakUserProvisioningService;
    }


    // Helper class to store blocking information
    @AllArgsConstructor
    private static class BlockingInfo {
        LocalDateTime start;
        LocalDateTime end;
        String eventName;
    }


    @Transactional
    @Override
    public List<NutzerDto> getAllUsers() {
        return new HashSet<>(Nutzer.<Nutzer>listAll()) // Duplikate entfernen
            .stream()
            .map(NutzerDto::from)
            .toList();
    }


    @Transactional
    @Override
    public List<NutzerDto> getAllUsers(Long veranstaltungId) {
        List<? extends Nutzer> organisatoren = Organisator.listAll();
        List<Nutzer> vNutzers = Nutzer.find("SELECT u FROM Nutzer u JOIN u.veranstaltungen v WHERE v.id = ?1", veranstaltungId).list();

        return Stream.concat(organisatoren.stream(), vNutzers.stream())
            .distinct()
            .map(NutzerDto::from)
            .toList();
    }


    @Transactional
    @Override
    public Nutzer findNutzer(Long id) {
        return Nutzer.findById(id);
    }


    @Transactional
    @Override
    public NutzerDto createUser(NutzerDto dto, List<Long> veranstaltungsIds) {
        Nutzer nutzer;
        if ("REFERENT".equals(dto.role)) {
            nutzer = new Referent();
        } else if ("TEILNEHMER".equals(dto.role)) {
            nutzer = new Teilnehmer();
        } else {
            nutzer = new Organisator();
        }

        // Ohne E-Mail-Adresse ist Passwort-Reset über Keycloak unmöglich - für Organisatoren
        // fatal, da es (anders als bei Referent/Teilnehmer) keine übergeordnete Instanz gibt,
        // die das Konto sonst wiederherstellen könnte.
        if (nutzer instanceof Organisator && StringUtils.isBlank(dto.email)) {
            throw new BusinessException("Organisator-Konten benötigen zwingend eine E-Mail-Adresse (sonst ist bei einem vergessenen Passwort keine Wiederherstellung möglich).");
        }

        // Vor dem Keycloak-Aufruf prüfen (statt den DB-Unique-Constraint bzw. Keycloaks eigene
        // Prüfung crashen zu lassen) - sonst landet ein ungefangener 500er beim Client.
        if (Nutzer.findByLoginName(dto.loginName) != null) {
            throw new BusinessException("Der Login-Name '" + dto.loginName + "' ist bereits vergeben.");
        }
        if (StringUtils.isNotBlank(dto.email) && Nutzer.findByEmail(dto.email) != null) {
            throw new BusinessException("Die E-Mail-Adresse '" + dto.email + "' wird bereits verwendet.");
        }

        nutzer.assignLoginName(dto.loginName);
        // Leerstring statt null wuerde beim naechsten Nutzer ohne E-Mail-Adresse am
        // DB-Unique-Constraint auf email scheitern (NULL ist von UNIQUE ausgenommen, '' nicht -
        // siehe #282).
        nutzer.setEmail(StringUtils.isBlank(dto.email) ? null : dto.email);
        nutzer.setFirstName(dto.firstName);
        nutzer.setLastName(dto.lastName);
        nutzer.setActive(dto.isActive);

        keycloakUserProvisioningService.createUser(nutzer);

        if (nutzer instanceof Referent r) {
            r.setJobRole(dto.jobRole);
            r.setOrganisation(dto.organisation);
        } else if (nutzer instanceof Teilnehmer t) {
            if (null != dto.gruppen) {
                dto.gruppen.forEach(t::addGruppe);
            }
            if (null != dto.neigungen) {
                t.setNeigungen(dto.neigungen);
            }
            if (null != dto.prioritaeten) {
                dto.prioritaeten.forEach(prioDto -> {
                    Wahlvortrag wv = Wahlvortrag.findById(prioDto.vortragId);
                    if (null == wv) {
                        LOG.error("Unbekannter Wahlvortrag zu id: " + prioDto.vortragId);
                    } else {
                        Prioritaet prioritaet = new Prioritaet(t, Wahlvortrag.findById(prioDto.vortragId),
                            prioDto.prioWert);
                        prioritaet.persist();
                        t.addPrioritaet(prioritaet);
                    }
                });
            }
        }

        nutzer.persistAndFlush();

        if (null != veranstaltungsIds) {
            for (Long veranstaltungId : veranstaltungsIds) {
                Veranstaltung v = Veranstaltung.findById(veranstaltungId);
                if (null == v) {
                    LOG.error("Unbekannte Veranstaltung zu id: " + veranstaltungId);
                } else {
                    nutzer.addVeranstaltung(v);
                }
            }
        }

        // Send registration confirmation email
        mailService.sendRegistrationConfirmation(nutzer);

        protokollService.log(ProtokollKategorie.NUTZER, "Nutzer erstellt", "Neuer Nutzer '" + nutzer.getLoginName() + "' mit Rolle '" + nutzer.getRole() + "' erstellt.", nutzer.getId());
        return NutzerDto.from(nutzer);
    }


    @Transactional
    @Override
    public NutzerDto updateUser(Long id, NutzerDto dto, List<Long> vUpdateIds) {
        Nutzer nutzer = Nutzer.findById(id);
        if (null == nutzer) {
            return null;
        }

        if (!Objects.equals(nutzer.getVersion(), dto.version)) {
            throw new OptimisticLockException("Der Nutzer wurde zwischenzeitlich von Dritten geändert. Bitte aktualisieren Sie die Daten und versuchen Sie es erneut.");
        }

        // Organisator-getriebene E-Mail-Änderung wird direkt übernommen (keine Bestätigung nötig -
        // anders als bei einer Self-Service-Änderung, die es nicht mehr gibt: das läuft jetzt
        // über Keycloaks Account-Console).
        String oldEmail = nutzer.getEmail();
        if (!Objects.equals(oldEmail, dto.email)) {
            if (StringUtils.isBlank(dto.email)) {
                // Siehe createUser: ein Organisator-Konto ohne E-Mail-Adresse ist bei einem
                // vergessenen Passwort unwiederbringlich verloren.
                if (nutzer instanceof Organisator) {
                    throw new UpdateNutzerException("Organisator-Konten benötigen zwingend eine E-Mail-Adresse (sonst ist bei einem vergessenen Passwort keine Wiederherstellung möglich).");
                }
                nutzer.setEmail(null);
                protokollService.log(ProtokollKategorie.NUTZER, "E-Mail-Adresse entfernt",
                    "E-Mail-Adresse für Nutzer '" + nutzer.getLoginName() + "' entfernt (vormals '" + oldEmail + "').", nutzer.getId());
            } else {
                if (Nutzer.findByEmail(dto.email) != null) {
                    throw new UpdateNutzerException("Die neue E-Mail-Adresse wird bereits verwendet.");
                }
                nutzer.setEmail(dto.email);
                protokollService.log(ProtokollKategorie.NUTZER, "E-Mail-Adresse geändert",
                    "E-Mail-Adresse für Nutzer '" + nutzer.getLoginName() + "' von '" + oldEmail + "' zu '" + dto.email + "' geändert.", nutzer.getId());
            }
        }

        nutzer.setFirstName(dto.firstName);
        nutzer.setLastName(dto.lastName);
        nutzer.setActive(dto.isActive);
        keycloakUserProvisioningService.updateUser(nutzer);

        if (null != vUpdateIds) {
            Set<Long> oldVIds = nutzer.getVeranstaltungen().stream().map(IdEntity::getId).collect(Collectors.toSet());
            Set<Long> vNewIdSet = new HashSet<>(vUpdateIds);

            Set<Long> toRemoves = difference(oldVIds, vNewIdSet).toSet();

            // alte ID nicht in updateIds enthalten -> entfernen
            for (Long toRemove : toRemoves) {
                Veranstaltung v = Veranstaltung.findById(toRemove);
                if (null != v) {
                    nutzer.removeVeranstaltung(v);
                }
            }

            Set<Long> toAdds = difference(vNewIdSet, oldVIds).toSet();
            for (Long toAdd : toAdds) {
                Veranstaltung v = Veranstaltung.findById(toAdd);
                if (null == v) {
                    LOG.error("Unbekannte Veranstaltung zu id: " + toAdd);
                } else {
                    nutzer.addVeranstaltung(v);
                }
            }
        }

        if (nutzer instanceof Referent r) {
            r.setJobRole(dto.jobRole);
            r.setOrganisation(dto.organisation);
        } else if (nutzer instanceof Teilnehmer t) {
            if (null != dto.gruppen) {
                // Voller Ersatz statt nur Hinzufuegen - sonst bleiben im Modal abgewaehlte
                // Gruppen unveraendert bestehen (siehe Bugreport: Gruppenzugehoerigkeit liess
                // sich ueber den Bearbeiten-Dialog nicht mehr entfernen).
                t.setGruppen(dto.gruppen);
            }
            if (null != dto.neigungen) {
                t.setNeigungen(dto.neigungen);
            }
            if (null != dto.prioritaeten) {
                dto.prioritaeten.forEach(prioDto -> {
                    Wahlvortrag wv = Wahlvortrag.findById(prioDto.vortragId);
                    if (null == wv) {
                        LOG.error("Unbekannter Wahlvortrag zu id: " + prioDto.vortragId);
                    } else {
                        Prioritaet prioritaet = new Prioritaet(t, Wahlvortrag.findById(prioDto.vortragId), prioDto.prioWert);
                        prioritaet.persist();
                        t.addPrioritaet(prioritaet);
                    }
                });
            }
        }

        nutzer.persistAndFlush();

        protokollService.log(ProtokollKategorie.NUTZER, "Nutzer aktualisiert", "Nutzer '" + nutzer.getEmail() + "' aktualisiert.", nutzer.getId());
        return NutzerDto.from(nutzer);
    }


    @Transactional
    @Override
    public void inviteUserToEvent(Long nutzerId, Long veranstaltungId) {
        Objects.requireNonNull(nutzerId, "userId darf nicht null sein.");
        Objects.requireNonNull(veranstaltungId, "veranstaltungId darf nicht null sein.");

        Nutzer nutzer = Nutzer.findById(nutzerId);
        if (null == nutzer) {
            throw new EntityNotFoundException(Nutzer.class, "Nutzer nicht gefunden.");
        }

        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        if (null == veranstaltung) {
            throw new EntityNotFoundException(Veranstaltung.class, "Nutzer oder Veranstaltung nicht gefunden.");
        }

        // Validierung: Veranstaltung darf nicht in der Vergangenheit liegen (Enddatum prüfen)
        LocalDateTime now = LocalDateTime.now();
        if (veranstaltung.getEndetAm() != null && veranstaltung.getEndetAm().isBefore(now)) {
            throw new VeranstaltungException("Die Veranstaltung '" + veranstaltung.getName() + "' ist bereits beendet.");
        }

        if (!nutzer.getVeranstaltungen().contains(veranstaltung)) {
            nutzer.addVeranstaltung(veranstaltung);
            mailService.sendEinladungZuVeranstaltung(nutzer, veranstaltung);
            LOG.info("Nutzer " + nutzer.getEmail() + " zu Veranstaltung " + veranstaltung.getName() + " eingeladen.");
            protokollService.log(ProtokollKategorie.SECURITY, "Nutzer zu Veranstaltung eingeladen", "Nutzer '" + nutzer.getEmail() + "' zu '" + veranstaltung.getName() + "' eingeladen.", veranstaltung.getId(), veranstaltung.getId());
        } else {
            LOG.info("Nutzer " + nutzer.getEmail() + " ist bereits für Veranstaltung " + veranstaltung.getName() + " registriert.");
        }
    }


    @Transactional
    @Override
    public boolean deleteUser(Long id) {
        Nutzer nutzer = Nutzer.findById(id);
        if (nutzer != null) {
            // Send user deletion notification email BEFORE deleting the user
            mailService.sendUserDeletionNotification(nutzer);

            String email = nutzer.getEmail();
            keycloakUserProvisioningService.deleteUser(nutzer);
            boolean deleted = Nutzer.deleteById(id);
            if (deleted) {
                protokollService.log(ProtokollKategorie.NUTZER, "Nutzer gelöscht", "Nutzer '" + email + "' gelöscht.", id);
            }
            return deleted;
        }
        return false;
    }


    /**
     * Setzt das Passwort eines beliebigen Nutzers in Keycloak direkt - Rettungsweg für Konten
     * ohne (funktionierende) E-Mail-Adresse, die den Passwort-Reset-Flow von Keycloak selbst
     * nicht nutzen können.
     */
    @Transactional
    @Override
    public boolean resetPassword(Long id, String newPassword) {
        Nutzer nutzer = Nutzer.findById(id);
        if (null == nutzer) {
            return false;
        }
        if (StringUtils.isBlank(newPassword) || newPassword.length() < 8) {
            throw new BusinessException("Das neue Passwort muss mindestens 8 Zeichen lang sein.");
        }

        keycloakUserProvisioningService.resetPassword(nutzer, newPassword);
        protokollService.log(ProtokollKategorie.SECURITY, "Passwort durch Organisator zurückgesetzt",
            "Passwort für Nutzer '" + nutzer.getLoginName() + "' wurde durch einen Organisator zurückgesetzt.", nutzer.getId());
        return true;
    }


    @Transactional
    @Override
    public void toggleUserStatus(Long id) {
        Nutzer entity = Nutzer.findById(id);
        if (entity != null) {
            entity.setActive(!entity.isActive());
            keycloakUserProvisioningService.updateUser(entity);
            protokollService.log(ProtokollKategorie.NUTZER, "Nutzer-Status geändert", "Status von '" + entity.getEmail() + "' auf " + (entity.isActive() ?
                "aktiv" : "inaktiv") + " geändert.", id);
        }
    }


    @Override
    public List<Vortrag> getAllVortraege(Long veranstaltungId) {
        return Vortrag.find("veranstaltung.id = ?1", veranstaltungId).list();
    }


    @Override
    public Vortrag getVeranstaltungsVortrag(Long veranstaltungId, Long vortragId) {
        return Vortrag.find("veranstaltung.id = ?1 and id = ?2", veranstaltungId, vortragId).firstResult();
    }


    @Override
    public List<Referent> getAllReferenten(Long veranstaltungId) {
        return Nutzer.find("role = 'REFERENT' AND veranstaltung.id = ?1", veranstaltungId).list();
    }


    @Transactional
    @Override
    public Vortrag createVortrag(VortragDto vortragDto) {
        Objects.requireNonNull(vortragDto, "VortragDTO darf nicht null sein.");
        Long veranstaltungId = vortragDto.veranstaltungId;
        Objects.requireNonNull(veranstaltungId, "veranstaltungId darf nicht null sein.");
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        Objects.requireNonNull(veranstaltung, "Unbekannte Veranstaltung zu id: " + veranstaltungId + ".");
        Referent referent = Referent.findById(vortragDto.referentId);
        Objects.requireNonNull(referent, "Unbekannter Referent zu id: " + vortragDto.referentId + ".");

        Vortrag created;

        if (vortragDto.istPflicht) {
            if (null == vortragDto.pflichtSlotId
                || null == vortragDto.pflichtRaumId
                || StringUtils.isBlank(vortragDto.pflichtGruppe)) {
                throw new CreateVortragException("Für Pflichtvorträge müssen Slot, Raum und Gruppe angegeben werden.");
            }

            Raum pflichtRaum = Raum.findById(vortragDto.pflichtRaumId);
            Objects.requireNonNull(pflichtRaum, "Unbekannter Raum zu id: " + vortragDto.pflichtRaumId + ".");
            Slot pflichtSlot = Slot.findById(vortragDto.pflichtSlotId);
            Objects.requireNonNull(pflichtSlot, "Unbekannter Slot zu id: " + vortragDto.pflichtSlotId + ".");

            // Vorbedingungen prüfen
            if (RaumVerfuegbarkeit.isRaumGebucht(vortragDto.pflichtRaumId, vortragDto.pflichtSlotId, veranstaltungId)) {
                throw new CreateVortragException("Raum '" + pflichtRaum.getName() + "' ist im Slot '"
                    + pflichtSlot.getDescription() + "' bereits belegt. (" +
                    veranstaltung.getName() + ")");
            }

            List<Teilnehmer> teilnehmerDerGruppe = getGruppenTeilnehmer(vortragDto.pflichtGruppe, veranstaltung);
            List<String> nichtVerfuegbareTeilnehmer =
                teilnehmerDerGruppe.stream()
                    .map(tn -> {
                            NutzerVerfuegbarkeit nv = tn.getVerfuegbarkeit(veranstaltung);
                            if (null == nv || nv.isVerfuegbar(vortragDto.pflichtSlotId)) {
                                return null;
                            } else {
                                return tn.getEmail();
                            }
                        }
                    )
                    .filter(Objects::nonNull)
                    .toList();

            if (!nichtVerfuegbareTeilnehmer.isEmpty()) {
                throw new CreateVortragException("Teilnehmer der Gruppe '" + vortragDto.pflichtGruppe
                    + "' sind im Slot '" + pflichtSlot.getDescription() + "' für '"
                    + veranstaltung.getName() + "'  nicht verfügbar: "
                    + String.join(", ", nichtVerfuegbareTeilnehmer) + ".");
            }

            if (kapazitaetZuGering(pflichtRaum, vortragDto.pflichtGruppe, veranstaltung)) {
                throw new CreateVortragException("Raumkapazität von '" + pflichtRaum.getName() + "' reicht für die Gruppe '"
                    + vortragDto.pflichtGruppe + "' nicht aus. (" + veranstaltung.getName() + ")");
            }

            // map vortragDTO to Vortrag
            created = Pflichtvortrag.create(vortragDto.titel, vortragDto.inhalt, referent,
                vortragDto.pflichtGruppe, pflichtRaum, pflichtSlot, veranstaltung);

            // Update availabilities
            RaumVerfuegbarkeit rv = RaumVerfuegbarkeit.findById(rvId(pflichtRaum, veranstaltung));
            rv.removeSlot(pflichtSlot);

            for (Teilnehmer teilnehmer : teilnehmerDerGruppe) {
                NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvId(teilnehmer, veranstaltung));
                if (nv != null) {
                    nv.removeSlot(pflichtSlot);
                }
            }
        } else {
            created = Wahlvortrag.create(vortragDto.titel, vortragDto.inhalt, referent,
                vortragDto.wiederholbar, vortragDto.maxWiederholungen, veranstaltung);
            ((Wahlvortrag) created).setNeigungen(vortragDto.neigungen);
        }


        created.setAusstattung(vortragDto.ausstattung);
        created.setAbschluss(vortragDto.abschluss);
        created.persistAndFlush();


        veranstaltung.addVortrag(created);
        veranstaltung.persistAndFlush();

        protokollService.log(ProtokollKategorie.VORTRAEGE, "Vortrag erstellt",
            "Vortrag '" + created.getTitel() + "' (" + (created.istPflicht() ? "Pflicht" : "Wahl") + ") erstellt.",
            created.getId(), veranstaltung.getId());

        return created;
    }


    @Transactional
    @Override
    public int importOrganisatorenFromCsv(Path csvFilePath) throws Exception {
        int count = 0;
        try (Reader reader = CsvHelper.openCsvReader(csvFilePath)) {
            CsvToBean<OrganisatorCsvDto> csvToBean = new CsvToBeanBuilder<OrganisatorCsvDto>(reader)
                .withType(OrganisatorCsvDto.class)
                .withSeparator(';')
                .withFilter(line -> line.length > 0 && !line[0].startsWith("#"))
                .withIgnoreLeadingWhiteSpace(true)
                .withThrowExceptions(false)
                .build();

            List<OrganisatorCsvDto> beans = csvToBean.parse();

            csvToBean.getCapturedExceptions().forEach(e ->
                LOG.error("CSV-Parsing-Fehler in " + csvFilePath.getFileName() + " (Zeile " + e.getLineNumber() + "): " + e.getMessage())
            );

            for (OrganisatorCsvDto dto : beans) {
                if (StringUtils.isBlank(dto.loginName)) {
                    LOG.warn("Organisator-Zeile übersprungen: loginName fehlt.");
                    continue;
                }
                // Siehe createUser/updateUser: ein Organisator-Konto ohne E-Mail-Adresse ist bei
                // einem vergessenen Passwort unwiederbringlich verloren.
                if (StringUtils.isBlank(dto.email)) {
                    LOG.warn("Organisator-Zeile übersprungen: E-Mail-Adresse fehlt (loginName: " + dto.loginName + ").");
                    continue;
                }
                String loginName = dto.loginName.trim().toLowerCase();
                Nutzer vorhandenerNutzer = Nutzer.findByLoginNameOrEmail(loginName, dto.email);
                if (null == vorhandenerNutzer) {
                    Organisator a = new Organisator();
                    a.assignLoginName(loginName);
                    a.setEmail(dto.email.trim().toLowerCase());
                    a.setFirstName(dto.vorname);
                    a.setLastName(dto.nachname);
                    keycloakUserProvisioningService.createUser(a);
                    a.persistAndFlush();
                    count++;
                    protokollService.log(ProtokollKategorie.NUTZER, "Organisator importiert", "Organisator '" + loginName + "' via CSV importiert.", a.getId());
                } else {
                    LOG.warn("Organisator '" + loginName + "' übersprungen: Nutzer mit diesem LoginName oder dieser E-Mail existiert bereits (vorhandener loginName: '"
                        + vorhandenerNutzer.getLoginName() + "').");
                }
            }
        } catch (Exception e) {
            LOG.error("Kritischer Fehler beim Importieren der Organisatoren aus CSV: " + csvFilePath, e);
            throw e;
        }
        LOG.info("Organisatoren-Import abgeschlossen: " + count + " Organisator(en) aus " + csvFilePath + " importiert.");
        return count;
    }


    @Transactional
    @Override
    public int importVortraegeFromCsv(Path csvFilePath, Long veranstaltungId) {
        int count = 0;
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);

        if (null == veranstaltung) {
            LOG.error("CSV-Import (Vorträge) abgebrochen: Veranstaltung mit ID " + veranstaltungId + " nicht gefunden.");
            throw new CsvImportException(csvFilePath, "Veranstaltung '" + veranstaltungId + "' nicht gefunden.");
        }
        List<Raum> v_raeume = veranstaltung.getRaeume();

        Map<String, Map<Gebaeude, Raum>> raeumeByName = new HashMap<>();
        for (Raum r : v_raeume) {
            if (!raeumeByName.containsKey(r.getName())) {
                raeumeByName.put(r.getName(), new HashMap<>());
            }
            raeumeByName.get(r.getName()).put(r.getGebaeude(), r);
        }

        Map<String, Slot> slotsByName = veranstaltung.getSlots().stream().collect(Collectors.toMap(Slot::getDescription, s -> s));

        try (Reader reader = CsvHelper.openCsvReader(csvFilePath)) {
            CsvToBean<VortragCsvDto> csvToBean = new CsvToBeanBuilder<VortragCsvDto>(reader)
                .withType(VortragCsvDto.class)
                .withSeparator(';')
                .withFilter(line -> line.length > 0 && !line[0].startsWith("#"))
                .withIgnoreEmptyLine(true)
                .withIgnoreLeadingWhiteSpace(true)
                .withThrowExceptions(false) // Allow parsing to continue on errors
                .build();

            List<VortragCsvDto> beans = csvToBean.parse();

            csvToBean.getCapturedExceptions().forEach(e ->
                LOG.error("CSV-Parsing-Fehler in " + csvFilePath.getFileName() + " (Zeile " + e.getLineNumber() + "): " + e.getMessage())
            );

            for (VortragCsvDto csvDto : beans) {
                if (StringUtils.isBlank(csvDto.titel)) {
                    LOG.warn("Vortrag übersprungen: Titel fehlt.");
                    continue;
                }

                String titel = csvDto.titel;
                String inhalt = csvDto.inhalt;
                if (titel.length() > Vortrag.MAX_VORTRAG_TITEL_LAENGE) {
                    inhalt = titel;
                    titel = kuerzenAnWortgrenze(titel, Vortrag.MAX_VORTRAG_TITEL_LAENGE);
                    LOG.info("Vortrag-Titel gekürzt (> " + Vortrag.MAX_VORTRAG_TITEL_LAENGE + " Zeichen): '" + titel
                        + "'. Voller Titel wurde als Inhalt gespeichert.");
                }

                VortragDto dto = new VortragDto();
                dto.veranstaltungId = veranstaltungId;
                dto.istPflicht = csvDto.istPflicht;
                dto.titel = titel;
                dto.inhalt = inhalt;
                dto.ausstattung = csvDto.ausstattung;
                Nutzer referent = Nutzer.findByLoginName(csvDto.referentLoginName);

                if (referent instanceof Referent) {
                    if (csvDto.istPflicht) {
                        if (StringUtils.isBlank(csvDto.pflichtGruppe)) {
                            LOG.warn("Vortrag '" + csvDto.titel + "': Gruppe fehlt." +
                                PV_FAIL_MESSAGE);
                            continue;
                        } else {
                            if (veranstaltung.getGruppen().contains(csvDto.pflichtGruppe)) {
                                dto.pflichtGruppe = csvDto.pflichtGruppe;
                            } else {
                                LOG.warn("Unbekannte Gruppe '" + csvDto.pflichtGruppe + "' für '" +
                                    dto.titel + "' in Veranstaltung '" + veranstaltung.getName() + "'" +
                                    PV_FAIL_MESSAGE);
                                continue;
                            }
                        }

                        if (StringUtils.isBlank(csvDto.pflichtSlot)
                            || !slotsByName.containsKey(csvDto.pflichtSlot)) {
                            LOG.warn("Vortrag '" + csvDto.titel + "': Slot '" + csvDto.pflichtSlot + "' nicht gefunden"
                                + PV_FAIL_MESSAGE);
                            continue;
                        }

                        dto.pflichtSlotId = slotsByName.get(csvDto.pflichtSlot).getId();

                        Map<Gebaeude, Raum> gebaeudeRaumMap = raeumeByName.get(csvDto.pflichtRaum);
                        if (null == gebaeudeRaumMap) {
                            if (StringUtils.isBlank(csvDto.pflichtRaum)) {
                                LOG.warn("Vortrag '" + csvDto.titel + "': Unbekannter Raum '" + csvDto.pflichtRaum + "'" + PV_FAIL_MESSAGE);
                            }
                            continue;
                        } else {
                            Set<Gebaeude> gebaeudeSet = veranstaltung.getGebaeude().stream()
                                .filter(gebaeudeRaumMap::containsKey)
                                .collect(Collectors.toSet());

                            if (gebaeudeSet.isEmpty()) {
                                LOG.warn("Vortrag '" + csvDto.titel + "': Raum '" + csvDto.pflichtRaum + "' nicht gefunden in Veranstaltungsgebäuden"
                                    + PV_FAIL_MESSAGE);
                                continue;
                            } else if (gebaeudeSet.size() == 1) {
                                dto.pflichtRaumId = gebaeudeRaumMap.get(gebaeudeSet.iterator().next()).getId();
                            } else {
                                LOG.warn("Vortrag '" + csvDto.titel + "': Raum '" + csvDto.pflichtRaum + "' nicht eindeutig in Veranstaltungsgebäuden"
                                    + PV_FAIL_MESSAGE);
                                continue;
                            }
                        }

                    } else {
                        dto.wiederholbar = csvDto.wiederholbar;
                        dto.maxWiederholungen = csvDto.maxWiederholungen;
                    }

                    if (StringUtils.isNotBlank(csvDto.neigungen)) {
                        Set<Neigung> gefundeneNeigungen = new HashSet<>();
                        for (String token : csvDto.neigungen.split("\\|")) {
                            Neigung gefunden = findNeigungByPrefix(token);
                            if (gefunden != null) {
                                gefundeneNeigungen.add(gefunden);
                            } else if (StringUtils.isNotBlank(token)) {
                                LOG.warn("Vortrag '" + csvDto.titel + "': Ungültige oder nicht eindeutige Neigung '" + token.trim() + "' übersprungen.");
                            }
                        }
                        dto.neigungen = gefundeneNeigungen;
                    }

                    if (StringUtils.isNotBlank(csvDto.abschluss)) {
                        AbschlussTyp foundAbschluss = findAbschlussByPrefix(csvDto.abschluss);
                        if (foundAbschluss != null) {
                            dto.abschluss = foundAbschluss;
                        } else {
                            LOG.warn("Vortrag '" + csvDto.titel + "' übersprungen: Ungültiger oder nicht eindeutiger Abschluss '" + csvDto.abschluss + "'.");
                            continue;
                        }
                    }

                    dto.referentId = referent.getId();

                    try {
                        Vortrag vortrag = createVortrag(dto);

                        count++;
                        protokollService.log(ProtokollKategorie.VORTRAEGE, "Vortrag importiert",
                            "Vortrag '" + dto.titel + "' via CSV importiert.", vortrag.getId(), vortrag.getVeranstaltung().getId());
                    } catch (IllegalArgumentException e) {
                        LOG.warn("Vortrag '" + csvDto.titel + "' übersprungen aufgrund von Validierungsfehler: " + e.getMessage());
                    }

                } else {
                    LOG.warn("Vortrag '" + csvDto.titel + "' übersprungen: Referent mit loginName " + csvDto.referentLoginName + " nicht gefunden oder kein Referent.");
                }
            }
        } catch (Exception e) {
            LOG.error("Kritischer Fehler beim Importieren der Vorträge aus CSV: " + csvFilePath, e);
            throw new CsvImportException(csvFilePath, e.getMessage());
        }
        LOG.info("Vortrag-Import abgeschlossen: " + count + " Vorträge aus " + csvFilePath + " importiert.");
        return count;
    }


    /**
     * Kürzt einen zu langen Vortragstitel an einer Wortgrenze auf unter maxLaenge Zeichen.
     * Enthält das Kürzungsfenster keine Wortgrenze, wird hart bei maxLaenge - 1 abgeschnitten.
     */
    private static String kuerzenAnWortgrenze(String titel, int maxLaenge) {
        String fenster = titel.substring(0, maxLaenge - 1);
        int lastSpace = fenster.lastIndexOf(' ');
        String gekuerzt = (lastSpace > 0) ? fenster.substring(0, lastSpace) : fenster;
        return gekuerzt.trim();
    }


    private Neigung findNeigungByPrefix(String prefix) {
        if (StringUtils.isBlank(prefix)) {
            return null;
        }
        String normalizedPrefix = prefix.trim().toLowerCase();
        List<Neigung> matches = new ArrayList<>();
        for (Neigung neigung : Neigung.values()) {
            if (neigung.getBezeichnung().toLowerCase().startsWith(normalizedPrefix)) {
                matches.add(neigung);
            }
        }
        if (matches.size() == 1) {
            return matches.getFirst();
        }
        return null; // Not found or ambiguous
    }


    private AbschlussTyp findAbschlussByPrefix(String prefix) {
        if (StringUtils.isBlank(prefix)) {
            return null;
        }
        String normalizedPrefix = prefix.trim().toLowerCase();
        List<AbschlussTyp> matches = new ArrayList<>();
        for (AbschlussTyp typ : AbschlussTyp.values()) {
            if (typ.getName().toLowerCase().startsWith(normalizedPrefix)) {
                matches.add(typ);
            }
        }
        if (matches.size() == 1) {
            return matches.getFirst();
        }
        return null; // Not found or ambiguous
    }


    @Transactional
    @Override
    public int importTeilnehmerWvPriosFromCsv(Path csvFilePath, Long veranstaltungId) throws Exception {
        int count = 0;
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        if (null == veranstaltung) {
            LOG.error("CSV-Import (WV-Prioritäten) abgebrochen: Veranstaltung mit ID " + veranstaltungId + " nicht gefunden.");
            throw new CsvImportException(csvFilePath, "Veranstaltung '" + veranstaltungId + "' nicht gefunden.");
        }

        boolean headerLineFound = false;
        Map<Integer, Wahlvortrag> legendIndexMap;

        try (BufferedReader reader = new BufferedReader(CsvHelper.openCsvReader(csvFilePath))) {
            String line = reader.readLine();

            if (line.startsWith(LEGENDE_TOKEN)) {
                List<Wahlvortrag> wahlvortraege = veranstaltung.getVortraege().stream()
                    .filter(v -> v instanceof Wahlvortrag)
                    .map(v -> (Wahlvortrag) v)
                    .toList();
                LOG.debug("Titel:\n" + wahlvortraege.stream().map(Wahlvortrag::getTitel).collect(Collectors.joining("\n")));
                legendIndexMap = parseLegende(line.substring(LEGENDE_TOKEN.length()), wahlvortraege);

                if (LOG.isDebugEnabled()) {
                    LOG.debug("legendIndexMap:\n" + legendIndexMap.entrySet().stream().sorted(Map.Entry.comparingByKey())
                        .map(entry -> entry.getKey() + " -> " + entry.getValue().getTitel())
                        .collect(Collectors.joining("\n")));
                }
            } else {
                LOG.error("CSV-Import (WV-Prioritäten) abgebrochen: Legende fehlt.");
                throw new CsvImportException(csvFilePath, "Legende fehlt.");
            }

            // Read data rows
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) {
                    continue;
                }

                if (!headerLineFound) {
                    if (CSV_PRIO_HEADER.equals(line)) {
                        headerLineFound = true;
                        continue;
                    } else {
                        LOG.error("CSV-Import (WV-Prioritäten) abgebrochen: Ungültiger Header");
                        throw new CsvImportException(csvFilePath,
                            "Ungültiger Header für Prio-Import in " + csvFilePath.getFileName());
                    }
                }

                String[] lineItems = line.split(";");
                String teilnehmerLoginName = lineItems[0].trim().toLowerCase();

                Nutzer nutzer = Nutzer.findByLoginName(teilnehmerLoginName);
                if (!(nutzer instanceof Teilnehmer teilnehmer)) {
                    LOG.warn("Priorität für '" + teilnehmerLoginName + "' übersprungen: Nutzer ist kein Teilnehmer.");
                    continue;
                }

                if (teilnehmer.getVeranstaltungen().stream().noneMatch(v -> v.getId().equals(veranstaltungId))) {
                    LOG.warn("Priorität für '" + teilnehmerLoginName + "' übersprungen: Teilnehmer gehört nicht zur Veranstaltung.");
                    continue;
                }

                String wvPrioStr = lineItems.length > 1 ? lineItems[1] : null;
                if (StringUtils.isNotBlank(wvPrioStr)) {
                    String[] wvPrios = wvPrioStr.split(",");

                    for (String wvPrio : wvPrios) {
                        wvPrio = wvPrio.trim();
                        if (!wvPrio.matches("\\d+\\s*:\\s*\\d+")) {
                            LOG.warn("Priorität für '" + teilnehmerLoginName + "' übersprungen: Prio-Format " + wvPrio + " ist " +
                                "ungültig.");
                            continue;
                        }
                        String[] data = wvPrio.split(":");
                        Integer index = Integer.parseInt(data[0].trim());
                        Wahlvortrag vortrag = legendIndexMap.get(index);

                        if (null == vortrag || !vortrag.getVeranstaltung().getId().equals(veranstaltungId)) {
                            LOG.warn("Priorität für '" + teilnehmerLoginName + "' übersprungen: legendenIndex " + index + " ist" +
                                " ungültig oder kein Wahlvortrag dieser Veranstaltung.");
                            continue;
                        }

                        try {
                            int prioWert = Integer.parseInt(data[1].trim());

                            Prioritaet prioritaet = Prioritaet.find("teilnehmer = ?1 and vortrag = ?2", teilnehmer, vortrag).firstResult();
                            if (null == prioritaet) {
                                prioritaet = new Prioritaet();
                                prioritaet.setTeilnehmer(teilnehmer);
                                prioritaet.setVortrag(vortrag);
                            }

                            prioritaet.setPrioWert(prioWert);
                            prioritaet.persistAndFlush();
                            count++;
                            protokollService.log(ProtokollKategorie.VORTRAEGE, "Priorität importiert", "Priorität für '" + teilnehmer.getEmail() + "' für Vortrag '" + vortrag.getTitel() + "' auf " + prioWert + " gesetzt.", vortrag.getId(), veranstaltungId);
                        } catch (NumberFormatException e) {
                            LOG.warn("Ungültiger Prioritätswert für Teilnehmer " + teilnehmerLoginName + " und Vortrag " + vortrag.getTitel() + ": " + e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Kritischer Fehler beim Importieren der Prioritäten aus CSV: " + csvFilePath, e);
            throw e;
        }
        LOG.info("Prioritäten-Import abgeschlossen: " + count + " Prioritäten aus " + csvFilePath + " importiert/aktualisiert.");
        return count;
    }


    private Map<Integer, Wahlvortrag> parseLegende(String legende, List<Wahlvortrag> wahlvortraege) {
        Map<Integer, Wahlvortrag> indexToVortragsIdMap = new HashMap<>();
        boolean macheLegendenVorschlag = false;

        for (String entry : legende.split(LEGENDEN_EINTRAG_SEP)) {
            String[] parts = entry.split("=");
            if (parts.length != 2) {
                LOG.warn("Ungültiger Legenden-Eintrag '" + entry + "'");
            } else {
                String titelSchluessel = TemplateExtensions.truncTo(parts[1].trim(), Vortrag.MAX_VORTRAG_TITEL_LAENGE);
                Wahlvortrag wv = wahlvortraege.stream()
                    .filter(v -> v.getTitel().contains(titelSchluessel))
                    .findFirst()
                    .orElse(null);

                if (wv != null) {
                    indexToVortragsIdMap.put(Integer.parseInt(parts[0].trim()), wv);
                } else {
                    LOG.warn("Legenden-Parser: Kein Wahlvortrag gefunden mit Legenden-Schlüssel '" + titelSchluessel);
                    macheLegendenVorschlag = true;
                }
            }
        }

        if (macheLegendenVorschlag) {
            Map<String, String> besteLegende = besteLegende(wahlvortraege);
            StringBuilder vorschlag = new StringBuilder("Vorschlag für Legende:\n");
            int index = 0;
            for (Wahlvortrag wv : wahlvortraege) {
                vorschlag.append(" # ")
                    .append(++index).append("=")
                    .append(besteLegende.get(wv.getTitel())).append('\n');
            }

            LOG.info(vorschlag.toString());
        }

        return indexToVortragsIdMap;
    }


    private static Map<String, String> besteLegende(List<Wahlvortrag> wahlvortraege) {
        Map<String, String> ergebnis = new HashMap<>();
        List<String> alleWvtitel = wahlvortraege.stream().map(Wahlvortrag::getTitel).toList();

        for (String zielTitel : alleWvtitel) {
            String eindeutigerBezeichner = null;
            String[] woerter = zielTitel.toLowerCase().split("\\s+");

            // Prüfe Wort-Sequenzen beginnend mit Länge 1 bis zur maximalen Wortanzahl
            for (int laenge = 1; laenge <= woerter.length; laenge++) {
                boolean bezeichnerGefunden = false;

                for (int i = 0; i <= woerter.length - laenge; i++) {
                    String[] sequenz = Arrays.copyOfRange(woerter, i, i + laenge);
                    String kandidat = String.join(" ", sequenz);

                    // Überprüfe, ob der Kandidat eindeutig ist
                    if (kommtNurInEinemTitelVor(kandidat, alleWvtitel, zielTitel)) {
                        eindeutigerBezeichner = kandidat;
                        bezeichnerGefunden = true;
                        break;
                    }
                }
                if (bezeichnerGefunden) {
                    break;
                }
            }

            ergebnis.put(zielTitel, eindeutigerBezeichner != null ? eindeutigerBezeichner : "Kein eindeutiger Bezeichner möglich");
        }

        return ergebnis;
    }


    private static boolean kommtNurInEinemTitelVor(String kandidat, List<String> alleTitel, String aktuellerTitel) {
        int anzahlTreffer = 0;
        for (String satz : alleTitel) {
            if (satz.toLowerCase().contains(kandidat)) {
                anzahlTreffer++;
                // Wenn wir im falschen Satz suchen oder mehr als 1 Treffer existieren, abbrechen
                if (anzahlTreffer > 1 || satz.equals(aktuellerTitel)) {
                    continue;
                }
                return false;
            }
        }

        return anzahlTreffer == 1;
    }


    @Override
    public List<Slot> getAllEventSlots(Long veranstaltungId) {
        return Slot.find("veranstaltung.id = ?1", veranstaltungId).list();
    }


    @Transactional
    @Override
    public Slot createSlot(SlotDto slotDto, Long veranstaltungId) {
        Objects.requireNonNull(veranstaltungId, "veranstaltungId darf nicht NULL sein");
        if (slotDto.veranstaltungId != null && !slotDto.veranstaltungId.equals(veranstaltungId)) {
            throw new CreateSlotException("Mismatching veranstaltungIds");
        }

        Veranstaltung v = Veranstaltung.findById(veranstaltungId);
        validateSlot(slotDto, v, null);

        Slot slot = new Slot(slotDto.description, slotDto.startTime, slotDto.endTime, v);
        slot.persistAndFlush();
        v.addSlot(slot);
        v.persistAndFlush();

        // Create availability for all participants of the event
        for (Nutzer nutzer : v.getNutzer()) {
            if (nutzer instanceof Teilnehmer || nutzer instanceof Referent) {
                NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvIdL(nutzer.getId(), veranstaltungId));

                if (null != nv) {
                    nv.addSlot(slot);
                    nv.persistAndFlush();
                }
            }
        }

        protokollService.log(ProtokollKategorie.VERANSTALTUNG, "Zeit-Slot erstellt",
            "Slot '" + slotDto.description + "' für '" + v.getName() + "' erstellt.", slot.getId(), veranstaltungId);

        return slot;
    }


    @Transactional
    @Override
    public Slot updateSlot(Long slotId, SlotDto updated, Long veranstaltungId) {
        Objects.requireNonNull(slotId, "slotId darf nicht NULL sein");
        Slot entity = Slot.findById(slotId);

        if (null == entity) {
            return null;
        }

        Objects.requireNonNull(veranstaltungId, "veranstaltungId darf nicht NULL sein");

        if (!Objects.equals(entity.getVersion(), updated.version)) {
            throw new OptimisticLockException("Der Slot wurde zwischenzeitlich von Dritten geändert. Bitte aktualisieren Sie die Daten und versuchen Sie es erneut.");
        }
        Veranstaltung v = Veranstaltung.findById(veranstaltungId);
        if (entity.getVeranstaltung().getId().equals(veranstaltungId)) {
            validateSlot(updated, v, slotId);
            entity.setDescription(updated.description);
            entity.setStartTime(updated.startTime);
            entity.setEndTime(updated.endTime);
            protokollService.log(ProtokollKategorie.VERANSTALTUNG, "Zeit-Slot aktualisiert", "Slot '" + entity.getDescription() + "' aktualisiert.", entity.getId(), veranstaltungId);
        }
        return entity;
    }


    private void validateSlot(SlotDto slotDto, Veranstaltung v, Long excludeId) {
        Objects.requireNonNull(slotDto, "Slot darf nicht NULL sein");
        Objects.requireNonNull(slotDto.description, "Slot-Beschreibung darf nicht NULL sein");
        Objects.requireNonNull(v, "Veranstaltung darf nicht NULL sein");

        if (null == slotDto.startTime || null == slotDto.endTime) {
            throw new CreateSlotException("Beginn und Ende müssen gesetzt sein.");
        }
        if (!slotDto.endTime.isAfter(slotDto.startTime)) {
            throw new CreateSlotException("Das Ende muss nach dem Beginn liegen.");
        }
        if (slotDto.startTime.isBefore(v.getBeginntAm())) {
            throw new CreateSlotException("Der Slot darf nicht vor der Veranstaltung beginnen.");
        }
        // NEUE PRÜFUNG: Slot darf nicht nach dem Ende der Veranstaltung enden.
        if (slotDto.endTime.isAfter(v.getEndetAm())) {
            throw new CreateSlotException("Der Slot darf nicht nach der Veranstaltung enden.");
        }

        List<Slot> existing = Slot.find("veranstaltung = ?1", v).list();
        for (Slot other : existing) {
            if (other.getId().equals(excludeId)) {
                continue;
            }
            // Überschneidungsprüfung: (StartA < EndeB) AND (EndA > StartB)
            if (slotDto.startTime.isBefore(other.getEndTime()) && slotDto.endTime.isAfter(other.getStartTime())) {
                throw new CreateSlotException("Der Zeit-Slot überschneidet sich mit einem vorhandenen Intervall (" + other.getDescription() + ").");
            }
        }
    }


    @Transactional
    @Override
    public boolean deleteSlot(Long id, Veranstaltung veranstaltung) {
        Slot slot = Slot.findById(id);
        if (slot != null && slot.getVeranstaltung().getId().equals(veranstaltung.getId())) {
            String desc = slot.getDescription();

            // Delete all availabilities associated with this slot
            NutzerVerfuegbarkeit.<NutzerVerfuegbarkeit>find("veranstaltungId", veranstaltung.getId()).stream()
                .forEach(v -> v.removeSlot(id));
            RaumVerfuegbarkeit.<RaumVerfuegbarkeit>find("veranstaltungId", veranstaltung.getId()).stream()
                .forEach(v -> v.removeSlot(id));


            long count = Slot.delete("id = ?1", id);
            if (count > 0) {
                protokollService.log(ProtokollKategorie.VERANSTALTUNG, "Zeit-Slot gelöscht", "Slot '" + desc + "' gelöscht.", id, veranstaltung.getId());
                return true;
            }
        }
        return false;
    }


    @Transactional
    @Override
    public int importSlotsFromCsv(Path csvFilePath, Long veranstaltungId) {
        int count = 0;
        Veranstaltung v = Veranstaltung.findById(veranstaltungId);
        if (null == v) {
            LOG.error("CSV-Import (Slots) abgebrochen: Veranstaltung mit ID " + veranstaltungId + " nicht gefunden.");
            throw new IllegalArgumentException("Veranstaltung mit ID " + veranstaltungId + " nicht gefunden.");
        }
        try (Reader reader = CsvHelper.openCsvReader(csvFilePath)) {
            CsvToBean<SlotCsvDto> csvToBean = new CsvToBeanBuilder<SlotCsvDto>(reader)
                .withType(SlotCsvDto.class)
                .withSeparator(';')
                .withFilter(line -> line.length > 0 && !line[0].startsWith("#"))
                .withIgnoreLeadingWhiteSpace(true)
                .withThrowExceptions(false)
                .build();

            List<SlotCsvDto> beans = csvToBean.parse();

            csvToBean.getCapturedExceptions().forEach(e ->
                LOG.error("CSV-Parsing-Fehler in " + csvFilePath.getFileName() + " (Zeile " + e.getLineNumber() + "): " + e.getMessage())
            );

            for (SlotCsvDto dto : beans) {
                if (StringUtils.isBlank(dto.bezeichnung)) {
                    LOG.warn("Slot übersprungen: Beschreibung fehlt.");
                    continue;
                }
                SlotDto slotDto = new SlotDto();
                slotDto.description = dto.bezeichnung;
                try {
                    slotDto.startTime = LocalDateTime.parse(dto.tag + " " + dto.beginntUm, DATE_FORMAT);
                    slotDto.endTime = LocalDateTime.parse(dto.tag + " " + dto.endetUm, DATE_FORMAT);
                } catch (Exception e) {
                    LOG.error("Fehler beim Parsen der Zeit für Slot '" + dto.bezeichnung + "': " + e.getMessage());
                    continue;
                }

                try {
                    Slot created = createSlot(slotDto, veranstaltungId);
                    count++;
                    protokollService.log(ProtokollKategorie.VERANSTALTUNG, "Zeit-Slot importiert",
                        "Slot '" + slotDto.description + "' via CSV importiert.", created.getId(), veranstaltungId);
                } catch (IllegalArgumentException e) {
                    LOG.warn("Slot '" + dto.bezeichnung + "' übersprungen aufgrund von Validierungsfehler: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            LOG.error("Kritischer Fehler beim Importieren der Slots aus CSV: " + csvFilePath, e);
            throw new CsvImportException(csvFilePath, e.getMessage());
        }
        LOG.info("Slot-Import abgeschlossen: " + count + " Slots " + csvFilePath +
            " importiert.");
        return count;
    }


    @Transactional
    @Override
    public VortragDto updateVortrag(Long vortragId, Long veranstaltungId, VortragDto updated) {
        Vortrag entity = Vortrag.findById(vortragId);
        if (null == entity || !entity.getVeranstaltung().getId().equals(veranstaltungId)) {
            return null;
        }

        if (!Objects.equals(entity.getVersion(), updated.version)) {
            throw new OptimisticLockException("Der Vortrag wurde zwischenzeitlich von Dritten geändert. Bitte aktualisieren Sie die Daten und versuchen Sie es erneut.");
        }

        // Check if type changes (e.g., Wahlvortrag to Pflichtvortrag or vice versa)
        // TODO von Pflicht-Vortrag nach Wahl-Vortrag erlauben?!
        if (entity.istPflicht() != updated.istPflicht) {
            throw new UpdateVortragException("Der Vortragstyp kann nicht geändert werden.");
        }

        entity.setTitel(updated.titel);
        entity.setInhalt(updated.inhalt);
        entity.setAusstattung(updated.ausstattung);
        entity.setAbschluss(updated.abschluss);

        if (entity instanceof Pflichtvortrag pv && updated.istPflicht) {
            pv.updatePflichtgruppe(updated.pflichtGruppe);
            pv.updatePflichtslot(Slot.findById(updated.pflichtSlotId));
            pv.updatePflichtraum(Raum.findById(updated.pflichtRaumId));
        } else if (entity instanceof Wahlvortrag wv && !updated.istPflicht) {
            wv.setWiederholbar(updated.wiederholbar);
            wv.setMaxWiederholungen(updated.maxWiederholungen);
            wv.setNeigungen(updated.neigungen);

            VortragVerfuegbarkeit vv = VortragVerfuegbarkeit.findById(vvIdL(updated.id, veranstaltungId));
            if (null == vv) {
                new VortragVerfuegbarkeit(updated.id, veranstaltungId, updated.verfuegbareSlotIds).persistAndFlush();
            } else {
                updated.verfuegbareSlotIds.forEach(vv::addSlot);
            }
        }
        entity.persistAndFlush();

        protokollService.log(ProtokollKategorie.VORTRAEGE, "Vortrag aktualisiert", "Vortrag '" + entity.getTitel() + "' aktualisiert.", entity.getId(), veranstaltungId);
        return VortragDto.from(entity);
    }


    @Transactional
    @Override
    public boolean deleteVortrag(Long id, Veranstaltung veranstaltung) {
        Objects.requireNonNull(id, "ID darf nicht NULL sein");
        Objects.requireNonNull(veranstaltung, "Veranstaltung darf nicht NULL sein");

        Vortrag entity = Vortrag.findById(id);
        if (null == entity || !entity.getVeranstaltung().getId().equals(veranstaltung.getId())) {
            return false;
        }

        if (entity instanceof Pflichtvortrag pv) {
            // delete RaumVerfuegbarkeit
            RaumVerfuegbarkeit rv = RaumVerfuegbarkeit.findById(rvId(pv.getPflichtraum(), veranstaltung));

            if (rv != null) {
                rv.addSlot(pv.getPflichtslot());
            }

            // delete NutzerVerfuegbarkeit'en
            List<Teilnehmer> teilnehmerDerGruppe = getGruppenTeilnehmer(pv.getPflichtgruppe(), veranstaltung);
            for (Teilnehmer teilnehmer : teilnehmerDerGruppe) {
                NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvId(teilnehmer, veranstaltung));
                if (nv != null) {
                    nv.addSlot(pv.getPflichtslot());
                }
            }
        }

        // delete VortragVerfuegbarkeit'en
        VortragVerfuegbarkeit vv = VortragVerfuegbarkeit.findById(vvIdL(id, veranstaltung.getId()));

        if (vv != null) {
            vv.delete();
        }

        String titel = entity.getTitel();
        boolean deleted = Vortrag.deleteById(id);

        if (deleted) {
            protokollService.log(ProtokollKategorie.VORTRAEGE, "Vortrag gelöscht", "Vortrag '" + titel + "' gelöscht.", id, veranstaltung.getId());
        }

        return deleted;
    }


    @Override
    public List<VortragStatDto> getStats(Long veranstaltungId) {
        List<Vortrag> all = Vortrag.find("veranstaltung.id = ?1", veranstaltungId).list();
        return all.stream().map(v -> new VortragStatDto(v.getTitel(), 0, 0, 0, 0, 0)).toList();
    }


    @Transactional
    @Override
    public List<RaumVerfuegbarkeitDto> getRaumVerfuegbarkeiten(Long veranstaltungId) {
        Veranstaltung aktuelleVeranstaltung = Veranstaltung.findById(veranstaltungId);
        if (null == aktuelleVeranstaltung) {
            return emptyList();
        }

        // 1. Lade alle relevanten Daten
        List<Raum> relevanteRaeume = aktuelleVeranstaltung.getRaeume();
        List<RaumVerfuegbarkeit> alleBelegungen = RaumVerfuegbarkeit.listAll();
        Map<Long, RaumVerfuegbarkeit> eigeneBelegungen = alleBelegungen.stream()
            .filter(rv -> rv.getVeranstaltungId().equals(veranstaltungId))
            .collect(Collectors.toMap(RaumVerfuegbarkeit::getRaumId, rv -> rv));

        // 2. Erstelle eine Map aller blockierten Zeitintervalle pro Raum (durch fremde Veranstaltungen)
        Map<Long, List<BlockingInfo>> blockierteIntervalleProRaum = new HashMap<>();
        alleBelegungen.stream()
            .filter(rv -> !rv.getVeranstaltungId().equals(veranstaltungId))
            .forEach(fremdeBelegung -> {
                Veranstaltung fremdeVeranstaltung = Veranstaltung.findById(fremdeBelegung.getVeranstaltungId());
                Set<Long> verfuegbareSlots = new HashSet<>(fremdeBelegung.getVerfuegbareSlotIds());

                fremdeVeranstaltung.getSlots().stream()
                    .filter(slot -> !verfuegbareSlots.contains(slot.getId())) // Finde die blockierten Slots
                    .forEach(blockierterSlot -> {
                        BlockingInfo info = new BlockingInfo(
                            blockierterSlot.getStartTime(),
                            blockierterSlot.getEndTime(),
                            fremdeVeranstaltung.getName()
                        );
                        blockierteIntervalleProRaum
                            .computeIfAbsent(fremdeBelegung.getRaumId(), k -> new ArrayList<>())
                            .add(info);
                    });
            });

        // 3. Erstelle die DTOs und prüfe auf Kollisionen
        List<RaumVerfuegbarkeitDto> dtos = new ArrayList<>();
        for (Raum raum : relevanteRaeume) {
            RaumVerfuegbarkeitDto dto = new RaumVerfuegbarkeitDto();
            dto.raumId = raum.getId();
            dto.veranstaltungId = veranstaltungId;

            RaumVerfuegbarkeit eigeneVerfuegbarkeit = eigeneBelegungen.get(raum.getId());
            if (eigeneVerfuegbarkeit != null) {
                dto.verfuegbareSlotIds = eigeneVerfuegbarkeit.getVerfuegbareSlotIds();
            } else {
                dto.verfuegbareSlotIds = aktuelleVeranstaltung.getSlots().stream().map(Slot::getId).collect(Collectors.toSet());
            }

            // Prüfe auf Kollisionen
            List<BlockingInfo> blockaden = blockierteIntervalleProRaum.get(raum.getId());
            if (blockaden != null) {
                for (Slot eigenerSlot : aktuelleVeranstaltung.getSlots()) {
                    for (BlockingInfo blockade : blockaden) {
                        // Prüfe auf Zeitüberlappung: (StartA < EndeB) AND (EndA > StartB)
                        if (eigenerSlot.getStartTime().isBefore(blockade.end) && eigenerSlot.getEndTime().isAfter(blockade.start)) {
                            dto.isBlockedByOtherEvent = true;
                            dto.blockingEventName = blockade.eventName;
                            break; // Ein Treffer pro Raum reicht für die DTO-Markierung
                        }
                    }
                    if (dto.isBlockedByOtherEvent) {
                        break;
                    }
                }
            }
            dtos.add(dto);
        }
        return dtos;
    }

// #################################################################################################################
// # GRUPPEN-VERWALTUNG
// #################################################################################################################


    @Transactional
    @Override
    public List<String> getGruppen(Long veranstaltungId) {
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        if (null == veranstaltung) {
            throw new EntityNotFoundException(Veranstaltung.class, "ID " + veranstaltungId + " nicht gefunden.");
        }
        return veranstaltung.getGruppen().stream().sorted(StringHelper.NUM_OR_ALPHA_COMPARATOR).toList();
    }


    @Transactional
    @Override
    public void createGruppe(Long veranstaltungId, String gruppenName) {
        if (StringUtils.isBlank(gruppenName)) {
            throw new CreateVortragException("Gruppenname darf nicht leer sein.");
        }
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        if (null == veranstaltung) {
            throw new CreateVortragException("Veranstaltung mit ID " + veranstaltungId + " nicht gefunden.");
        }
        if (!veranstaltung.addGruppe(gruppenName)) {
            throw new CreateVortragException("Gruppe '" + gruppenName + "' existiert bereits.");
        }
        protokollService.log(ProtokollKategorie.VERANSTALTUNG, "Gruppe erstellt", "Gruppe '" + gruppenName + "' zu Veranstaltung '" + veranstaltung.getName() + "' hinzugefügt.", veranstaltungId, veranstaltungId);
    }


    @Transactional
    @Override
    public void renameGruppe(Long veranstaltungId, String alterName, String neuerName) {
        if (StringUtils.isBlank(alterName) || StringUtils.isBlank(neuerName)) {
            throw new UpdateVortragException("Gruppenname darf nicht leer sein.");
        }
        if (alterName.equals(neuerName)) {
            return; // Nichts zu tun
        }

        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        if (null == veranstaltung) {
            throw new UpdateVortragException("Veranstaltung mit ID " + veranstaltungId + " nicht gefunden.");
        }
        if (!veranstaltung.getGruppen().contains(alterName)) {
            throw new UpdateVortragException("Gruppe '" + alterName + "' existiert nicht.");
        }
        if (veranstaltung.getGruppen().contains(neuerName)) {
            throw new UpdateVortragException("Gruppe '" + neuerName + "' existiert bereits.");
        }

        // 1. Gruppe in Veranstaltung umbenennen
        veranstaltung.removeGruppe(alterName);
        veranstaltung.addGruppe(neuerName);

        // 2. Alle Teilnehmer der Veranstaltung durchgehen und Gruppe umbenennen
        List<Teilnehmer> betroffeneTeilnehmer = Teilnehmer.find("?1 MEMBER OF gruppen AND ?2 MEMBER OF veranstaltungen", alterName, veranstaltung).list();
        for (Teilnehmer teilnehmer : betroffeneTeilnehmer) {
            teilnehmer.removeGruppe(alterName);
            teilnehmer.addGruppe(neuerName);
        }

        protokollService.log(ProtokollKategorie.VERANSTALTUNG, "Gruppe umbenannt", "Gruppe von '" + alterName + "' zu '" + neuerName + "' in Veranstaltung '" + veranstaltung.getName() + "' umbenannt.", veranstaltungId, veranstaltungId);
    }


    @Transactional
    @Override
    public void deleteGruppe(Long veranstaltungId, String gruppenName) {
        if (StringUtils.isBlank(gruppenName)) {
            throw new DeleteVortragsgruppeException("Gruppenname darf nicht leer sein.");
        }
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        if (null == veranstaltung) {
            throw new DeleteVortragsgruppeException("Veranstaltung mit ID " + veranstaltungId + " nicht gefunden.");
        }

        // 1. Gruppe aus Veranstaltung entfernen
        if (!veranstaltung.removeGruppe(gruppenName)) {
            throw new DeleteVortragsgruppeException("Gruppe '" + gruppenName + "' konnte nicht entfernt werden, da sie nicht existiert.");
        }

        // 2. Gruppe aus allen Teilnehmern der Veranstaltung entfernen
        List<Teilnehmer> betroffeneTeilnehmer = Teilnehmer.find("?1 MEMBER OF gruppen AND ?2 MEMBER OF veranstaltungen", gruppenName, veranstaltung).list();
        for (Teilnehmer teilnehmer : betroffeneTeilnehmer) {
            teilnehmer.removeGruppe(gruppenName);
        }

        protokollService.log(ProtokollKategorie.VERANSTALTUNG, "Gruppe gelöscht", "Gruppe '" + gruppenName + "' aus Veranstaltung '" + veranstaltung.getName() + "' entfernt.", veranstaltungId, veranstaltungId);
    }

    // ... am Ende der OrganisatorService.java Klasse ...


    @Transactional
    @Override
    public ImportResultDto importNutzerVerfuegbarkeitenFromCsv(Path csvFilePath,
                                                               Class<? extends Nutzer> nutzerKlasse, Long veranstaltungId) {
        int count = 0;
        List<String> fehler = new ArrayList<>();
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        if (null == veranstaltung) {
            throw new CsvImportException(csvFilePath, "Veranstaltung nicht gefunden.");
        }

        List<Slot> sortedSlots = veranstaltung.getSlots().stream()
            .sorted(Comparator.comparing(Slot::getStartTime))
            .toList();
        List<Long> sortedSlotIds = sortedSlots.stream().map(Slot::getId).toList();
        String nutzerTyp = nutzerKlasse.getSimpleName();

        try (Reader reader = CsvHelper.openCsvReader(csvFilePath)) {
            CsvToBean<NutzerVerfuegbarkeitCsvDto> csvToBean = new CsvToBeanBuilder<NutzerVerfuegbarkeitCsvDto>(reader)
                .withType(NutzerVerfuegbarkeitCsvDto.class)
                .withSeparator(';')
                .withIgnoreEmptyLine(true)
                .withFilter(line -> line.length > 0 && !line[0].startsWith("#"))
                .withIgnoreLeadingWhiteSpace(true)
                .withThrowExceptions(false)
                .build();

            List<NutzerVerfuegbarkeitCsvDto> beans = csvToBean.parse();

            csvToBean.getCapturedExceptions().forEach(e -> {
                String msg = "Zeile " + e.getLineNumber() + ": " + e.getMessage();
                LOG.error("CSV-Parsing-Fehler in " + csvFilePath.getFileName() + " (" + msg + ")");
                fehler.add(msg);
            });

            for (NutzerVerfuegbarkeitCsvDto dto : beans) {
                Nutzer nutzer = Nutzer.findByLoginName(dto.loginName);
                if (null == nutzer) {
                    String msg = nutzerTyp + " mit loginName '" + dto.loginName + "' nicht gefunden.";
                    LOG.warn("Nutzer-Verfügbarkeit übersprungen: " + msg);
                    fehler.add(msg);
                    continue;
                }
                if (!nutzerKlasse.isInstance(nutzer)) {
                    String msg = nutzerTyp + " mit loginName '" + dto.loginName + "' ist kein " + nutzerKlasse.getSimpleName() + " " +
                        "(falsche CSV-Datei?).";
                    LOG.warn("Nutzer-Verfügbarkeit übersprungen: " + msg);
                    fehler.add(msg);
                    continue;
                }

                Set<Long> verfuegbareSlotIds = parseSlotIndices(dto.verfuegbareSlots, sortedSlotIds);
                NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvId(nutzer, veranstaltung));
                if (null == nv) {
                    nv = new NutzerVerfuegbarkeit(nutzer.getId(), veranstaltungId, verfuegbareSlotIds);
                } else {
                    nv.setVerfuegbareSlotIds(verfuegbareSlotIds);
                }
                nv.persist();
                count++;
            }
        } catch (Exception e) {
            throw new CsvImportException(csvFilePath, e.getMessage());
        }
        LOG.info("Import von Verfügbarkeiten für " + nutzerTyp +
            " abgeschlossen: " + count + " Einträge aus " +
            csvFilePath + " verarbeitet.");
        return new ImportResultDto(count, fehler);
    }


    @Transactional
    @Override
    public ImportResultDto importRaumVerfuegbarkeitenFromCsv(Path csvFilePath, Long veranstaltungId) {
        int count = 0;
        List<String> fehler = new ArrayList<>();
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        if (null == veranstaltung) {
            throw new CsvImportException(csvFilePath, "Veranstaltung nicht gefunden.");
        }

        List<Slot> sortedSlots = veranstaltung.getSlots().stream()
            .sorted(Comparator.comparing(Slot::getStartTime))
            .toList();
        List<Long> sortedSlotIds = sortedSlots.stream().map(Slot::getId).toList();

        try (Reader reader = CsvHelper.openCsvReader(csvFilePath)) {
            CsvToBean<RaumVerfuegbarkeitCsvDto> csvToBean = new CsvToBeanBuilder<RaumVerfuegbarkeitCsvDto>(reader)
                .withType(RaumVerfuegbarkeitCsvDto.class)
                .withSeparator(';')
                .withFilter(line -> line.length > 0 && !line[0].startsWith("#"))
                .withIgnoreEmptyLine(true)
                .withIgnoreLeadingWhiteSpace(true)
                .withThrowExceptions(false)
                .build();

            List<RaumVerfuegbarkeitCsvDto> beans = csvToBean.parse();

            csvToBean.getCapturedExceptions().forEach(e -> {
                String msg = "Zeile " + e.getLineNumber() + ": " + e.getMessage();
                LOG.error("CSV-Parsing-Fehler in " + csvFilePath.getFileName() + " (" + msg + ")");
                fehler.add(msg);
            });

            for (RaumVerfuegbarkeitCsvDto dto : beans) {
                Raum raum = Raum.find("name = ?1 and gebaeude.name = ?2", dto.raum, dto.gebaeude).firstResult();
                if (null == raum) {
                    String msg = "Raum '" + dto.raum + "' in Gebäude '" + dto.gebaeude + "' nicht gefunden.";
                    LOG.warn("Raum-Verfügbarkeit übersprungen: " + msg);
                    fehler.add(msg);
                    continue;
                }

                Set<Long> verfuegbareSlotIds = parseSlotIndices(dto.verfuegbareSlots, sortedSlotIds);
                RaumVerfuegbarkeit rv = RaumVerfuegbarkeit.findById(rvId(raum, veranstaltung));
                if (null == rv) {
                    rv = new RaumVerfuegbarkeit(raum.getId(), veranstaltungId, verfuegbareSlotIds);
                } else {
                    rv.setVerfuegbareSlotIds(verfuegbareSlotIds);
                }
                rv.persist();
                count++;
            }
        } catch (Exception e) {
            throw new CsvImportException(csvFilePath, e.getMessage());
        }
        LOG.info("Raum-Verfügbarkeiten-Import abgeschlossen: " + count + " Einträge aus " +
            csvFilePath + " verarbeitet.");
        return new ImportResultDto(count, fehler);
    }


    private Set<Long> parseSlotIndices(String indicesString, List<Long> sortedSlotIds) {
        if (StringUtils.isBlank(indicesString)) {
            return new HashSet<>();
        }
        try {
            return Arrays.stream(indicesString.split(","))
                .map(String::trim)
                .mapToInt(Integer::parseInt)
                .mapToObj(i -> sortedSlotIds.get(i - 1)) // 1-based index to 0-based
                .collect(Collectors.toSet());
        } catch (Exception e) {
            LOG.warn("Fehler beim Split der Slot-Indizes: " + e.getMessage());
            return new HashSet<>();
        }
    }


    // -------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------


    private boolean kapazitaetZuGering(Raum raum, String gruppe, Veranstaltung veranstaltung) {
        if (null == raum || raum.getKapazitaet() == null) {
            return false;
        }
        long activeTeilnehmerCount = getGruppenTeilnehmer(gruppe, veranstaltung).size();

        return raum.getKapazitaet() < activeTeilnehmerCount;
    }
}
