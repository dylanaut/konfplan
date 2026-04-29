package kreyj.vortragsmanager.service;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import kreyj.vortragsmanager.dto.UserDto;
import kreyj.vortragsmanager.dto.VortragStatDto;
import kreyj.vortragsmanager.dto.csv.AdminCsvDto;
import kreyj.vortragsmanager.dto.csv.EventSlotCsvDto;
import kreyj.vortragsmanager.dto.csv.VortragCsvDto;
import kreyj.vortragsmanager.entity.*;
import org.jboss.logging.Logger;

import java.io.FileReader;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static kreyj.vortragsmanager.util.DateHelper.DATE_FORMAT;
import static org.apache.commons.collections4.SetUtils.difference;

@ApplicationScoped
public class AdminService {
    private static final Logger LOG = Logger.getLogger(AdminService.class);

    @Inject
    MailService mailService;

    public List<UserDto> getAllUsers(Long veranstaltungId) {
        List<Nutzer> admins = Nutzer.list("role = 'ADMIN'");
        List<Nutzer> vNutzers = Nutzer.find("SELECT u FROM Nutzer u JOIN u.veranstaltungen v WHERE v.id = ?1", veranstaltungId).list();

        return Stream.concat(admins.stream(), vNutzers.stream())
                .distinct()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public List<UserDto> getAllUsers() {
        return Nutzer.<Nutzer>listAll().stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional
    public UserDto createUser(UserDto dto, List<Long> veranstaltungsIds) {
        Nutzer nutzer;
        if ("REFERENT".equals(dto.role)) {
            nutzer = new Referent();
        } else if ("TEILNEHMER".equals(dto.role)) {
            nutzer = new Teilnehmer();
        } else {
            nutzer = new Admin();
        }

        nutzer.email = dto.email;
        nutzer.firstName = dto.firstName;
        nutzer.lastName = dto.lastName;
        nutzer.isActive = dto.isActive;

        if (dto.email != null) {
            nutzer.passwordHash = BcryptUtil.bcryptHash("start123");
        }

        if (nutzer instanceof Referent r) {
            r.biography = dto.biography;
            r.jobRole = dto.jobRole;
            r.organisation = dto.organisation;
            r.slogan = dto.slogan;
        } else if (nutzer instanceof Teilnehmer t) {
            t.gruppe = dto.gruppe;
        }

        nutzer.persist();

        if (null != veranstaltungsIds) {
            for (Long veranstaltungId : veranstaltungsIds) {
                Veranstaltung v = Veranstaltung.findById(veranstaltungId);
                if (null == v) {
                    LOG.error("Unbekannte Veranstaltung zu id: " + veranstaltungId);
                } else {
                    nutzer.addVeranstaltung(v);
                    nutzer.persist();
                }
            }
        }

        return mapToDto(nutzer);
    }

    @Transactional
    public UserDto updateUser(Long id, UserDto dto, List<Long> vUpdateIds) {
        Nutzer nutzer = Nutzer.findById(id);
        if (nutzer == null) {
            return null;
        }

        nutzer.firstName = dto.firstName;
        nutzer.lastName = dto.lastName;
        nutzer.email = dto.email;
        nutzer.isActive = dto.isActive;

        if (null != vUpdateIds) {
            Set<Long> oldVIds = nutzer.veranstaltungen.stream().map(v -> v.id).collect(Collectors.toSet());
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
            r.biography = dto.biography;
            r.jobRole = dto.jobRole;
            r.organisation = dto.organisation;
            r.slogan = dto.slogan;
        } else if (nutzer instanceof Teilnehmer t) {
            t.gruppe = dto.gruppe;
        }

        return mapToDto(nutzer);
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
        if (event.endetAm != null && event.endetAm.isBefore(now)) {
            throw new IllegalArgumentException("Die Veranstaltung '" + event.name + "' ist bereits beendet.");
        }

        if (!nutzer.veranstaltungen.contains(event)) {
            nutzer.addVeranstaltung(event);
            mailService.sendEventInvitation(nutzer, event);
            LOG.info("Nutzer " + nutzer.email + " zu Veranstaltung " + event.name + " eingeladen.");
        } else {
            LOG.info("Nutzer " + nutzer.email + " ist bereits für Veranstaltung " + event.name + " registriert.");
        }
    }

    private UserDto mapToDto(Nutzer u) {
        UserDto dto = new UserDto();
        dto.id = u.id;
        dto.email = u.email;
        dto.firstName = u.firstName;
        dto.lastName = u.lastName;
        dto.role = u.role;
        dto.isActive = u.isActive;
        dto.veranstaltungIds = null != u.veranstaltungen ? u.veranstaltungen.stream().map(v -> v.id).toList() : Collections.emptyList();

        if (u instanceof Referent r) {
            dto.biography = r.biography;
            dto.jobRole = r.jobRole;
            dto.organisation = r.organisation;
            dto.slogan = r.slogan;
        } else if (u instanceof Teilnehmer t) {
            dto.gruppe = t.gruppe;
        }
        return dto;
    }

    @Transactional
    public boolean deleteUser(Long id) {
        return Nutzer.deleteById(id);
    }

    @Transactional
    public void toggleUserStatus(Long id) {
        Nutzer entity = Nutzer.findById(id);
        if (entity != null) {
            entity.isActive = !entity.isActive;
        }
    }

    public List<Vortrag> getAllVortraege(Long veranstaltungId) {
        return Vortrag.find("veranstaltung.id", veranstaltungId).list();
    }

    public List<Nutzer> getAllReferenten(Long veranstaltungId) {
        return Nutzer.find("role = 'REFERENT' and veranstaltung.id = ?1", veranstaltungId).list();
    }

    @Transactional
    public Vortrag createVortrag(Vortrag vortrag, Long veranstaltungId) {
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        if (veranstaltung == null) {
            throw new IllegalArgumentException("Veranstaltung nicht gefunden.");
        }
        vortrag.veranstaltung = veranstaltung;

        if (vortrag instanceof Pflichtvortrag pv) {
            if (pv.pflichtslot == null || pv.pflichtraum == null || pv.pflichtgruppe == null || pv.pflichtgruppe.isBlank()) {
                throw new IllegalArgumentException("Für Pflichtvorträge müssen Slot, Raum und Gruppe angegeben werden.");
            }

            // Vorbedingungen prüfen
            // 1. Raum darf für Slot nicht belegt sein
            if (!checkRaumAvailability(pv.pflichtraum, pv.pflichtslot)) {
                throw new IllegalArgumentException("Raum '" + pv.pflichtraum.name + "' ist im Slot '" + pv.pflichtslot.description + "' bereits belegt.");
            }

            // 2. Jeder TN der Gruppe muss für Slot verfügbar sein
            List<Teilnehmer> teilnehmerDerGruppe = getActiveTeilnehmerByGruppe(pv.pflichtgruppe, veranstaltungId);
            if (!checkTeilnehmerAvailability(teilnehmerDerGruppe, pv.pflichtslot)) {
                throw new IllegalArgumentException("Nicht alle Teilnehmer der Gruppe '" + pv.pflichtgruppe + "' sind im Slot '" + pv.pflichtslot.description + "' verfügbar.");
            }

            // 3. Raumkapazität muss ausreichen
            if (!checkRaumCapacity(pv.pflichtraum, pv.pflichtgruppe, veranstaltungId)) {
                throw new IllegalArgumentException("Raumkapazität von '" + pv.pflichtraum.name + "' reicht für die Gruppe '" + pv.pflichtgruppe + "' nicht aus.");
            }

            // Effekte anwenden
            vortrag.persist(); // Persistieren, um ID zu erhalten
            updateRaumAvailability(pv.pflichtraum, pv.pflichtslot, true, pv.id);
            updateTeilnehmerAvailability(teilnehmerDerGruppe, pv.pflichtslot, false, pv.id);

        } else {
            vortrag.persist();
        }

        veranstaltung.addVortrag(vortrag); // Assuming addVortrag handles duplicates or is idempotent
        veranstaltung.persist(); // Persist veranstaltung to update relationship

        return vortrag;
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
                    a.email = email;
                    a.firstName = dto.firstName;
                    a.lastName = dto.lastName;
                    a.passwordHash = BcryptUtil.bcryptHash(UUID.randomUUID().toString());
                    a.persist();
                    count++;
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
        var v_raeume = veranstaltung.gebaeude.stream()
                .flatMap(g -> g.raeume.stream())
                .toList();

        Map<String, Map<Gebaeude, Raum>> raeumeByName = new HashMap<>();
        for (Raum r : v_raeume) {
            if (!raeumeByName.containsKey(r.name)) {
                raeumeByName.put(r.name, new HashMap<>());
            }
            raeumeByName.get(r.name).put(r.gebaeude, r);
        }

        Map<String, EventSlot> slotsByName = veranstaltung.eventSlots.stream().collect(Collectors.toMap(s -> s.description, s -> s));

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

            for (VortragCsvDto dto : beans) {
                if (dto.titel == null || dto.titel.isBlank()) {
                    LOG.warn("Vortrag übersprungen: Titel fehlt.");
                    continue;
                }

                Nutzer referent = Nutzer.findByEmail(dto.referentEmail);
                if (referent instanceof Referent) {
                    Vortrag newVortrag;
                    if (dto.istPflicht) {
                        Pflichtvortrag pv = new Pflichtvortrag();
                        pv.pflichtgruppe = dto.pflichtGruppe;
                        pv.pflichtslot = slotsByName.get(dto.pflichtSlot);
                        if (pv.pflichtslot == null && dto.pflichtSlot != null && !dto.pflichtSlot.isBlank()) {
                            LOG.warn("Vortrag '" + dto.titel + "': Slot '" + dto.pflichtSlot + "' nicht gefunden. Pflichtvortrag kann nicht erstellt werden.");
                            continue;
                        }

                        Map<Gebaeude, Raum> gebaeudeRaumMap = raeumeByName.get(dto.pflichtRaum);
                        if (null == gebaeudeRaumMap) {
                            if (dto.pflichtRaum != null && !dto.pflichtRaum.isBlank()) {
                                LOG.warn("Vortrag '" + dto.titel + "': Unbekannter Raum '" + dto.pflichtRaum + "'. Pflichtvortrag kann nicht erstellt werden.");
                            }
                            continue;
                        } else {
                            Set<Gebaeude> gebaeudeSet = veranstaltung.gebaeude.stream()
                                    .filter(gebaeudeRaumMap::containsKey)
                                    .collect(Collectors.toSet());

                            if (gebaeudeSet.isEmpty()) {
                                LOG.warn("Vortrag '" + dto.titel + "': Raum '" + dto.pflichtRaum + "' nicht gefunden in Veranstaltungsgebäuden. Pflichtvortrag kann nicht erstellt werden.");
                                continue;
                            } else if (gebaeudeSet.size() == 1) {
                                pv.pflichtraum = gebaeudeRaumMap.get(gebaeudeSet.iterator().next());
                            } else {
                                LOG.warn("Vortrag '" + dto.titel + "': Raum '" + dto.pflichtRaum + "' nicht eindeutig in Veranstaltungsgebäuden. Pflichtvortrag kann nicht erstellt werden.");
                                continue;
                            }
                        }
                        newVortrag = pv;
                    } else {
                        Wahlvortrag wv = new Wahlvortrag();
                        wv.wiederholbar = dto.wiederholbar;
                        wv.maxWiederholungen = dto.maxWiederholungen;
                        newVortrag = wv;
                    }

                    newVortrag.titel = dto.titel;
                    newVortrag.inhalt = dto.inhalt;
                    newVortrag.referent = (Referent) referent;
                    newVortrag.veranstaltung = veranstaltung;

                    try {
                        createVortrag(newVortrag, veranstaltungId); // Use the new createVortrag logic
                        count++;
                    } catch (IllegalArgumentException e) {
                        LOG.warn("Vortrag '" + dto.titel + "' übersprungen aufgrund von Validierungsfehler: " + e.getMessage());
                    }

                } else {
                    LOG.warn("Vortrag '" + dto.titel + "' übersprungen: Referent mit Email " + dto.referentEmail + " nicht gefunden oder kein Referent.");
                }
            }
        } catch (Exception e) {
            LOG.error("Kritischer Fehler beim Importieren der Vorträge aus CSV: " + csvFilePath, e);
            throw e;
        }
        LOG.info("Vortrag-Import abgeschlossen: " + count + " Vorträge importiert.");
        return count;
    }

    public List<EventSlot> getAllEventSlots(Long veranstaltungId) {
        return EventSlot.find("veranstaltung.id", veranstaltungId).list();
    }

    @Transactional
    public EventSlot createEventSlot(EventSlot slot, Long veranstaltungId) {
        Veranstaltung v = Veranstaltung.findById(veranstaltungId);
        validateSlot(slot, v, null);
        slot.veranstaltung = v;
        slot.persist();
        v.addSlot(slot);
        return slot;
    }

    @Transactional
    public EventSlot updateEventSlot(Long id, EventSlot updated, Long veranstaltungId) {
        EventSlot entity = EventSlot.findById(id);
        Veranstaltung v = Veranstaltung.findById(veranstaltungId);
        if (entity != null && entity.veranstaltung.id.equals(veranstaltungId)) {
            validateSlot(updated, v, id);
            entity.description = updated.description;
            entity.startTime = updated.startTime;
            entity.endTime = updated.endTime;
        }
        return entity;
    }

    private void validateSlot(EventSlot slot, Veranstaltung v, Long excludeId) {
        if (v == null) {
            throw new IllegalArgumentException("Veranstaltung nicht gefunden.");
        }
        if (slot.startTime == null || slot.endTime == null) {
            throw new IllegalArgumentException("Beginn und Ende müssen gesetzt sein.");
        }
        if (!slot.endTime.isAfter(slot.startTime)) {
            throw new IllegalArgumentException("Das Ende muss nach dem Beginn liegen.");
        }
        if (slot.startTime.isBefore(v.beginntAm)) {
            throw new IllegalArgumentException("Der Slot darf nicht vor der Veranstaltung beginnen.");
        }

        List<EventSlot> existing = EventSlot.find("veranstaltung = ?1", v).list();
        for (EventSlot other : existing) {
            if (excludeId != null && other.id.equals(excludeId)) {
                continue;
            }
            // Überschneidungsprüfung: (StartA < EndeB) AND (EndA > StartB)
            if (slot.startTime.isBefore(other.endTime) && slot.endTime.isAfter(other.startTime)) {
                throw new IllegalArgumentException("Der Zeit-Slot überschneidet sich mit einem vorhandenen Intervall (" + other.description + ").");
            }
        }
    }

    @Transactional
    public boolean deleteEventSlot(Long id, Long veranstaltungId) {
        return EventSlot.delete("id = ?1 and veranstaltung.id = ?2", id, veranstaltungId) > 0;
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
            CsvToBean<EventSlotCsvDto> csvToBean = new CsvToBeanBuilder<EventSlotCsvDto>(reader)
                    .withType(EventSlotCsvDto.class)
                    .withSeparator(';')
                    .withIgnoreLeadingWhiteSpace(true)
                    .withThrowExceptions(false)
                    .build();

            List<EventSlotCsvDto> beans = csvToBean.parse();

            csvToBean.getCapturedExceptions().forEach(e ->
                    LOG.error("CSV-Parsing-Fehler in " + csvFilePath.getFileName() + " (Zeile " + e.getLineNumber() + "): " + e.getMessage())
            );

            for (EventSlotCsvDto dto : beans) {
                if (dto.description == null || dto.description.isBlank()) {
                    LOG.warn("Slot übersprungen: Beschreibung fehlt.");
                    continue;
                }
                EventSlot slot = new EventSlot();
                slot.description = dto.description;
                try {
                    slot.startTime = LocalDateTime.parse(dto.day + " " + dto.startTime, DATE_FORMAT);
                    slot.endTime = LocalDateTime.parse(dto.day + " " + dto.endTime, DATE_FORMAT);
                } catch (Exception e) {
                    LOG.error("Fehler beim Parsen der Zeit für Slot '" + dto.description + "': " + e.getMessage());
                    continue;
                }
                slot.veranstaltung = v;
                slot.persist();

                v.addSlot(slot);
                v.persist();

                count++;
            }
        } catch (Exception e) {
            LOG.error("Kritischer Fehler beim Importieren der Slots aus CSV: " + csvFilePath, e);
            throw e;
        }
        LOG.info("Slot-Import abgeschlossen: " + count + " Slots importiert.");
        return count;
    }

