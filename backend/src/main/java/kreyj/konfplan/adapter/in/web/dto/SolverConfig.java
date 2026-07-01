package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Embeddable
public class SolverConfig {
    private String solver;
    private int timeout = 120; // in Sekunden
    private int numThreads = 4; // Anzahl paralleler Threads
    private int maxInstanzen = 2; // Maximale Anzahl der Instanzen pro Wahlvortrag
    private boolean auffuellen = true; // Maximale Anzahl der Instanzen pro Wahlvortrag
    private int messeSlotStrategie = 0; // -1 = RANDOM, 0 = keine, > 0 = SlotIndex freihalten für Messe


    public SolverConfig(int timeout, int numThreads, int maxInstanzen) {
        this("cp-sat", timeout, numThreads, maxInstanzen, true, 0);
    }
    public SolverConfig(int timeout, int numThreads, int maxInstanzen, boolean auffuellen) {
        this("cp-sat", timeout, numThreads, maxInstanzen, auffuellen, 0);
    }
}
