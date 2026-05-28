package kreyj.konfplan.presentation.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
@RegisterForReflection
public class FileUploadDto {
    @RestForm("file")
    public FileUpload file;
}