package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
@SuppressWarnings("unused")
public class ImportResultDto {
    public int anzahlErfolgreich;
    public List<String> fehler;

    public ImportResultDto() {
    }

    public ImportResultDto(int anzahlErfolgreich, List<String> fehler) {
        this.anzahlErfolgreich = anzahlErfolgreich;
        this.fehler = fehler;
    }
}
