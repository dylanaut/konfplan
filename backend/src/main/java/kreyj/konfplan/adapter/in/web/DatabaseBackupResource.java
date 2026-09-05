package kreyj.konfplan.adapter.in.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import kreyj.konfplan.domain.service.DatabaseBackupService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Path("/api/administrator/backup")
@RolesAllowed("ADMINISTRATOR")
@Tag(name = "DatabaseBackup", description = "Export eines PostgreSQL-Backups von konfplan- und Keycloak-Datenbank für Administratoren")
public class DatabaseBackupResource {

    private static final DateTimeFormatter FILENAME_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");

    private final DatabaseBackupService databaseBackupService;


    public DatabaseBackupResource(DatabaseBackupService databaseBackupService) {
        this.databaseBackupService = databaseBackupService;
    }


    @GET
    @Path("/export")
    @Produces("application/zip")
    @Operation(summary = "Datenbank-Backup exportieren",
        description = "Erstellt per pg_dump je einen Dump der konfplan- und der Keycloak-Datenbank und liefert beide gebündelt als ZIP. "
            + "Ein Restore ist bewusst nicht über die Oberfläche möglich, siehe db/restore_prod_db.sh.")
    public Response export() {
        String filename = "konfplan-backup_" + LocalDateTime.now().format(FILENAME_TIMESTAMP) + ".zip";
        StreamingOutput stream = databaseBackupService::writeBackupZip;
        return Response.ok(stream)
            .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
            .build();
    }
}
