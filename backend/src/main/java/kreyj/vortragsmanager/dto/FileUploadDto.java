package kreyj.vortragsmanager.dto;

import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

public class FileUploadDto {
    @RestForm("file")
    public FileUpload file;
}