package kreyj.konfplan.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CsvHelperTest {

    @Test
    void skipsUtf8BomIfPresent(@org.junit.jupiter.api.io.TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("mit-bom.csv");
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] content = "Nachname;Vorname\nMustermann;Max\n".getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[bom.length + content.length];
        System.arraycopy(bom, 0, withBom, 0, bom.length);
        System.arraycopy(content, 0, withBom, bom.length, content.length);
        Files.write(csv, withBom);

        try (Reader reader = CsvHelper.openCsvReader(csv)) {
            char[] buffer = new char[4];
            int read = reader.read(buffer);
            assertThat(new String(buffer, 0, read)).isEqualTo("Nach");
        }
    }


    @Test
    void leavesContentUnchangedWithoutBom(@org.junit.jupiter.api.io.TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("ohne-bom.csv");
        Files.writeString(csv, "Nachname;Vorname\nMustermann;Max\n", StandardCharsets.UTF_8);

        try (Reader reader = CsvHelper.openCsvReader(csv)) {
            char[] buffer = new char[4];
            int read = reader.read(buffer);
            assertThat(new String(buffer, 0, read)).isEqualTo("Nach");
        }
    }


    @Test
    void handlesFileShorterThanBomLength(@org.junit.jupiter.api.io.TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("kurz.csv");
        Files.writeString(csv, "ab", StandardCharsets.UTF_8);

        try (Reader reader = CsvHelper.openCsvReader(csv)) {
            char[] buffer = new char[4];
            int read = reader.read(buffer);
            assertThat(new String(buffer, 0, read)).isEqualTo("ab");
        }
    }
}
