package kreyj.konfplan.resource;

import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import kreyj.konfplan.dto.AdminPrioritaetUpdateRequestDto;
import kreyj.konfplan.dto.RaumBelegbarkeitDto;
import kreyj.konfplan.dto.NutzerDto;
import kreyj.konfplan.dto.VerfuegbarkeitDto;
import kreyj.konfplan.dto.VortragPrioDto;
import kreyj.konfplan.persistence.*;
import kreyj.konfplan.service.AdminService;
import kreyj.konfplan.service.MailService;
import kreyj.konfplan.service.PrioritaetService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.Collections;
import java.util.List;

@Path("/api/admin")
@RolesAllowed("ADMIN")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Admin", description = "Administrative Endpunkte für die Nutzer- und Veranstaltungsverwaltung")
public class AdminResource {

    @Inject
    AdminService adminService;

    @Inject
    PrioritaetService prioritaetService;

    @Inject // MailService injizieren
    MailService mailService;

    public static NutzerDto mapNutzerToDto(Nutzer u) {
        NutzerDto dto = new NutzerDto();
        dto.id = u.id;
        dto.version = u.version;
        dto.email = u.email;
        dto.firstName = u.firstName;
        dto.lastName = u.lastName;
        dto.role = u.role;
        dto.isActive = u.isActive;
        dto.veranstaltungIds = null != u.getVeranstaltungen() ? u.getVeranstaltungen().stream().map(v -> v.id).toList() : Collections.emptyList();

        if (u instanceof Referent r) {
            dto.biography = r.biography;
            dto.jobRole = r.jobRole;
            dto.organisation = r.organisation;
            dto.slogan = r.slogan;
        } else if (u instanceof Teilnehmer t) {
            dto.gruppe = t.gruppe;
            if (t.prioritaeten != null) {
                dto.prioritaeten = t.prioritaeten.stream().map(VortragPrioDto::from).toList();
            }
        }
        return dto;
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
    public NutzerDto createUser(@RequestBody(description = "Die Daten des neuen Nutzers", required = true) NutzerDto dto) {
        NutzerDto createdNutzerDto = adminService.createUser(dto, dto.veranstaltungIds);
        // E-Mail nach erfolgreicher Erstellung senden
        Nutzer createdNutzer = Nutzer.findById(createdNutzerDto.id);
        if (createdNutzer != null) {
            mailService.sendRegistrationConfirmation(createdNutzer);
        }
        return createdNutzerDto;
    }

    @GET
    @Path("/nutzer/{id}")
    @Transactional
    @Operation(summary = "Einen Nutzer abrufen", description = "Ruft einen einzelnen Nutzer anhand seiner ID ab.")
    public Response getUser(@PathParam("id") Long id) {
            Nutzer nutzer =  adminService.findNutzer(id);

            if (null == nutzer) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }

            return Response.ok().entity(mapNutzerToDto(nutzer)).build();
    }

