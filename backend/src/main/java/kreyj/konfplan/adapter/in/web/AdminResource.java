package kreyj.konfplan.adapter.in.web;

import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.konfplan.adapter.in.web.dto.VortragPrioDto;
import kreyj.konfplan.adapter.in.web.dto.ImportResultDto;
import kreyj.konfplan.adapter.in.web.dto.NutzerDto;
import kreyj.konfplan.adapter.in.web.dto.NutzerVerfuegbarkeitDto;
import kreyj.konfplan.adapter.in.web.dto.RaumVerfuegbarkeitDto;
import kreyj.konfplan.application.port.in.AdminServiceInterface;
import kreyj.konfplan.domain.service.MailService;
import kreyj.konfplan.domain.service.PrioritaetService;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.NutzerVerfuegbarkeit;
import kreyj.konfplan.persistence.RaumVerfuegbarkeit;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Vortrag;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.List;

import static kreyj.konfplan.persistence.NutzerVerfuegbarkeitId.nvIdL;
import static kreyj.konfplan.persistence.RaumVerfuegbarkeitId.rvIdL;

@Path("/api/admin")
@RolesAllowed("ADMIN")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Admin", description = "Administrative Endpunkte für die Nutzer- und Veranstaltungsverwaltung")
public class AdminResource {

    private final AdminServiceInterface adminService;

    private final PrioritaetService prioritaetService;

    private final MailService mailService;


    public AdminResource(AdminServiceInterface adminService, PrioritaetService prioritaetService, MailService mailService) {
        this.adminService = adminService;
        this.prioritaetService = prioritaetService;
        this.mailService = mailService;
    }


    @GET
    @Path("/nutzer")
    @Operation(summary = "Alle Nutzer abrufen", description = "Gibt eine Liste aller Nutzer (Admins, Referenten, Teilnehmer) zurück.")
    public List<NutzerDto> getAllUsers() {
        return adminService.getAllUsers();
    }


    @POST
    @Path("/nutzer")
    @Operation(summary = "Neuen Nutzer erstellen", description = "Erstellt einen neuen Nutzer und sendet eine Bestätigungs-E-Mail.")
    public NutzerDto createUser(@RequestBody(description = "Die Daten des neuen Nutzers") NutzerDto dto) {
        NutzerDto createdNutzerDto = adminService.createUser(dto, dto.veranstaltungIds);
        // E-Mail nach erfolgreicher Erstellung senden
        Nutzer createdNutzer = Nutzer.findById(createdNutzerDto.id);
        if (null != createdNutzer) {
            mailService.sendRegistrationConfirmation(createdNutzer);
        }
        return createdNutzerDto;
    }


    @GET
    @Path("/nutzer/{id}")
    @Transactional
    @Operation(summary = "Einen Nutzer abrufen", description = "Ruft einen einzelnen Nutzer anhand seiner ID ab.")
    public Response getUser(@PathParam("id") Long id) {
        Nutzer nutzer = adminService.findNutzer(id);

        if (null == nutzer) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok().entity(NutzerDto.from(nutzer)).build();
    }


    @PUT
    @Path("/nutzer/{id}")
    @Operation(summary = "Nutzer aktualisieren", description = "Aktualisiert die Daten eines Nutzers.")
    public Response updateUser(@PathParam("id") Long id, @RequestBody(description = "Die aktualisierten Nutzerdaten") NutzerDto dto) {
        try {
            NutzerDto updateUser = adminService.updateUser(id, dto, dto.veranstaltungIds);
            return Response.ok().entity(updateUser).build();
        } catch (Exception e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        }
    }


    @DELETE
    @Path("/nutzer/{id}")
    @Operation(summary = "Nutzer löschen", description = "Löscht einen Nutzer und sendet eine Benachrichtigungs-E-Mail.")
    public void deleteUser(@PathParam("id") Long id) {
        Nutzer nutzerToDelete = Nutzer.findById(id); // Nutzer vor dem Löschen abrufen

        if (null != nutzerToDelete) {
            adminService.deleteUser(id);
        }
    }


    @POST
    @Path("/nutzer/{userId}/einladen/{eventId}")
    @Operation(summary = "Nutzer zu Veranstaltung einladen", description = "Fügt einen Nutzer zu einer Veranstaltung hinzu.")
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
    @Operation(summary = "Admins importieren", description = "Importiert eine Liste von Admins aus einer CSV-Datei.")
    public Response importAdmins(@RestForm("file") FileUpload file) {
        try {
            int count = adminService.importAdminsFromCsv(file.uploadedFile().toFile().toPath());
            return Response.ok("Import erfolgreich: " + count + " Admins angelegt.").build();
        } catch (Exception e) {
            Log.error(e.getMessage(), e);
            return Response.status(Response.Status.BAD_REQUEST).entity("Fehler: " + e.getMessage()).build();
        }
    }


