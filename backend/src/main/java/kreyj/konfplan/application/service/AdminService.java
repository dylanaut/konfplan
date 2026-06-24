package kreyj.konfplan.application.service;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.dto.NutzerDto;
import kreyj.konfplan.adapter.in.web.dto.RaumVerfuegbarkeitDto;
import kreyj.konfplan.adapter.in.web.dto.SlotDto;
import kreyj.konfplan.adapter.in.web.dto.VortragDto;
import kreyj.konfplan.adapter.in.web.dto.VortragPrioDto;
import kreyj.konfplan.adapter.in.web.dto.VortragStatDto;
import kreyj.konfplan.adapter.in.web.dto.csv.AdminCsvDto;
import kreyj.konfplan.adapter.in.web.dto.csv.NutzerVerfuegbarkeitCsvDto;
import kreyj.konfplan.adapter.in.web.dto.csv.RaumVerfuegbarkeitCsvDto;
import kreyj.konfplan.adapter.in.web.dto.csv.SlotCsvDto;
import kreyj.konfplan.adapter.in.web.dto.csv.VortragCsvDto;
import kreyj.konfplan.application.port.in.AdminServiceInterface;
import kreyj.konfplan.domain.exception.CreateSlotException;
import kreyj.konfplan.domain.exception.CreateVortragException;
import kreyj.konfplan.domain.exception.CsvImportException;
import kreyj.konfplan.domain.exception.DeleteVortragsgruppeException;
import kreyj.konfplan.domain.exception.EntityNotFoundException;
import kreyj.konfplan.domain.exception.UpdateNutzerException;
import kreyj.konfplan.domain.exception.UpdateVortragException;
import kreyj.konfplan.domain.exception.VeranstaltungException;
import kreyj.konfplan.persistence.Admin;
import kreyj.konfplan.persistence.Berufsfeld;
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
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Vortrag;
import kreyj.konfplan.persistence.VortragVerfuegbarkeit;
import kreyj.konfplan.persistence.Wahlvortrag;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;

import java.io.BufferedReader;
import java.io.FileReader;
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
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.EMPTY_MAP;
import static java.util.Collections.emptyList;
import static kreyj.konfplan.persistence.NutzerVerfuegbarkeitId.nvId;
import static kreyj.konfplan.persistence.NutzerVerfuegbarkeitId.nvIdL;
import static kreyj.konfplan.persistence.RaumVerfuegbarkeitId.rvId;
import static kreyj.konfplan.persistence.Teilnehmer.getGruppenTeilnehmer;
import static kreyj.konfplan.persistence.VortragVerfuegbarkeitId.vvIdL;
import static kreyj.konfplan.util.DateHelper.DATE_FORMAT;
import static org.apache.commons.collections4.SetUtils.difference;

@ApplicationScoped
public class AdminService implements AdminServiceInterface {
    private static final Logger LOG = Logger.getLogger(AdminService.class);
    public static final String CSV_PRIO_HEADER = "Teilnehmer E-Mail;Prioritäten";
    public static final String PV_FAIL_MESSAGE = ". Pflichtvortrag kann nicht erstellt werden.";
    public static final String LEGENDE = "# Legende:";

    private final MailService mailService;

    private final ProtokollService protokollService;


    public AdminService(MailService mailService, ProtokollService protokollService) {
        this.mailService = mailService;
        this.protokollService = protokollService;
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
            .map(AdminService::mapNutzerToDto)
            .toList();
    }


    @Transactional
    @Override
    public List<NutzerDto> getAllUsers(Long veranstaltungId) {
        List<Nutzer> admins = Nutzer.list("role = 'ADMIN'");
        List<Nutzer> vNutzers = Nutzer.find("SELECT u FROM Nutzer u JOIN u.veranstaltungen v WHERE v.id = ?1", veranstaltungId).list();

        return Stream.concat(admins.stream(), vNutzers.stream())
            .distinct()
            .map(AdminService::mapNutzerToDto)
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
            nutzer = new Admin();
        }