    @Transactional
    public Vortrag updateVortrag(Long id, Vortrag updated, Long veranstaltungId) {
        Vortrag entity = Vortrag.findById(id);
        if (entity == null || !entity.veranstaltung.id.equals(veranstaltungId)) {
            return null;
        }

        // Check if type changes (e.g., Wahlvortrag to Pflichtvortrag or vice versa)
        if (entity.istPflicht() != updated.istPflicht()) {
            throw new IllegalArgumentException("Der Vortragstyp kann nicht geändert werden.");
        }

        entity.titel = updated.titel;
        entity.inhalt = updated.inhalt;

        if (entity instanceof Pflichtvortrag oldPv && updated instanceof Pflichtvortrag newPv) {
            // Store old values for comparison
            String oldGruppe = oldPv.pflichtgruppe;
            EventSlot oldSlot = oldPv.pflichtslot;
            Raum oldRaum = oldPv.pflichtraum;

            // Update entity with new values
            oldPv.pflichtgruppe = newPv.pflichtgruppe;
            oldPv.pflichtslot = newPv.pflichtslot;
            oldPv.pflichtraum = newPv.pflichtraum;

            // Use the updated entity for validations
            if (oldPv.pflichtslot == null || oldPv.pflichtraum == null || oldPv.pflichtgruppe == null || oldPv.pflichtgruppe.isBlank()) {
                throw new IllegalArgumentException("Für Pflichtvorträge müssen Slot, Raum und Gruppe angegeben werden.");
            }

            // Use Case 1: Slot ändern
            if (!Objects.equals(oldSlot, oldPv.pflichtslot)) {
                // Vorbedingungen:
                // * alle TN der Gruppe müssen im neuen Slot verfügbar sein
                List<Teilnehmer> teilnehmerDerGruppe = getActiveTeilnehmerByGruppe(oldPv.pflichtgruppe, veranstaltungId);
                if (!checkTeilnehmerAvailability(teilnehmerDerGruppe, oldPv.pflichtslot)) {
                    throw new IllegalArgumentException("Nicht alle Teilnehmer der Gruppe '" + oldPv.pflichtgruppe + "' sind im neuen Slot '" + oldPv.pflichtslot.description + "' verfügbar.");
                }
                // * Raum ist im neuen Slot belegbar
                if (!checkRaumAvailability(oldPv.pflichtraum, oldPv.pflichtslot)) {
                    throw new IllegalArgumentException("Raum '" + oldPv.pflichtraum.name + "' ist im neuen Slot '" + oldPv.pflichtslot.description + "' bereits belegt.");
                }

                // Effekte:
                // * Raum wird für alten Slot wieder belegbar
                updateRaumAvailability(oldRaum, oldSlot, false, oldPv.id);
                // * alle TN der Gruppe sind im alten Slot wieder verfügbar
                updateTeilnehmerAvailability(teilnehmerDerGruppe, oldSlot, true, oldPv.id);
                // * Raum ist für neuen Slot belegt
                updateRaumAvailability(oldPv.pflichtraum, oldPv.pflichtslot, true, oldPv.id);
                // * alle TN der Gruppe sind im neuen Slot nicht verfügbar
                updateTeilnehmerAvailability(teilnehmerDerGruppe, oldPv.pflichtslot, false, oldPv.id);
            }

            // Use Case 2: Raum ändern
            if (!Objects.equals(oldRaum, oldPv.pflichtraum)) {
                // Vorbedingungen:
                // * Raum ist im neuen Slot belegbar
                if (!checkRaumAvailability(oldPv.pflichtraum, oldPv.pflichtslot)) {
                    throw new IllegalArgumentException("Neuer Raum '" + oldPv.pflichtraum.name + "' ist im Slot '" + oldPv.pflichtslot.description + "' bereits belegt.");
                }
                // * Raumkapazität reicht für Anzahl der Teilnehmer aus
                if (!checkRaumCapacity(oldPv.pflichtraum, oldPv.pflichtgruppe, veranstaltungId)) {
                    throw new IllegalArgumentException("Raumkapazität von '" + oldPv.pflichtraum.name + "' reicht für die Gruppe '" + oldPv.pflichtgruppe + "' nicht aus.");
                }

                // Effekte:
                // * Alter Raum wird für Slot wieder belegbar
                updateRaumAvailability(oldRaum, oldPv.pflichtslot, false, oldPv.id);
                // * Neuer Raum ist für Slot belegt
                updateRaumAvailability(oldPv.pflichtraum, oldPv.pflichtslot, true, oldPv.id);
            }

            // Use Case 3: Gruppe ändern
            if (!Objects.equals(oldGruppe, oldPv.pflichtgruppe)) {
                // Vorbedingungen:
                // * Raumkapazität für Slot reicht für Anzahl der Teilnehmer aus (für neue Gruppe)
                if (!checkRaumCapacity(oldPv.pflichtraum, oldPv.pflichtgruppe, veranstaltungId)) {
                    throw new IllegalArgumentException("Raumkapazität von '" + oldPv.pflichtraum.name + "' reicht für die neue Gruppe '" + oldPv.pflichtgruppe + "' nicht aus.");
                }
                // * alle TN der neuen Gruppe müssen im Slot verfügbar sein
                List<Teilnehmer> neueTeilnehmerDerGruppe = getActiveTeilnehmerByGruppe(oldPv.pflichtgruppe, veranstaltungId);
                if (!checkTeilnehmerAvailability(neueTeilnehmerDerGruppe, oldPv.pflichtslot)) {
                    throw new IllegalArgumentException("Nicht alle Teilnehmer der neuen Gruppe '" + oldPv.pflichtgruppe + "' sind im Slot '" + oldPv.pflichtslot.description + "' verfügbar.");
                }

                // Effekte:
                // * alle TN der alten Gruppe sind im Slot wieder verfügbar
                List<Teilnehmer> alteTeilnehmerDerGruppe = getActiveTeilnehmerByGruppe(oldGruppe, veranstaltungId);
                updateTeilnehmerAvailability(alteTeilnehmerDerGruppe, oldPv.pflichtslot, true, oldPv.id);
                // * alle TN der neuen Gruppe sind im Slot nicht mehr verfügbar
                updateTeilnehmerAvailability(neueTeilnehmerDerGruppe, oldPv.pflichtslot, false, oldPv.id);
            }

            // If no specific change, but still a Pflichtvortrag, ensure consistency
            // This handles cases where only titel/inhalt change, but also ensures initial state if no change was detected above
            if (Objects.equals(oldSlot, oldPv.pflichtslot) && Objects.equals(oldRaum, oldPv.pflichtraum) && Objects.equals(oldGruppe, oldPv.pflichtgruppe)) {
                // No change in PV specific fields, but ensure availability is set if it wasn't before
                List<Teilnehmer> teilnehmerDerGruppe = getActiveTeilnehmerByGruppe(oldPv.pflichtgruppe, veranstaltungId);
                updateRaumAvailability(oldPv.pflichtraum, oldPv.pflichtslot, true, oldPv.id);
                updateTeilnehmerAvailability(teilnehmerDerGruppe, oldPv.pflichtslot, false, oldPv.id);
            }

        } else if (entity instanceof Wahlvortrag wv && updated instanceof Wahlvortrag updatedWv) {
            wv.wiederholbar = updatedWv.wiederholbar;
            wv.maxWiederholungen = updatedWv.maxWiederholungen;
            wv.wahlSlots = updatedWv.wahlSlots; // Assuming this is a collection and handled by JPA
        }
        return entity;
    }

