package kreyj.konfplan.domain.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfStamper;
import org.openpdf.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Wandelt HTML in PDF-Bytes um und verschluesselt sie optional mit einem Oeffnungspasswort.
 * openhtmltopdf bietet keine Verschluesselungs-Hooks (per javap gegen die tatsaechliche Jar
 * verifiziert) - daher zweistufig: erst rendern, dann die fertigen PDF-Bytes mit OpenPDF
 * (org.openpdf.text.pdf, nicht das alte com.lowagie.text.pdf) nachverschluesseln.
 */
@ApplicationScoped
public class PdfService {

    public byte[] renderHtmlToPdf(String html) throws IOException {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        }
    }


    public byte[] encrypt(byte[] pdfBytes, String openPassword) throws Exception {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream();
             PdfReader reader = new PdfReader(pdfBytes);
             PdfStamper stamper = new PdfStamper(reader, os)) {
            stamper.setEncryption(
                openPassword.getBytes(StandardCharsets.ISO_8859_1),
                null, // ownerPassword=null -> OpenPDF generiert intern einen zufaelligen Owner-Wert
                PdfWriter.ALLOW_PRINTING,
                PdfWriter.ENCRYPTION_AES_128);
            stamper.close();
            return os.toByteArray();
        }
    }


    public byte[] renderEncryptedPdf(String html, String openPassword) throws Exception {
        return encrypt(renderHtmlToPdf(html), openPassword);
    }
}
