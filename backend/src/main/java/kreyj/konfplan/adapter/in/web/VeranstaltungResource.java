package kreyj.konfplan.adapter.in.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.konfplan.adapter.in.web.dto.*;
import kreyj.konfplan.application.port.in.AdminServiceInterface;
import kreyj.konfplan.application.port.in.ReferentServiceInterface;
import kreyj.konfplan.application.port.in.TeilnehmerServiceInterface;
import kreyj.konfplan.application.port.in.VeranstaltungServiceInterface;
import kreyj.konfplan.application.service.AdminService;
import kreyj.konfplan.application.service.PlanErstellungService;
import kreyj.konfplan.application.service.PlanService;
import kreyj.konfplan.application.service.ReferentService;
import kreyj.konfplan.application.service.VeranstaltungService;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Vortrag;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.List;

import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static kreyj.konfplan.application.service.AdminService.mapSlotToDto;

@Path("/api/veranstaltungen")
@RolesAllowed("ADMIN")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Veranstaltungen", description = "Zentrale Endpunkte für die Verwaltung von Veranstaltungen und deren Inhalten")
@Transactional
public class VeranstaltungResource {
    private static final Logger LOG = Logger.getLogger(VeranstaltungResource.class);

    private final VeranstaltungServiceInterface veranstaltungService;

    private final AdminServiceInterface adminService;

    private final ReferentServiceInterface referentService;

    private final TeilnehmerServiceInterface teilnehmerService;

    private final PlanErstellungService planErstellungService;

    private final PlanService planService;


    public VeranstaltungResource(VeranstaltungServiceInterface veranstaltungService, AdminServiceInterface adminService, ReferentServiceInterface referentService,
                                 TeilnehmerServiceInterface teilnehmerService, PlanErstellungService planErstellungService, PlanService planService) {
        this.veranstaltungService = veranstaltungService;
        this.adminService = adminService;
        this.referentService = referentService;
        this.teilnehmerService = teilnehmerService;
        this.planErstellungService = planErstellungService;
        this.planService = planService;
    }


    @GET
    @Operation(summary = "Alle Veranstaltungen abrufen", description = "Gibt eine Liste aller Veranstaltungen zurück.")
    public List<VeranstaltungDto> getAll() {
        return veranstaltungService.listAll().stream()
                .map(VeranstaltungService::mapVeranstaltungToDto)
                .toList();
    }


    @GET
    @Path("/{vid}")
    @Operation(summary = "Eine Veranstaltung abrufen", description = "Ruft eine einzelne Veranstaltung anhand ihrer ID ab.")
    public Response getOne(@PathParam("vid") Long vid) {
        Veranstaltung vEntity = veranstaltungService.findById(vid);
        if (vEntity == null) {
            return Response.status(NOT_FOUND).build();
        }
        return Response.ok(VeranstaltungService.mapVeranstaltungToDto(vEntity)).build();
    }


