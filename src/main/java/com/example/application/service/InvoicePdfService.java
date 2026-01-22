package com.example.application.service;

import com.example.application.domain.*;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for generating sales invoice PDFs.
 * Creates professional invoice documents with company branding,
 * line items, tax breakdown, and payment terms.
 */
@Service
public class InvoicePdfService {

    private static final Logger log = LoggerFactory.getLogger(InvoicePdfService.class);

    // Fonts
    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 24, Font.BOLD, new Color(52, 73, 94));
    private static final Font HEADING_FONT = new Font(Font.HELVETICA, 12, Font.BOLD);
    private static final Font NORMAL_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL);
    private static final Font BOLD_FONT = new Font(Font.HELVETICA, 10, Font.BOLD);
    private static final Font SMALL_FONT = new Font(Font.HELVETICA, 8, Font.NORMAL);
    private static final Font SMALL_BOLD_FONT = new Font(Font.HELVETICA, 8, Font.BOLD);
    private static final Font TABLE_HEADER_FONT = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
    private static final Font TABLE_CELL_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL);
    private static final Font LARGE_BOLD_FONT = new Font(Font.HELVETICA, 14, Font.BOLD);

    // Colors
    private static final Color PRIMARY_COLOR = new Color(52, 73, 94);    // Dark blue-gray
    private static final Color ACCENT_COLOR = new Color(41, 128, 185);   // Blue
    private static final Color ALT_ROW_BG = new Color(245, 247, 249);    // Light gray
    private static final Color LIGHT_BLUE_BG = new Color(235, 245, 251); // Light blue
    private static final Color SUCCESS_GREEN = new Color(39, 174, 96);   // Green for paid
    private static final Color WARNING_RED = new Color(231, 76, 60);     // Red for overdue

    private final NumberFormat currencyFormat;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy");
    private final DateTimeFormatter shortDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public InvoicePdfService() {
        this.currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "NZ"));
    }

    /**
     * Generates a professional PDF for a sales invoice.
     *
     * @param invoice The invoice to generate PDF for
     * @return byte array containing the PDF content
     */
    public byte[] generateInvoicePdf(SalesInvoice invoice) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 50, 50, 50, 50);
            PdfWriter.getInstance(document, baos);
            document.open();

            addInvoiceContent(document, invoice);

            document.close();

            log.info("Generated invoice PDF for invoice {}: {} bytes",
                invoice.getInvoiceNumber(), baos.size());

            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Failed to generate invoice PDF for {}", invoice.getInvoiceNumber(), e);
            throw new RuntimeException("Failed to generate invoice PDF: " + e.getMessage(), e);
        }
    }

    private void addInvoiceContent(Document document, SalesInvoice invoice) throws DocumentException {
        Company company = invoice.getCompany();
        Contact customer = invoice.getContact();

        // Header with INVOICE title and company info
        addHeader(document, company, invoice);

        // Bill To section
        addBillToSection(document, customer);

        // Invoice details (number, dates)
        addInvoiceDetails(document, invoice);

        // Line items table
        addLineItemsTable(document, invoice);

        // Totals section
        addTotalsSection(document, invoice);

        // Payment information
        addPaymentInfo(document, company, invoice);

        // Footer with notes
        addFooter(document, invoice);
    }

    private void addHeader(Document document, Company company, SalesInvoice invoice) throws DocumentException {
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{60, 40});
        headerTable.setSpacingAfter(20);

        // Left: Company info
        PdfPCell companyCell = new PdfPCell();
        companyCell.setBorder(Rectangle.NO_BORDER);

        Paragraph companyName = new Paragraph(company.getName(), LARGE_BOLD_FONT);
        companyCell.addElement(companyName);

        // Company address/contact would go here (placeholder for now)
        Paragraph companyDetails = new Paragraph();
        companyDetails.setFont(SMALL_FONT);
        companyDetails.add(new Chunk("Tax Invoice", SMALL_BOLD_FONT));
        companyCell.addElement(companyDetails);

        headerTable.addCell(companyCell);

        // Right: INVOICE title and status
        PdfPCell invoiceCell = new PdfPCell();
        invoiceCell.setBorder(Rectangle.NO_BORDER);
        invoiceCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Paragraph invoiceTitle = new Paragraph("INVOICE", TITLE_FONT);
        invoiceTitle.setAlignment(Element.ALIGN_RIGHT);
        invoiceCell.addElement(invoiceTitle);

        // Status badge
        String statusText = invoice.getStatus().name();
        Color statusColor = switch (invoice.getStatus()) {
            case ISSUED -> invoice.isPaid() ? SUCCESS_GREEN :
                          invoice.isOverdue() ? WARNING_RED : ACCENT_COLOR;
            case DRAFT -> Color.GRAY;
            case VOID -> WARNING_RED;
        };

        if (invoice.isIssued() && invoice.isPaid()) {
            statusText = "PAID";
        } else if (invoice.isIssued() && invoice.isOverdue()) {
            statusText = "OVERDUE";
        }

        Font statusFont = new Font(Font.HELVETICA, 11, Font.BOLD, statusColor);
        Paragraph status = new Paragraph(statusText, statusFont);
        status.setAlignment(Element.ALIGN_RIGHT);
        invoiceCell.addElement(status);

        headerTable.addCell(invoiceCell);

        document.add(headerTable);
    }

    private void addBillToSection(Document document, Contact customer) throws DocumentException {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(50);
        table.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.setSpacingAfter(15);

        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(LIGHT_BLUE_BG);
        cell.setPadding(10);
        cell.setBorderColor(ACCENT_COLOR);

        cell.addElement(new Paragraph("BILL TO", SMALL_BOLD_FONT));

        Paragraph customerName = new Paragraph(customer.getName(), BOLD_FONT);
        cell.addElement(customerName);

        if (customer.getCode() != null && !customer.getCode().isBlank()) {
            cell.addElement(new Paragraph("Account: " + customer.getCode(), SMALL_FONT));
        }

        if (customer.getFormattedAddress() != null && !customer.getFormattedAddress().isBlank()) {
            Paragraph address = new Paragraph(customer.getFormattedAddress(), SMALL_FONT);
            address.setSpacingBefore(5);
            cell.addElement(address);
        }

        if (customer.getEmail() != null && !customer.getEmail().isBlank()) {
            cell.addElement(new Paragraph(customer.getEmail(), SMALL_FONT));
        }

        table.addCell(cell);
        document.add(table);
    }

    private void addInvoiceDetails(Document document, SalesInvoice invoice) throws DocumentException {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{25, 25, 25, 25});
        table.setSpacingAfter(20);

        // Invoice Number
        addDetailBox(table, "Invoice Number", invoice.getInvoiceNumber());

        // Issue Date
        addDetailBox(table, "Issue Date", invoice.getIssueDate().format(shortDateFormatter));

        // Due Date
        addDetailBox(table, "Due Date", invoice.getDueDate().format(shortDateFormatter));

        // Reference (if any)
        String reference = invoice.getReference() != null && !invoice.getReference().isBlank()
            ? invoice.getReference()
            : "-";
        addDetailBox(table, "Reference", reference);

        document.add(table);
    }

    private void addDetailBox(PdfPTable table, String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setBorderColor(Color.LIGHT_GRAY);
        cell.setPadding(8);

        Paragraph labelPara = new Paragraph(label, SMALL_FONT);
        labelPara.setSpacingAfter(3);
        cell.addElement(labelPara);

        Paragraph valuePara = new Paragraph(value, BOLD_FONT);
        cell.addElement(valuePara);

        table.addCell(cell);
    }

    private void addLineItemsTable(Document document, SalesInvoice invoice) throws DocumentException {
        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{8, 35, 12, 15, 12, 18});
        table.setSpacingAfter(10);

        // Headers
        addTableHeader(table, "#");
        addTableHeader(table, "Description");
        addTableHeader(table, "Qty");
        addTableHeader(table, "Unit Price");
        addTableHeader(table, "Tax");
        addTableHeader(table, "Amount");

        // Line items
        boolean alternate = false;
        int lineNum = 1;
        for (SalesInvoiceLine line : invoice.getLines()) {
            Color bgColor = alternate ? ALT_ROW_BG : Color.WHITE;
            alternate = !alternate;

            addTableCell(table, String.valueOf(lineNum++), bgColor, Element.ALIGN_CENTER);

            String description = line.getDescription() != null ? line.getDescription() : "";
            if (line.getProduct() != null && (description.isBlank())) {
                description = line.getProduct().getName();
            }
            addTableCell(table, description, bgColor, Element.ALIGN_LEFT);

            addTableCell(table, formatQuantity(line.getQuantity()), bgColor, Element.ALIGN_CENTER);
            addTableCell(table, formatCurrency(line.getUnitPrice()), bgColor, Element.ALIGN_RIGHT);

            String taxInfo = line.getTaxCode() != null ? line.getTaxCode() : "-";
            addTableCell(table, taxInfo, bgColor, Element.ALIGN_CENTER);

            addTableCell(table, formatCurrency(line.getGrossTotal()), bgColor, Element.ALIGN_RIGHT);
        }

        document.add(table);
    }

    private void addTotalsSection(Document document, SalesInvoice invoice) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(45);
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.setWidths(new float[]{60, 40});
        table.setSpacingAfter(20);

        // Subtotal
        addTotalRow(table, "Subtotal", invoice.getSubtotal(), false);

        // Tax breakdown by tax code
        Map<String, BigDecimal> taxByCode = new LinkedHashMap<>();
        for (SalesInvoiceLine line : invoice.getLines()) {
            if (line.getTaxCode() != null && line.getTaxAmount() != null
                && line.getTaxAmount().compareTo(BigDecimal.ZERO) > 0) {
                String code = line.getTaxCode();
                taxByCode.merge(code, line.getTaxAmount(), BigDecimal::add);
            }
        }

        if (taxByCode.isEmpty() && invoice.getTaxTotal().compareTo(BigDecimal.ZERO) > 0) {
            addTotalRow(table, "GST", invoice.getTaxTotal(), false);
        } else {
            for (Map.Entry<String, BigDecimal> entry : taxByCode.entrySet()) {
                addTotalRow(table, entry.getKey(), entry.getValue(), false);
            }
        }

        // Total
        addTotalRow(table, "TOTAL", invoice.getTotal(), true);

        // Amount paid (if any)
        if (invoice.getAmountPaid() != null && invoice.getAmountPaid().compareTo(BigDecimal.ZERO) > 0) {
            addTotalRow(table, "Amount Paid", invoice.getAmountPaid().negate(), false);
            addTotalRow(table, "BALANCE DUE", invoice.getBalance(), true);
        }

        document.add(table);
    }

    private void addTotalRow(PdfPTable table, String label, BigDecimal amount, boolean isTotal) {
        Font labelFont = isTotal ? BOLD_FONT : NORMAL_FONT;
        Font amountFont = isTotal ? BOLD_FONT : NORMAL_FONT;
        Color bgColor = isTotal ? LIGHT_BLUE_BG : Color.WHITE;

        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(Rectangle.TOP);
        labelCell.setBorderColor(Color.LIGHT_GRAY);
        labelCell.setBackgroundColor(bgColor);
        labelCell.setPadding(8);
        labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(labelCell);

        PdfPCell amountCell = new PdfPCell(new Phrase(formatCurrency(amount), amountFont));
        amountCell.setBorder(Rectangle.TOP);
        amountCell.setBorderColor(Color.LIGHT_GRAY);
        amountCell.setBackgroundColor(bgColor);
        amountCell.setPadding(8);
        amountCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(amountCell);
    }

    private void addPaymentInfo(Document document, Company company, SalesInvoice invoice) throws DocumentException {
        if (invoice.isPaid()) {
            // Show "PAID" stamp
            Paragraph paid = new Paragraph("PAID IN FULL",
                new Font(Font.HELVETICA, 14, Font.BOLD, SUCCESS_GREEN));
            paid.setAlignment(Element.ALIGN_CENTER);
            paid.setSpacingBefore(10);
            paid.setSpacingAfter(20);
            document.add(paid);
        } else {
            // Payment instructions
            PdfPTable table = new PdfPTable(1);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10);
            table.setSpacingAfter(20);

            PdfPCell cell = new PdfPCell();
            cell.setBackgroundColor(LIGHT_BLUE_BG);
            cell.setPadding(15);
            cell.setBorderColor(ACCENT_COLOR);

            cell.addElement(new Paragraph("Payment Information", HEADING_FONT));

            Paragraph terms = new Paragraph();
            terms.setSpacingBefore(10);
            terms.add(new Chunk("Payment Due: ", BOLD_FONT));
            terms.add(new Chunk(invoice.getDueDate().format(dateFormatter), NORMAL_FONT));
            cell.addElement(terms);

            // Bank details would typically come from company settings
            Paragraph bankInfo = new Paragraph();
            bankInfo.setSpacingBefore(5);
            bankInfo.add(new Chunk("Please quote invoice number ", SMALL_FONT));
            bankInfo.add(new Chunk(invoice.getInvoiceNumber(), SMALL_BOLD_FONT));
            bankInfo.add(new Chunk(" with your payment.", SMALL_FONT));
            cell.addElement(bankInfo);

            table.addCell(cell);
            document.add(table);
        }
    }

    private void addFooter(Document document, SalesInvoice invoice) throws DocumentException {
        // Notes section
        if (invoice.getNotes() != null && !invoice.getNotes().isBlank()) {
            Paragraph notesTitle = new Paragraph("Notes:", SMALL_BOLD_FONT);
            notesTitle.setSpacingBefore(10);
            document.add(notesTitle);

            Paragraph notes = new Paragraph(invoice.getNotes(), SMALL_FONT);
            document.add(notes);
        }

        // Footer line
        document.add(Chunk.NEWLINE);

        Paragraph footer = new Paragraph(
            "Generated on " + LocalDate.now().format(dateFormatter) +
            " • Thank you for your business",
            SMALL_FONT
        );
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(20);
        document.add(footer);
    }

    private void addTableHeader(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, TABLE_HEADER_FONT));
        cell.setBackgroundColor(PRIMARY_COLOR);
        cell.setPadding(8);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private void addTableCell(PdfPTable table, String text, Color bgColor, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, TABLE_CELL_FONT));
        cell.setBackgroundColor(bgColor);
        cell.setPadding(6);
        cell.setHorizontalAlignment(alignment);
        cell.setBorderColor(Color.LIGHT_GRAY);
        table.addCell(cell);
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return currencyFormat.format(BigDecimal.ZERO);
        return currencyFormat.format(amount);
    }

    private String formatQuantity(BigDecimal qty) {
        if (qty == null) return "1";
        // Remove trailing zeros
        return qty.stripTrailingZeros().toPlainString();
    }
}
