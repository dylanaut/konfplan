package kreyj.konfplan.domain.service;

import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfServiceTest {

    private final PdfService pdfService = new PdfService();


    @Test
    void rendersHtmlToNonEmptyPdf() throws Exception {
        byte[] pdf = pdfService.renderHtmlToPdf("<html><body><p>Test</p></body></html>");
        assertThat(pdf).isNotEmpty();
    }


    @Test
    void encryptedPdfOpensWithCorrectPassword() throws Exception {
        byte[] pdf = pdfService.renderEncryptedPdf("<html><body><p>Test 123</p></body></html>", "geheim123");

        try (PdfReader reader = new PdfReader(pdf, "geheim123".getBytes(StandardCharsets.ISO_8859_1))) {
            assertThat(reader.getNumberOfPages()).isGreaterThan(0);
        }
    }


    @Test
    void encryptedPdfRejectsWrongPassword() throws Exception {
        byte[] pdf = pdfService.renderEncryptedPdf("<html><body><p>Test 123</p></body></html>", "geheim123");

        assertThatThrownBy(() -> new PdfReader(pdf, "falsch".getBytes(StandardCharsets.ISO_8859_1)))
            .isInstanceOf(IOException.class);
    }
}