    @POST
    @Path("/veranstaltungen/{vid}/prioritaeten/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Prioritäten importieren", description = "Importiert Teilnehmer-Prioritäten für eine Veranstaltung aus einer CSV-Datei.")
    public Response importPrioritaeten(@PathParam("vid") Long vid, @RestForm("file") FileUpload file) {
        try {
            int count = adminService.importPrioritaetenFromCsv(file.uploadedFile().toFile().toPath(), vid);
            return Response.ok("Import erfolgreich: " + count + " Prioritäten importiert/aktualisiert.").build();
        } catch (Exception e) {
            Log.error(e.getMessage(), e);
            return Response.status(Response.Status.BAD_REQUEST).entity("Fehler: " + e.getMessage()).build();
        }
    }


    @POST
    @Path("/veranstaltungen/{vid}/teilnehmer/verfuegbarkeiten/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Teilnehmer-Verfügbarkeiten importieren", description = "Importiert Teilnehmer-Verfügbarkeiten für eine Veranstaltung aus einer CSV-Datei.")
    public Response importTeilnehmerVerfuegbarkeiten(@PathParam("vid") Long vid, @RestForm("file") FileUpload file) {
        try {
            ImportResultDto result = adminService.importNutzerVerfuegbarkeitenFromCsv(file.uploadedFile().toFile().toPath(), Teilnehmer.class, vid);
            return Response.ok(result).build();
        } catch (Exception e) {
            Log.error(e.getMessage(), e);
            return Response.status(Response.Status.BAD_REQUEST).entity(new ImportResultDto(0, List.of(e.getMessage()))).build();
        }
    }


    @POST
    @Path("/veranstaltungen/{vid}/referenten/verfuegbarkeiten/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Referenten-Verfügbarkeiten importieren", description = "Importiert Referenten-Verfügbarkeiten für eine Veranstaltung aus einer CSV-Datei.")
    public Response importReferentenVerfuegbarkeiten(@PathParam("vid") Long vid, @RestForm("file") FileUpload file) {
        try {
            ImportResultDto result = adminService.importNutzerVerfuegbarkeitenFromCsv(file.uploadedFile().toFile().toPath(), Referent.class, vid);
            return Response.ok(result).build();
        } catch (Exception e) {
            Log.error(e.getMessage(), e);
            return Response.status(Response.Status.BAD_REQUEST).entity(new ImportResultDto(0, List.of(e.getMessage()))).build();
        }
    }


    @POST
    @Path("/veranstaltungen/{vid}/raeume/verfuegbarkeiten/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Raum-Verfügbarkeiten importieren", description = "Importiert Raum-Verfügbarkeiten für eine Veranstaltung aus einer CSV-Datei.")
    public Response importRaumVerfuegbarkeiten(@PathParam("vid") Long vid, @RestForm("file") FileUpload file) {
        try {
            ImportResultDto result = adminService.importRaumVerfuegbarkeitenFromCsv(file.uploadedFile().toFile().toPath(), vid);
            return Response.ok(result).build();
        } catch (Exception e) {
            Log.error(e.getMessage(), e);
            return Response.status(Response.Status.BAD_REQUEST).entity(new ImportResultDto(0, List.of(e.getMessage()))).build();
        }
    }


    @GET
    @Path("/veranstaltungen/{vid}/verfuegbarkeiten")
    @Operation(summary = "Verfügbarkeiten abrufen", description = "Ruft die Verfügbarkeiten aller Nutzer für eine Veranstaltung ab.")
    public List<NutzerVerfuegbarkeitDto> getVerfuegbarkeiten(@PathParam("vid") Long vid) {
        List<NutzerVerfuegbarkeit> nvs = NutzerVerfuegbarkeit.find("veranstaltungId", vid).list();

        return nvs.stream()
            .map(NutzerVerfuegbarkeitDto::new)
            .toList();
    }


    @POST
    @Path("/veranstaltungen/{vid}/verfuegbarkeiten")
    @Transactional
    @Operation(summary = "Verfügbarkeit aktualisieren", description = "Aktualisiert die Verfügbarkeit eines Nutzers für einen bestimmten Slot.")
    public Response updateVerfuegbarkeit(@PathParam("vid") Long vid,
                                         @RequestBody(description = "Verfügbarkeitsdaten für einen Nutzer") NutzerVerfuegbarkeitDto dto) {
        NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvIdL(dto.nutzerId, vid));

