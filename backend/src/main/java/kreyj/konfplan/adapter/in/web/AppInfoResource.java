package kreyj.konfplan.adapter.in.web;

import io.quarkus.info.BuildInfo;
import io.quarkus.info.GitInfo;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import kreyj.konfplan.adapter.in.web.dto.AppInfoDto;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/info")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Info", description = "Anwendungsinformationen für den Info-Dialog im Header")
public class AppInfoResource {

    @ConfigProperty(name = "quarkus.application.name")
    String appName;

    @Inject
    Instance<BuildInfo> buildInfo;

    @Inject
    Instance<GitInfo> gitInfo;


    @GET
    @Operation(summary = "Anwendungsinformationen abrufen", description = "Liefert Name, Version, Build-Datum und Git-Commit der laufenden Anwendung.")
    public AppInfoDto getInfo() {
        String version = buildInfo.isResolvable() ? buildInfo.get().version() : null;
        String buildTime = buildInfo.isResolvable() ? buildInfo.get().time().toString() : null;
        String gitCommit = gitInfo.isResolvable() ? gitInfo.get().latestCommitId() : null;

        return new AppInfoDto(appName, version, buildTime, gitCommit);
    }
}
