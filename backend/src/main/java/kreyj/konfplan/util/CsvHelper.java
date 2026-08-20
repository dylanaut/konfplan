package kreyj.konfplan.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CsvHelper {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private CsvHelper() {
        // never instantiate
    }


    /**
     * Öffnet eine CSV-Datei als UTF-8-Reader und überspringt dabei ein ggf. vorhandenes
     * UTF-8-BOM (z.B. von Excels "CSV UTF-8"-Export) - ohne diesen Sprung würde das BOM als
     * Teil des ersten Spaltennamens der Kopfzeile gelesen und die Pflichtspalten-Erkennung
     * von OpenCSV schlägt fehl.
     */
    public static Reader openCsvReader(Path csvFilePath) throws IOException {
        InputStream in = Files.newInputStream(csvFilePath);
        PushbackInputStream pushbackIn = new PushbackInputStream(in, UTF8_BOM.length);
        byte[] firstBytes = new byte[UTF8_BOM.length];
        int bytesRead = pushbackIn.readNBytes(firstBytes, 0, UTF8_BOM.length);
        if (bytesRead < UTF8_BOM.length || !java.util.Arrays.equals(firstBytes, UTF8_BOM)) {
            pushbackIn.unread(firstBytes, 0, bytesRead);
        }
        return new InputStreamReader(pushbackIn, StandardCharsets.UTF_8);
    }
}
