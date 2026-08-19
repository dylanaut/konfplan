package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class AppInfoDto {
    public String name;
    public String version;
    public String buildTime;
    public String gitCommit;

    public AppInfoDto(String name, String version, String buildTime, String gitCommit) {
        this.name = name;
        this.version = version;
        this.buildTime = buildTime;
        this.gitCommit = gitCommit;
    }
}
