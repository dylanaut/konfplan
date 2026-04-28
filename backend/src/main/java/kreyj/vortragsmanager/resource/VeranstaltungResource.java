package kreyj.vortragsmanager.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.vortragsmanager.dto.*;
import kreyj.vortragsmanager.entity.Admin;
import kreyj.vortragsmanager.entity.EventSlot;
import kreyj.vortragsmanager.entity.Veranstaltung;
import kreyj.vortragsmanager.entity.Vortrag;
import kreyj.vortragsmanager.service.*;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.List;
import java.util.stream.Collectors;

@Path("/api/veranstaltungen")
@RolesAllowed("ADMIN")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VeranstaltungResource {
    private static final Logger LOG = Logger.getLogger(VeranstaltungResource.class);

    @Inject
    VeranstaltungService veranstaltungService;

    @Inject
    AdminService adminService;

    @Inject
    ReferentService referentService;

    @Inject
    TeilnehmerService teilnehmerService;

    @Inject
    OptimierungService optimierungService;

    @Inject
    PlanService planService;

    // --- BASIS: VERANSTALTUNGEN ---

    @GET
    public List<VeranstaltungDto> getAll() {
        return veranstaltungService.listAll().stream()
                .map(VeranstaltungResource::mapVeranstaltungToDto)
                .collect(Collectors.toList());
    }

    @GET
    @Path("/{vid}")
    public Response getOne(@PathParam("vid") Long vid) {
        Veranstaltung vEntity = veranstaltungService.findById(vid);
        if (vEntity == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(mapVeranstaltungToDto(vEntity)).build();
    }

    @POST
    public Response create(VeranstaltungDto vDto) {
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
    public Response update(@PathParam("id") Long id, VeranstaltungDto vDto) {
        vDto.id = id;
        try {
            VeranstaltungDto updated = veranstaltungService.save(vDto);
            if (updated == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return Response.ok(updated).build();
        } catch (IllegalArgumentException e) {
            LOG.error(e.getMessage(), e);
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @DELETE
    @Path("/{id}")
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
    public List<UserDto> getNutzer(@PathParam("vid") Long vid) {
        return adminService.getAllUsers(vid);
    }

    @POST
    @Path("/{vid}/nutzer")
    public Response createNutzer(@PathParam("vid") Long vid, UserDto userDto) {
        UserDto created = adminService.createUser(userDto, List.of(vid));
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{vid}/nutzer/{id}")
    public Response updateNutzer(@PathParam("vid") Long vid, @PathParam("id") Long id, UserDto userDto) {
        UserDto updated = adminService.updateUser(id, userDto, List.of(vid));
        if (updated == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(updated).build();
    }

    @DELETE
    @Path("/{vid}/nutzer/{id}")
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
    public Response saveTeilnehmerPrioritaeten(@PathParam("vid") Long vid, @PathParam("userId") Long userId, List<VortragPrioDto> priorityDtos) {
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
    @Path("/{vid}/vortraege")
    public List<VortragDto> getVortraege(@PathParam("vid") Long vid) {
        List<Vortrag> allVortraege = adminService.getAllVortraege(vid);
        return allVortraege.stream().map(ReferentResource::mapVortragToDto).toList();
    }

    @POST
    @Path("/{vid}/vortraege")
    public Response createVortrag(@PathParam("vid") Long vid, Vortrag vortrag) {
        Vortrag created = adminService.createVortrag(vortrag, vid);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{vid}/vortraege/{id}")
    public Response updateVortrag(@PathParam("vid") Long vid, @PathParam("id") Long id, Vortrag vortrag) {
        Vortrag updated = adminService.updateVortrag(id, vortrag, vid);
        if (updated == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(updated).build();
    }

    @DELETE
    @Path("/{vid}/vortraege/{id}")
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
    @Path("/{vid}/slots")
    public List<EventSlotDto> getSlots(@PathParam("vid") Long vid) {
        return adminService.getAllEventSlots(vid)
                .stream()
                .map(SlotResource::mapSlotToDto).toList();
    }


    @POST
    @Path("/{vid}/slots")
    public Response createSlot(@PathParam("vid") Long vid, EventSlot slot) {
        try {
            EventSlot created = adminService.createEventSlot(slot, vid);
            return Response.status(Response.Status.CREATED).entity(SlotResource.mapSlotToDto(created)).build();
        } catch (IllegalArgumentException e) {
            LOG.error(e.getMessage(), e);
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @PUT
    @Path("/{vid}/slots/{id}")
    public Response updateSlot(@PathParam("vid") Long vid, @PathParam("id") Long id, EventSlot slot) {
        try {
            EventSlot updated = adminService.updateEventSlot(id, slot, vid);
            if (updated == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return Response.ok(SlotResource.mapSlotToDto(updated)).build();
        } catch (IllegalArgumentException e) {
            LOG.error(e.getMessage(), e);
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @DELETE
    @Path("/{vid}/slots/{id}")
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
    public List<VortragStatDto> getStats(@PathParam("vid") Long vid) {
        return adminService.getStats(vid);
    }

    // --- PLANUNG & ERGEBNISSE ---

    @GET
    @Path("/{vid}/plan/details")
    public List<VortragBelegungDto> getDetaillierterPlan(@PathParam("vid") Long vid) {
        return planService.getDetaillierterPlan(vid);
    }

    @GET
    @Path("/{vid}/plan/qualitaet")
    public PlanQualitaetDto getPlanQualitaet(@PathParam("vid") Long vid) {
        return planService.getPlanQualitaet(vid);
    }

    @GET
    @Path("/{vid}/plan")
    public List<ZuweisungDto> getGesamtplan(@PathParam("vid") Long vid) {
        return planService.getGesamtplan(vid);
    }

    @POST
    @Path("/{vid}/optimierung/start")
    public Response starteOptimierung(@PathParam("vid") Long vid, SolverConfigDto config) {
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
        dto.id = v.id;
        dto.version = v.version;

        dto.name = v.name;
        dto.beginntAm = v.beginntAm;
        dto.endetAm = v.endetAm;
        dto.deadlineReferenten = v.deadlineReferenten;
        dto.deadlineTeilnehmer = v.deadlineTeilnehmer;
        dto.logo = v.logo;
        dto.logo_link = v.logo_link;

        // Organisatoren filtern und hinzufügen
        if (v.nutzer != null) {
            v.nutzer.stream()
                    .filter(u -> u instanceof Admin)
                    .forEach(u -> {
                        dto.organisatorIds.add(u.id);
                        dto.organisatorNamen.add(u.lastName);
                    });
        }

        dto.gebaeude = v.gebaeude.stream().map(GebaeudeResource::mapToDto).toList();

        return dto;
    }
}