    @Transactional
    public boolean deleteVortrag(Long id, Long veranstaltungId) {
        Vortrag entity = Vortrag.findById(id);
        if (entity == null || !entity.veranstaltung.id.equals(veranstaltungId)) {
            return false;
        }

        if (entity instanceof Pflichtvortrag pv) {
            // Effekte:
            // * alle Teilnehmer der Gruppe sind für Slot wieder verfügbar
            List<Teilnehmer> teilnehmerDerGruppe = getActiveTeilnehmerByGruppe(pv.pflichtgruppe, veranstaltungId);
            updateTeilnehmerAvailability(teilnehmerDerGruppe, pv.pflichtslot, true, pv.id); // pv.id als excludeId
            // * Raum ist in Slot wieder belegbar
            updateRaumAvailability(pv.pflichtraum, pv.pflichtslot, false, pv.id); // pv.id als excludeId
        }

        return Vortrag.deleteById(id);
    }

    public List<VortragStatDto> getStats(Long veranstaltungId) {
        List<Vortrag> all = Vortrag.find("veranstaltung.id", veranstaltungId).list();
        return all.stream().map(v -> new VortragStatDto(v.titel, 0, 0, 0, 0, 0)).collect(Collectors.toList());
    }

    public Response exportCsv(Long vid) {
        return Response.ok().build();
    }

