package kreyj.vortragsmanager.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.vortragsmanager.dto.ParticipantImportDto;
import kreyj.vortragsmanager.service.ParticipantService;

@Path("/admin/participants")
@RolesAllowed("ADMIN") // Nur Admins dürfen importieren
public class ParticipantResource {

    @Inject
    ParticipantService participantService;

    @POST
    @Path("/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadCsv(ParticipantImportDto data) {
        try {
            participantService.importFromCsv(data.file.uploadedFile().toFile().toPath());
            return Response.ok("Import erfolgreich").build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Fehler beim Import: " + e.getMessage()).build();
        }
    }
}