        if (null == nv) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        nv.setVerfuegbareSlotIds(dto.verfuegbareSlotIds);
        return Response.ok().build();
    }


    @GET
    @Path("/veranstaltungen/{vid}/raeume/verfuegbarkeiten")
    @Operation(summary = "Raum-Verfügbarkeiten abrufen", description = "Ruft die Verfügbarkeiten (Belegungen) aller Räume für eine Veranstaltung ab.")
    public List<RaumVerfuegbarkeitDto> getRaumVerfuegbarkeiten(@PathParam("vid") Long vid) {
        return adminService.getRaumVerfuegbarkeiten(vid);
    }


    @POST
    @Path("/veranstaltungen/{vid}/raeume/verfuegbarkeiten")
    @Transactional
    @Operation(summary = "Raum-Verfügbarkeit aktualisieren", description = "Aktualisiert die Belegung eines Raumes für einen bestimmten Slot.")
    public Response updateRaumVerfuegbarkeit(@PathParam("vid") Long vid, @RequestBody(description = "Die Raum-Verfügbarkeitsdaten") RaumVerfuegbarkeitDto dto) {
        RaumVerfuegbarkeit verfuegbarkeit = RaumVerfuegbarkeit.findById(rvIdL(dto.raumId, vid));
        if (null == verfuegbarkeit) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        verfuegbarkeit.setVerfuegbareSlotIds(dto.verfuegbareSlotIds);
        verfuegbarkeit.persist();
        return Response.ok().build();
    }


    @PUT
    @Path("/veranstaltungen/{vid}/teilnehmer/{tid}/priorities")
    @Transactional
    @Operation(summary = "Teilnehmer-Prioritäten aktualisieren", description = "Aktualisiert eine oder mehrere Prioritäten eines Teilnehmers für eine Veranstaltung.")
    public Response updateTeilnehmerPrioritaet(
        @PathParam("vid") Long vid,
        @PathParam("tid") Long tid,
        @RequestBody(description = "Eine Liste von Prioritäts-Updates") List<VortragPrioDto> dtoList) { // Changed to List

        Teilnehmer teilnehmer = Teilnehmer.findById(tid);
        if (null == teilnehmer) {
            return Response.status(Response.Status.NOT_FOUND).entity("Teilnehmer nicht gefunden.").build();
        }

        if (teilnehmer.getVeranstaltungen().stream().noneMatch(v -> v.getId().equals(vid))) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Teilnehmer gehört nicht zu dieser Veranstaltung.").build();
        }

        try {
            for (VortragPrioDto dto : dtoList) {
                Vortrag vortrag = Vortrag.findById(dto.vortragId);
                if (null == vortrag) {
                    return Response.status(Response.Status.NOT_FOUND).entity("Vortrag mit ID " + dto.vortragId + " nicht gefunden.").build();
                }
                if (!vortrag.getVeranstaltung().getId().equals(vid)) {
                    return Response.status(Response.Status.BAD_REQUEST).entity("Vortrag mit ID " + dto.vortragId + " gehört nicht zu dieser Veranstaltung.").build();
                }
                prioritaetService.updateSinglePrioritaet(tid, dto.vortragId, dto.prioWert);
            }
            return Response.ok().build();
        } catch (WebApplicationException e) {
            return Response.status(e.getResponse().getStatus()).entity(e.getMessage()).build();
        } catch (Exception e) {
            Log.error("Fehler beim Aktualisieren der Priorität: " + e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Interner Serverfehler beim Aktualisieren der Priorität.").build();
        }
    }

    // --- GRUPPEN-VERWALTUNG ---


    @GET
    @Path("/veranstaltungen/{vid}/gruppen")
    @Operation(summary = "Alle Gruppen einer Veranstaltung abrufen")
    public Response getGruppen(@PathParam("vid") Long vid) {
        try {
            return Response.ok(adminService.getGruppen(vid)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        }
    }


    @POST
    @Path("/veranstaltungen/{vid}/gruppen")
    @Operation(summary = "Eine neue Gruppe zu einer Veranstaltung hinzufügen")
    public Response createGruppe(@PathParam("vid") Long vid, @RequestBody(description = "Der Name der neuen Gruppe") String gruppenName) {
        try {
            adminService.createGruppe(vid, gruppenName);
            return Response.status(Response.Status.CREATED).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }


    @PUT
    @Path("/veranstaltungen/{vid}/gruppen")
    @Operation(summary = "Eine Gruppe umbenennen")
    public Response renameGruppe(@PathParam("vid") Long vid, @QueryParam("alterName") String alterName, @QueryParam("neuerName") String neuerName) {
        try {
            adminService.renameGruppe(vid, alterName, neuerName);
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }


    @DELETE
    @Path("/veranstaltungen/{vid}/gruppen/{gruppenName}")
    @Operation(summary = "Eine Gruppe aus einer Veranstaltung löschen")
    public Response deleteGruppe(@PathParam("vid") Long vid, @PathParam("gruppenName") String gruppenName) {
        try {
            adminService.deleteGruppe(vid, gruppenName);
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }
}