    // #################################################################################################################
    // # Pflichtvortrag Helper Methods
    // #################################################################################################################

    /**
     * Holt alle aktiven Teilnehmer einer bestimmten Gruppe für eine Veranstaltung.
     *
     * @param gruppe          Die Gruppe der Teilnehmer.
     * @param veranstaltungId Die ID der Veranstaltung.
     * @return Eine Liste von Teilnehmern.
     */
    private List<Teilnehmer> getActiveTeilnehmerByGruppe(String gruppe, Long veranstaltungId) {
        return Teilnehmer.find("SELECT t FROM Teilnehmer t JOIN t.veranstaltungen v WHERE t.gruppe = ?1 AND v.id = ?2 AND t.isActive = true",
                        gruppe, veranstaltungId)
                .list();
    }

    /**
     * Prüft, ob alle übergebenen Teilnehmer im gegebenen Slot verfügbar sind (isAvailable = true).
     *
     * @param teilnehmer Die Liste der zu prüfenden Teilnehmer.
     * @param slot       Der EventSlot.
     * @return True, wenn alle verfügbar sind, sonst False.
     */
    private boolean checkTeilnehmerAvailability(List<Teilnehmer> teilnehmer, EventSlot slot) {
        if (teilnehmer.isEmpty()) {
            return true; // Keine Teilnehmer zu prüfen, also "verfügbar"
        }
        for (Teilnehmer tn : teilnehmer) {
            Optional<Verfuegbarkeit> verfuegbarkeit = Verfuegbarkeit.find("nutzer = ?1 and slot = ?2", tn, slot).firstResultOptional();
            if (verfuegbarkeit.isPresent() && !verfuegbarkeit.get().isAvailable) {
                LOG.info(String.format("Teilnehmer %s ist in Slot %s nicht verfügbar.", tn.email, slot.description));
                return false; // Mindestens ein Teilnehmer ist nicht verfügbar
            }
        }
        return true;
    }

