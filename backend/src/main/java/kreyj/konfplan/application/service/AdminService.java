package kreyj.konfplan.application.service;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import kreyj.konfplan.persistence.Admin;
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
import kreyj.konfplan.presentation.AdminResource;
import kreyj.konfplan.presentation.dto.NutzerDto;
import kreyj.konfplan.presentation.dto.VortragDto;
import kreyj.konfplan.presentation.dto.VortragStatDto;
import kreyj.konfplan.presentation.dto.csv.AdminCsvDto;
import kreyj.konfplan.presentation.dto.csv.SlotCsvDto;
import kreyj.konfplan.presentation.dto.csv.VortragCsvDto;
import org.jboss.logging.Logger;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static kreyj.konfplan.persistence.NutzerVerfuegbarkeitId.nvId;
import static kreyj.konfplan.persistence.NutzerVerfuegbarkeitId.nvIdL;
import static kreyj.konfplan.persistence.RaumVerfuegbarkeitId.rvId;
import static kreyj.konfplan.persistence.RaumVerfuegbarkeitId.rvIdL;
import static kreyj.konfplan.persistence.VortragVerfuegbarkeitId.vvIdL;
import static kreyj.konfplan.util.DateHelper.DATE_FORMAT;
import static org.apache.commons.collections4.SetUtils.difference;

@ApplicationScoped
public class AdminService {
    private static final Logger LOG = Logger.getLogger(AdminService.class);
    public static final String CSV_PRIO_HEADER = "Teilnehmer E-Mail;Prioritäten";

    private final MailService mailService;

    private final ProtokollService protokollService;

    public AdminService(MailService mailService, ProtokollService protokollService) {
        this.mailService = mailService;
        this.protokollService = protokollService;
    }

    @Transactional
    public List<NutzerDto> getAllUsers() {
        return new HashSet<>(Nutzer.<Nutzer>listAll()) // Duplikate entfernen
                .stream()
                .map(AdminResource::mapNutzerToDto)
                .toList();
    }

    @Transactional
    public List<NutzerDto> getAllUsers(Long veranstaltungId) {
        List<Nutzer> admins = Nutzer.list("role = 'ADMIN'");
        List<Nutzer> vNutzers = Nutzer.find("SELECT u FROM Nutzer u JOIN u.veranstaltungen v WHERE v.id = ?1", veranstaltungId).list();

        return Stream.concat(admins.stream(), vNutzers.stream())
                .distinct()
                .map(AdminResource::mapNutzerToDto)
                .toList();
    }

    @Transactional
    public Nutzer findNutzer(Long id) {
        return Nutzer.findById(id);
    }

