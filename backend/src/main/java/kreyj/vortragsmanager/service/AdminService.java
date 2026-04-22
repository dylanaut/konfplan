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
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.commons.collections4.SetUtils.difference;

@ApplicationScoped
public class AdminService {
    private static final Logger LOG = Logger.getLogger(AdminService.class);

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Inject
    MailService mailService;

    public List<UserDto> getAllUsers(Long veranstaltungId) {
        List<User> admins = User.list("role = 'ADMIN'");
        List<User> vUsers = User.find("SELECT u FROM User u JOIN u.veranstaltungen v WHERE v.id = ?1", veranstaltungId).list();

        return Stream.concat(admins.stream(), vUsers.stream())
                .distinct()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public List<UserDto> getAllUsers() {
        return User.<User>listAll().stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional
    public UserDto createUser(UserDto dto, List<Long> veranstaltungsIds) {
        User user;
        if ("REFERENT".equals(dto.role)) {
            user = new Referent();
        } else if ("TEILNEHMER".equals(dto.role)) {
            user = new Teilnehmer();
        } else {
            user = new Admin();
        }

        user.email = dto.email;
        user.firstName = dto.firstName;
        user.lastName = dto.lastName;
        user.isActive = dto.isActive;

        if (dto.email != null) {
            user.passwordHash = BcryptUtil.bcryptHash("start123");
        }

        if (null != veranstaltungsIds) {
            for (Long veranstaltungId : veranstaltungsIds) {
                Veranstaltung v = Veranstaltung.findById(veranstaltungId);
                if (null == v) {
                    LOG.error("Unbekannte Veranstaltung zu id: " + veranstaltungId);
                } else {
                    user.veranstaltungen.add(v);
                }
            }
        }

        if (user instanceof Referent r) {
            r.biography = dto.biography;
            r.jobRole = dto.jobRole;
            r.organisation = dto.organisation;
            r.slogan = dto.slogan;
        } else if (user instanceof Teilnehmer t) {
            t.gruppe = dto.gruppe;
        }

        user.persist();

        return mapToDto(user);
    }

    @Transactional
    public UserDto updateUser(Long id, UserDto dto, List<Long> vUpdateIds) {
        User user = User.findById(id);
        if (user == null) {
            return null;
        }

        user.firstName = dto.firstName;
        user.lastName = dto.lastName;
        user.email = dto.email;
        user.isActive = dto.isActive;

        if (null != vUpdateIds) {
            Set<Long> oldVIds = user.veranstaltungen.stream().map(v -> v.id).collect(Collectors.toSet());
            Set<Long> vNewIdSet = new HashSet<>(vUpdateIds);

            Set<Long> toRemoves = difference(oldVIds, vNewIdSet).toSet();

            // alte ID nicht in updateIds enthalten -> entfernen
            for (Long toRemove : toRemoves) {
                Veranstaltung v = Veranstaltung.findById(toRemove);
                if (null != v) {
                    user.removeVeranstaltung(v);
                }
            }


            Set<Long> toAdds = difference(vNewIdSet, oldVIds).toSet();
            for (Long toAdd : toAdds) {
                Veranstaltung v = Veranstaltung.findById(toAdd);
                if (null == v) {
                    LOG.error("Unbekannte Veranstaltung zu id: " + toAdd);
                } else {
                    user.addVeranstaltung(v);
                }
            }
        }

        if (user instanceof Referent r) {
            r.biography = dto.biography;
            r.jobRole = dto.jobRole;
            r.organisation = dto.organisation;
            r.slogan = dto.slogan;
        } else if (user instanceof Teilnehmer t) {
            t.gruppe = dto.gruppe;
        }

        return mapToDto(user);
    }

    @Transactional
    public void inviteUserToEvent(Long userId, Long eventId) {
        User user = User.findById(userId);
        Veranstaltung event = Veranstaltung.findById(eventId);

        if (user == null || event == null) {
            throw new IllegalArgumentException("Benutzer oder Veranstaltung nicht gefunden.");
        }

        // Validierung: Veranstaltung darf nicht in der Vergangenheit liegen (Enddatum prüfen)
        LocalDateTime now = LocalDateTime.now();
        if (event.endetAm != null && event.endetAm.isBefore(now)) {
            throw new IllegalArgumentException("Die Veranstaltung '" + event.name + "' ist bereits beendet.");
        }

        if (!user.veranstaltungen.contains(event)) {
            user.addVeranstaltung(event);
            mailService.sendEventInvitation(user, event);
            LOG.info("Benutzer " + user.email + " zu Veranstaltung " + event.name + " eingeladen.");
        } else {
            LOG.info("Benutzer " + user.email + " ist bereits für Veranstaltung " + event.name + " registriert.");
        }
    }

    private UserDto mapToDto(User u) {
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
        return User.deleteById(id);
    }

    @Transactional
    public void toggleUserStatus(Long id) {
        User entity = User.findById(id);
        if (entity != null) {
            entity.isActive = !entity.isActive;
        }
    }

    public List<Vortrag> getAllVortraege(Long veranstaltungId) {
        return Vortrag.find("veranstaltung.id", veranstaltungId).list();
    }

    public List<User> getAllReferenten(Long veranstaltungId) {
        return User.find("role = 'REFERENT' and veranstaltung.id = ?1", veranstaltungId).list();
    }

    @Transactional
    public Vortrag createVortrag(Vortrag v, Long veranstaltungId) {
        v.veranstaltung = Veranstaltung.findById(veranstaltungId);
        v.persist();
        return v;
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
                User byEmail = User.findByEmail(email);
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
                    .withThrowExceptions(false)
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

                User referent = User.findByEmail(dto.referentEmail);
                if (referent instanceof Referent) {
                    Vortrag v = dto.istPflicht ? new Pflichtvortrag() : new Wahlvortrag();
                    v.titel = dto.titel;
                    v.inhalt = dto.inhalt;
                    v.referent = (Referent) referent;
                    v.veranstaltung = veranstaltung;

                    if (v instanceof Pflichtvortrag pflichtvortrag) {
                        pflichtvortrag.pflichtgruppe = dto.pflichtGruppe;
                        pflichtvortrag.pflichtslot = slotsByName.get(dto.pflichtSlot);
                        if (pflichtvortrag.pflichtslot == null && dto.pflichtSlot != null && !dto.pflichtSlot.isBlank()) {
                            LOG.warn("Vortrag '" + v.titel + "': Slot '" + dto.pflichtSlot + "' nicht gefunden.");
                        }

                        Map<Gebaeude, Raum> gebaeudeRaumMap = raeumeByName.get(dto.pflichtRaum);
                        if (null == gebaeudeRaumMap) {
                            if (dto.pflichtRaum != null && !dto.pflichtRaum.isBlank()) {
                                LOG.warn("Vortrag '" + v.titel + "': Unbekannter Raum '" + dto.pflichtRaum + "'");
                            }
                        } else {
                            Set<Gebaeude> gebaeudeSet = veranstaltung.gebaeude.stream()
                                    .filter(gebaeudeRaumMap::containsKey)
                                    .collect(Collectors.toSet());

                            if (gebaeudeSet.isEmpty()) {
                                LOG.warn("Vortrag '" + v.titel + "': Raum '" + dto.pflichtRaum + "' nicht gefunden in Veranstaltungsgebäuden.");
                            } else if (gebaeudeSet.size() == 1) {
                                pflichtvortrag.pflichtraum = gebaeudeRaumMap.get(gebaeudeSet.iterator().next());
                            } else {
                                LOG.warn("Vortrag '" + v.titel + "': Raum '" + dto.pflichtRaum + "' nicht eindeutig in Veranstaltungsgebäuden.");
                            }
                        }
                    } else if (v instanceof Wahlvortrag wahlvortrag) {
                        wahlvortrag.wiederholbar = dto.wiederholbar;
                        wahlvortrag.maxWiederholungen = dto.maxWiederholungen;
                    }

                    v.persist();
                    count++;
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
        slot.veranstaltung = Veranstaltung.findById(veranstaltungId);
        slot.persist();
        return slot;
    }

    @Transactional
    public EventSlot updateEventSlot(Long id, EventSlot updated, Long veranstaltungId) {
        EventSlot entity = EventSlot.findById(id);
        if (entity != null && entity.veranstaltung.id.equals(veranstaltungId)) {
            entity.description = updated.description;
            entity.startTime = updated.startTime;
            entity.endTime = updated.endTime;
        }
        return entity;
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
        entity.titel = updated.titel;
        entity.inhalt = updated.inhalt;
        if (entity instanceof Pflichtvortrag pv && updated instanceof Pflichtvortrag updatedPv) {
            pv.pflichtgruppe = updatedPv.pflichtgruppe;
            pv.pflichtslot = updatedPv.pflichtslot;
            pv.pflichtraum = updatedPv.pflichtraum;
        } else if (entity instanceof Wahlvortrag wv && updated instanceof Wahlvortrag updatedWv) {
            wv.wiederholbar = updatedWv.wiederholbar;
            wv.maxWiederholungen = updatedWv.maxWiederholungen;
            wv.wahlSlots = updatedWv.wahlSlots;
        }
        return entity;
    }

    @Transactional
    public boolean deleteVortrag(Long id, Long veranstaltungId) {
        return Vortrag.delete("id = ?1 and veranstaltung.id = ?2", id, veranstaltungId) > 0;
    }

    public List<VortragStatDto> getStats(Long veranstaltungId) {
        List<Vortrag> all = Vortrag.find("veranstaltung.id", veranstaltungId).list();
        return all.stream().map(v -> new VortragStatDto(v.titel, 0, 0, 0, 0, 0)).collect(Collectors.toList());
    }

    public Response exportCsv(Long vid) {
        return Response.ok().build();
    }
}
