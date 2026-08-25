package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public class TeilnehmerPasswortPdfRequestDto {
    public List<Long> nutzerIds;
    public String pdfPassword;


    public TeilnehmerPasswortPdfRequestDto() {
    }


    public TeilnehmerPasswortPdfRequestDto(List<Long> nutzerIds, String pdfPassword) {
        this.nutzerIds = nutzerIds;
        this.pdfPassword = pdfPassword;
    }
}
