package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
@RegisterForReflection
@SuppressWarnings("unused")
public class FileUploadDto {
    @RestForm("file")
    public FileUpload file;
}
