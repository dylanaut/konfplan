package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class PlanExportMetadata {
    private Long veranstaltungId;
    private SolverConfig solverConfig;
    private List<Long> teilnehmerOids;
    private List<Long> wahlvortragOids;
    private List<Long> slotOids;
    private List<Long> raumOids;
}
