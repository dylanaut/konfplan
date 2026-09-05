package kreyj.konfplan.domain.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import kreyj.konfplan.domain.exception.DatabaseExportException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Erstellt ein ZIP mit je einem pg_dump (Custom-Format) der konfplan- und der Keycloak-Datenbank,
 * fuer den Administrator-Export (siehe DatabaseBackupResource). Import/Restore laeuft bewusst
 * NICHT durch diesen Service, sondern nur ueber deploy/restore_db.sh (siehe
 * Deployment-DockerCompose.adoc, Abschnitt "Backup-Hinweise").
 */
@ApplicationScoped
public class DatabaseBackupService {

    @ConfigProperty(name = "pg-dump.path")
    String pgDumpPath;

    @ConfigProperty(name = "db-export.host")
    String host;

    @ConfigProperty(name = "db-export.port")
    String port;

    @ConfigProperty(name = "db-export.user")
    String user;

    // Optional<String>, da smallrye-config einen leeren String-Default (lokal ohne DB_PASSWORD,
    // z.B. Homebrew-Postgres ohne Passwort) sonst als "kein Wert vorhanden" behandelt und den
    // Start mit einer ConfigurationException verweigert.
    @ConfigProperty(name = "db-export.password")
    Optional<String> password;

    @ConfigProperty(name = "db-export.konfplan-db-name")
    String konfplanDbName;

    @ConfigProperty(name = "db-export.keycloak-db-name")
    String keycloakDbName;


    public void writeBackupZip(OutputStream target) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(target)) {
            dumpDatabase(konfplanDbName, "konfplan.dump", zip);
            dumpDatabase(keycloakDbName, "keycloak.dump", zip);
        }
    }


    private void dumpDatabase(String dbName, String entryName, ZipOutputStream zip) throws IOException {
        Process process;
        try {
            ProcessBuilder builder = new ProcessBuilder(pgDumpPath, "-Fc", "-h", host, "-p", port, "-U", user, dbName);
            builder.environment().put("PGPASSWORD", password.orElse(""));
            process = builder.start();
        } catch (IOException e) {
            throw new DatabaseExportException("pg_dump konnte nicht gestartet werden (Pfad '" + pgDumpPath + "'): " + e.getMessage(), e);
        }

        // stdout traegt den binaeren Dump, stderr etwaige Fehlermeldungen - getrennt lesen,
        // sonst blockiert pg_dump, sobald der (hier ungenutzte) stderr-Puffer vollläuft.
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        Thread stderrReader = new Thread(() -> {
            try {
                process.getErrorStream().transferTo(stderr);
            } catch (IOException e) {
                Log.warn("Konnte stderr von pg_dump nicht vollständig lesen", e);
            }
        });
        stderrReader.start();

        zip.putNextEntry(new ZipEntry(entryName));
        process.getInputStream().transferTo(zip);
        zip.closeEntry();

        try {
            stderrReader.join();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new DatabaseExportException("pg_dump für '" + dbName + "' fehlgeschlagen (Exit-Code " + exitCode + "): "
                        + stderr.toString(StandardCharsets.UTF_8).trim());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseExportException("pg_dump für '" + dbName + "' wurde unterbrochen.", e);
        }
    }
}
