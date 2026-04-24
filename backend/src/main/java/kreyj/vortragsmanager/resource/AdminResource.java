package kreyj.vortragsmanager.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.vortragsmanager.dto.UserDto;
import kreyj.vortragsmanager.dto.VerfuegbarkeitDto;
import kreyj.vortragsmanager.entity.EventSlot;
import kreyj.vortragsmanager.entity.Nutzer;
import kreyj.vortragsmanager.entity.Verfuegbarkeit;
import kreyj.vortragsmanager.service.AdminService;
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
                .collect(Collectors.toList());
    }

    @POST
    @Path("/veranstaltung/{vid}/verfuegbarkeit")
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
}
