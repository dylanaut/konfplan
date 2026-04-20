package kreyj.vortragsmanager.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.vortragsmanager.dto.*;
import kreyj.vortragsmanager.entity.EventSlot;
import kreyj.vortragsmanager.entity.Veranstaltung;
import kreyj.vortragsmanager.entity.Vortrag;
import kreyj.vortragsmanager.service.*;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.List;
import java.util.stream.Collectors;

@Path("/api/veranstaltungen")
@RolesAllowed({"ADMIN", "REFERENT"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VeranstaltungResource {

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

    @Inject
    JsonWebToken jwt;

    // --- BASIS: VERANSTALTUNGEN ---

    @GET
    @RolesAllowed("ADMIN")
    public List<VeranstaltungDto> getAll() {
        return veranstaltungService.listAll().stream()
                .map(VeranstaltungResource::mapToDto)
                .collect(Collectors.toList());
    }

    @GET
    @Path("/{vid}")
    public Response getOne(@PathParam("vid") Long vid) {
        Veranstaltung vEntity = veranstaltungService.findById(vid);
        if (vEntity == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(mapToDto(vEntity)).build();
    }

    @POST
    @RolesAllowed("ADMIN")
    public Response create(VeranstaltungDto vDto) {
        try {
            VeranstaltungDto saved = veranstaltungService.save(vDto);
            return Response.status(Response.Status.CREATED).entity(saved).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    public Response update(@PathParam("id") Long id, VeranstaltungDto vDto) {
        vDto.id = id;
        try {
            VeranstaltungDto updated = veranstaltungService.save(vDto);
            if (updated == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return Response.ok(updated).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    public Response delete(@PathParam("id") Long id) {
        boolean deleted = veranstaltungService.delete(id);
        if (!deleted) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.noContent().build();
    }

    @POST
    @Path("/import")
    @RolesAllowed("ADMIN")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response importVeranstaltungen(@RestForm("file") FileUpload file) {
        try {
            int count = veranstaltungService.importFromCsv(file.uploadedFile().toFile().toPath());
            return Response.ok("Import erfolgreich: " + count + " Veranstaltung(en) angelegt.").build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Fehler: " + e.getMessage()).build();
        }
    }

    // --- HIERARCHISCH (PRO VERANSTALTUNG) ---

    @GET
    @Path("/{vid}/benutzer")
    @RolesAllowed("ADMIN")
    public List<UserDto> getBenutzer(@PathParam("vid") Long vid) {
        return adminService.getAllUsers(vid);
    }

    @POST
    @Path("/{vid}/benutzer")
    @RolesAllowed("ADMIN")
    public Response createBenutzer(@PathParam("vid") Long vid, UserDto userDto) {
        UserDto created = adminService.createUser(userDto, vid);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{vid}/benutzer/{id}")
    @RolesAllowed("ADMIN")
    public Response updateBenutzer(@PathParam("vid") Long vid, @PathParam("id") Long id, UserDto userDto) {
        UserDto updated = adminService.updateUser(id, userDto, vid);
        if (updated == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(updated).build();
    }

    @DELETE
    @Path("/{vid}/benutzer/{id}")
    @RolesAllowed("ADMIN")
    public Response deleteBenutzer(@PathParam("vid") Long vid, @PathParam("id") Long id) {
        boolean deleted = adminService.deleteUser(id);
        if (!deleted) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.noContent().build();
    }

    @POST
    @Path("/{vid}/referenten/import")
    @RolesAllowed("ADMIN")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response importReferenten(@PathParam("vid") Long vid, @RestForm("file") FileUpload file) {
        try {
            int count = referentService.importFromCsv(file.uploadedFile().toFile().toPath(), vid);
            return Response.ok("Import erfolgreich: " + count + " Referenten angelegt.").build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Fehler: " + e.getMessage()).build();
        }
    }

    @POST
    @Path("/{vid}/teilnehmer/import")
    @RolesAllowed("ADMIN")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadTeilnehmerCsv(@PathParam("vid") Long vid, @RestForm("file") FileUpload file) {
        try {
            int count = teilnehmerService.importFromCsv(file.uploadedFile().toFile().toPath(), vid);
            return Response.ok("Import erfolgreich: " + count + " Teilnehmer angelegt.").build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Fehler: " + e.getMessage()).build();
        }
    }

    @GET
    @Path("/{vid}/vortraege")
    @RolesAllowed("ADMIN")
    public List<Vortrag> getVortraege(@PathParam("vid") Long vid) {
        return adminService.getAllVortraege(vid);
    }

    @POST
    @Path("/{vid}/vortraege")
    @RolesAllowed("ADMIN")
    public Response createVortrag(@PathParam("vid") Long vid, Vortrag vortrag) {
        Vortrag created = adminService.createVortrag(vortrag, vid);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{vid}/vortraege/{id}")
    @RolesAllowed("ADMIN")
    public Response updateVortrag(@PathParam("vid") Long vid, @PathParam("id") Long id, Vortrag vortrag) {
        Vortrag updated = adminService.updateVortrag(id, vortrag, vid);
        if (updated == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(updated).build();
    }

    @DELETE
    @Path("/{vid}/vortraege/{id}")
    @RolesAllowed("ADMIN")
    public Response deleteVortrag(@PathParam("vid") Long vid, @PathParam("id") Long id) {
        boolean deleted = adminService.deleteVortrag(id, vid);
        if (!deleted) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.noContent().build();
    }

    @POST
    @Path("/{vid}/vortraege/import")
    @RolesAllowed("ADMIN")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response importVortraege(@PathParam("vid") Long vid, @RestForm("file") FileUpload file) {
        try {
            int count = adminService.importVortraegeFromCsv(file.uploadedFile().toFile().toPath(), vid);
            return Response.ok("Import erfolgreich: " + count + " Vorträge angelegt.").build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Fehler: " + e.getMessage()).build();
        }
    }

    @GET
    @Path("/{vid}/slots")
    public List<EventSlotDto> getSlots(@PathParam("vid") Long vid) {
        return adminService.getAllEventSlots(vid)
                .stream()
                .map(VeranstaltungResource::mapSlotToDto).toList();
    }


    @POST
    @Path("/{vid}/slots")
    @RolesAllowed("ADMIN")
    public Response createSlot(@PathParam("vid") Long vid, EventSlot slot) {
        EventSlot created = adminService.createEventSlot(slot, vid);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{vid}/slots/{id}")
    @RolesAllowed("ADMIN")
    public Response updateSlot(@PathParam("vid") Long vid, @PathParam("id") Long id, EventSlot slot) {
        EventSlot updated = adminService.updateEventSlot(id, slot, vid);
        if (updated == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(updated).build();
    }

    @DELETE
    @Path("/{vid}/slots/{id}")
    @RolesAllowed("ADMIN")
    public Response deleteSlot(@PathParam("vid") Long vid, @PathParam("id") Long id) {
        boolean deleted = adminService.deleteEventSlot(id, vid);
        if (!deleted) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.noContent().build();
    }

    @POST
    @Path("/{vid}/slots/import")
    @RolesAllowed("ADMIN")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response importSlots(@PathParam("vid") Long vid, @RestForm("file") FileUpload file) {
        try {
            int count = adminService.importSlotsFromCsv(file.uploadedFile().toFile().toPath(), vid);
            return Response.ok("Import erfolgreich: " + count + " Zeit-Slots angelegt.").build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Fehler: " + e.getMessage()).build();
        }
    }

    @GET
    @Path("/{vid}/stats")
    @RolesAllowed("ADMIN")
    public List<VortragStatDto> getStats(@PathParam("vid") Long vid) {
        return adminService.getStats(vid);
    }

    @POST
    @Path("/{vid}/vortraege/{tid}/register")
    @RolesAllowed("REFERENT")
    public Response registerTalkForEvent(@PathParam("vid") Long vid, @PathParam("tid") Long tid) {
        referentService.registerTalkForEvent(jwt.getSubject(), tid, vid);
        return Response.ok().build();
    }

    @DELETE
    @Path("/{vid}/vortraege/{tid}/deregister")
    @RolesAllowed("REFERENT")
    public Response deregisterTalkFromEvent(@PathParam("vid") Long vid, @PathParam("tid") Long tid) {
        referentService.deregisterTalkFromEvent(jwt.getSubject(), tid, vid);
        return Response.ok().build();
    }

    // --- PLANUNG & ERGEBNISSE ---

    @GET
    @Path("/{vid}/plan/details")
    @RolesAllowed("ADMIN")
    public List<VortragBelegungDto> getDetaillierterPlan(@PathParam("vid") Long vid) {
        return planService.getDetaillierterPlan(vid);
    }

    @GET
    @Path("/{vid}/plan/qualitaet")
    @RolesAllowed("ADMIN")
    public PlanQualitaetDto getPlanQualitaet(@PathParam("vid") Long vid) {
        return planService.getPlanQualitaet(vid);
    }

    @GET
    @Path("/{vid}/plan")
    @RolesAllowed("ADMIN")
    public List<ZuweisungDto> getGesamtplan(@PathParam("vid") Long vid) {
        return planService.getGesamtplan(vid);
    }

    @POST
    @Path("/{vid}/optimierung/start")
    @RolesAllowed("ADMIN")
    public Response starteOptimierung(@PathParam("vid") Long vid, SolverConfigDto config) {
        try {
            optimierungService.starteOptimierung(vid, config);
            return Response.ok("Optimierung erfolgreich abgeschlossen.").build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Fehler bei der Optimierung: " + e.getMessage()).build();
        }
    }

    // -------------------------------------------------------------------
    // helper methods
    // -------------------------------------------------------------------

    public static VeranstaltungDto mapToDto(Veranstaltung v) {
        VeranstaltungDto dto = new VeranstaltungDto();
        dto.id = v.id;
        dto.version = v.version;

        dto.name = v.name;
        dto.beginntAm = v.beginntAm;
        dto.endetAm = v.endetAm;
        dto.logo = v.logo;
        dto.logo_link = v.logo_link;

        // Organisatoren filtern und hinzufügen
        if (v.benutzer != null) {
            v.benutzer.stream()
                    .filter(u -> "ADMIN".equals(u.role))
                    .forEach(u -> {
                        dto.organisatorIds.add(u.id);
                        dto.organisatorNamen.add(u.lastName);
                    });
        }

        dto.gebaeude = v.gebaeude.stream().map(GebaeudeResource::mapToDto).toList();

        return dto;
    }

    public static EventSlotDto mapSlotToDto(EventSlot eventSlot) {
        EventSlotDto dto = new EventSlotDto();

        dto.id = eventSlot.id;
        dto.version = eventSlot.version;

        dto.description = eventSlot.description;
        dto.startTime = eventSlot.startTime;
        dto.endTime = eventSlot.endTime;

        return dto;
    }
}
