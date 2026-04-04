package kreyj.vortragsmanager.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class VortragStatDto {
    public String titel; // Umbenannt von title
    public long countPrio1;
    public long countPrio2;
    public long countPrio3;
    public long countTop3;
    public long totalVotes;

    public VortragStatDto() {
    }

    public VortragStatDto(String titel, long countPrio1, long countPrio2, long countPrio3, long countTop3, long totalVotes) {
        this.titel = titel;
        this.countPrio1 = countPrio1;
        this.countPrio2 = countPrio2;
        this.countPrio3 = countPrio3;
        this.countTop3 = countTop3;
        this.totalVotes = totalVotes;
    }
}
