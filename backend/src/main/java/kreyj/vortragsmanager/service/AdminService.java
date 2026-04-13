package kreyj.vortragsmanager.service;

import com.opencsv.bean.CsvToBeanBuilder;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import kreyj.vortragsmanager.dto.*;
import kreyj.vortragsmanager.entity.*;

import java.io.FileReader;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
public class AdminService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public List<UserDto> getAllUsers(Long veranstaltungId) {
        List<User> globals = User.list("role = 'ADMIN'");
        List<User> localized = User.list("veranstaltung.id = ?1", veranstaltungId);

        return Stream.concat(globals.stream(), localized.stream())
                .distinct()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public List<UserDto> getAllUsers() {
        return User.<User>listAll().stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional
    public UserDto createUser(UserDto dto, Long veranstaltungId) {
        User user;
        if ("REFERENT".equals(dto.role)) user = new Referent();
        else if ("TEILNEHMER".equals(dto.role)) user = new Teilnehmer();
        else user = new Admin();

        user.email = dto.email;
        user.firstName = dto.firstName;
        user.lastName = dto.lastName;
        user.isActive = dto.isActive;

        if (dto.email != null) {
            user.passwordHash = BcryptUtil.bcryptHash("start123");
        }

        if (user instanceof Referent r) {
            r.biography = dto.biography;
            r.jobRole = dto.jobRole;
            r.organisation = dto.organisation;
            r.slogan = dto.slogan;
            r.veranstaltung = Veranstaltung.findById(veranstaltungId);
        } else if (user instanceof Teilnehmer t) {
            t.gruppe = dto.gruppe;
            t.veranstaltung = Veranstaltung.findById(veranstaltungId);
        }

        user.persist();

        UserDto userDto = mapToDto(user);
        userDto.role = dto.role;

        return userDto;
    }

    @Transactional
    public UserDto updateUser(Long id, UserDto dto, Long veranstaltungId) {
        User entity = User.findById(id);
        if (entity == null) return null;

        entity.firstName = dto.firstName;
        entity.lastName = dto.lastName;
        entity.email = dto.email;
        entity.isActive = dto.isActive;

        if (entity instanceof Referent r) {
            r.biography = dto.biography;
            r.jobRole = dto.jobRole;
            r.organisation = dto.organisation;
            r.slogan = dto.slogan;
        } else if (entity instanceof Teilnehmer t) {
            t.gruppe = dto.gruppe;
        }

        return mapToDto(entity);
    }

    private UserDto mapToDto(User u) {
        UserDto dto = new UserDto();
        dto.id = u.id;
        dto.email = u.email;
        dto.firstName = u.firstName;
        dto.lastName = u.lastName;
        dto.role = u.role;
        dto.isActive = u.isActive;
        dto.veranstaltungId = u.veranstaltung != null ? u.veranstaltung.id : null;

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

    // ... (Restliche Methoden für Vorträge, Slots etc. analog anpassen oder beibehalten) ...
    // Hinweis: Auch für Vorträge sollten wir DTOs nutzen, um Zyklen zu vermeiden!

    @Transactional
    public boolean deleteUser(Long id) {
        return User.deleteById(id);
    }

    @Transactional
    public void toggleUserStatus(Long id) {
        User entity = User.findById(id);
        if (entity != null) entity.isActive = !entity.isActive;
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
            List<AdminCsvDto> beans = new CsvToBeanBuilder<AdminCsvDto>(reader)
                    .withType(AdminCsvDto.class).withSeparator(';').withIgnoreLeadingWhiteSpace(true).build().parse();
            for (AdminCsvDto dto : beans) {
                if (User.findByEmail(dto.email) == null) {
                    Admin a = new Admin();
                    a.email = dto.email.trim().toLowerCase();
                    a.firstName = dto.firstName;
                    a.lastName = dto.lastName;
                    a.passwordHash = BcryptUtil.bcryptHash(UUID.randomUUID().toString());
                    a.persist();
                    count++;
                }
            }
        }
        return count;
    }

    @Transactional
    public int importVortraegeFromCsv(Path csvFilePath, Long veranstaltungId) throws Exception {
        int count = 0;
        Veranstaltung v_ent = Veranstaltung.findById(veranstaltungId);
        try (FileReader reader = new FileReader(csvFilePath.toFile())) {
            List<VortragCsvDto> beans = new CsvToBeanBuilder<VortragCsvDto>(reader).withType(VortragCsvDto.class).withSeparator(';').withIgnoreLeadingWhiteSpace(true).build().parse();
            for (VortragCsvDto dto : beans) {
                User referent = User.findByEmail(dto.referentEmail);
                if (referent instanceof Referent) {
                    Vortrag v = dto.istPflicht ? new Pflichtvortrag() : new Wahlvortrag();
                    v.titel = dto.titel;
                    v.inhalt = dto.inhalt;
                    v.zielgruppe = dto.zielgruppe;
                    v.referent = (Referent) referent;
                    v.veranstaltung = v_ent;
                    v.persist();
                    count++;
                }
            }
        }
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
        try (FileReader reader = new FileReader(csvFilePath.toFile())) {
            List<EventSlotCsvDto> beans = new CsvToBeanBuilder<EventSlotCsvDto>(reader).withType(EventSlotCsvDto.class).withSeparator(';').withIgnoreLeadingWhiteSpace(true).build().parse();
            for (EventSlotCsvDto dto : beans) {
                EventSlot s = new EventSlot();
                s.description = dto.description;
                s.startTime = LocalDateTime.parse(dto.startTime, DATE_FORMAT);
                s.endTime = LocalDateTime.parse(dto.endTime, DATE_FORMAT);
                s.veranstaltung = v;
                s.persist();
                count++;
            }
        }
        return count;
    }

    @Transactional
    public Vortrag updateVortrag(Long id, Vortrag updated, Long veranstaltungId) {
        Vortrag entity = Vortrag.findById(id);
        if (entity == null || !entity.veranstaltung.id.equals(veranstaltungId)) return null;
        entity.titel = updated.titel;
        entity.inhalt = updated.inhalt;
        entity.zielgruppe = updated.zielgruppe;
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
