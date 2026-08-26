package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public class TeilnehmerPasswortZipRequestDto {
    public List<Long> nutzerIds;
    public String zipPassword;


    public TeilnehmerPasswortZipRequestDto() {
    }


    public TeilnehmerPasswortZipRequestDto(List<Long> nutzerIds, String zipPassword) {
        this.nutzerIds = nutzerIds;
        this.zipPassword = zipPassword;
    }
}
