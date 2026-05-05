# AGENTS.md – resource/

## Zweck

Resource-Klassen sind die **JAX-RS REST-Controller**. Sie empfangen HTTP-Anfragen, delegieren die Verarbeitung an Services und geben HTTP-Antworten zurück. In den Resource-Klassen findet **keine Geschäftslogik** statt, außer einfachem Request-Mapping und Fehlerbehandlung.

## Vorhandene Resources

| Klasse                         | Basis-Pfad             | Rolle(n)             |
|--------------------------------|------------------------|----------------------|
| `AuthResource`                 | `/api/auth`            | Public               |
| `AdminResource`                | `/api/admin`           | ADMIN                |
| `VeranstaltungResource`        | `/api/veranstaltungen` | ADMIN                |
| `GebaeudeResource`             | `/api/gebaeude`        | ADMIN                |
| `ReferentResource`             | `/api/referenten`      | REFERENT, ADMIN      |
| `TeilnehmerResource`           | `/api/teilnehmer`      | TEILNEHMER, ADMIN    |
| `TeilnehmerPlanResource`       | `/api/teilnehmer`      | TEILNEHMER, ADMIN    |
| `TeilnehmerPrioritaetenResource`| `/api/teilnehmer/priorities` | TEILNEHMER |
| `SlotResource`                 | `/api/slots`           | Public (für Auswahl) |

## Regeln & Konventionen

- **Request-Mapping**: Nur Mapping von Pfad-/Query-Parametern und DTOs.
- **DTO-Nutzung**: Zur Kommunikation mit dem Frontend werden fast ausschließlich DTOs verwendet (siehe Paket `dto`).
- **Security**: Alle Klassen sind mit `@RolesAllowed` oder `@Authenticated` abgesichert. Nutzer-spezifische Daten werden über das `JsonWebToken` (`JwtHelper.getUPN(jwt)`) identifiziert.
- **Deadline-Checks**: Operationen, die Daten verändern (POST, PUT, DELETE), prüfen in den Services die in der `Veranstaltung` hinterlegten Deadlines und geben bei Überschreitung `403 Forbidden` zurück.

## Wichtige Endpunkte (Beispiele)

### Verfügbarkeiten
- `GET /api/admin/veranstaltungen/{vid}/verfuegbarkeiten`: Alle Nutzer-Verfügbarkeiten einer Veranstaltung.
- `GET /api/admin/veranstaltungen/{vid}/raeume/verfuegbarkeiten`: Raumverfügbarkeiten inklusive veranstaltungsübergreifender Belegungsprüfung.

### Hierarchische Struktur
Viele Endpunkte folgen dem Muster `/api/veranstaltungen/{vid}/...`, um den Kontext der aktuellen Veranstaltung direkt im Pfad abzubilden.

## Datei-Upload-Pattern (CSV-Import)

```java
@POST
@Path("/{vid}/referenten/import")
@Consumes(MediaType.MULTIPART_FORM_DATA)
public Response importReferenten(@PathParam("vid") Long vid, @RestForm("file") FileUpload file) {
    try {
        int count = referentService.importFromCsv(file.uploadedFile().toFile().toPath(), vid);
        return Response.ok("Import erfolgreich: " + count + " Referenten angelegt.").build();
    } catch (Exception e) {
        return Response.status(Response.Status.BAD_REQUEST).entity("Fehler: " + e.getMessage()).build();
    }
}
```
