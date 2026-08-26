package kreyj.konfplan.domain.service;

import jakarta.enterprise.context.ApplicationScoped;
import net.lingala.zip4j.io.outputstream.ZipOutputStream;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Verpackt eine einzelne Datei in eine passwortverschluesselte ZIP-Datei (AES-256 ueber zip4j -
 * java.util.zip selbst bietet keine Verschluesselung).
 */
@ApplicationScoped
public class ZipService {

    public byte[] encryptSingleEntry(String fileNameInZip, byte[] content, String password) throws IOException {
        // ZipOutputStream schreibt das zentrale Verzeichnis erst beim close() - os.toByteArray()
        // darf daher erst NACH dem try-with-resources-Block gelesen werden.
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(os, password.toCharArray())) {
            ZipParameters params = new ZipParameters();
            params.setFileNameInZip(fileNameInZip);
            params.setEncryptFiles(true);
            params.setEncryptionMethod(EncryptionMethod.AES);
            params.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);

            zos.putNextEntry(params);
            zos.write(content);
            zos.closeEntry();
        }
        return os.toByteArray();
    }
}
