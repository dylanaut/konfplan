package kreyj.konfplan.adapter.in.web;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import kreyj.konfplan.adapter.in.web.dto.LaufbandDto;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

/**
 * Oeffentlicher, unauthentifizierter Endpunkt fuer das News-Laufband auf der Login-Seite -
 * wird bereits vor jedem Login abgefragt, kann also keine Security-Annotation tragen.
 */
@Path("/api/laufband")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Laufband", description = "Oeffentliches News-Laufband auf der Login-Seite")
public class LaufbandResource {
    private static final Logger LOG = Logger.getLogger(LaufbandResource.class);

    @ConfigProperty(name = "konfplan.laufband.path")
    String laufbandPath;


    @GET
    @Operation(summary = "News-Laufband abrufen",
        description = "Liefert alle nicht-leeren Zeilen der konfigurierten laufband.txt, sofern die Datei existiert "
            + "und ihre erste Zeile nicht leer ist. Andernfalls eine leere Liste.")
    public LaufbandDto getNews() {
        java.nio.file.Path pfad = java.nio.file.Path.of(laufbandPath);
        if (!Files.isRegularFile(pfad)) {
            return new LaufbandDto(Collections.emptyList());
        }

        try {
            List<String> zeilen = Files.readAllLines(pfad);
            if (zeilen.isEmpty() || zeilen.get(0).trim().isEmpty()) {
                return new LaufbandDto(Collections.emptyList());
            }
            List<String> news = zeilen.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
            return new LaufbandDto(news);
        } catch (IOException e) {
            LOG.warn("Laufband-Datei konnte nicht gelesen werden: " + pfad, e);
            return new LaufbandDto(Collections.emptyList());
        }
    }
}
