# AGENTS.md – resource/

## Zweck

Resource-Klassen sind die **JAX-RS REST-Controller**. Sie empfangen HTTP-Anfragen, delegieren die Verarbeitung an Services und geben HTTP-Antworten zurück. Keine Geschäftslogik hier.

## Vorhandene Resources

| Klasse                      | Basis-Pfad                          | Rolle           |
|-----------------------------|-------------------------------------|-----------------|
| `AuthResource`              | `/api/auth`                         | Public (Login, Reset) |
| `AdminResource`             | `/api/admin`                        | ADMIN           |
| `VeranstaltungResource`     | `/api/veranstaltungen`              | ADMIN           |
| `GebaeudeResource`          | `/api/gebaeude`                     | ADMIN           |
| `ReferentResource`          | `/api/referenten`                   | REFERENT        |
| `TeilnehmerResource`        | `/api/teilnehmer`                   | TEILNEHMER      |
| `ParticipantPriorityResource` | `/api/veranstaltungen/{vid}/prioritaeten` | TEILNEHMER |
| `ParticipantPlanResource`   | `/api/veranstaltungen/{vid}/plan`   | TEILNEHMER      |

## Pflichtstruktur für neue Resource-Klassen

```java
@Path("/api/meine-ressource")
@RolesAllowed("ADMIN")                          // Zugriff auf Rollenebene einschränken
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MeineResource {

    @Inject
    MeinService meinService;

    @GET
    public List<MeinDto> getAll() {
        return meinService.listAll();
    }

    @GET
    @Path("/{id}")
    public Response getById(@PathParam("id") Long id) {
        MeinDto dto = meinService.findById(id);
        if (dto == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(dto).build();
    }

    @POST
    public Response create(MeinDto dto) {
        try {
            MeinDto saved = meinService.create(dto);
            return Response.ok(saved).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(e.getMessage()).build();
        }
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") Long id, MeinDto dto) {
        MeinDto updated = meinService.update(id, dto);
        if (updated == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(updated).build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        boolean deleted = meinService.delete(id);
        if (!deleted) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.noContent().build();
    }
}
```

## Regeln & Konventionen

- **Keine Geschäftslogik** in Resource-Klassen – nur HTTP-Mapping
- Jede Resource braucht `@Produces` und `@Consumes` auf Klassenebene
- `@RolesAllowed` auf Klassenebene setzen; ggf. einzelne Methoden überschreiben
- Rückgabetyp: `Response` wenn 404/Fehler möglich; direkter Typ bei immer-erfolgreich
- Für Datei-Uploads: `@RestForm FileUpload` + `@Consumes(MediaType.MULTIPART_FORM_DATA)`
- Pfad-Parameter mit `@PathParam`, Query-Parameter mit `@QueryParam`

## Datei-Upload-Pattern (CSV-Import)

```java
@POST
@Path("/import")
@Consumes(MediaType.MULTIPART_FORM_DATA)
public Response importCsv(@RestForm("file") FileUpload file,
                           @PathParam("vid") Long vid) {
    try {
        ImportResultDto result = meinService.importCsv(
            Files.newInputStream(file.uploadedFile()), vid);
        return Response.ok(result).build();
    } catch (Exception e) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(e.getMessage()).build();
    }
}
```

## Rollenmatrix

| Endpunkt-Typ                        | Rolle       |
|-------------------------------------|-------------|
| Veranstaltungs- und User-Verwaltung | ADMIN       |
| Vortrag anlegen, Optimierung starten | ADMIN      |
| Eigenes Profil, eigene Vorträge     | REFERENT    |
| Prioritäten setzen, Plan einsehen   | TEILNEHMER  |
| Login, Passwort-Reset               | Public      |