        nutzer.setEmail(dto.email);
        nutzer.setFirstName(dto.firstName);
        nutzer.setLastName(dto.lastName);
        nutzer.setActive(dto.isActive);

        if (dto.email != null) {
            // todo replace with uuid string for PROD
            nutzer.setPasswordHash(BcryptUtil.bcryptHash("start123"));
        }

        if (nutzer instanceof Referent r) {
            r.setBiography(dto.biography);
            r.setJobRole(dto.jobRole);
            r.setOrganisation(dto.organisation);
            r.setSlogan(dto.slogan);
        } else if (nutzer instanceof Teilnehmer t) {
            t.setGruppen(dto.gruppen);
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

        protokollService.log(ProtokollKategorie.NUTZER, "Nutzer erstellt", "Neuer Nutzer '" + nutzer.getEmail() + "' mit Rolle '" + nutzer.getRole() + "' erstellt.", nutzer.getId());
        return mapNutzerToDto(nutzer);
    }


    @Transactional
    @Override
    public NutzerDto updateUser(Long id, NutzerDto dto, List<Long> vUpdateIds) {
        Nutzer nutzer = Nutzer.findById(id);
        if (nutzer == null) {
            return null;
        }

        if (!Objects.equals(nutzer.getVersion(), dto.version)) {
            throw new OptimisticLockException("Der Nutzer wurde zwischenzeitlich von Dritten geändert. Bitte aktualisieren Sie die Daten und versuchen Sie es erneut.");
        }

        // Check for email change
        String oldEmail = nutzer.getEmail();
        if (!oldEmail.equals(dto.email)) {
            // Check if the new email is already in use
            if (Nutzer.findByEmail(dto.email) != null) {
                throw new UpdateNutzerException("Die neue E-Mail-Adresse wird bereits verwendet.");
            }

            // Generate a confirmation token
            String token = UUID.randomUUID().toString();
            nutzer.setNewEmail(dto.email);
            nutzer.setEmailChangeToken(token);
            nutzer.setEmailChangeTokenExpiry(LocalDateTime.now().plusHours(24)); // Token is valid for 24 hours

            // Send confirmation email to the new address
            mailService.sendEmailChangeConfirmationNewAddress(nutzer, dto.email, token);
            // Notify the user at their old address
            mailService.sendEmailChangeNotificationOldAddress(nutzer, oldEmail, dto.email);

            protokollService.log(ProtokollKategorie.NUTZER, "E-Mail-Änderung eingeleitet",
                "E-Mail-Änderung für Nutzer '" + oldEmail + "' zu '" + dto.email + "' eingeleitet.", nutzer.getId());
        }

        nutzer.setFirstName(dto.firstName);
        nutzer.setLastName(dto.lastName);
        nutzer.setActive(dto.isActive);

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
            r.setBiography(dto.biography);
            r.setJobRole(dto.jobRole);
            r.setOrganisation(dto.organisation);
            r.setSlogan(dto.slogan);
        } else if (nutzer instanceof Teilnehmer t) {
            t.setGruppen(dto.gruppen);
        }

        nutzer.persistAndFlush();

