package kreyj.konfplan.adapter.in.web;

import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.konfplan.adapter.in.web.dto.FileUploadDto;
import kreyj.konfplan.adapter.in.web.dto.VeranstaltungImportDatasetDto;
import kreyj.konfplan.application.port.in.VeranstaltungImportServiceInterface;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/administrator/veranstaltung-import")
@RolesAllowed("ADMINISTRATOR")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "VeranstaltungImport", description = "Bündelimport eines vollständigen CSV-Satzes zu einer neuen Veranstaltung aus einem Server-Verzeichnis")
public class VeranstaltungImportResource {

    private final VeranstaltungImportServiceInterface veranstaltungImportService;


    public VeranstaltungImportResource(VeranstaltungImportServiceInterface veranstaltungImportService) {
        this.veranstaltungImportService = veranstaltungImportService;
    }


    @GET
    @Path("/datasets")
    @Operation(summary = "Verfügbare Veranstaltungsverzeichnisse auflisten",
        description = "Listet alle Unterverzeichnisse des konfigurierten Basis-Verzeichnisses und markiert, ob sie alle Pflicht-CSV-Dateien enthalten.")
    public List<VeranstaltungImportDatasetDto> listDatasets() {
        return veranstaltungImportService.listDatasets();
    }


    @POST
    @Path("/datasets/{name}/import")
    @Operation(summary = "CSV-Satz aus Verzeichnis importieren",
        description = "Importiert den kompletten CSV-Satz eines Verzeichnisses und legt dabei eine neue Veranstaltung an. Schlägt eine Datei fehl, wird der gesamte Import zurückgerollt.")
    public Response importDataset(@PathParam("name") String name) {
        try {
            return Response.ok(veranstaltungImportService.importDataset(name)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (Exception e) {
            Log.error(e.getMessage(), e);
            return Response.status(Response.Status.BAD_REQUEST).entity("Fehler: " + e.getMessage()).build();
        }
    }


    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "CSV-Satz aus ZIP importieren",
        description = "Extrahiert ein hochgeladenes ZIP und importiert den enthaltenen CSV-Satz genauso wie beim Verzeichnis-Import.")
    public Response importZip(FileUploadDto data) {
        try {
            return Response.ok(veranstaltungImportService.importFromZip(data.file.uploadedFile())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (Exception e) {
            Log.error(e.getMessage(), e);
            return Response.status(Response.Status.BAD_REQUEST).entity("Fehler: " + e.getMessage()).build();
        }
    }
}
