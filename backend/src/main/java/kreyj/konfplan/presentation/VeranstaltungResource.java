package kreyj.konfplan.presentation;

import jakarta.annotation.security.RolesAllowed;
import jakarta.persistence.OptimisticLockException;
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
import kreyj.konfplan.application.service.AdminService;
import kreyj.konfplan.application.service.OptimierungService;
import kreyj.konfplan.application.service.PlanService;
import kreyj.konfplan.application.service.ReferentService;
import kreyj.konfplan.application.service.TeilnehmerService;
import kreyj.konfplan.application.service.VeranstaltungService;
import kreyj.konfplan.presentation.dto.NutzerDto;
import kreyj.konfplan.presentation.dto.PlanQualitaetDto;
import kreyj.konfplan.presentation.dto.RaumBelegungUebersichtDto;
import kreyj.konfplan.presentation.dto.SlotDto;
import kreyj.konfplan.presentation.dto.SolverConfigDto;
import kreyj.konfplan.presentation.dto.VeranstaltungDto;
import kreyj.konfplan.presentation.dto.VortragDto;
import kreyj.konfplan.presentation.dto.VortragPrioDto;
import kreyj.konfplan.presentation.dto.VortragStatDto;
import kreyj.konfplan.presentation.dto.ZuweisungDto;
import kreyj.konfplan.persistence.Admin;
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

@Path("/api/veranstaltungen")
@RolesAllowed("ADMIN")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Veranstaltungen", description = "Zentrale Endpunkte für die Verwaltung von Veranstaltungen und deren Inhalten")
public class VeranstaltungResource {
    private static final Logger LOG = Logger.getLogger(VeranstaltungResource.class);

    private final VeranstaltungService veranstaltungService;

    private final AdminService adminService;

    private final ReferentService referentService;

    private final TeilnehmerService teilnehmerService;

    private final OptimierungService optimierungService;

    private final PlanService planService;

    public VeranstaltungResource(VeranstaltungService veranstaltungService, AdminService adminService, ReferentService referentService,
                                 TeilnehmerService teilnehmerService, OptimierungService optimierungService, PlanService planService) {
        this.veranstaltungService = veranstaltungService;
        this.adminService = adminService;
        this.referentService = referentService;
        this.teilnehmerService = teilnehmerService;
        this.optimierungService = optimierungService;
        this.planService = planService;
    }
// --- BASIS: VERANSTALTUNGEN ---

    @GET
    @Operation(summary = "Alle Veranstaltungen abrufen", description = "Gibt eine Liste aller Veranstaltungen zurück.")
    public List<VeranstaltungDto> getAll() {
        return veranstaltungService.listAll().stream()
                .map(VeranstaltungResource::mapVeranstaltungToDto)
                .toList();
    }

