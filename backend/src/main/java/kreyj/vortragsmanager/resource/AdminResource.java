package kreyj.vortragsmanager.resource;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.vortragsmanager.dto.FileUploadDto;
import kreyj.vortragsmanager.dto.UserDto;
import kreyj.vortragsmanager.service.AdminService;

import java.util.List;

@Path("/api/admin")
@RolesAllowed("ADMIN")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminResource {

    @Inject
    AdminService adminService;

    // --- GLOBALE ADMIN FUNKTIONEN ---

    @GET
    @Path("/benutzer") // Globale Suche nach allen Usern (nicht veranstaltungsbezogen)
    public List<UserDto> getAllUsers() {
        return adminService.getAllUsers();
    }

    @POST
    @Transactional
    @Path("/benutzer") // Alle Arten von User anlegen, basierend auf 'role'
    public Response createUser(UserDto userDto) {
        var created = adminService.createUser(userDto, userDto.veranstaltungId);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @POST
    @Path("/admins/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response importAdmins(FileUploadDto data) {
        try {
            int count = adminService.importAdminsFromCsv(data.file.uploadedFile().toFile().toPath());
            return Response.ok("Import erfolgreich: " + count + " Administratoren angelegt.").build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Fehler: " + e.getMessage()).build();
        }
    }
}
