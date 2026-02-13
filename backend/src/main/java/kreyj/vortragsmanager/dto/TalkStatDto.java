package kreyj.vortragsmanager.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class TalkStatDto {
    public String title;
    public long countPrio1;  // Anzahl der Teilnehmer, die diesen Talk als Prio 1 gewählt haben
    public long countTop3;   // Anzahl der Teilnehmer, die diesen Talk in ihren Top 3 haben
    public long totalVotes;  // Wie oft wurde dieser Talk insgesamt priorisiert

    public TalkStatDto() {
    }

    public TalkStatDto(String title, long countPrio1, long countTop3, long totalVotes) {
        this.title = title;
        this.countPrio1 = countPrio1;
        this.countTop3 = countTop3;
        this.totalVotes = totalVotes;
    }
}