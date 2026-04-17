package kreyj.vortragsmanager.util;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import io.quarkus.runtime.ShutdownEvent;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@ApplicationScoped
public class SQLiteBackup {

    @Inject
    AgroalDataSource dataSource;

    private volatile boolean isSqlite = false;

    @PostConstruct
    void init() {
        // Prüfen, ob diese DataSource eine SQLite‑URL hat
        String url = System.getProperty("quarkus.datasource.jdbc.url");
        this.isSqlite = url != null && url.startsWith("jdbc:sqlite:");
    }

    void onShutdown(@Observes ShutdownEvent event) {
        if (!isSqlite) {
            return;  // Nur SQLite
        }
        backup();
    }

    void backup() {
        String url = System.getProperty("quarkus.datasource.jdbc.url");
        int prefixLength = "jdbc:sqlite:".length();
        int queryParamsIdx = url.indexOf('?');
        int length = (queryParamsIdx != -1) ? queryParamsIdx : url.length();
        String dbFile = url.substring(prefixLength, length);

        var originalDbPath = Paths.get(dbFile);
        var backupPath = originalDbPath.getParent().resolve(originalDbPath.getFileName() + "_backup");

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("backup to " + backupPath.toAbsolutePath());

            // Tausche Original gegen Backup
            Files.move(backupPath, originalDbPath,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        } catch (SQLException | java.io.IOException e) {
            throw new RuntimeException("Failed to backup SQLite DB on shutdown", e);
        }
    }
}