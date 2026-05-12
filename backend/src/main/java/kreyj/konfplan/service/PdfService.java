package kreyj.konfplan.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.enterprise.context.ApplicationScoped;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Zuweisung;
import kreyj.konfplan.persistence.Raum;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class PdfService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public byte[] generiereTuerschilder(Long veranstaltungId) throws Exception {
        Veranstaltung v = Veranstaltung.findById(veranstaltungId);
        List<Zuweisung> all = Zuweisung.find("slot.veranstaltung.id", veranstaltungId).list();

        // Gruppieren nach Raum
        Map<Raum, List<Zuweisung>> raumPlan = all.stream()
                .collect(Collectors.groupingBy(z -> z.raum));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, out);

        document.open();

        Font fontTitle = new Font(Font.HELVETICA, 24, Font.BOLD);
        Font fontSubtitle = new Font(Font.HELVETICA, 18, Font.NORMAL);
        Font fontTableHead = new Font(Font.HELVETICA, 12, Font.BOLD, Color.WHITE);
        Font fontTableCell = new Font(Font.HELVETICA, 12, Font.NORMAL);

        for (Map.Entry<Raum, List<Zuweisung>> entry : raumPlan.entrySet()) {
            Raum raum = entry.getKey();
            List<Zuweisung> zuweisungen = entry.getValue().stream()
                    .collect(Collectors.groupingBy(z -> z.slot.id)) // Eindeutige Slots
                    .values().stream().map(l -> l.get(0))
                    .sorted((a, b) -> a.slot.startTime.compareTo(b.slot.startTime))
                    .collect(Collectors.toList());

            // Header mit Logo
            if (v.logo != null && !v.logo.isEmpty()) {
                try {
                    Image logo = Image.getInstance(v.logo);
                    logo.scaleToFit(100, 100);
                    logo.setAlignment(Element.ALIGN_CENTER);
                    document.add(logo);
                } catch (Exception e) {
                    document.add(new Paragraph("[Logo Fehler]"));
                }
            }

            Paragraph pVeranstaltung = new Paragraph(v.name, fontSubtitle);
            pVeranstaltung.setAlignment(Element.ALIGN_CENTER);
            document.add(pVeranstaltung);

            Paragraph pRaum = new Paragraph("Raum: " + raum.name, fontTitle);
            pRaum.setAlignment(Element.ALIGN_CENTER);
            pRaum.setSpacingAfter(30);
            document.add(pRaum);

            // Tabelle der Belegung
            PdfPTable table = new PdfPTable(3);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{20, 50, 30});

            addTableCell(table, "Zeit", fontTableHead, true);
            addTableCell(table, "Vortrag", fontTableHead, true);
            addTableCell(table, "Referent", fontTableHead, true);

            for (Zuweisung z : zuweisungen) {
                String zeit = z.slot.startTime.format(TIME_FORMAT) + " - " + z.slot.endTime.format(TIME_FORMAT);
                addTableCell(table, zeit, fontTableCell, false);
                addTableCell(table, z.vortrag.titel, fontTableCell, false);
                addTableCell(table, z.vortrag.referent.lastName, fontTableCell, false);
            }

            document.add(table);
            document.newPage(); // Nächster Raum auf neue Seite
        }

        document.close();
        return out.toByteArray();
    }

    private void addTableCell(PdfPTable table, String text, Font font, boolean isHeader) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(8);
        if (isHeader) {
            cell.setBackgroundColor(new Color(79, 70, 229)); // Indigo-600
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        }
        table.addCell(cell);
    }
}
