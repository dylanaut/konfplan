package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class GruppenkategorieAnfrageDto {
    public String name;
    public boolean mehrwertig;
    public boolean pflicht;
}