    @PUT
    @Path("/nutzer/{id}")
    @Operation(summary = "Nutzer aktualisieren", description = "Aktualisiert die Daten eines Nutzers.")
    public Response updateUser(@PathParam("id") Long id, @RequestBody(description = "Die aktualisierten Nutzerdaten", required = true) NutzerDto dto) {
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
        adminService.deleteUser(id);
        if (nutzerToDelete != null) {
            mailService.sendUserDeletionNotification(nutzerToDelete);
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

    @GET
    @Path("/veranstaltungen/{vid}/verfuegbarkeiten")
    @Operation(summary = "Verfügbarkeiten abrufen", description = "Ruft die Verfügbarkeiten aller Nutzer für eine Veranstaltung ab.")
    public List<VerfuegbarkeitDto> getVerfuegbarkeiten(@PathParam("vid") Long vid) {
        return Verfuegbarkeit.find("select v from Verfuegbarkeit v join v.nutzer u join u.veranstaltungen va where va.id = ?1", vid).stream()
                .map(v -> {
                    Verfuegbarkeit vf = (Verfuegbarkeit) v;
                    return new VerfuegbarkeitDto(vf.nutzer.id, vf.slot.id, vf.isAvailable);
                })
                .toList();
    }

    @POST
    @Path("/veranstaltungen/{vid}/verfuegbarkeiten")
    @Transactional
    @Operation(summary = "Verfügbarkeit aktualisieren", description = "Aktualisiert die Verfügbarkeit eines Nutzers für einen bestimmten Slot.")
    public Response updateVerfuegbarkeit(@PathParam("vid") Long vid, @RequestBody(description = "Die Verfügbarkeitsdaten", required = true) VerfuegbarkeitDto dto) {
        Nutzer nutzer = Nutzer.findById(dto.userId);
        EventSlot slot = EventSlot.findById(dto.slotId);
        if (nutzer == null || slot == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Validierung: Gehört der Slot zur angegebenen Veranstaltung?
        if (!slot.veranstaltung.id.equals(vid)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Slot gehört nicht zur angegebenen Veranstaltung.").build();
        }

        Verfuegbarkeit v = Verfuegbarkeit.find("nutzer = ?1 and slot = ?2", nutzer, slot).firstResult();
        if (v == null) {
            v = new Verfuegbarkeit();
            v.nutzer = nutzer;
            v.slot = slot;
        }
        v.isAvailable = dto.isAvailable;
        v.persist();
        return Response.ok().build();
    }

    @GET
    @Path("/veranstaltungen/{vid}/raeume/verfuegbarkeiten")
    @Operation(summary = "Raum-Verfügbarkeiten abrufen", description = "Ruft die Verfügbarkeiten (Belegungen) aller Räume für eine Veranstaltung ab.")
    public List<RaumBelegbarkeitDto> getRaumVerfuegbarkeiten(@PathParam("vid") Long vid) {
        Veranstaltung event = Veranstaltung.findById(vid);
        if (event == null) {
            throw new NotFoundException();
        }

        List<EventSlot> slots = EventSlot.find("veranstaltung.id", vid).list();
        List<Raum> raeume = event.getGebaeude().stream().flatMap(g -> g.getRaeume().stream()).toList();

        return raeume.stream().flatMap(r -> slots.stream().map(s -> {
            RaumBelegbarkeit rv = RaumBelegbarkeit.find("raum = ?1 and slot = ?2", r, s).firstResult();
            RaumBelegbarkeitDto dto = new RaumBelegbarkeitDto(r.id, s.id, rv != null && rv.isBelegt);

            // Cross-event check: Is this room busy in ANY other event at a time that overlaps with this slot?
            List<RaumBelegbarkeit> otherRvs = RaumBelegbarkeit.find("raum = ?1 and isBelegt = true and slot.veranstaltung.id != ?2", r, vid).list();
            for (RaumBelegbarkeit otherRv : otherRvs) {
                if (otherRv.slot.startTime.isBefore(s.endTime) && otherRv.slot.endTime.isAfter(s.startTime)) {
                    dto.isBlockedByOtherEvent = true;
                    dto.blockingEventName = otherRv.slot.veranstaltung.name;
                    break;
                }
            }
            return dto;
        })).toList();
    }

    @POST
    @Path("/veranstaltungen/{vid}/raeume/verfuegbarkeiten")
    @Transactional
    @Operation(summary = "Raum-Verfügbarkeit aktualisieren", description = "Aktualisiert die Belegung eines Raumes für einen bestimmten Slot.")
    public Response updateRaumVerfuegbarkeit(@PathParam("vid") Long vid, @RequestBody(description = "Die Raum-Verfügbarkeitsdaten", required = true) RaumBelegbarkeitDto dto) {
        Raum raum = Raum.findById(dto.raumId);
        EventSlot slot = EventSlot.findById(dto.slotId);
        if (raum == null || slot == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        RaumBelegbarkeit rv = RaumBelegbarkeit.find("raum = ?1 and slot = ?2", raum, slot).firstResult();
        if (rv == null) {
            rv = new RaumBelegbarkeit();
            rv.raum = raum;
            rv.slot = slot;
        }
        rv.isBelegt = dto.isBelegt;
        rv.persist();
        return Response.ok().build();
    }

    @PUT
    @Path("/veranstaltungen/{vid}/teilnehmer/{tid}/priorities")
    @Transactional
    @Operation(summary = "Teilnehmer-Prioritäten aktualisieren", description = "Aktualisiert eine oder mehrere Prioritäten eines Teilnehmers für eine Veranstaltung.")
    public Response updateTeilnehmerPrioritaet(
            @PathParam("vid") Long vid,
            @PathParam("tid") Long tid,
            @RequestBody(description = "Eine Liste von Prioritäts-Updates", required = true) List<AdminPrioritaetUpdateRequestDto> dtoList) { // Changed to List

        Teilnehmer teilnehmer = Teilnehmer.findById(tid);
        if (teilnehmer == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("Teilnehmer nicht gefunden.").build();
        }

        if (teilnehmer.getVeranstaltungen().stream().noneMatch(v -> v.id.equals(vid))) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Teilnehmer gehört nicht zu dieser Veranstaltung.").build();
        }

        try {
            for (AdminPrioritaetUpdateRequestDto dto : dtoList) {
                Vortrag vortrag = Vortrag.findById(dto.vortragId);
                if (vortrag == null) {
                    return Response.status(Response.Status.NOT_FOUND).entity("Vortrag mit ID " + dto.vortragId + " nicht gefunden.").build();
                }
                if (!vortrag.veranstaltung.id.equals(vid)) {
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
}