    /**
     * Aktualisiert die Verfügbarkeit der Teilnehmer für einen Slot.
     * Wenn 'available' true ist, wird die Verfügbarkeit nur gesetzt, wenn keine andere PV sie belegt.
     *
     * @param teilnehmer              Die Liste der Teilnehmer.
     * @param slot                    Der EventSlot.
     * @param available               Der gewünschte Verfügbarkeitsstatus.
     * @param excludePflichtvortragId Die ID des aktuellen Pflichtvortrags, der bei der Prüfung anderer PVs ignoriert werden soll.
     */
    private void updateTeilnehmerAvailability(List<Teilnehmer> teilnehmer, EventSlot slot, boolean available, Long excludePflichtvortragId) {
        for (Teilnehmer tn : teilnehmer) {
            Verfuegbarkeit verfuegbarkeit = (Verfuegbarkeit) Verfuegbarkeit.find("nutzer = ?1 and slot = ?2", tn, slot)
                    .firstResultOptional()
                    .orElseGet(() -> {
                        Verfuegbarkeit newV = new Verfuegbarkeit();
                        newV.nutzer = tn;
                        newV.slot = slot;
                        return newV;
                    });

            if (available) {
                // Bedingte Freigabe: Nur freigeben, wenn keine andere PV diesen TN im Slot belegt
                List<Pflichtvortrag> otherPvs = getOtherPflichtvortraegeForTeilnehmerGroupAndSlot(tn.gruppe, slot, excludePflichtvortragId);
                if (otherPvs.isEmpty()) {
                    verfuegbarkeit.isAvailable = true;
                }
            } else {
                // Belegung: Immer auf false setzen
                verfuegbarkeit.isAvailable = false;
            }
            verfuegbarkeit.persist();
        }
    }

