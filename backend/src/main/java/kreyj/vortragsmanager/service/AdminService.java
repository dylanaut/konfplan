package kreyj.vortragsmanager.service;

import com.opencsv.bean.CsvToBeanBuilder;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import kreyj.vortragsmanager.dto.AdminCsvDto;
import kreyj.vortragsmanager.dto.EventSlotCsvDto;
import kreyj.vortragsmanager.dto.VortragCsvDto;
import kreyj.vortragsmanager.dto.VortragStatDto;
import kreyj.vortragsmanager.entity.*;

import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AdminService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public List<User> getAllUsers() {
        return User.listAll();
    }

    @Transactional
    public User createUser(User user, Long veranstaltungId) {
        if (user.passwordHash == null || user.passwordHash.isEmpty()) {
            user.passwordHash = BcryptUtil.bcryptHash("start123");
        }
        if (user instanceof Referent || user instanceof Teilnehmer) {
            user.veranstaltung = Veranstaltung.findById(veranstaltungId);
        }
        user.persist();
        return user;
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
                    String tempPassword = UUID.randomUUID().toString();
                    a.passwordHash = BcryptUtil.bcryptHash(tempPassword);
                    a.persist();
                    count++;
                }
            }
        }
        return count;
    }

    @Transactional
    public User updateUser(Long id, User updated, Long veranstaltungId) {
        User entity = User.findById(id);
        if (entity == null) return null;
        entity.firstName = updated.firstName;
        entity.lastName = updated.lastName;
        entity.email = updated.email;
        if (entity instanceof Teilnehmer && updated instanceof Teilnehmer) {
            ((Teilnehmer) entity).gruppe = ((Teilnehmer) updated).gruppe;
            entity.veranstaltung = Veranstaltung.findById(veranstaltungId);
        } else if (entity instanceof Referent && updated instanceof Referent) {
            ((Referent) entity).biography = ((Referent) updated).biography;
            ((Referent) entity).jobRole = ((Referent) updated).jobRole;
            ((Referent) entity).organisation = ((Referent) updated).organisation;
            ((Referent) entity).slogan = ((Referent) updated).slogan;
            entity.veranstaltung = Veranstaltung.findById(veranstaltungId);
        }
        entity.isActive = updated.isActive;
        return entity;
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
        return Vortrag.list("veranstaltung.id", veranstaltungId);
    }

    public List<User> getAllReferenten(Long veranstaltungId) {
        return User.list("role = 'REFERENT' and veranstaltung.id = ?1", veranstaltungId);
    }

    @Transactional
    public Vortrag createVortrag(Vortrag v, Long veranstaltungId) {
        v.veranstaltung = Veranstaltung.findById(veranstaltungId);
        v.persist();
        return v;
    }

    @Transactional
    public int importVortraegeFromCsv(Path csvFilePath, Long veranstaltungId) throws Exception {
        int count = 0;
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        if (veranstaltung == null) throw new IllegalArgumentException("Veranstaltung nicht gefunden.");

        try (FileReader reader = new FileReader(csvFilePath.toFile())) {
            List<VortragCsvDto> beans = new CsvToBeanBuilder<VortragCsvDto>(reader)
                    .withType(VortragCsvDto.class).withSeparator(';').withIgnoreLeadingWhiteSpace(true).build().parse();
            for (VortragCsvDto dto : beans) {
                User referent = User.findByEmail(dto.referentEmail);
                if (referent instanceof Referent && referent.veranstaltung.id.equals(veranstaltungId)) {
                    Vortrag v;
                    if (dto.istPflicht) {
                        Pflichtvortrag pv = new Pflichtvortrag();
                        if (dto.pflichtraumName != null) {
                            pv.pflichtraum = Raum.find("name", dto.pflichtraumName).firstResult();
                        }
                        if (dto.pflichtslotBeschreibung != null) {
                            pv.pflichtslot = EventSlot.find("description", dto.pflichtslotBeschreibung).firstResult();
                        }
                        v = pv;
                    } else {
                        Wahlvortrag wv = new Wahlvortrag();
                        wv.wiederholbar = dto.wiederholbar;
                        wv.maxWiederholungen = dto.maxWiederholungen;
                        v = wv;
                    }
                    v.titel = dto.titel;
                    v.inhalt = dto.inhalt;
                    v.zielgruppe = dto.zielgruppe;
                    v.referent = (Referent) referent;
                    v.veranstaltung = veranstaltung;
                    v.persist();
                    count++;
                }
            }
        }
        return count;
    }

    public List<EventSlot> getAllEventSlots(Long veranstaltungId) {
        return EventSlot.list("veranstaltung.id", veranstaltungId);
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
        if (entity == null || !entity.veranstaltung.id.equals(veranstaltungId)) return null;
        entity.description = updated.description;
        entity.startTime = updated.startTime;
        entity.endTime = updated.endTime;
        return entity;
    }

    @Transactional
    public boolean deleteEventSlot(Long id, Long veranstaltungId) {
        return EventSlot.delete("id = ?1 and veranstaltung.id = ?2", id, veranstaltungId) > 0;
    }

    @Transactional
    public int importSlotsFromCsv(Path csvFilePath, Long veranstaltungId) throws Exception {
        int count = 0;
        Veranstaltung veranstaltung = Veranstaltung.findById(veranstaltungId);
        if (veranstaltung == null) throw new IllegalArgumentException("Veranstaltung nicht gefunden.");

        try (FileReader reader = new FileReader(csvFilePath.toFile())) {
            List<EventSlotCsvDto> beans = new CsvToBeanBuilder<EventSlotCsvDto>(reader)
                    .withType(EventSlotCsvDto.class).withSeparator(';').withIgnoreLeadingWhiteSpace(true).build().parse();
            for (EventSlotCsvDto dto : beans) {
                EventSlot s = new EventSlot();
                s.description = dto.description;
                s.startTime = LocalDateTime.parse(dto.startTime, DATE_FORMAT);
                s.endTime = LocalDateTime.parse(dto.endTime, DATE_FORMAT);
                s.veranstaltung = veranstaltung;
                s.persist();
                count++;
            }
        }
        return count;
    }

    @Transactional
    public Vortrag updateVortrag(Long id, Vortrag updated, Long veranstaltungId) {
        Vortrag entity = Vortrag.findById(id);
        if (entity == null || updated == null || !entity.veranstaltung.id.equals(veranstaltungId)) return null;
        
        entity.titel = updated.titel;
        entity.inhalt = updated.inhalt;
        entity.zielgruppe = updated.zielgruppe;
        entity.referent = updated.referent;

        if (entity instanceof Pflichtvortrag && updated instanceof Pflichtvortrag) {
            Pflichtvortrag pEntity = (Pflichtvortrag) entity;
            Pflichtvortrag pUpdated = (Pflichtvortrag) updated;
            pEntity.pflichtraum = pUpdated.pflichtraum;
            pEntity.pflichtslot = pUpdated.pflichtslot;
        } else if (entity instanceof Wahlvortrag && updated instanceof Wahlvortrag) {
            Wahlvortrag wEntity = (Wahlvortrag) entity;
            Wahlvortrag wUpdated = (Wahlvortrag) updated;
            wEntity.wiederholbar = wUpdated.wiederholbar;
            wEntity.maxWiederholungen = wUpdated.maxWiederholungen;
            wEntity.wahlslots.clear();
            wEntity.wahlslots.addAll(wUpdated.wahlslots);
        }

        return entity;
    }

    @Transactional
    public boolean deleteVortrag(Long id, Long veranstaltungId) {
        return Vortrag.delete("id = ?1 and veranstaltung.id = ?2", id, veranstaltungId) > 0;
    }

    @Transactional
    public boolean forceUpdatePrioritaet(Long teilnehmerId, Prioritaet newPrio) {
        if (newPrio == null || newPrio.vortrag == null || newPrio.vortrag.id == null) return false;
        Prioritaet entity = Prioritaet.find("teilnehmer.id = ?1 and vortrag.id = ?2", teilnehmerId, newPrio.vortrag.id).firstResult();
        if (entity == null) return false;
        entity.prioWert = newPrio.prioWert;
        entity.lastUpdated = newPrio.lastUpdated;
        return true;
    }

    public List<VortragStatDto> getStats(Long veranstaltungId) {
        List<Vortrag> allVortraege = Vortrag.list("veranstaltung.id", veranstaltungId);
        return allVortraege.stream().map(v -> {
            long p1 = Prioritaet.count("vortrag = ?1 and prioWert = 1", v);
            long p2 = Prioritaet.count("vortrag = ?1 and prioWert = 2", v);
            long p3 = Prioritaet.count("vortrag = ?1 and prioWert = 3", v);
            long top3 = Prioritaet.count("vortrag = ?1 and prioWert <= 3", v);
            long total = Prioritaet.count("vortrag = ?1", v);
            return new VortragStatDto(v.titel, p1, p2, p3, top3, total);
        }).toList();
    }

    public Response exportCsv(Long veranstaltungId) {
        List<Prioritaet> allPrioritaeten = Prioritaet.list("vortrag.veranstaltung.id", veranstaltungId);
        StreamingOutput stream = output -> {
            try (Writer writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
                writer.write("Teilnehmer_Email;Nachname;Vorname;Gruppe;Vortrag_Titel;Prioritaet;Zeitstempel\n");
                for (Prioritaet p : allPrioritaeten) {
                    String email = p.teilnehmer != null ? p.teilnehmer.email : "";
                    String lastName = p.teilnehmer != null ? p.teilnehmer.lastName : "";
                    String firstName = p.teilnehmer != null ? p.teilnehmer.firstName : "";
                    String gruppe = p.teilnehmer != null ? p.teilnehmer.gruppe : "";
                    String title = p.vortrag != null ? p.vortrag.titel.replace(";", ",") : "";
                    String ts = p.lastUpdated != null ? p.lastUpdated.toString() : "";
                    writer.write(email + ";" + lastName + ";" + firstName + ";" + gruppe + ";" + title + ";" + p.prioWert + ";" + ts + "\n");
                }
                writer.flush();
            }
        };
        return Response.ok(stream).header("Content-Disposition", "attachment; filename=prioritaeten.csv").build();
    }
}
