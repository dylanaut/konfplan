package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.persistence.Admin;
import lombok.NoArgsConstructor;

@RegisterForReflection
@NoArgsConstructor
public class OrganisatorDto {
    public Long id;
    public String name;
    public String email;

    public static OrganisatorDto from(Admin admin) {
        OrganisatorDto dto = new OrganisatorDto();
        dto.id = admin.getId();
        dto.name = admin.getFullName();
        dto.email = admin.getEmail();
        return dto;
    }
}
