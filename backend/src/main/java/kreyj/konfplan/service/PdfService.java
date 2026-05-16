package kreyj.konfplan.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import io.quarkus.qute.TemplateInstance;
import jakarta.enterprise.context.ApplicationScoped;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.w3c.dom.Document;

import java.io.ByteArrayOutputStream;

@ApplicationScoped
public class PdfService {

    public byte[] generatePdf(TemplateInstance templateInstance) {
        try {
            String html = templateInstance.render();
            W3CDom w3cDom = new W3CDom();
            Document w3cDoc = w3cDom.fromJsoup(Jsoup.parse(html));

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withW3cDocument(w3cDoc, "/");
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Fehler bei der PDF-Generierung: " + e.getMessage(), e);
        }
    }
}