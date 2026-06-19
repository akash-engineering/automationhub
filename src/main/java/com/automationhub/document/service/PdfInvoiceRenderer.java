package com.automationhub.document.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Component
public class PdfInvoiceRenderer {

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 22, Font.BOLD);
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 12, Font.BOLD);
    private static final Font BODY_FONT = new Font(Font.HELVETICA, 11);
    private static final Color HEADER_BG = new Color(220, 220, 220);

    public byte[] render(InvoiceData data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document pdf = new Document(PageSize.A4);
        try {
            PdfWriter.getInstance(pdf, out);
            pdf.open();

            pdf.add(new Paragraph(safe(data.title(), "Invoice"), TITLE_FONT));
            pdf.add(new Paragraph(" "));
            pdf.add(new Paragraph("Date: " + LocalDate.now(), BODY_FONT));
            pdf.add(new Paragraph("To: " + safe(data.recipient(), ""), BODY_FONT));
            pdf.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{3f, 1f});

            table.addCell(headerCell("Description", Element.ALIGN_LEFT));
            table.addCell(headerCell("Amount", Element.ALIGN_RIGHT));

            BigDecimal total = BigDecimal.ZERO;
            String currency = safe(data.currency(), "USD");
            if (data.lines() != null) {
                for (InvoiceLine line : data.lines()) {
                    BigDecimal amount = line.amount() == null ? BigDecimal.ZERO : line.amount();
                    table.addCell(new PdfPCell(new Phrase(safe(line.description(), ""), BODY_FONT)));
                    table.addCell(amountCell(amount, currency, BODY_FONT));
                    total = total.add(amount);
                }
            }

            table.addCell(headerCell("Total", Element.ALIGN_LEFT));
            table.addCell(amountCell(total, currency, HEADER_FONT));

            pdf.add(table);
        } finally {
            pdf.close();
        }
        return out.toByteArray();
    }

    private static PdfPCell headerCell(String text, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, HEADER_FONT));
        cell.setBackgroundColor(HEADER_BG);
        cell.setHorizontalAlignment(alignment);
        return cell;
    }

    private static PdfPCell amountCell(BigDecimal amount, String currency, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(formatMoney(amount, currency), font));
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return cell;
    }

    private static String formatMoney(BigDecimal amount, String currency) {
        return currency + " " + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String safe(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