    /**
     * Prüft, ob der Raum im gegebenen Slot nicht belegt ist (isBelegt = false).
     *
     * @param raum Der Raum.
     * @param slot Der EventSlot.
     * @return True, wenn der Raum verfügbar ist, sonst False.
     */
    private boolean checkRaumAvailability(Raum raum, EventSlot slot) {
        Optional<RaumBelegbarkeit> raumBelegbarkeit = RaumBelegbarkeit.find("raum = ?1 and slot = ?2", raum, slot).firstResultOptional();
        return raumBelegbarkeit.isEmpty() || !raumBelegbarkeit.get().isBelegt;
    }

    /**
     * Aktualisiert die Belegbarkeit eines Raums für einen Slot.
     * Wenn 'belegt' false ist, wird die Belegbarkeit nur freigegeben, wenn keine andere PV ihn belegt.
     *
     * @param raum                    Der Raum.
     * @param slot                    Der EventSlot.
     * @param belegt                  Der gewünschte Belegungsstatus.
     * @param excludePflichtvortragId Die ID des aktuellen Pflichtvortrags, der bei der Prüfung anderer PVs ignoriert werden soll.
     */
    private void updateRaumAvailability(Raum raum, EventSlot slot, boolean belegt, Long excludePflichtvortragId) {
        RaumBelegbarkeit raumBelegbarkeit = (RaumBelegbarkeit) RaumBelegbarkeit.find("raum = ?1 and slot = ?2", raum, slot)
                .firstResultOptional()
                .orElseGet(() -> {
                    RaumBelegbarkeit newRb = new RaumBelegbarkeit();
                    newRb.raum = raum;
                    newRb.slot = slot;
                    return newRb;
                });

        if (!belegt) {
            // Bedingte Freigabe: Nur freigeben, wenn keine andere PV diesen Raum im Slot belegt
            List<Pflichtvortrag> otherPvs = getOtherPflichtvortraegeForRaumAndSlot(raum, slot, excludePflichtvortragId);
            if (otherPvs.isEmpty()) {
                raumBelegbarkeit.isBelegt = false;
            }
        } else {
            // Belegung: Immer auf true setzen
            raumBelegbarkeit.isBelegt = true;
        }
        raumBelegbarkeit.persist();
    }