    @GET
    @Path("/{vid}")
    @Operation(summary = "Eine Veranstaltung abrufen", description = "Ruft eine einzelne Veranstaltung anhand ihrer ID ab.")
    public Response getOne(@PathParam("vid") Long vid) {
        Veranstaltung vEntity = veranstaltungService.findById(vid);
        if (vEntity == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(mapVeranstaltungToDto(vEntity)).build();
    }

    @POST
    @Operation(summary = "Neue Veranstaltung erstellen", description = "Erstellt eine neue Veranstaltung.")
    public Response create(@RequestBody(description = "Die zu erstellende Veranstaltung", required = true) VeranstaltungDto vDto) {
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
    public Response update(@PathParam("id") Long id, @RequestBody(description = "Die aktualisierten Veranstaltungsdaten", required = true) VeranstaltungDto vDto) {
        vDto.id = id;
        try {
            VeranstaltungDto updated = veranstaltungService.save(vDto);
            if (updated == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
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
            return Response.status(Response.Status.NOT_FOUND).build();
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
    public Response createNutzer(@PathParam("vid") Long vid, @RequestBody(description = "Die Daten des neuen Nutzers", required = true) NutzerDto nutzerDto) {
        NutzerDto created = adminService.createUser(nutzerDto, List.of(vid));
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{vid}/nutzer/{id}")
    @Operation(summary = "Nutzer in Veranstaltung aktualisieren", description = "Aktualisiert die Daten eines Nutzers im Kontext einer Veranstaltung.")
    public Response updateNutzer(@PathParam("vid") Long vid, @PathParam("id") Long id, @RequestBody(description = "Die aktualisierten Nutzerdaten", required = true) NutzerDto nutzerDto) {
        try {
            NutzerDto updated = adminService.updateUser(id, nutzerDto, List.of(vid));

            if (updated == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
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
            return Response.status(Response.Status.NOT_FOUND).build();
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
    public Response saveTeilnehmerPrioritaeten(@PathParam("vid") Long vid, @PathParam("userId") Long userId, @RequestBody(description = "Liste der Prioritäten", required = true) List<VortragPrioDto> priorityDtos) {
        try {
            teilnehmerService.savePriorities(userId, vid, priorityDtos);
            return Response.noContent().build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
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
    @RolesAllowed({"ADMIN", "TEILNEHMER"})
    @Path("/{vid}/vortraege")
    @Operation(summary = "Vorträge einer Veranstaltung abrufen", description = "Ruft alle Vorträge ab, die zu einer Veranstaltung gehören.")
    public List<VortragDto> getVortraege(@PathParam("vid") Long vid) {
        List<Vortrag> allVortraege = adminService.getAllVortraege(vid);
        return allVortraege.stream().map(ReferentResource::mapVortragToDto).toList();
    }

    @GET
    @Path("/{vid}/vortraege/{tid}")
    @Operation(summary = "Einen Vortrag einer Veranstaltung abrufen", description = "Ruft einen einzelnen Vortrag einer Veranstaltung ab.")
    public Response getVeranstaltungsVortrag(@PathParam("vid") Long vid, @PathParam("tid") Long tid) {
        Vortrag vortrag = adminService.getVeranstaltungsVortrag(vid, tid);
        if (null == vortrag) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(ReferentResource.mapVortragToDto(vortrag)).build();
    }

    @POST
    @Path("/{vid}/vortraege")
    @Operation(summary = "Neuen Vortrag in Veranstaltung erstellen", description = "Erstellt einen neuen Vortrag innerhalb einer Veranstaltung.")
    public Response createVortrag(@PathParam("vid") Long vid, @RequestBody(description = "Der zu erstellende Vortrag", required = true) Vortrag vortrag) {
        Vortrag created = adminService.createVortrag(vortrag, vid);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{vid}/vortraege/{talkId}")
    @Operation(summary = "Vortrag in Veranstaltung aktualisieren", description = "Aktualisiert einen bestehenden Vortrag innerhalb einer Veranstaltung.")
    public Response updateVortrag(@PathParam("vid") Long vid, @PathParam("talkId") Long talkId, @RequestBody(description = "Die aktualisierten Vortragsdaten", required = true) VortragDto vortragDto) {
        Vortrag updated = null;
        try {
            updated = adminService.updateVortrag(vid, talkId, vortragDto);
        } catch (OptimisticLockException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        }
        if (updated == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(updated).build();
    }

    @DELETE
    @Path("/{vid}/vortraege/{id}")
    @Operation(summary = "Vortrag aus Veranstaltung löschen", description = "Löscht einen Vortrag aus einer Veranstaltung.")
    public Response deleteVortrag(@PathParam("vid") Long vid, @PathParam("id") Long id) {
        boolean deleted = adminService.deleteVortrag(id, vid);
        if (!deleted) {
            return Response.status(Response.Status.NOT_FOUND).build();
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
                .map(SlotResource::mapSlotToDto).toList();
    }


    @POST
    @Path("/{vid}/slots")
    @Operation(summary = "Neuen Slot in Veranstaltung erstellen", description = "Erstellt einen neuen Zeit-Slot innerhalb einer Veranstaltung.")
    public Response createSlot(@PathParam("vid") Long vid, @RequestBody(description = "Der zu erstellende Slot", required = true) Slot slot) {
        try {
            Slot created = adminService.createEventSlot(slot, vid);
            return Response.status(Response.Status.CREATED).entity(SlotResource.mapSlotToDto(created)).build();
        } catch (IllegalArgumentException e) {
            LOG.error(e.getMessage(), e);
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @PUT
    @Path("/{vid}/slots/{id}")
    @Operation(summary = "Slot in Veranstaltung aktualisieren", description = "Aktualisiert einen bestehenden Zeit-Slot innerhalb einer Veranstaltung.")
    public Response updateSlot(@PathParam("vid") Long vid, @PathParam("id") Long id, @RequestBody(description = "Die aktualisierten Slot-Daten", required = true) Slot slot) {
        try {
            Slot updated = adminService.updateEventSlot(id, slot, vid);
            if (updated == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return Response.ok(SlotResource.mapSlotToDto(updated)).build();
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
        boolean deleted = adminService.deleteEventSlot(id, vid);
        if (!deleted) {
            return Response.status(Response.Status.NOT_FOUND).build();
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
    public List<RaumBelegungUebersichtDto> getDetaillierterPlan(@PathParam("vid") Long vid) {
        return planService.getDetaillierterPlan(vid);
    }

    @GET
    @Path("/{vid}/plan/qualitaet")
    @Operation(summary = "Qualität des Plans abrufen", description = "Ruft Kennzahlen zur Qualität der aktuellen Zuweisungsplanung ab.")
    public PlanQualitaetDto getPlanQualitaet(@PathParam("vid") Long vid) {
        return planService.getPlanQualitaet(vid);
    }

    @GET
    @Path("/{vid}/plan")
    @Operation(summary = "Gesamtplan (Zuweisungen) abrufen", description = "Ruft die vollständige Liste aller Zuweisungen (Teilnehmer zu Vorträgen) ab.")
    public List<ZuweisungDto> getGesamtplan(@PathParam("vid") Long vid) {
        return planService.getGesamtplan(vid);
    }

    @POST
    @Path("/{vid}/optimierung/start")
    @Operation(summary = "Optimierung starten", description = "Startet den Optimierungsprozess (MiniZinc), um die Teilnehmer den Vorträgen zuzuordnen.")
    public Response starteOptimierung(@PathParam("vid") Long vid, @RequestBody(description = "Konfiguration für den Solver", required = true) SolverConfigDto config) {
        try {
            optimierungService.starteOptimierung(vid, config);
            return Response.ok("Optimierung erfolgreich abgeschlossen.").build();
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Fehler bei der Optimierung: " + e.getMessage()).build();
        }
    }

    // -------------------------------------------------------------------
    // helper methods
    // -------------------------------------------------------------------

    public static VeranstaltungDto mapVeranstaltungToDto(Veranstaltung v) {
        VeranstaltungDto dto = new VeranstaltungDto();
        dto.id = v.getId();
        dto.version = v.getVersion();

        dto.name = v.getName();
        dto.beginntAm = v.getBeginntAm();
        dto.endetAm = v.getEndetAm();
        dto.deadlineReferenten = v.getDeadlineReferenten();
        dto.deadlineTeilnehmer = v.getDeadlineTeilnehmer();
        dto.logo = v.getLogo();
        dto.logo_link = v.getLogo_link();

        // Organisatoren filtern und hinzufügen
        if (v.getNutzer() != null) {
            v.getNutzer().stream()
                    .filter(u -> u instanceof Admin)
                    .forEach(u -> {
                        dto.organisatorIds.add(u.getId());
                        dto.organisatorNamen.add(u.getLastName());
                    });
        }

        dto.gebaeude = v.getGebaeude().stream().map(GebaeudeResource::mapToDto).toList();

        return dto;
    }
}