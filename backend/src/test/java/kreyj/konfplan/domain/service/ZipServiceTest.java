package kreyj.konfplan.domain.service;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.io.inputstream.ZipInputStream;
import net.lingala.zip4j.model.FileHeader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZipServiceTest {

    private final ZipService zipService = new ZipService();


    @Test
    void encryptedZipContainsEntryReadableWithCorrectPassword(@TempDir Path tempDir) throws Exception {
        byte[] content = "Name;Login\nMax Mustermann;max.mustermann".getBytes(StandardCharsets.UTF_8);
        byte[] zipBytes = zipService.encryptSingleEntry("test.csv", content, "geheim123");

        Path zipFile = tempDir.resolve("test.zip");
        Files.write(zipFile, zipBytes);

        try (ZipFile zip = new ZipFile(zipFile.toFile(), "geheim123".toCharArray())) {
            FileHeader header = zip.getFileHeaders().get(0);
            assertThat(header.getFileName()).isEqualTo("test.csv");

            try (ZipInputStream in = zip.getInputStream(header)) {
                byte[] extracted = in.readAllBytes();
                assertThat(extracted).isEqualTo(content);
            }
        }
    }


    @Test
    void encryptedZipRejectsWrongPassword(@TempDir Path tempDir) throws Exception {
        byte[] zipBytes = zipService.encryptSingleEntry("test.csv", "Inhalt".getBytes(StandardCharsets.UTF_8), "geheim123");

        Path zipFile = tempDir.resolve("test.zip");
        Files.write(zipFile, zipBytes);

        try (ZipFile zip = new ZipFile(zipFile.toFile(), "falsch".toCharArray())) {
            FileHeader header = zip.getFileHeaders().get(0);
            assertThatThrownBy(() -> {
                try (ZipInputStream in = zip.getInputStream(header)) {
                    in.readAllBytes();
                }
            }).isInstanceOf(ZipException.class).hasMessageContaining("Wrong Password");
        }
    }
}
