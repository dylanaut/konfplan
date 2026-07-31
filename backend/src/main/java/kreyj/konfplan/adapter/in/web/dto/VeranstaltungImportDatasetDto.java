package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public class VeranstaltungImportDatasetDto {
    public String name;
    public List<String> vorhandeneDateien;
    public List<String> fehlendeDateien;
    public boolean auswaehlbar;
}
