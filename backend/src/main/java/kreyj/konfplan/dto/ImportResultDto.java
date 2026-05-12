package kreyj.konfplan.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class ImportResultDto {
    public int successCount;
    public int errorCount;
    public String errorMessage;

    public ImportResultDto() {}

    public ImportResultDto(int successCount, int errorCount) {
        this.successCount = successCount;
        this.errorCount = errorCount;
    }

    public ImportResultDto(int successCount, int errorCount, String errorMessage) {
        this.successCount = successCount;
        this.errorCount = errorCount;
        this.errorMessage = errorMessage;
    }
}
