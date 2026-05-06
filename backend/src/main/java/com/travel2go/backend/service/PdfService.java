package com.travel2go.backend.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.PdfWriter;
import com.travel2go.backend.model.GlobalSettings;
import com.travel2go.backend.model.HolidayPackage;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;

@Service
public class PdfService {

    public byte[] generatePackagePdf(HolidayPackage pkg, GlobalSettings settings) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 54, 36);
        PdfWriter.getInstance(document, baos);
        document.open();

        // Fonts
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 24, Color.BLACK);
        Font subTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.DARK_GRAY);
        Font sectionHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new Color(37, 99, 235)); // Blue-600
        Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 12, Color.BLACK);
        Font italicFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 11, Color.GRAY);
        Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);

        // Header Image (Thumbnail)
        if (pkg.getMedia() != null && pkg.getMedia().getThumbnailUrl() != null && !pkg.getMedia().getThumbnailUrl().isEmpty()) {
            try {
                Image img = Image.getInstance(new URL(pkg.getMedia().getThumbnailUrl()));
                img.scaleToFit(500, 200);
                img.setAlignment(Element.ALIGN_CENTER);
                document.add(img);
            } catch (Exception e) {
                // Skip if image fails
            }
        }

        // Title
        Paragraph title = new Paragraph(pkg.getTitle(), titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingBefore(20);
        title.setSpacingAfter(10);
        document.add(title);

        // Duration & Location
        Paragraph subtitle = new Paragraph(pkg.getDuration().getDays() + " Days / " + pkg.getDuration().getNights() + " Nights | " + pkg.getDestination(), italicFont);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        subtitle.setSpacingAfter(20);
        document.add(subtitle);

        // Overview
        addSection(document, "Overview", stripHtml(pkg.getOverview()), sectionHeaderFont, normalFont);

        // Itinerary
        if (pkg.getItinerary() != null && !pkg.getItinerary().isEmpty()) {
            Paragraph itinHeader = new Paragraph("Itinerary", sectionHeaderFont);
            itinHeader.setSpacingBefore(15);
            itinHeader.setSpacingAfter(10);
            document.add(itinHeader);

            for (HolidayPackage.ItineraryDay day : pkg.getItinerary()) {
                Paragraph dayTitle = new Paragraph("Day " + day.getDay() + ": " + day.getTitle(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12));
                document.add(dayTitle);
                Paragraph dayDesc = new Paragraph(stripHtml(day.getActivities()), normalFont);
                dayDesc.setSpacingAfter(10);
                document.add(dayDesc);
            }
        }

        // Inclusions/Exclusions
        if (pkg.getInclusions() != null && !pkg.getInclusions().isEmpty()) {
            addSection(document, "Inclusions", String.join("\n• ", pkg.getInclusions()), sectionHeaderFont, normalFont);
        }
        if (pkg.getExclusions() != null && !pkg.getExclusions().isEmpty()) {
            addSection(document, "Exclusions", String.join("\n• ", pkg.getExclusions()), sectionHeaderFont, normalFont);
        }

        // Special Notes
        if (pkg.getSpecialNotes() != null && !pkg.getSpecialNotes().isEmpty()) {
            addSection(document, "Important Notes", stripHtml(pkg.getSpecialNotes()), sectionHeaderFont, normalFont);
        }

        // Terms & Conditions
        if (settings != null && settings.getTermsAndConditions() != null && !settings.getTermsAndConditions().isEmpty()) {
            document.add(new Chunk(new com.lowagie.text.pdf.draw.LineSeparator()));
            Paragraph termsHeader = new Paragraph("Terms & Conditions", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.GRAY));
            termsHeader.setSpacingBefore(20);
            document.add(termsHeader);
            Paragraph terms = new Paragraph(stripHtml(settings.getTermsAndConditions()), smallFont);
            document.add(terms);
        }

        document.close();
        return baos.toByteArray();
    }

    private void addSection(Document document, String title, String content, Font headerFont, Font contentFont) throws DocumentException {
        Paragraph header = new Paragraph(title, headerFont);
        header.setSpacingBefore(15);
        header.setSpacingAfter(5);
        document.add(header);
        Paragraph body = new Paragraph(content, contentFont);
        body.setSpacingAfter(10);
        document.add(body);
    }

    private String stripHtml(String html) {
        if (html == null) return "";
        // Simple regex to strip HTML tags
        return html.replaceAll("<[^>]*>", "")
                   .replaceAll("&nbsp;", " ")
                   .replaceAll("&amp;", "&")
                   .replaceAll("&quot;", "\"")
                   .replaceAll("&lt;", "<")
                   .replaceAll("&gt;", ">")
                   .trim();
    }
}