    @POST
    @Operation(summary = "Neue Veranstaltung erstellen", description = "Erstellt eine neue Veranstaltung.")
    public Response create(@RequestBody(description = "Die zu erstellende Veranstaltung") VeranstaltungDto vDto) {
        try {
            VeranstaltungDto saved = veranstaltungService.save(vDto);
            return Response.status(Response.Status.CREATED).entity(saved).build();
        } catch (IllegalArgumentException e) {
            LOG.error(e.getMessage(), e);
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }


    @PUT
    @Path("/{id}")
    @Operation(summary = "Veranstaltung aktualisieren", description = "Aktualisiert eine bestehende Veranstaltung.")
    public Response update(@PathParam("id") Long id, @RequestBody(description = "Die aktualisierten Veranstaltungsdaten") VeranstaltungDto vDto) {
        vDto.id = id;
        try {
            VeranstaltungDto updated = veranstaltungService.save(vDto);
            if (updated == null) {
                return Response.status(NOT_FOUND).build();
            }
            return Response.ok(updated).build();
        } catch (OptimisticLockException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        } catch (IllegalArgumentException e) {
            LOG.error(e.getMessage(), e);
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }


    @DELETE
    @Path("/{id}")
    @Operation(summary = "Veranstaltung löschen", description = "Löscht eine Veranstaltung.")
    public Response delete(@PathParam("id") Long id) {
        boolean deleted = veranstaltungService.delete(id);
        if (!deleted) {
            return Response.status(NOT_FOUND).build();
        }
        return Response.noContent().build();
    }


    @POST
    @Path("/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Veranstaltungen importieren", description = "Importiert Veranstaltungen aus einer CSV-Datei.")
    public Response importVeranstaltungen(@RestForm("file") FileUpload file) {
        try {
            int count = veranstaltungService.importFromCsv(file.uploadedFile().toFile().toPath());
            return Response.ok("Import erfolgreich: " + count + " Veranstaltung(en) angelegt.").build();
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            return Response.status(Response.Status.BAD_REQUEST).entity("Fehler: " + e.getMessage()).build();
        }
    }

    // --- HIERARCHISCH (PRO VERANSTALTUNG) ---


    @GET
    @Path("/{vid}/nutzer")
    @Operation(summary = "Nutzer einer Veranstaltung abrufen", description = "Ruft alle Nutzer (Admins, Referenten, Teilnehmer) einer Veranstaltung ab.")
    public List<NutzerDto> getNutzer(@PathParam("vid") Long vid) {
        return adminService.getAllUsers(vid);
    }


    @POST
    @Path("/{vid}/nutzer")
    @Operation(summary = "Neuen Nutzer zu Veranstaltung hinzufügen", description = "Erstellt einen neuen Nutzer und fügt ihn direkt zu einer Veranstaltung hinzu.")
    public Response createNutzer(@PathParam("vid") Long vid, @RequestBody(description = "Die Daten des neuen Nutzers") NutzerDto nutzerDto) {
        NutzerDto created = adminService.createUser(nutzerDto, List.of(vid));
        return Response.status(Response.Status.CREATED).entity(created).build();
    }


    @PUT
    @Path("/{vid}/nutzer/{id}")
    @Operation(summary = "Nutzer in Veranstaltung aktualisieren", description = "Aktualisiert die Daten eines Nutzers im Kontext einer Veranstaltung.")
    public Response updateNutzer(@PathParam("vid") Long vid, @PathParam("id") Long id, @RequestBody(description = "Die aktualisierten Nutzerdaten") NutzerDto nutzerDto) {
        try {
            NutzerDto updated = adminService.updateUser(id, nutzerDto, List.of(vid));

            if (updated == null) {
                return Response.status(NOT_FOUND).build();
            }

            return Response.ok(updated).build();
        } catch (OptimisticLockException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        }
    }


    @DELETE
    @Path("/{vid}/nutzer/{id}")
    @Operation(summary = "Nutzer aus Veranstaltung entfernen", description = "Entfernt einen Nutzer aus einer Veranstaltung (löscht ihn aber nicht global).")
    public Response deleteNutzer(@PathParam("vid") Long vid, @PathParam("id") Long id) {
        boolean deleted = adminService.deleteUser(id);
        if (!deleted) {
            return Response.status(NOT_FOUND).build();
        }
        return Response.noContent().build();
    }


    @POST
    @Path("/{vid}/referenten/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Referenten für Veranstaltung importieren", description = "Importiert Referenten aus einer CSV-Datei und fügt sie zur Veranstaltung hinzu.")
    public Response importReferenten(@PathParam("vid") Long vid, @RestForm("file") FileUpload file) {
        try {
            int count = referentService.importFromCsv(file.uploadedFile().toFile().toPath(), vid);
            return Response.ok("Import erfolgreich: " + count + " Referenten angelegt.").build();
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            return Response.status(Response.Status.BAD_REQUEST).entity("Fehler: " + e.getMessage()).build();
        }
    }


    @POST
    @Path("/{vid}/teilnehmer/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Teilnehmer für Veranstaltung importieren", description = "Importiert Teilnehmer aus einer CSV-Datei und fügt sie zur Veranstaltung hinzu.")
    public Response uploadTeilnehmerCsv(@PathParam("vid") Long vid, @RestForm("file") FileUpload file) {
        try {
            int count = teilnehmerService.importFromCsv(file.uploadedFile().toFile().toPath(), vid);
            return Response.ok("Import erfolgreich: " + count + " Teilnehmer angelegt.").build();
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            return Response.status(Response.Status.BAD_REQUEST).entity("Fehler: " + e.getMessage()).build();
        }
    }


    @PUT
    @Path("/{vid}/teilnehmer/{userId}/prioritaeten")
    @Operation(summary = "Prioritäten eines Teilnehmers speichern", description = "Speichert die Vortragsprioritäten für einen Teilnehmer in einer Veranstaltung.")
    public Response saveTeilnehmerPrioritaeten(@PathParam("vid") Long vid, @PathParam("userId") Long userId, @RequestBody(description = "Liste der Prioritäten") List<VortragPrioDto> priorityDtos) {
        try {
            teilnehmerService.savePriorities(userId, vid, priorityDtos);
            return Response.noContent().build();
        } catch (NotFoundException e) {
            return Response.status(NOT_FOUND).entity(e.getMessage()).build();
        } catch (ForbiddenException e) {
            return Response.status(Response.Status.FORBIDDEN).entity(e.getMessage()).build();
        } catch (BadRequestException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (Exception e) {
            LOG.error("Fehler beim Speichern der Prioritäten für Teilnehmer " + userId + " in Veranstaltung " + vid, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Fehler beim Speichern der Prioritäten: " + e.getMessage()).build();
        }
    }


    @GET
    @RolesAllowed({"ADMIN", "TEILNEHMER", "REFERENT"})
    @Path("/{vid}/vortraege")
    @Operation(summary = "Vorträge einer Veranstaltung abrufen", description = "Ruft alle Vorträge ab, die zu einer Veranstaltung gehören.")
    public List<VortragDto> getVortraege(@PathParam("vid") Long vid) {
        List<Vortrag> allVortraege = adminService.getAllVortraege(vid);
        return allVortraege.stream().map(ReferentService::mapVortragToDto).toList();
    }


    @GET
    @Path("/{vid}/vortraege/{tid}")
    @Operation(summary = "Einen Vortrag einer Veranstaltung abrufen", description = "Ruft einen einzelnen Vortrag einer Veranstaltung ab.")
    public Response getVeranstaltungsVortrag(@PathParam("vid") Long vid, @PathParam("tid") Long tid) {
        Vortrag vortrag = adminService.getVeranstaltungsVortrag(vid, tid);
        if (null == vortrag) {
            return Response.status(NOT_FOUND).build();
        }
        return Response.ok(ReferentService.mapVortragToDto(vortrag)).build();
    }


    @POST
    @Path("/{vid}/vortraege")
    @Operation(summary = "Neuen Vortrag in Veranstaltung erstellen", description = "Erstellt einen neuen Vortrag innerhalb einer Veranstaltung.")
    public Response createVortrag(@PathParam("vid") Long vid,
                                  @RequestBody(description = "Der zu erstellende Vortrag") VortragDto vDto) {
        Vortrag created = adminService.createVortrag(vDto);
        return Response.status(Response.Status.CREATED).entity(ReferentService.mapVortragToDto(created)).build();
    }


    @PUT
    @Path("/{vid}/vortraege/{vortragId}")
    @Operation(summary = "Vortrag in Veranstaltung aktualisieren", description = "Aktualisiert einen bestehenden Vortrag innerhalb einer Veranstaltung.")
    public Response updateVortrag(@PathParam("vid") Long vid, @PathParam("vortragId") Long vortragId, @RequestBody(description = "Die aktualisierten Vortragsdaten") VortragDto vortragDto) {
        VortragDto updated;
        try {
            updated = adminService.updateVortrag(vortragId, vid, vortragDto);
        } catch (OptimisticLockException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        }
        if (null == updated) {
            return Response.status(NOT_FOUND).build();
        }
        return Response.ok(updated).build();
    }


    @DELETE
    @Path("/{vid}/vortraege/{id}")
    @Operation(summary = "Vortrag aus Veranstaltung löschen", description = "Löscht einen Vortrag aus einer Veranstaltung.")
    public Response deleteVortrag(@PathParam("vid") Long vid, @PathParam("id") Long id) {
        if (vid == null || id == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) {
            return Response.status(NOT_FOUND).build();
        }
        boolean deleted = adminService.deleteVortrag(id, veranstaltung);
        if (!deleted) {
            return Response.status(NOT_FOUND).build();
        }
        return Response.noContent().build();
    }


    @POST
    @Path("/{vid}/vortraege/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Vorträge für Veranstaltung importieren", description = "Importiert Vorträge aus einer CSV-Datei und fügt sie zur Veranstaltung hinzu.")
    public Response importVortraege(@PathParam("vid") Long vid, @RestForm("file") FileUpload file) {
        try {
            int count = adminService.importVortraegeFromCsv(file.uploadedFile().toFile().toPath(), vid);
            return Response.ok("Import erfolgreich: " + count + " Vorträge angelegt.").build();
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            return Response.status(Response.Status.BAD_REQUEST).entity("Fehler: " + e.getMessage()).build();
        }
    }


    @GET
    @RolesAllowed({"ADMIN", "TEILNEHMER", "REFERENT"})
    @Path("/{vid}/slots")
    @Operation(summary = "Slots einer Veranstaltung abrufen", description = "Ruft alle Zeit-Slots ab, die zu einer Veranstaltung gehören.")
    public List<SlotDto> getSlots(@PathParam("vid") Long vid) {
        return adminService.getAllEventSlots(vid)
                .stream()
                .map(AdminService::mapSlotToDto).toList();
    }


    @POST
    @Path("/{vid}/slots")
    @Operation(summary = "Neuen Slot in Veranstaltung erstellen", description = "Erstellt einen neuen Zeit-Slot innerhalb einer Veranstaltung.")
    public Response createSlot(@PathParam("vid") Long vid,
                               @RequestBody(description = "Der zu erstellende Slot") SlotDto slotDto) {
        try {
            Slot created = adminService.createSlot(slotDto, vid);
            return Response.status(Response.Status.CREATED).entity(mapSlotToDto(created)).build();
        } catch (IllegalArgumentException e) {
            LOG.error(e.getMessage(), e);
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }


    @PUT
    @Path("/{vid}/slots/{id}")
    @Operation(summary = "Slot in Veranstaltung aktualisieren", description = "Aktualisiert einen bestehenden Zeit-Slot innerhalb einer Veranstaltung.")
    public Response updateSlot(@PathParam("vid") Long vid, @PathParam("id") Long id,
                               @RequestBody(description = "Die aktualisierten Slot-Daten") SlotDto slotDto) {
        try {
            Slot updated = adminService.updateSlot(id, slotDto, vid);
            if (updated == null) {
                return Response.status(NOT_FOUND).build();
            }
            return Response.ok(mapSlotToDto(updated)).build();
        } catch (OptimisticLockException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        } catch (IllegalArgumentException e) {
            LOG.error(e.getMessage(), e);
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }


    @DELETE
    @Path("/{vid}/slots/{id}")
    @Operation(summary = "Slot aus Veranstaltung löschen", description = "Löscht einen Zeit-Slot aus einer Veranstaltung.")
    public Response deleteSlot(@PathParam("vid") Long vid, @PathParam("id") Long id) {
        if (vid == null || id == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) {
            return Response.status(NOT_FOUND).build();
        }

        boolean deleted = adminService.deleteSlot(id, veranstaltung);
        if (!deleted) {
            return Response.status(NOT_FOUND).build();
        }
        return Response.noContent().build();
    }


    @POST
    @Path("/{vid}/slots/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Slots für Veranstaltung importieren", description = "Importiert Zeit-Slots aus einer CSV-Datei und fügt sie zur Veranstaltung hinzu.")
    public Response importSlots(@PathParam("vid") Long vid, @RestForm("file") FileUpload file) {
        try {
            int count = adminService.importSlotsFromCsv(file.uploadedFile().toFile().toPath(), vid);
            return Response.ok("Import erfolgreich: " + count + " Zeit-Slots angelegt.").build();
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            return Response.status(Response.Status.BAD_REQUEST).entity("Fehler: " + e.getMessage()).build();
        }
    }


    @GET
    @Path("/{vid}/stats")
    @Operation(summary = "Statistiken für eine Veranstaltung abrufen", description = "Ruft Statistiken zu den Vorträgen einer Veranstaltung ab (z.B. Anzahl der Priorisierungen).")
    public List<VortragStatDto> getStats(@PathParam("vid") Long vid) {
        return adminService.getStats(vid);
    }

    // --- PLANUNG & ERGEBNISSE ---


    @GET
    @Path("/{vid}/plan/details")
    @Operation(summary = "Detaillierten Plan abrufen", description = "Ruft einen detaillierten Belegungsplan für alle Räume und Slots der Veranstaltung ab.")
    public Response getDetaillierterPlan(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung =
                Veranstaltung.findById(vid);
        if (veranstaltung == null) {
            return Response.status(NOT_FOUND).build();
        }
        return Response.ok(planService.getDetaillierterPlan(veranstaltung)).build();
    }


    @GET
    @Path("/{vid}/plan/qualitaet")
    @Operation(summary = "Qualität des Plans abrufen", description = "Ruft Kennzahlen zur Qualität der aktuellen Zuweisungsplanung ab.")
    public Response getPlanQualitaet(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = Veranstaltung.findById(vid);
        if (veranstaltung == null) {
            return Response.status(NOT_FOUND).build();
        }

        return Response.ok(planService.getPlanQualitaet(veranstaltung)).build();
    }


    @GET
    @Path("/{vid}/plan")
    @Operation(summary = "Gesamtplan (Zuweisungen) abrufen", description = "Ruft die vollständige Liste aller Zuweisungen (Teilnehmer zu Vorträgen) ab.")
    public Response getGesamtplan(@PathParam("vid") Long vid) {
        Veranstaltung veranstaltung = veranstaltungService.findById(vid);
        if (veranstaltung == null) {
            return Response.status(NOT_FOUND).build();
        }

        List<ZuweisungDto> plan = planService.getGesamtplan(veranstaltung);

        return Response.ok(plan).build();
    }


    @POST
    @Path("/{vid}/planungen")
    @Operation(summary = "Planerstellung starten", description = "Startet den Planerstellungsprozess (MiniZinc), um die " +
            "Teilnehmer den Vorträgen zuzuordnen.")
    public Response erstellePlan(@PathParam("vid") Long vid, @RequestBody(description = "Konfiguration für den Solver") SolverConfig config) {
        try {
            planErstellungService.erstellePlan(vid, config);
            return Response.ok("Planerstellung erfolgreich abgeschlossen.").build();
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Fehler bei der Planerstellung: " + e.getMessage()).build();
        }
    }


    @GET
    @Path("/{vid}/raum-verfuegbarkeiten")
    @Operation(summary = "Raumverfügbarkeiten für eine Veranstaltung abrufen", description = "Ruft die Verfügbarkeiten aller Räume für die Slots einer Veranstaltung ab und prüft auf Kollisionen mit anderen Veranstaltungen.")
    public List<RaumVerfuegbarkeitDto> getRaumVerfuegbarkeiten(@PathParam("vid") Long vid) {
        return adminService.getRaumVerfuegbarkeiten(vid);
    }
}