    @Transactional
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
            t.setGruppe(dto.gruppe);
        }

        nutzer.persist();

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
        return AdminResource.mapNutzerToDto(nutzer);
    }

    @Transactional
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
                throw new IllegalArgumentException("Die neue E-Mail-Adresse wird bereits verwendet.");
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
            t.setGruppe(dto.gruppe);
        }

        nutzer.persistAndFlush();

        protokollService.log(ProtokollKategorie.NUTZER, "Nutzer aktualisiert", "Nutzer '" + nutzer.getEmail() + "' aktualisiert.", nutzer.getId());
        return AdminResource.mapNutzerToDto(nutzer);
    }

    @Transactional
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
    public void inviteUserToEvent(Long userId, Long eventId) {
        Nutzer nutzer = Nutzer.findById(userId);
        Veranstaltung event = Veranstaltung.findById(eventId);

        if (nutzer == null || event == null) {
            throw new IllegalArgumentException("Nutzer oder Veranstaltung nicht gefunden.");
        }

        // Validierung: Veranstaltung darf nicht in der Vergangenheit liegen (Enddatum prüfen)
        LocalDateTime now = LocalDateTime.now();
        if (event.getEndetAm() != null && event.getEndetAm().isBefore(now)) {
            throw new IllegalArgumentException("Die Veranstaltung '" + event.getName() + "' ist bereits beendet.");
        }

        if (!nutzer.getVeranstaltungen().contains(event)) {
            nutzer.addVeranstaltung(event);
            mailService.sendEinladungZuVeranstaltung(nutzer, event);
            LOG.info("Nutzer " + nutzer.getEmail() + " zu Veranstaltung " + event.getName() + " eingeladen.");
            protokollService.log(ProtokollKategorie.SECURITY, "Nutzer zu Veranstaltung eingeladen", "Nutzer '" + nutzer.getEmail() + "' zu '" + event.getName() + "' eingeladen.", event.getId());
        } else {
            LOG.info("Nutzer " + nutzer.getEmail() + " ist bereits für Veranstaltung " + event.getName() + " registriert.");
        }
    }

    @Transactional
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
    public void toggleUserStatus(Long id) {
        Nutzer entity = Nutzer.findById(id);
        if (entity != null) {
            entity.setActive(!entity.isActive());
            protokollService.log(ProtokollKategorie.NUTZER, "Nutzer-Status geändert", "Status von '" + entity.getEmail() + "' auf " + (entity.isActive() ?
                    "aktiv" : "inaktiv") + " geändert.", id);
        }
    }

    public List<Vortrag> getAllVortraege(Long veranstaltungId) {
        return Vortrag.find("veranstaltung.id = ?1", veranstaltungId).list();
    }

    public Vortrag getVeranstaltungsVortrag(Long veranstaltungId, Long vortragId) {
        return Vortrag.find("veranstaltung.id = ?1 and id = ?2", veranstaltungId, vortragId).firstResult();
    }

    public List<Referent> getAllReferenten(Long veranstaltungId) {
        return Nutzer.find("role = 'REFERENT' AND veranstaltung.id = ?1", veranstaltungId).list();
    }

    @Transactional
    public Vortrag createVortrag(VortragDto vortragDto) {
        Objects.requireNonNull(vortragDto, "VortragDTO darf nicht null sein.");
        Long veranstaltungId = vortragDto.veranstaltungId;
        Objects.requireNonNull(veranstaltungId, "veranstaltungId darf nicht null sein.");
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        Objects.requireNonNull(veranstaltung, "Unbekannte Veranstaltung zu id: " + veranstaltungId + ".");
        Referent referent = Referent.findById(vortragDto.referentId);
        Objects.requireNonNull(referent, "Unbekannter Referent zu id: " + vortragDto.referentId + ".");

        Vortrag created = null;

        if (vortragDto.istPflicht) {
            if (vortragDto.pflichtSlotId == null
                    || vortragDto.pflichtRaumId == null
                    || vortragDto.pflichtGruppe == null
                    || vortragDto.pflichtGruppe.isBlank()) {
                throw new IllegalArgumentException("Für Pflichtvorträge müssen Slot, Raum und Gruppe angegeben werden.");
            }

            Raum pflichtRaum = Raum.findById(vortragDto.pflichtRaumId);
            Objects.requireNonNull(pflichtRaum, "Unbekannter Raum zu id: " + vortragDto.pflichtRaumId + ".");
            Slot pflichtSlot = Slot.findById(vortragDto.pflichtSlotId);
            Objects.requireNonNull(pflichtSlot, "Unbekannter Slot zu id: " + vortragDto.pflichtSlotId + ".");

            // Vorbedingungen prüfen
            if (isRaumGebucht(vortragDto.pflichtRaumId, vortragDto.pflichtSlotId, veranstaltungId)) {
                throw new IllegalArgumentException("Raum '" + pflichtRaum.getName() + "' ist im Slot '"
                        + pflichtSlot.getDescription() + "' bereits belegt.");
            }

            List<Teilnehmer> teilnehmerDerGruppe = getActiveTeilnehmerByGruppe(vortragDto.pflichtGruppe, veranstaltungId);
            if (sindTeilnehmerGebucht(teilnehmerDerGruppe, vortragDto.pflichtSlotId, veranstaltungId)) {
                throw new IllegalArgumentException("Nicht alle Teilnehmer der Gruppe '" + vortragDto.pflichtGruppe
                        + "' sind im Slot '" + pflichtSlot.getDescription() + "' verfügbar.");
            }

            if (kapazitaetZuGering(pflichtRaum, vortragDto.pflichtGruppe, veranstaltungId)) {
                throw new IllegalArgumentException("Raumkapazität von '" + pflichtRaum.getName() + "' reicht für die Gruppe '"
                        + vortragDto.pflichtGruppe + "' nicht aus.");
            }

            // map vortragDTO to Vortrag
            Pflichtvortrag pv = new Pflichtvortrag();
            pv.setTitel(vortragDto.titel);
            pv.setInhalt(vortragDto.inhalt);
            pv.setReferent(referent);
            pv.setPflichtgruppe(vortragDto.pflichtGruppe);
            pv.setPflichtslot(pflichtSlot);
            pv.setPflichtraum(pflichtRaum);

            pv.persistAndFlush();
            created = pv;

            // Update availabilities
            RaumVerfuegbarkeit rv = RaumVerfuegbarkeit.findById(rvId(pflichtRaum, veranstaltung));
            if (rv != null) {
                rv.removeSlot(pflichtSlot);
            }

            for (Teilnehmer teilnehmer : teilnehmerDerGruppe) {
                NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvId(teilnehmer, veranstaltung));
                if (nv != null) {
                    nv.removeSlot(pflichtSlot);
                }
            }
        } else {
            Wahlvortrag wv = new Wahlvortrag();
            wv.setTitel(vortragDto.titel);
            wv.setInhalt(vortragDto.inhalt);
            wv.setReferent(referent);
            wv.setWiederholbar(vortragDto.wiederholbar);
            wv.setMaxWiederholungen(vortragDto.maxWiederholungen);

            wv.persistAndFlush();
            created = wv;
        }

        veranstaltung.addVortrag(created);
        veranstaltung.persist();

        protokollService.log(ProtokollKategorie.VORTRAEGE, "Vortrag erstellt",
                "Vortrag '" + created.getTitel() + "' (" + (created.istPflicht() ? "Pflicht" : "Wahl") + ") erstellt.",
                created.getId());

        return created;
    }

    @Transactional
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
                if (dto.email == null || dto.email.isBlank()) {
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
                    a.persist();
                    count++;
                    protokollService.log(ProtokollKategorie.NUTZER, "Admin importiert", "Admin '" + email + "' via CSV importiert.", a.getId());
                } else {
                    LOG.warn("Admin '" + email + "' übersprungen: Existiert bereits.");
                }
            }
        } catch (Exception e) {
            LOG.error("Kritischer Fehler beim Importieren der Admins aus CSV: " + csvFilePath, e);
            throw e;
        }
        LOG.info("Admin-Import abgeschlossen: " + count + " Admins importiert.");
        return count;
    }

    @Transactional
    public int importVortraegeFromCsv(Path csvFilePath, Long veranstaltungId) throws Exception {
        int count = 0;
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);

        if (veranstaltung == null) {
            LOG.error("CSV-Import (Vorträge) abgebrochen: Veranstaltung mit ID " + veranstaltungId + " nicht gefunden.");
            throw new IllegalArgumentException("Veranstaltung nicht gefunden.");
        }
        var v_raeume = veranstaltung.getGebaeude().stream()
                .flatMap(g -> g.getRaeume().stream())
                .toList();

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
                if (csvDto.titel == null || csvDto.titel.isBlank()) {
                    LOG.warn("Vortrag übersprungen: Titel fehlt.");
                    continue;
                }

                VortragDto dto = new VortragDto();
                dto.istPflicht = csvDto.istPflicht;
                dto.titel = csvDto.titel;
                dto.inhalt = csvDto.inhalt;

                Nutzer referent = Nutzer.findByEmail(csvDto.referentEmail);

                if (referent instanceof Referent) {
                    if (csvDto.istPflicht) {
                        if (csvDto.pflichtGruppe == null
                                || csvDto.pflichtGruppe.isBlank()) {
                            LOG.warn("Vortrag '" + csvDto.titel + "': Gruppe fehlt." +
                                    " Pflichtvortrag kann nicht erstellt werden.");
                            continue;
                        }

                        dto.pflichtGruppe = csvDto.pflichtGruppe;


                        if (csvDto.pflichtSlot == null
                                || csvDto.pflichtSlot.isBlank()
                                || !slotsByName.containsKey(csvDto.pflichtSlot)) {
                            LOG.warn("Vortrag '" + csvDto.titel + "': Slot '" + csvDto.pflichtSlot + "' nicht gefunden. Pflichtvortrag kann nicht erstellt werden.");
                            continue;
                        }

                        dto.pflichtSlotId = slotsByName.get(csvDto.pflichtSlot).getId();

                        Map<Gebaeude, Raum> gebaeudeRaumMap = raeumeByName.get(csvDto.pflichtRaum);
                        if (null == gebaeudeRaumMap) {
                            if (csvDto.pflichtRaum != null && !csvDto.pflichtRaum.isBlank()) {
                                LOG.warn("Vortrag '" + csvDto.titel + "': Unbekannter Raum '" + csvDto.pflichtRaum + "'. Pflichtvortrag kann nicht erstellt werden.");
                            }
                            continue;
                        } else {
                            Set<Gebaeude> gebaeudeSet = veranstaltung.getGebaeude().stream()
                                    .filter(gebaeudeRaumMap::containsKey)
                                    .collect(Collectors.toSet());

                            if (gebaeudeSet.isEmpty()) {
                                LOG.warn("Vortrag '" + csvDto.titel + "': Raum '" + csvDto.pflichtRaum + "' nicht gefunden in Veranstaltungsgebäuden. Pflichtvortrag kann nicht erstellt werden.");
                                continue;
                            } else if (gebaeudeSet.size() == 1) {
                                dto.pflichtRaumId = gebaeudeRaumMap.get(gebaeudeSet.iterator().next()).getId();
                            } else {
                                LOG.warn("Vortrag '" + csvDto.titel + "': Raum '" + csvDto.pflichtRaum + "' nicht eindeutig in Veranstaltungsgebäuden. Pflichtvortrag kann nicht erstellt werden.");
                                continue;
                            }
                        }

                    } else {
                        dto.wiederholbar = csvDto.wiederholbar;
                        dto.maxWiederholungen = csvDto.maxWiederholungen;
                    }

                    dto.referentId = referent.getId();

                    try {
                        Vortrag vortrag = createVortrag(dto);

                        count++;
                        protokollService.log(ProtokollKategorie.VORTRAEGE, "Vortrag importiert",
                                "Vortrag '" + dto.titel + "' via" +
                                        " CSV importiert.", vortrag.getId());
                    } catch (IllegalArgumentException e) {
                        LOG.warn("Vortrag '" + csvDto.titel + "' übersprungen aufgrund von Validierungsfehler: " + e.getMessage());
                    }

                } else {
                    LOG.warn("Vortrag '" + csvDto.titel + "' übersprungen: Referent mit Email " + csvDto.referentEmail + " nicht gefunden oder kein Referent.");
                }
            }
        } catch (Exception e) {
            LOG.error("Kritischer Fehler beim Importieren der Vorträge aus CSV: " + csvFilePath, e);
            throw e;
        }
        LOG.info("Vortrag-Import abgeschlossen: " + count + " Vorträge importiert.");
        return count;
    }

    @Transactional
    public int importPrioritaetenFromCsv(Path csvFilePath, Long veranstaltungId) throws Exception {
        int count = 0;
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        if (veranstaltung == null) {
            LOG.error("CSV-Import (Prioritäten) abgebrochen: Veranstaltung mit ID " + veranstaltungId + " nicht gefunden.");
            throw new IllegalArgumentException("Veranstaltung nicht gefunden.");
        }

        boolean headerLineFound = false;

        try (BufferedReader reader = new BufferedReader(new FileReader(csvFilePath.toFile()))) {
            String line;

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
                        throw new IllegalArgumentException("Ungültiger Header für Prio-Import in " + csvFilePath.getFileName());
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

                String wvPrioStr = lineItems[1];
                if (wvPrioStr != null && !wvPrioStr.isBlank()) {
                    String[] wvPrios = wvPrioStr.split(",");

                    for (String wvPrio : wvPrios) {
                        wvPrio = wvPrio.trim();
                        if (!wvPrio.matches("\\d+\\s*:\\s*\\d+")) {
                            LOG.warn("Priorität für '" + teilnehmerEmail + "' übersprungen: Prio format " + wvPrio + " ist ungültig.");
                            continue;
                        }
                        String[] data = wvPrio.split(":");
                        Long vortragId = Long.parseLong(data[0].trim());
                        Vortrag vortrag = Vortrag.findById(vortragId);

                        if (!(vortrag instanceof Wahlvortrag) || !vortrag.getVeranstaltung().getId().equals(veranstaltungId)) {
                            LOG.warn("Priorität für '" + teilnehmerEmail + "' übersprungen: Vortrag mit ID " + vortragId + " ist ungültig oder kein Wahlvortrag dieser Veranstaltung.");
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
                            prioritaet.persist();
                            count++;
                            protokollService.log(ProtokollKategorie.VORTRAEGE, "Priorität importiert", "Priorität für '" + teilnehmer.getEmail() + "' für Vortrag '" + vortrag.getTitel() + "' auf " + prioWert + " gesetzt.", vortrag.getId());
                        } catch (NumberFormatException e) {
                            LOG.warn("Ungültiger Prioritätswert für Teilnehmer " + teilnehmerEmail + " und Vortrag " + vortragId);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Kritischer Fehler beim Importieren der Prioritäten aus CSV: " + csvFilePath, e);
            throw e;
        }
        LOG.info("Prioritäten-Import abgeschlossen: " + count + " Prioritäten importiert/aktualisiert.");
        return count;
    }


    public List<Slot> getAllEventSlots(Long veranstaltungId) {
        return Slot.find("veranstaltung.id = ?1", veranstaltungId).list();
    }

    @Transactional
    public Slot createEventSlot(Slot slot, Long veranstaltungId) {
        Veranstaltung v = Veranstaltung.findById(veranstaltungId);
        validateSlot(slot, v, null);
        slot.setVeranstaltung(v);
        slot.persist();
        v.addSlot(slot);

        // Create availability for all participants of the event
        for (Nutzer nutzer : v.getNutzer()) {
            if (nutzer instanceof Teilnehmer || nutzer instanceof Referent) {
                NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvIdL(nutzer.getId(), veranstaltungId));

                if (null != nv) {
                    nv.addSlot(slot.getId());
                }
            }
        }

        protokollService.log(ProtokollKategorie.VERANSTALTUNG, "Zeit-Slot erstellt", "Slot '" + slot.getDescription() + "' für '" + v.getName() + "' erstellt.", slot.getId());
        return slot;
    }

    @Transactional
    public Slot updateEventSlot(Long id, Slot updated, Long veranstaltungId) {
        Slot entity = Slot.findById(id);

        if (null == entity) {
            return null;
        }

        if (!Objects.equals(entity.getVersion(), updated.getVersion())) {
            throw new OptimisticLockException("Der Slot wurde zwischenzeitlich von Dritten geändert. Bitte aktualisieren Sie die Daten und versuchen Sie es erneut.");
        }
        Veranstaltung v = Veranstaltung.findById(veranstaltungId);
        if (entity.getVeranstaltung().getId().equals(veranstaltungId)) {
            validateSlot(updated, v, id);
            entity.setDescription(updated.getDescription());
            entity.setStartTime(updated.getStartTime());
            entity.setEndTime(updated.getEndTime());
            protokollService.log(ProtokollKategorie.VERANSTALTUNG, "Zeit-Slot aktualisiert", "Slot '" + entity.getDescription() + "' aktualisiert.", entity.getId());
        }
        return entity;
    }

    private void validateSlot(Slot slot, Veranstaltung v, Long excludeId) {
        if (v == null) {
            throw new IllegalArgumentException("Veranstaltung nicht gefunden.");
        }
        if (slot.getStartTime() == null || slot.getEndTime() == null) {
            throw new IllegalArgumentException("Beginn und Ende müssen gesetzt sein.");
        }
        if (!slot.getEndTime().isAfter(slot.getStartTime())) {
            throw new IllegalArgumentException("Das Ende muss nach dem Beginn liegen.");
        }
        if (slot.getStartTime().isBefore(v.getBeginntAm())) {
            throw new IllegalArgumentException("Der Slot darf nicht vor der Veranstaltung beginnen.");
        }
        // NEUE PRÜFUNG: Slot darf nicht nach dem Ende der Veranstaltung enden.
        if (slot.getEndTime().isAfter(v.getEndetAm())) {
            throw new IllegalArgumentException("Der Slot darf nicht nach der Veranstaltung enden.");
        }

        List<Slot> existing = Slot.find("veranstaltung = ?1", v).list();
        for (Slot other : existing) {
            if (other.getId().equals(excludeId)) {
                continue;
            }
            // Überschneidungsprüfung: (StartA < EndeB) AND (EndA > StartB)
            if (slot.getStartTime().isBefore(other.getEndTime()) && slot.getEndTime().isAfter(other.getStartTime())) {
                throw new IllegalArgumentException("Der Zeit-Slot überschneidet sich mit einem vorhandenen Intervall (" + other.getDescription() + ").");
            }
        }
    }

    @Transactional
    public boolean deleteEventSlot(Long id, Long veranstaltungId) {
        Slot slot = Slot.findById(id);
        if (slot != null && slot.getVeranstaltung().getId().equals(veranstaltungId)) {
            String desc = slot.getDescription();

            // Delete all availabilities associated with this slot
            NutzerVerfuegbarkeit.<NutzerVerfuegbarkeit>find("veranstaltungId", veranstaltungId).stream()
                    .forEach(v -> v.removeSlot(id));
            RaumVerfuegbarkeit.<RaumVerfuegbarkeit>find("veranstaltungId", veranstaltungId).stream()
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
    public int importSlotsFromCsv(Path csvFilePath, Long veranstaltungId) throws Exception {
        int count = 0;
        Veranstaltung v = Veranstaltung.findById(veranstaltungId);
        if (v == null) {
            LOG.error("CSV-Import (Slots) abgebrochen: Veranstaltung mit ID " + veranstaltungId + " nicht gefunden.");
            throw new IllegalArgumentException("Veranstaltung nicht gefunden.");
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
                if (dto.bezeichnung == null || dto.bezeichnung.isBlank()) {
                    LOG.warn("Slot übersprungen: Beschreibung fehlt.");
                    continue;
                }
                Slot slot = new Slot();
                slot.setDescription(dto.bezeichnung);
                try {
                    slot.setStartTime(LocalDateTime.parse(dto.tag + " " + dto.beginntUm, DATE_FORMAT));
                    slot.setEndTime(LocalDateTime.parse(dto.tag + " " + dto.endetUm, DATE_FORMAT));
                } catch (Exception e) {
                    LOG.error("Fehler beim Parsen der Zeit für Slot '" + dto.bezeichnung + "': " + e.getMessage());
                    continue;
                }
                slot.setVeranstaltung(v);

                try {
                    createEventSlot(slot, veranstaltungId); // Use createEventSlot to also create availabilities
                    count++;
                    protokollService.log(ProtokollKategorie.VERANSTALTUNG, "Zeit-Slot importiert", "Slot '" + slot.getDescription() + "' via CSV importiert.", slot.getId());
                } catch (IllegalArgumentException e) {
                    LOG.warn("Slot '" + dto.bezeichnung + "' übersprungen aufgrund von Validierungsfehler: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            LOG.error("Kritischer Fehler beim Importieren der Slots aus CSV: " + csvFilePath, e);
            throw e;
        }
        LOG.info("Slot-Import abgeschlossen: " + count + " Slots importiert.");
        return count;
    }

    @Transactional
    public Vortrag updateVortrag(Long veranstaltungId, Long vortragId, VortragDto updated) {
        Vortrag entity = Vortrag.findById(vortragId);
        if (entity == null || !entity.getVeranstaltung().getId().equals(veranstaltungId)) {
            return null;
        }

        if (!Objects.equals(entity.getVersion(), updated.version)) {
            throw new OptimisticLockException("Der Vortrag wurde zwischenzeitlich von Dritten geändert. Bitte aktualisieren Sie die Daten und versuchen Sie es erneut.");
        }

        // Check if type changes (e.g., Wahlvortrag to Pflichtvortrag or vice versa)
        if (entity.istPflicht() != updated.istPflicht) {
            throw new IllegalArgumentException("Der Vortragstyp kann nicht geändert werden.");
        }

        entity.setTitel(updated.titel);
        entity.setInhalt(updated.inhalt);

        if (entity instanceof Pflichtvortrag pv && updated.istPflicht) {
            pv.setPflichtgruppe(updated.pflichtGruppe);
            pv.setPflichtslot(Slot.findById(updated.pflichtSlotId));
            pv.setPflichtraum(Raum.findById(updated.pflichtRaumId));
        } else if (entity instanceof Wahlvortrag wv && !updated.istPflicht) {
            wv.setWiederholbar(updated.wiederholbar);
            wv.setMaxWiederholungen(updated.maxWiederholungen);

            VortragVerfuegbarkeit vv = VortragVerfuegbarkeit.findById(vvIdL(updated.id, veranstaltungId));
            if (null == vv) {
                new VortragVerfuegbarkeit(updated.id, veranstaltungId, updated.verfuegbareSlotIds).persist();
            } else {
                updated.verfuegbareSlotIds.forEach(vv::addSlot);
            }
        }
        protokollService.log(ProtokollKategorie.VORTRAEGE, "Vortrag aktualisiert", "Vortrag '" + entity.getTitel() + "' aktualisiert.", entity.getId());
        return entity;
    }

    @Transactional
    public boolean deleteVortrag(Long id, Long veranstaltungId) {
        Vortrag entity = Vortrag.findById(id);
        if (entity == null || !entity.getVeranstaltung().getId().equals(veranstaltungId)) {
            return false;
        }

        String titel = entity.getTitel();
        if (entity instanceof Pflichtvortrag pv) {
            // Restore availabilities
            RaumVerfuegbarkeit rv = RaumVerfuegbarkeit.findById(rvIdL(pv.getPflichtraum().getId(), veranstaltungId));
            if (rv != null) {
                rv.addSlot(pv.getPflichtslot().getId());
            }

            List<Teilnehmer> teilnehmerDerGruppe = getActiveTeilnehmerByGruppe(pv.getPflichtgruppe(), veranstaltungId);
            for (Teilnehmer teilnehmer : teilnehmerDerGruppe) {
                NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvIdL(teilnehmer.getId(),
                        veranstaltungId));
                if (nv != null) {
                    nv.addSlot(pv.getPflichtslot().getId());
                }
            }
        }

        boolean deleted = Vortrag.deleteById(id);
        if (deleted) {
            protokollService.log(ProtokollKategorie.VORTRAEGE, "Vortrag gelöscht", "Vortrag '" + titel + "' gelöscht.", id);
        }
        return deleted;
    }

    public List<VortragStatDto> getStats(Long veranstaltungId) {
        List<Vortrag> all = Vortrag.find("veranstaltung.id = ?1", veranstaltungId).list();
        return all.stream().map(v -> new VortragStatDto(v.getTitel(), 0, 0, 0, 0, 0)).toList();
    }

    // #################################################################################################################
    // # Helper Methods
    // #################################################################################################################

    private void erstelleVerfuegbarkeitenFuerNutzerInVeranstaltung(Nutzer nutzer, Veranstaltung veranstaltung) {
        Set<Long> slotIds = veranstaltung.getSlots().stream().map(IdEntity::getId).collect(Collectors.toSet());

        NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvId(nutzer, veranstaltung));

        if (null == nv) {
            new NutzerVerfuegbarkeit(nutzer, veranstaltung, slotIds).persist();
        }
    }

    private boolean sindTeilnehmerGebucht(List<Teilnehmer> teilnehmer, Long slotId, Long veranstaltungId) {
        if (teilnehmer.isEmpty()) {
            return false;
        }
        List<Long> teilnehmerIds = teilnehmer.stream().map(IdEntity::getId).toList();
        List<NutzerVerfuegbarkeit> verfuegbarkeiten = NutzerVerfuegbarkeit.find("nutzerId in ?1 and veranstaltungId = ?2",
                teilnehmerIds, veranstaltungId).list();

        for (NutzerVerfuegbarkeit verfuegbarkeit : verfuegbarkeiten) {
            if (!verfuegbarkeit.getVerfuegbareSlotIds().contains(slotId)) {
                return true; // At least one participant is not available
            }
        }
        return false;
    }

    private boolean isRaumGebucht(Long raumId, Long slotId, Long veranstaltungId) {
        RaumVerfuegbarkeit rv = RaumVerfuegbarkeit.findById(rvIdL(raumId, veranstaltungId));

        return null != rv && !rv.getVerfuegbareSlotIds().contains(slotId);
    }

    private boolean kapazitaetZuGering(Raum raum, String gruppe, Long veranstaltungId) {
        if (raum == null || raum.getKapazitaet() == null) {
            return false;
        }
        long activeTeilnehmerCount = getActiveTeilnehmerByGruppe(gruppe, veranstaltungId).size();
        return raum.getKapazitaet() < activeTeilnehmerCount;
    }

    private List<Teilnehmer> getActiveTeilnehmerByGruppe(String gruppe, Long veranstaltungId) {
        return Teilnehmer.find("SELECT t FROM Teilnehmer t JOIN t.veranstaltungen v WHERE t.gruppe = ?1 AND v.id = ?2 AND t.isActive = true",
                        gruppe, veranstaltungId)
                .list();
    }
}