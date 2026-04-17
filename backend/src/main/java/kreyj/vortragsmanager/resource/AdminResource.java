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
import kreyj.vortragsmanager.entity.User;
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
    @Path("/benutzer")
    public List<UserDto> getAllUsers() {
        return adminService.getAllUsers();
    }

    @POST
    @Path("/benutzer")
    public UserDto createUser(UserDto dto) {
        return adminService.createUser(dto, dto.veranstaltungId);
    }

    @PUT
    @Path("/benutzer/{id}")
    public UserDto updateUser(@PathParam("id") Long id, UserDto dto) {
        return adminService.updateUser(id, dto, dto.veranstaltungId);
    }

    @DELETE
    @Path("/benutzer/{id}")
    public void deleteUser(@PathParam("id") Long id) {
        adminService.deleteUser(id);
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
        return Verfuegbarkeit.find("user.veranstaltung.id = ?1", vid).stream()
                .map(v -> {
                    Verfuegbarkeit vf = (Verfuegbarkeit) v;
                    return new VerfuegbarkeitDto(vf.user.id, vf.slot.id, vf.isAvailable);
                })
                .collect(Collectors.toList());
    }

    @POST
    @Path("/verfuegbarkeit")
    @Transactional
    public Response updateVerfuegbarkeit(VerfuegbarkeitDto dto) {
        User user = User.findById(dto.userId);
        EventSlot slot = EventSlot.findById(dto.slotId);
        if (user == null || slot == null) return Response.status(Response.Status.NOT_FOUND).build();

        Verfuegbarkeit v = Verfuegbarkeit.find("user = ?1 and slot = ?2", user, slot).firstResult();
        if (v == null) {
            v = new Verfuegbarkeit();
            v.user = user;
            v.slot = slot;
        }
        v.isAvailable = dto.isAvailable;
        v.persist();
        return Response.ok().build();
    }
}
