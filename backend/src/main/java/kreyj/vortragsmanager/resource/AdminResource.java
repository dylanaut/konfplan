package kreyj.vortragsmanager.resource;

import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.vortragsmanager.dto.AdminPrioritaetUpdateRequestDto;
import kreyj.vortragsmanager.dto.RaumBelegbarkeitDto;
import kreyj.vortragsmanager.dto.UserDto;
import kreyj.vortragsmanager.dto.VerfuegbarkeitDto;
import kreyj.vortragsmanager.entity.*;
import kreyj.vortragsmanager.service.AdminService;
import kreyj.vortragsmanager.service.PrioritaetService;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.List;
import java.util.stream.Collectors;

@Path("/api/admin")
@RolesAllowed("ADMIN")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminResource {

    @Inject
    AdminService adminService;

    @Inject
    PrioritaetService prioritaetService;

    @GET
    @Path("/nutzer")
    public List<UserDto> getAllUsers() {
        return adminService.getAllUsers();
    }

    @POST
    @Path("/nutzer")
    public UserDto createUser(UserDto dto) {
        return adminService.createUser(dto, dto.veranstaltungIds);
    }

    @PUT
    @Path("/nutzer/{id}")
    public UserDto updateUser(@PathParam("id") Long id, UserDto dto) {
        return adminService.updateUser(id, dto, dto.veranstaltungIds);
    }

    @DELETE
    @Path("/nutzer/{id}")
    public void deleteUser(@PathParam("id") Long id) {
        adminService.deleteUser(id);
    }

    @POST
    @Path("/nutzer/{userId}/einladen/{eventId}")
    public Response inviteUser(@PathParam("userId") Long userId, @PathParam("eventId") Long eventId) {
        try {
            adminService.inviteUserToEvent(userId, eventId);
            return Response.ok("Nutzer erfolgreich eingeladen.").build();
        } catch (IllegalArgumentException e) {
            Log.error(e.getMessage(), e);
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("/admins/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response importAdmins(@RestForm("file") FileUpload file) {
        try {
            int count = adminService.importAdminsFromCsv(file.uploadedFile().toFile().toPath());
            return Response.ok("Import erfolgreich: " + count + " Admins angelegt.").build();
        } catch (Exception e) {
            Log.error(e.getMessage(), e);
            return Response.status(Response.Status.BAD_REQUEST).entity("Fehler: " + e.getMessage()).build();
        }
    }

    @GET
    @Path("/veranstaltung/{vid}/verfuegbarkeiten")
    public List<VerfuegbarkeitDto> getVerfuegbarkeiten(@PathParam("vid") Long vid) {
        return Verfuegbarkeit.find("select v from Verfuegbarkeit v join v.nutzer u join u.veranstaltungen va where va.id = ?1", vid).stream()
                .map(v -> {
                    Verfuegbarkeit vf = (Verfuegbarkeit) v;
                    return new VerfuegbarkeitDto(vf.nutzer.id, vf.slot.id, vf.isAvailable);
                })
                .toList();
    }

    @POST
    @Path("/veranstaltung/{vid}/verfuegbarkeiten")
    @Transactional
    public Response updateVerfuegbarkeit(@PathParam("vid") Long vid, VerfuegbarkeitDto dto) {
        Nutzer nutzer = Nutzer.findById(dto.userId);
        EventSlot slot = EventSlot.findById(dto.slotId);
        if (nutzer == null || slot == null) return Response.status(Response.Status.NOT_FOUND).build();

        // Validierung: Gehört der Slot zur angegebenen Veranstaltung?
        if (!slot.veranstaltung.id.equals(vid)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Slot gehört nicht zur angegebenen Veranstaltung.").build();
        }

        Verfuegbarkeit v = Verfuegbarkeit.find("nutzer = ?1 and slot = ?2", nutzer, slot).firstResult();
        if (v == null) {
            v = new Verfuegbarkeit();
            v.nutzer = nutzer;
            v.slot = slot;
        }
        v.isAvailable = dto.isAvailable;
        v.persist();
        return Response.ok().build();
    }

    @GET
    @Path("/veranstaltung/{vid}/raeume/verfuegbarkeiten")
    public List<RaumBelegbarkeitDto> getRaumVerfuegbarkeiten(@PathParam("vid") Long vid) {
        Veranstaltung event = Veranstaltung.findById(vid);
        if (event == null) throw new NotFoundException();

        List<EventSlot> slots = EventSlot.find("veranstaltung.id", vid).list();
        List<Raum> raeume = event.gebaeude.stream().flatMap(g -> g.raeume.stream()).toList();

        return raeume.stream().flatMap(r -> slots.stream().map(s -> {
            RaumBelegbarkeit rv = RaumBelegbarkeit.find("raum = ?1 and slot = ?2", r, s).firstResult();
            RaumBelegbarkeitDto dto = new RaumBelegbarkeitDto(r.id, s.id, rv != null && rv.isBelegt);

            // Cross-event check: Is this room busy in ANY other event at a time that overlaps with this slot?
            List<RaumBelegbarkeit> otherRvs = RaumBelegbarkeit.find("raum = ?1 and isBelegt = true and slot.veranstaltung.id != ?2", r, vid).list();
            for (RaumBelegbarkeit otherRv : otherRvs) {
                if (otherRv.slot.startTime.isBefore(s.endTime) && otherRv.slot.endTime.isAfter(s.startTime)) {
                    dto.isBlockedByOtherEvent = true;
                    dto.blockingEventName = otherRv.slot.veranstaltung.name;
                    break;
                }
            }
            return dto;
        })).toList();
    }

    @POST
    @Path("/veranstaltung/{vid}/raeume/verfuegbarkeiten")
    @Transactional
    public Response updateRaumVerfuegbarkeit(@PathParam("vid") Long vid, RaumBelegbarkeitDto dto) {
        Raum raum = Raum.findById(dto.raumId);
        EventSlot slot = EventSlot.findById(dto.slotId);
        if (raum == null || slot == null) return Response.status(Response.Status.NOT_FOUND).build();

        RaumBelegbarkeit rv = RaumBelegbarkeit.find("raum = ?1 and slot = ?2", raum, slot).firstResult();
        if (rv == null) {
            rv = new RaumBelegbarkeit();
            rv.raum = raum;
            rv.slot = slot;
        }
        rv.isBelegt = dto.isBelegt;
        rv.persist();
        return Response.ok().build();
    }

    @PUT
    @Path("/veranstaltung/{vid}/teilnehmer/{tid}/priorities")
    @Transactional
    public Response updateTeilnehmerPrioritaet(
            @PathParam("vid") Long vid,
            @PathParam("tid") Long tid,
            AdminPrioritaetUpdateRequestDto dto) {

        // Basic validation: Check if the participant and vortrag belong to the same event
        Teilnehmer teilnehmer = Teilnehmer.findById(tid);
        Vortrag vortrag = Vortrag.findById(dto.vortragId);

        if (teilnehmer == null || vortrag == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("Teilnehmer oder Vortrag nicht gefunden.").build();
        }

        if (!teilnehmer.veranstaltungen.stream().anyMatch(v -> v.id.equals(vid))) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Teilnehmer gehört nicht zu dieser Veranstaltung.").build();
        }

        if (!vortrag.veranstaltung.id.equals(vid)) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Vortrag gehört nicht zu dieser Veranstaltung.").build();
        }

        try {
            prioritaetService.updateSinglePrioritaet(tid, dto.vortragId, dto.prioWert);
            return Response.ok().build();
        } catch (WebApplicationException e) {
            return Response.status(e.getResponse().getStatus()).entity(e.getMessage()).build();
        } catch (Exception e) {
            Log.error("Fehler beim Aktualisieren der Priorität: " + e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Interner Serverfehler beim Aktualisieren der Priorität.").build();
        }
    }
}