    /**
     * Prüft, ob die Kapazität des Raums für die Anzahl der aktiven Teilnehmer der Gruppe ausreicht.
     *
     * @param raum            Der Raum.
     * @param gruppe          Die Gruppe der Teilnehmer.
     * @param veranstaltungId Die ID der Veranstaltung.
     * @return True, wenn die Kapazität ausreicht, sonst False.
     */
    private boolean checkRaumCapacity(Raum raum, String gruppe, Long veranstaltungId) {
        if (raum == null || raum.kapazitaet == null) {
            // Wenn keine Kapazität definiert ist, gehen wir davon aus, dass sie ausreicht.
            return true;
        }
        long activeTeilnehmerCount = getActiveTeilnehmerByGruppe(gruppe, veranstaltungId).size();
        return raum.kapazitaet >= activeTeilnehmerCount;
    }

    /**
     * Findet andere Pflichtvorträge, die dieselbe Gruppe und denselben Slot betreffen.
     * Wird für die bedingte Freigabe von Verfügbarkeiten benötigt.
     *
     * @param gruppe                  Die Gruppe der Teilnehmer.
     * @param slot                    Der EventSlot.
     * @param excludePflichtvortragId Die ID des Pflichtvortrags, der bei der Suche ignoriert werden soll (z.B. der gerade gelöschte/geänderte PV).
     * @return Eine Liste von Pflichtvorträgen.
     */
    private List<Pflichtvortrag> getOtherPflichtvortraegeForTeilnehmerGroupAndSlot(String gruppe, EventSlot slot, Long excludePflichtvortragId) {
        if (gruppe == null || slot == null) {
            return Collections.emptyList();
        }

        String query = "SELECT pv FROM Pflichtvortrag pv WHERE pv.pflichtgruppe = ?1 AND pv.pflichtslot = ?2";

        if (excludePflichtvortragId != null) {
            query += " AND pv.id != ?3";
            return Pflichtvortrag.find(query, gruppe, slot, excludePflichtvortragId).list();
        } else {
            return Pflichtvortrag.find(query, gruppe, slot).list();
        }
    }

    /**
     * Findet andere Pflichtvorträge, die denselben Raum und denselben Slot betreffen.
     * Wird für die bedingte Freigabe von Belegbarkeiten benötigt.
     *
     * @param raum                    Der Raum.
     * @param slot                    Der EventSlot.
     * @param excludePflichtvortragId Die ID des Pflichtvortrags, der bei der Suche ignoriert werden soll (z.B. der gerade gelöschte/geänderte PV).
     * @return Eine Liste von Pflichtvorträgen.
     */
    private List<Pflichtvortrag> getOtherPflichtvortraegeForRaumAndSlot(Raum raum, EventSlot slot, Long excludePflichtvortragId) {
        if (raum == null || slot == null) {
            return Collections.emptyList();
        }
        // Using JPQL for more robust querying with null excludeId handling
        String query = "SELECT pv FROM Pflichtvortrag pv WHERE pv.pflichtraum = ?1 AND pv.pflichtslot = ?2";
        if (excludePflichtvortragId != null) {
            query += " AND pv.id != ?3";
            return Pflichtvortrag.find(query, raum, slot, excludePflichtvortragId).list();
        } else {
            return Pflichtvortrag.find(query, raum, slot).list();
        }
    }
}