        protokollService.log(ProtokollKategorie.NUTZER, "Nutzer aktualisiert", "Nutzer '" + nutzer.getEmail() + "' aktualisiert.", nutzer.getId());
        return mapNutzerToDto(nutzer);
    }


    @Transactional
    @Override
    public boolean confirmEmailChange(String token) {
        Nutzer nutzer = Nutzer.find("emailChangeToken", token).firstResult();

        if (nutzer == null) {
            LOG.warn("Ungültiger Token für E-Mail-Änderung: " + token);
            return false;
        }

        if (nutzer.getEmailChangeTokenExpiry().isBefore(LocalDateTime.now())) {
            LOG.warn("Abgelaufener Token für E-Mail-Änderung für Nutzer: " + nutzer.getEmail());
            nutzer.setEmailChangeToken(null);
            nutzer.setEmailChangeTokenExpiry(null);
            nutzer.setNewEmail(null);
            return false;
        }

        String oldEmail = nutzer.getEmail();
        nutzer.setEmail(nutzer.getNewEmail());
        nutzer.setNewEmail(null);
        nutzer.setEmailChangeToken(null);
        nutzer.setEmailChangeTokenExpiry(null);

        protokollService.log(ProtokollKategorie.NUTZER, "E-Mail-Adresse bestätigt",
            "E-Mail-Adresse für Nutzer von '" + oldEmail + "' zu '" + nutzer.getEmail() + "' geändert.", nutzer.getId());

        return true;
    }


    @Transactional
    @Override
    public void inviteUserToEvent(Long nutzerId, Long veranstaltungId) {
        Objects.requireNonNull(nutzerId, "userId darf nicht null sein.");
        Objects.requireNonNull(veranstaltungId, "veranstaltungId darf nicht null sein.");

        Nutzer nutzer = Nutzer.findById(nutzerId);
        if (nutzer == null) {
            throw new EntityNotFoundException(Nutzer.class, "Nutzer nicht gefunden.");
        }

        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        if (veranstaltung == null) {
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
            protokollService.log(ProtokollKategorie.SECURITY, "Nutzer zu Veranstaltung eingeladen", "Nutzer '" + nutzer.getEmail() + "' zu '" + veranstaltung.getName() + "' eingeladen.", veranstaltung.getId());
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
            boolean deleted = Nutzer.deleteById(id);
            if (deleted) {
                protokollService.log(ProtokollKategorie.NUTZER, "Nutzer gelöscht", "Nutzer '" + email + "' gelöscht.", id);
            }
            return deleted;
        }
        return false;
    }


    @Transactional
    @Override
    public void toggleUserStatus(Long id) {
        Nutzer entity = Nutzer.findById(id);
        if (entity != null) {
            entity.setActive(!entity.isActive());
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
            if (vortragDto.pflichtSlotId == null
                || vortragDto.pflichtRaumId == null
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
                            if (nv == null || nv.isVerfuegbar(vortragDto.pflichtSlotId)) {
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
        }


        created.setAusstattung(vortragDto.ausstattung);
        created.setBerufsfeld(vortragDto.berufsfeld);
        created.persistAndFlush();


        veranstaltung.addVortrag(created);
        veranstaltung.persistAndFlush();

        protokollService.log(ProtokollKategorie.VORTRAEGE, "Vortrag erstellt",
            "Vortrag '" + created.getTitel() + "' (" + (created.istPflicht() ? "Pflicht" : "Wahl") + ") erstellt.",
            created.getId());

        return created;
    }


    @Transactional
    @Override
    public int importAdminsFromCsv(Path csvFilePath) throws Exception {
        int count = 0;
        try (FileReader reader = new FileReader(csvFilePath.toFile())) {
            CsvToBean<AdminCsvDto> csvToBean = new CsvToBeanBuilder<AdminCsvDto>(reader)
                .withType(AdminCsvDto.class)
                .withSeparator(';')
                .withIgnoreLeadingWhiteSpace(true)
                .withThrowExceptions(false)
                .build();

            List<AdminCsvDto> beans = csvToBean.parse();

            csvToBean.getCapturedExceptions().forEach(e ->
                LOG.error("CSV-Parsing-Fehler in " + csvFilePath.getFileName() + " (Zeile " + e.getLineNumber() + "): " + e.getMessage())
            );

            for (AdminCsvDto dto : beans) {
                if (StringUtils.isBlank(dto.email)) {
                    LOG.warn("Admin-Zeile übersprungen: Email fehlt.");
                    continue;
                }
                String email = dto.email.trim().toLowerCase();
                Nutzer byEmail = Nutzer.findByEmail(email);
                if (byEmail == null) {
                    Admin a = new Admin();
                    a.setEmail(email);
                    a.setFirstName(dto.vorname);
                    a.setLastName(dto.nachname);
                    a.setPasswordHash(BcryptUtil.bcryptHash(UUID.randomUUID().toString()));
                    a.persistAndFlush();
                    count++;
                    protokollService.log(ProtokollKategorie.NUTZER, "Organisator importiert", "Organisator '" + email + "' via CSV importiert.", a.getId());
                } else {
                    LOG.warn("Organisator '" + email + "' übersprungen: Existiert bereits.");
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

        if (veranstaltung == null) {
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

        try (FileReader reader = new FileReader(csvFilePath.toFile())) {
            CsvToBean<VortragCsvDto> csvToBean = new CsvToBeanBuilder<VortragCsvDto>(reader)
                .withType(VortragCsvDto.class)
                .withSeparator(';')
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

                VortragDto dto = new VortragDto();
                dto.veranstaltungId = veranstaltungId;
                dto.istPflicht = csvDto.istPflicht;
                dto.titel = csvDto.titel;
                dto.inhalt = csvDto.inhalt;
                dto.ausstattung = csvDto.ausstattung;
                Nutzer referent = Nutzer.findByEmail(csvDto.referentEmail);

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

                    if (StringUtils.isNotBlank(csvDto.berufsfeld)) {
                        Berufsfeld foundBerufsfeld = findBerufsfeldByPrefix(csvDto.berufsfeld);
                        if (foundBerufsfeld != null) {
                            dto.berufsfeld = foundBerufsfeld;
                        } else {
                            LOG.warn("Vortrag '" + csvDto.titel + "' übersprungen: Ungültiges oder nicht eindeutiges Berufsfeld '" + csvDto.berufsfeld + "'.");
                            continue;
                        }
                    }

                    dto.referentId = referent.getId();

                    try {
                        Vortrag vortrag = createVortrag(dto);

                        count++;
                        protokollService.log(ProtokollKategorie.VORTRAEGE, "Vortrag importiert",
                            "Vortrag '" + dto.titel + "' via CSV importiert.", vortrag.getId());
                    } catch (IllegalArgumentException e) {
                        LOG.warn("Vortrag '" + csvDto.titel + "' übersprungen aufgrund von Validierungsfehler: " + e.getMessage());
                    }

                } else {
                    LOG.warn("Vortrag '" + csvDto.titel + "' übersprungen: Referent mit Email " + csvDto.referentEmail + " nicht gefunden oder kein Referent.");
                }
            }
        } catch (Exception e) {
            LOG.error("Kritischer Fehler beim Importieren der Vorträge aus CSV: " + csvFilePath, e);
            throw new CsvImportException(csvFilePath, e.getMessage());
        }
        LOG.info("Vortrag-Import abgeschlossen: " + count + " Vorträge aus " + csvFilePath + " importiert.");
        return count;
    }


    private Berufsfeld findBerufsfeldByPrefix(String prefix) {
        if (StringUtils.isBlank(prefix)) {
            return null;
        }
        String normalizedPrefix = prefix.trim().toLowerCase();
        List<Berufsfeld> matches = new ArrayList<>();
        for (Berufsfeld feld : Berufsfeld.values()) {
            if (feld.getName().toLowerCase().startsWith(normalizedPrefix)) {
                matches.add(feld);
            }
        }
        if (matches.size() == 1) {
            return matches.getFirst();
        }
        return null; // Not found or ambiguous
    }


    @Transactional
    @Override
    public int importPrioritaetenFromCsv(Path csvFilePath, Long veranstaltungId) throws Exception {
        int count = 0;
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        if (veranstaltung == null) {
            LOG.error("CSV-Import (Prioritäten) abgebrochen: Veranstaltung mit ID " + veranstaltungId + " nicht gefunden.");
            throw new CsvImportException(csvFilePath, "Veranstaltung '" + veranstaltungId + "' nicht gefunden.");
        }

        boolean headerLineFound = false;
        Map<Integer, Wahlvortrag> legendIndexMap = EMPTY_MAP;

        try (BufferedReader reader = new BufferedReader(new FileReader(csvFilePath.toFile()))) {
            String line = reader.readLine();

            if (line.startsWith(LEGENDE)) {
                List<Wahlvortrag> wahlvortraege = veranstaltung.getVortraege().stream()
                    .filter(v -> v instanceof Wahlvortrag)
                    .map(v -> (Wahlvortrag) v)
                    .toList();
                legendIndexMap = parseLegende(line.substring(LEGENDE.length()), wahlvortraege);
            } else {
                LOG.error("CSV-Import (Prioritäten) abgebrochen: Legende fehlt.");
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
                        LOG.error("CSV-Import (Prioritäten) abgebrochen: Ungültiger Header");
                        throw new CsvImportException(csvFilePath,
                            "Ungültiger Header für Prio-Import in " + csvFilePath.getFileName());
                    }
                }

                String[] lineItems = line.split(";");
                String teilnehmerEmail = lineItems[0].trim();

                Nutzer nutzer = Nutzer.findByEmail(teilnehmerEmail);
                if (!(nutzer instanceof Teilnehmer teilnehmer)) {
                    LOG.warn("Priorität für '" + teilnehmerEmail + "' übersprungen: Nutzer ist kein Teilnehmer.");
                    continue;
                }

                if (teilnehmer.getVeranstaltungen().stream().noneMatch(v -> v.getId().equals(veranstaltungId))) {
                    LOG.warn("Priorität für '" + teilnehmerEmail + "' übersprungen: Teilnehmer gehört nicht zur Veranstaltung.");
                    continue;
                }

                String wvPrioStr = lineItems.length > 1 ? lineItems[1] : null;
                if (StringUtils.isNotBlank(wvPrioStr)) {
                    String[] wvPrios = wvPrioStr.split(",");

                    for (String wvPrio : wvPrios) {
                        wvPrio = wvPrio.trim();
                        if (!wvPrio.matches("\\d+\\s*:\\s*\\d+")) {
                            LOG.warn("Priorität für '" + teilnehmerEmail + "' übersprungen: Prio format " + wvPrio + " ist ungültig.");
                            continue;
                        }
                        String[] data = wvPrio.split(":");
                        Integer index = Integer.parseInt(data[0].trim());
                        Wahlvortrag vortrag = legendIndexMap.get(index);

                        if (vortrag == null || !vortrag.getVeranstaltung().getId().equals(veranstaltungId)) {
                            LOG.warn("Priorität für '" + teilnehmerEmail + "' übersprungen: legendenIndex " + index + " ist" +
                                " ungültig oder kein Wahlvortrag dieser Veranstaltung.");
                            continue;
                        }

                        try {
                            int prioWert = Integer.parseInt(data[1].trim());

                            Prioritaet prioritaet = Prioritaet.find("teilnehmer = ?1 and vortrag = ?2", teilnehmer, vortrag).firstResult();
                            if (prioritaet == null) {
                                prioritaet = new Prioritaet();
                                prioritaet.setTeilnehmer(teilnehmer);
                                prioritaet.setVortrag(vortrag);
                            }

                            prioritaet.setPrioWert(prioWert);
                            prioritaet.persistAndFlush();
                            count++;
                            protokollService.log(ProtokollKategorie.VORTRAEGE, "Priorität importiert", "Priorität für '" + teilnehmer.getEmail() + "' für Vortrag '" + vortrag.getTitel() + "' auf " + prioWert + " gesetzt.", vortrag.getId());
                        } catch (NumberFormatException e) {
                            LOG.warn("Ungültiger Prioritätswert für Teilnehmer " + teilnehmerEmail + " und Vortrag " + vortrag.getTitel() + ": " + e.getMessage());
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


    private Map<Integer, Wahlvortrag> parseLegende(String legende, List<Wahlvortrag> wvs) {
        Map<Integer, Wahlvortrag> indexToVortragsIdMap = new HashMap<>();

        for (String entry : legende.split(",")) {
            String[] parts = entry.split("=");
            if (parts.length != 2) {
                LOG.warn("Uungültiger Legenden-Eintrag '" + parts + "'");
            } else {
                String titelPrefix = parts[1].trim();
                Wahlvortrag wv = wvs.stream()
                    .filter(v -> v.getTitel().startsWith(titelPrefix))
                    .findFirst()
                    .orElse(null);

                if (wv != null) {
                    indexToVortragsIdMap.put(Integer.parseInt(parts[0].trim()), wv);
                } else {
                    LOG.warn("Kein Wahlvortrag gefunden mit Legenden-Präfix '" + titelPrefix);
                }
            }
        }

        return indexToVortragsIdMap;
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
            "Slot '" + slotDto.description + "' für '" + v.getName() + "' erstellt.", slot.getId());

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
            protokollService.log(ProtokollKategorie.VERANSTALTUNG, "Zeit-Slot aktualisiert", "Slot '" + entity.getDescription() + "' aktualisiert.", entity.getId());
        }
        return entity;
    }


    private void validateSlot(SlotDto slotDto, Veranstaltung v, Long excludeId) {
        Objects.requireNonNull(slotDto, "Slot darf nicht NULL sein");
        Objects.requireNonNull(slotDto.description, "Slot-Beschreibung darf nicht NULL sein");
        Objects.requireNonNull(v, "Veranstaltung darf nicht NULL sein");

        if (slotDto.startTime == null || slotDto.endTime == null) {
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
        if (slot != null && slot.getVeranstaltung().getId().equals(veranstaltung)) {
            String desc = slot.getDescription();

            // Delete all availabilities associated with this slot
            NutzerVerfuegbarkeit.<NutzerVerfuegbarkeit>find("veranstaltungId", veranstaltung.getId()).stream()
                .forEach(v -> v.removeSlot(id));
            RaumVerfuegbarkeit.<RaumVerfuegbarkeit>find("veranstaltungId", veranstaltung.getId()).stream()
                .forEach(v -> v.removeSlot(id));


            long count = Slot.delete("id = ?1", id);
            if (count > 0) {
                protokollService.log(ProtokollKategorie.VERANSTALTUNG, "Zeit-Slot gelöscht", "Slot '" + desc + "' gelöscht.", id);
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
        if (v == null) {
            LOG.error("CSV-Import (Slots) abgebrochen: Veranstaltung mit ID " + veranstaltungId + " nicht gefunden.");
            throw new IllegalArgumentException("Veranstaltung mit ID " + veranstaltungId + " nicht gefunden.");
        }
        try (FileReader reader = new FileReader(csvFilePath.toFile())) {
            CsvToBean<SlotCsvDto> csvToBean = new CsvToBeanBuilder<SlotCsvDto>(reader)
                .withType(SlotCsvDto.class)
                .withSeparator(';')
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
                        "Slot '" + slotDto.description + "' via CSV importiert.", created.getId());
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
        if (entity == null || !entity.getVeranstaltung().getId().equals(veranstaltungId)) {
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
        entity.setBerufsfeld(updated.berufsfeld);

        if (entity instanceof Pflichtvortrag pv && updated.istPflicht) {
            pv.updatePflichtgruppe(updated.pflichtGruppe);
            pv.updatePflichtslot(Slot.findById(updated.pflichtSlotId));
            pv.updatePflichtraum(Raum.findById(updated.pflichtRaumId));
        } else if (entity instanceof Wahlvortrag wv && !updated.istPflicht) {
            wv.setWiederholbar(updated.wiederholbar);
            wv.setMaxWiederholungen(updated.maxWiederholungen);

            VortragVerfuegbarkeit vv = VortragVerfuegbarkeit.findById(vvIdL(updated.id, veranstaltungId));
            if (null == vv) {
                new VortragVerfuegbarkeit(updated.id, veranstaltungId, updated.verfuegbareSlotIds).persistAndFlush();
            } else {
                updated.verfuegbareSlotIds.forEach(vv::addSlot);
            }
        }
        entity.persistAndFlush();

        protokollService.log(ProtokollKategorie.VORTRAEGE, "Vortrag aktualisiert", "Vortrag '" + entity.getTitel() + "' aktualisiert.", entity.getId());
        return ReferentService.mapVortragToDto(entity);
    }


    @Transactional
    @Override
    public boolean deleteVortrag(Long id, Veranstaltung veranstaltung) {
        Objects.requireNonNull(id, "ID darf nicht NULL sein");
        Objects.requireNonNull(veranstaltung, "Veranstaltung darf nicht NULL sein");

        Vortrag entity = Vortrag.findById(id);
        if (entity == null || !entity.getVeranstaltung().getId().equals(veranstaltung.getId())) {
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
            protokollService.log(ProtokollKategorie.VORTRAEGE, "Vortrag gelöscht", "Vortrag '" + titel + "' gelöscht.", id);
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
        if (aktuelleVeranstaltung == null) {
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
        if (veranstaltung == null) {
            throw new EntityNotFoundException(Veranstaltung.class, "ID " + veranstaltungId + " nicht gefunden.");
        }
        return veranstaltung.getGruppen().stream().sorted().toList();
    }


    @Transactional
    @Override
    public void createGruppe(Long veranstaltungId, String gruppenName) {
        if (StringUtils.isBlank(gruppenName)) {
            throw new CreateVortragException("Gruppenname darf nicht leer sein.");
        }
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        if (veranstaltung == null) {
            throw new CreateVortragException("Veranstaltung mit ID " + veranstaltungId + " nicht gefunden.");
        }
        if (!veranstaltung.addGruppe(gruppenName)) {
            throw new CreateVortragException("Gruppe '" + gruppenName + "' existiert bereits.");
        }
        protokollService.log(ProtokollKategorie.VERANSTALTUNG, "Gruppe erstellt", "Gruppe '" + gruppenName + "' zu Veranstaltung '" + veranstaltung.getName() + "' hinzugefügt.", veranstaltungId);
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
        if (veranstaltung == null) {
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

        protokollService.log(ProtokollKategorie.VERANSTALTUNG, "Gruppe umbenannt", "Gruppe von '" + alterName + "' zu '" + neuerName + "' in Veranstaltung '" + veranstaltung.getName() + "' umbenannt.", veranstaltungId);
    }


    @Transactional
    @Override
    public void deleteGruppe(Long veranstaltungId, String gruppenName) {
        if (StringUtils.isBlank(gruppenName)) {
            throw new DeleteVortragsgruppeException("Gruppenname darf nicht leer sein.");
        }
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        if (veranstaltung == null) {
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

        protokollService.log(ProtokollKategorie.VERANSTALTUNG, "Gruppe gelöscht", "Gruppe '" + gruppenName + "' aus Veranstaltung '" + veranstaltung.getName() + "' entfernt.", veranstaltungId);
    }

    // ... am Ende der AdminService.java Klasse ...


    @Transactional
    @Override
    public int importNutzerVerfuegbarkeitenFromCsv(Path csvFilePath, Long veranstaltungId) {
        int count = 0;
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        if (veranstaltung == null) {
            throw new CsvImportException(csvFilePath, "Veranstaltung nicht gefunden.");
        }

        List<Slot> sortedSlots = veranstaltung.getSlots().stream()
            .sorted(Comparator.comparing(Slot::getStartTime))
            .toList();
        List<Long> sortedSlotIds = sortedSlots.stream().map(Slot::getId).toList();

        try (FileReader reader = new FileReader(csvFilePath.toFile())) {
            CsvToBean<NutzerVerfuegbarkeitCsvDto> csvToBean = new CsvToBeanBuilder<NutzerVerfuegbarkeitCsvDto>(reader)
                .withType(NutzerVerfuegbarkeitCsvDto.class)
                .withSeparator(';')
                .withIgnoreLeadingWhiteSpace(true)
                .build();

            for (NutzerVerfuegbarkeitCsvDto dto : csvToBean) {
                Nutzer nutzer = Nutzer.findByEmail(dto.email);
                if (nutzer == null) {
                    LOG.warn("Nutzer-Verfügbarkeit übersprungen: Nutzer mit E-Mail '" + dto.email + "' nicht gefunden.");
                    continue;
                }

                Set<Long> verfuegbareSlotIds = parseSlotIndices(dto.verfuegbareSlots, sortedSlotIds);
                NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvId(nutzer, veranstaltung));
                if (nv == null) {
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
        LOG.info("Nutzer-Verfügbarkeiten-Import abgeschlossen: " + count + " Einträge verarbeitet.");
        return count;
    }


    @Transactional
    @Override
    public int importRaumVerfuegbarkeitenFromCsv(Path csvFilePath, Long veranstaltungId) {
        int count = 0;
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        if (veranstaltung == null) {
            throw new CsvImportException(csvFilePath, "Veranstaltung nicht gefunden.");
        }

        List<Slot> sortedSlots = veranstaltung.getSlots().stream()
            .sorted(Comparator.comparing(Slot::getStartTime))
            .toList();
        List<Long> sortedSlotIds = sortedSlots.stream().map(Slot::getId).toList();

        try (FileReader reader = new FileReader(csvFilePath.toFile())) {
            CsvToBean<RaumVerfuegbarkeitCsvDto> csvToBean = new CsvToBeanBuilder<RaumVerfuegbarkeitCsvDto>(reader)
                .withType(RaumVerfuegbarkeitCsvDto.class)
                .withSeparator(';')
                .withIgnoreLeadingWhiteSpace(true)
                .build();

            for (RaumVerfuegbarkeitCsvDto dto : csvToBean) {
                Raum raum = Raum.find("name = ?1 and gebaeude.name = ?2", dto.raum, dto.gebaeude).firstResult();
                if (raum == null) {
                    LOG.warn("Raum-Verfügbarkeit übersprungen: Raum '" + dto.raum + "' in Gebäude '" + dto.gebaeude + "' nicht gefunden.");
                    continue;
                }

                Set<Long> verfuegbareSlotIds = parseSlotIndices(dto.verfuegbareSlots, sortedSlotIds);
                RaumVerfuegbarkeit rv = RaumVerfuegbarkeit.findById(rvId(raum, veranstaltung));
                if (rv == null) {
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
        LOG.info("Raum-Verfügbarkeiten-Import abgeschlossen: " + count + " Einträge verarbeitet.");
        return count;
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
            LOG.warn("Fehler beim Parsen der Slot-Indizes: '" + indicesString + "'. " + e.getMessage());
            return new HashSet<>();
        }
    }


// -------------------------------------------------------------------
// Mapper methods
// -------------------------------------------------------------------


    @Transactional
    public static NutzerDto mapNutzerToDto(Nutzer u) {
        NutzerDto dto = new NutzerDto();
        dto.id = u.getId();
        dto.version = u.getVersion();
        dto.email = u.getEmail();
        dto.firstName = u.getFirstName();
        dto.lastName = u.getLastName();
        dto.role = u.getRole();
        dto.isActive = u.isActive();
        dto.veranstaltungIds = null != u.getVeranstaltungen() ? u.getVeranstaltungen().stream().map(IdEntity::getId).toList() : emptyList();

        if (u instanceof Referent r) {
            dto.biography = r.getBiography();
            dto.jobRole = r.getJobRole();
            dto.organisation = r.getOrganisation();
            dto.slogan = r.getSlogan();
        } else if (u instanceof Teilnehmer tn) {
            dto.gruppen = tn.getGruppen();
            if (tn.getPrioritaeten() != null) {
                dto.prioritaeten = tn.getPrioritaeten().stream().map(VortragPrioDto::from).toList();
            }
        }
        return dto;
    }


    public static SlotDto mapSlotToDto(Slot slot) {
        SlotDto dto = new SlotDto();

        dto.id = slot.getId();
        dto.version = slot.getVersion();
        dto.description = slot.getDescription();
        dto.startTime = slot.getStartTime();
        dto.endTime = slot.getEndTime();
        dto.veranstaltungId = slot.getVeranstaltung().getId();

        return dto;
    }

// -------------------------------------------------------------------
// Helper methods
// -------------------------------------------------------------------


    private boolean kapazitaetZuGering(Raum raum, String gruppe, Veranstaltung veranstaltung) {
        if (raum == null || raum.getKapazitaet() == null) {
            return false;
        }
        long activeTeilnehmerCount = getGruppenTeilnehmer(gruppe, veranstaltung).size();

        return raum.getKapazitaet() < activeTeilnehmerCount;
    }
}
