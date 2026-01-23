package com.example.application.service;

import com.example.application.domain.*;
import com.example.application.repository.ReceivableAllocationRepository;
import com.example.application.repository.SalesInvoiceRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for generating customer statements.
 * Supports both open-item statements (showing outstanding invoices with aging)
 * and balance-forward statements (showing activity over a period).
 */
@Service
@Transactional(readOnly = true)
public class StatementService {

    private static final Logger log = LoggerFactory.getLogger(StatementService.class);

    private final SalesInvoiceRepository invoiceRepository;
    private final ReceivableAllocationRepository allocationRepository;

    /**
     * Statement type options.
     */
    public enum StatementType {
        OPEN_ITEM,
        BALANCE_FORWARD
    }

    // Fonts
    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 20, Font.BOLD, new Color(52, 73, 94));
    private static final Font HEADING_FONT = new Font(Font.HELVETICA, 12, Font.BOLD);
    private static final Font NORMAL_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL);
    private static final Font BOLD_FONT = new Font(Font.HELVETICA, 10, Font.BOLD);
    private static final Font SMALL_FONT = new Font(Font.HELVETICA, 8, Font.NORMAL);
    private static final Font SMALL_BOLD_FONT = new Font(Font.HELVETICA, 8, Font.BOLD);
    private static final Font TABLE_HEADER_FONT = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
    private static final Font TABLE_CELL_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL);
    private static final Font LARGE_BOLD_FONT = new Font(Font.HELVETICA, 14, Font.BOLD);

    // Colors
    private static final Color PRIMARY_COLOR = new Color(52, 73, 94);
    private static final Color ACCENT_COLOR = new Color(41, 128, 185);
    private static final Color ALT_ROW_BG = new Color(245, 247, 249);
    private static final Color LIGHT_BLUE_BG = new Color(235, 245, 251);
    private static final Color WARNING_COLOR = new Color(231, 76, 60);
    private static final Color SUCCESS_COLOR = new Color(39, 174, 96);

    private final NumberFormat currencyFormat;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy");
    private final DateTimeFormatter shortDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public StatementService(SalesInvoiceRepository invoiceRepository,
                            ReceivableAllocationRepository allocationRepository) {
        this.invoiceRepository = invoiceRepository;
        this.allocationRepository = allocationRepository;
        this.currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "NZ"));
    }

    /**
     * DTO for statement data.
     */
    public record StatementData(
        Contact contact,
        Company company,
        LocalDate statementDate,
        List<StatementLine> lines,
        BigDecimal currentBalance,
        BigDecimal days30Balance,
        BigDecimal days60Balance,
        BigDecimal days90PlusBalance,
        BigDecimal totalBalance
    ) {
        public record StatementLine(
            SalesInvoice invoice,
            LocalDate date,
            String reference,
            String description,
            BigDecimal amount,
            BigDecimal balance,
            int daysOverdue
        ) {}
    }

    /**
     * DTO for balance-forward statement data (spec 09).
     * Shows activity over a period with opening/closing balances.
     */
    public record BalanceForwardStatementData(
        Contact contact,
        Company company,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal openingBalance,
        List<BalanceForwardLine> lines,
        BigDecimal totalDebits,
        BigDecimal totalCredits,
        BigDecimal closingBalance
    ) {
        /**
         * A line in a balance-forward statement.
         * Can represent an invoice (debit) or payment/credit note (credit).
         */
        public record BalanceForwardLine(
            LocalDate date,
            String reference,
            String description,
            BigDecimal debit,
            BigDecimal credit,
            BigDecimal runningBalance
        ) {}
    }

    /**
     * Generates statement data for a customer.
     *
     * @param company The company
     * @param contact The customer contact
     * @param asOfDate The date to generate the statement as of
     * @return Statement data
     */
    public StatementData generateStatementData(Company company, Contact contact, LocalDate asOfDate) {
        // Get all unpaid issued invoices for this customer (already filtered by balance > 0)
        List<SalesInvoice> unpaidInvoices = invoiceRepository.findOutstandingByCompanyAndContact(company, contact)
            .stream()
            .sorted(Comparator.comparing(SalesInvoice::getIssueDate))
            .toList();

        // Build statement lines
        List<StatementData.StatementLine> lines = new ArrayList<>();
        BigDecimal currentBalance = BigDecimal.ZERO;
        BigDecimal days30Balance = BigDecimal.ZERO;
        BigDecimal days60Balance = BigDecimal.ZERO;
        BigDecimal days90PlusBalance = BigDecimal.ZERO;

        for (SalesInvoice invoice : unpaidInvoices) {
            BigDecimal balance = invoice.getBalance();
            int daysOverdue = 0;

            if (invoice.getDueDate() != null && asOfDate.isAfter(invoice.getDueDate())) {
                daysOverdue = (int) ChronoUnit.DAYS.between(invoice.getDueDate(), asOfDate);
            }

            // Categorize by aging bucket
            if (daysOverdue <= 0) {
                currentBalance = currentBalance.add(balance);
            } else if (daysOverdue <= 30) {
                days30Balance = days30Balance.add(balance);
            } else if (daysOverdue <= 60) {
                days60Balance = days60Balance.add(balance);
            } else {
                days90PlusBalance = days90PlusBalance.add(balance);
            }

            lines.add(new StatementData.StatementLine(
                invoice,
                invoice.getIssueDate(),
                invoice.getInvoiceNumber(),
                "Invoice",
                invoice.getTotal(),
                balance,
                daysOverdue
            ));
        }

        BigDecimal totalBalance = currentBalance.add(days30Balance).add(days60Balance).add(days90PlusBalance);

        return new StatementData(
            contact,
            company,
            asOfDate,
            lines,
            currentBalance,
            days30Balance,
            days60Balance,
            days90PlusBalance,
            totalBalance
        );
    }

    /**
     * Generates a PDF statement for a customer.
     *
     * @param company The company
     * @param contact The customer contact
     * @param asOfDate The date to generate the statement as of
     * @return byte array containing the PDF
     */
    public byte[] generateStatementPdf(Company company, Contact contact, LocalDate asOfDate) {
        StatementData data = generateStatementData(company, contact, asOfDate);
        return generateStatementPdf(data);
    }

    /**
     * Generates a PDF from statement data.
     *
     * @param data The statement data
     * @return byte array containing the PDF
     */
    public byte[] generateStatementPdf(StatementData data) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 50, 50, 50, 50);
            PdfWriter.getInstance(document, baos);
            document.open();

            addStatementContent(document, data);

            document.close();

            log.info("Generated statement PDF for customer {}: {} bytes",
                data.contact().getCode(), baos.size());

            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Failed to generate statement PDF for customer {}", data.contact().getCode(), e);
            throw new RuntimeException("Failed to generate statement PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Generates balance-forward statement data for a customer (spec 09).
     * Shows activity over a period with opening/closing balances and running total.
     *
     * @param company The company
     * @param contact The customer contact
     * @param periodStart Start date of the period
     * @param periodEnd End date of the period
     * @return Balance-forward statement data
     */
    public BalanceForwardStatementData generateBalanceForwardStatementData(
            Company company, Contact contact, LocalDate periodStart, LocalDate periodEnd) {

        // Calculate opening balance (unpaid invoices as of period start)
        BigDecimal openingBalance = invoiceRepository.sumOutstandingByContactAsOfDate(company, contact, periodStart);
        if (openingBalance == null) {
            openingBalance = BigDecimal.ZERO;
        }

        // Get all invoices/credit notes issued in the period
        List<SalesInvoice> periodInvoices = invoiceRepository.findByCompanyAndContactAndDateRange(
            company, contact, periodStart, periodEnd);

        // Get all payment allocations made in the period
        Instant startInstant = periodStart.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endInstant = periodEnd.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        List<ReceivableAllocation> periodAllocations = allocationRepository.findByContactAndDateRange(
            company, contact, startInstant, endInstant);

        // Build combined list of activity sorted by date
        List<ActivityItem> activities = new ArrayList<>();

        // Add invoices as debits (positive amounts for regular invoices)
        for (SalesInvoice invoice : periodInvoices) {
            boolean isCreditNote = invoice.getType() == SalesInvoice.InvoiceType.CREDIT_NOTE;
            activities.add(new ActivityItem(
                invoice.getIssueDate(),
                invoice.getInvoiceNumber(),
                isCreditNote ? "Credit Note" : "Invoice",
                isCreditNote ? BigDecimal.ZERO : invoice.getTotal(),
                isCreditNote ? invoice.getTotal() : BigDecimal.ZERO
            ));
        }

        // Add payments as credits
        for (ReceivableAllocation allocation : periodAllocations) {
            LocalDate allocationDate = allocation.getAllocatedAt()
                .atZone(ZoneId.systemDefault()).toLocalDate();
            activities.add(new ActivityItem(
                allocationDate,
                allocation.getReceiptTransaction().getReference() != null
                    ? allocation.getReceiptTransaction().getReference()
                    : "Payment",
                "Payment - " + allocation.getSalesInvoice().getInvoiceNumber(),
                BigDecimal.ZERO,
                allocation.getAmount()
            ));
        }

        // Sort by date
        activities.sort(Comparator.comparing(ActivityItem::date)
            .thenComparing(a -> a.debit.compareTo(BigDecimal.ZERO) > 0 ? 0 : 1));

        // Build statement lines with running balance
        List<BalanceForwardStatementData.BalanceForwardLine> lines = new ArrayList<>();
        BigDecimal runningBalance = openingBalance;
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;

        for (ActivityItem activity : activities) {
            runningBalance = runningBalance.add(activity.debit).subtract(activity.credit);
            totalDebits = totalDebits.add(activity.debit);
            totalCredits = totalCredits.add(activity.credit);

            lines.add(new BalanceForwardStatementData.BalanceForwardLine(
                activity.date,
                activity.reference,
                activity.description,
                activity.debit.compareTo(BigDecimal.ZERO) > 0 ? activity.debit : null,
                activity.credit.compareTo(BigDecimal.ZERO) > 0 ? activity.credit : null,
                runningBalance
            ));
        }

        BigDecimal closingBalance = openingBalance.add(totalDebits).subtract(totalCredits);

        return new BalanceForwardStatementData(
            contact,
            company,
            periodStart,
            periodEnd,
            openingBalance,
            lines,
            totalDebits,
            totalCredits,
            closingBalance
        );
    }

    // Helper record for sorting activity
    private record ActivityItem(
        LocalDate date,
        String reference,
        String description,
        BigDecimal debit,
        BigDecimal credit
    ) {}

    /**
     * Generates a balance-forward PDF statement for a customer.
     *
     * @param company The company
     * @param contact The customer contact
     * @param periodStart Start date of the period
     * @param periodEnd End date of the period
     * @return byte array containing the PDF
     */
    public byte[] generateBalanceForwardStatementPdf(Company company, Contact contact,
                                                      LocalDate periodStart, LocalDate periodEnd) {
        BalanceForwardStatementData data = generateBalanceForwardStatementData(
            company, contact, periodStart, periodEnd);
        return generateBalanceForwardStatementPdf(data);
    }

    /**
     * Generates a PDF from balance-forward statement data.
     *
     * @param data The balance-forward statement data
     * @return byte array containing the PDF
     */
    public byte[] generateBalanceForwardStatementPdf(BalanceForwardStatementData data) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 50, 50, 50, 50);
            PdfWriter.getInstance(document, baos);
            document.open();

            addBalanceForwardContent(document, data);

            document.close();

            log.info("Generated balance-forward statement PDF for customer {}: {} bytes",
                data.contact().getCode(), baos.size());

            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Failed to generate balance-forward statement PDF for customer {}",
                data.contact().getCode(), e);
            throw new RuntimeException("Failed to generate balance-forward statement PDF: " + e.getMessage(), e);
        }
    }

    private void addBalanceForwardContent(Document document, BalanceForwardStatementData data)
            throws DocumentException {
        // Header
        addBalanceForwardHeader(document, data);

        // Customer info
        addBalanceForwardCustomerInfo(document, data);

        // Opening balance
        addOpeningBalance(document, data);

        // Transaction list
        addBalanceForwardTransactionList(document, data);

        // Closing balance and footer
        addBalanceForwardFooter(document, data);
    }

    private void addBalanceForwardHeader(Document document, BalanceForwardStatementData data)
            throws DocumentException {
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{60, 40});
        headerTable.setSpacingAfter(20);

        // Company info
        PdfPCell companyCell = new PdfPCell();
        companyCell.setBorder(Rectangle.NO_BORDER);

        Paragraph companyName = new Paragraph(data.company().getName(), LARGE_BOLD_FONT);
        companyCell.addElement(companyName);

        Paragraph statementType = new Paragraph("BALANCE FORWARD STATEMENT", SMALL_BOLD_FONT);
        statementType.setSpacingBefore(5);
        companyCell.addElement(statementType);

        headerTable.addCell(companyCell);

        // Statement title
        PdfPCell titleCell = new PdfPCell();
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Paragraph title = new Paragraph("STATEMENT", TITLE_FONT);
        title.setAlignment(Element.ALIGN_RIGHT);
        titleCell.addElement(title);

        Paragraph dateRange = new Paragraph(
            data.periodStart().format(shortDateFormatter) + " to " +
            data.periodEnd().format(shortDateFormatter), NORMAL_FONT);
        dateRange.setAlignment(Element.ALIGN_RIGHT);
        titleCell.addElement(dateRange);

        headerTable.addCell(titleCell);

        document.add(headerTable);
    }

    private void addBalanceForwardCustomerInfo(Document document, BalanceForwardStatementData data)
            throws DocumentException {
        Contact customer = data.contact();

        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(50);
        table.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.setSpacingAfter(20);

        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(LIGHT_BLUE_BG);
        cell.setPadding(10);
        cell.setBorderColor(ACCENT_COLOR);

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

        table.addCell(cell);
        document.add(table);
    }

    private void addOpeningBalance(Document document, BalanceForwardStatementData data)
            throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{70, 30});
        table.setSpacingAfter(10);

        PdfPCell labelCell = new PdfPCell(new Phrase("Opening Balance as at " +
            data.periodStart().format(shortDateFormatter), BOLD_FONT));
        labelCell.setBackgroundColor(ALT_ROW_BG);
        labelCell.setPadding(8);
        labelCell.setBorder(Rectangle.BOX);
        labelCell.setBorderColor(Color.LIGHT_GRAY);
        table.addCell(labelCell);

        PdfPCell amountCell = new PdfPCell(new Phrase(formatCurrency(data.openingBalance()), BOLD_FONT));
        amountCell.setBackgroundColor(ALT_ROW_BG);
        amountCell.setPadding(8);
        amountCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        amountCell.setBorder(Rectangle.BOX);
        amountCell.setBorderColor(Color.LIGHT_GRAY);
        table.addCell(amountCell);

        document.add(table);
    }

    private void addBalanceForwardTransactionList(Document document, BalanceForwardStatementData data)
            throws DocumentException {
        Paragraph heading = new Paragraph("Activity", HEADING_FONT);
        heading.setSpacingAfter(10);
        document.add(heading);

        if (data.lines().isEmpty()) {
            Paragraph noActivity = new Paragraph("No activity during this period", NORMAL_FONT);
            noActivity.setSpacingAfter(20);
            document.add(noActivity);
            return;
        }

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{15, 20, 25, 20, 20});
        table.setSpacingAfter(10);

        // Headers
        addTableHeader(table, "Date");
        addTableHeader(table, "Reference");
        addTableHeader(table, "Description");
        addTableHeader(table, "Debit");
        addTableHeader(table, "Credit");

        // Rows
        boolean alternate = false;
        for (BalanceForwardStatementData.BalanceForwardLine line : data.lines()) {
            Color bgColor = alternate ? ALT_ROW_BG : Color.WHITE;
            alternate = !alternate;

            addTableCell(table, line.date().format(shortDateFormatter), bgColor, Element.ALIGN_CENTER);
            addTableCell(table, line.reference(), bgColor, Element.ALIGN_LEFT);
            addTableCell(table, line.description(), bgColor, Element.ALIGN_LEFT);
            addTableCell(table, line.debit() != null ? formatCurrency(line.debit()) : "", bgColor, Element.ALIGN_RIGHT);
            addTableCell(table, line.credit() != null ? formatCurrency(line.credit()) : "", bgColor, Element.ALIGN_RIGHT);
        }

        // Totals row
        PdfPCell totalLabelCell = new PdfPCell(new Phrase("Totals", BOLD_FONT));
        totalLabelCell.setColspan(3);
        totalLabelCell.setBackgroundColor(LIGHT_BLUE_BG);
        totalLabelCell.setPadding(6);
        totalLabelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalLabelCell.setBorderColor(Color.LIGHT_GRAY);
        table.addCell(totalLabelCell);

        PdfPCell totalDebitCell = new PdfPCell(new Phrase(formatCurrency(data.totalDebits()), BOLD_FONT));
        totalDebitCell.setBackgroundColor(LIGHT_BLUE_BG);
        totalDebitCell.setPadding(6);
        totalDebitCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalDebitCell.setBorderColor(Color.LIGHT_GRAY);
        table.addCell(totalDebitCell);

        PdfPCell totalCreditCell = new PdfPCell(new Phrase(formatCurrency(data.totalCredits()), BOLD_FONT));
        totalCreditCell.setBackgroundColor(LIGHT_BLUE_BG);
        totalCreditCell.setPadding(6);
        totalCreditCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalCreditCell.setBorderColor(Color.LIGHT_GRAY);
        table.addCell(totalCreditCell);

        document.add(table);
    }

    private void addBalanceForwardFooter(Document document, BalanceForwardStatementData data)
            throws DocumentException {
        // Closing balance
        PdfPTable closingTable = new PdfPTable(2);
        closingTable.setWidthPercentage(100);
        closingTable.setWidths(new float[]{70, 30});
        closingTable.setSpacingBefore(10);
        closingTable.setSpacingAfter(20);

        PdfPCell labelCell = new PdfPCell(new Phrase("Closing Balance as at " +
            data.periodEnd().format(shortDateFormatter), BOLD_FONT));
        labelCell.setBackgroundColor(LIGHT_BLUE_BG);
        labelCell.setPadding(10);
        labelCell.setBorder(Rectangle.BOX);
        labelCell.setBorderColor(ACCENT_COLOR);
        closingTable.addCell(labelCell);

        Font balanceFont = data.closingBalance().compareTo(BigDecimal.ZERO) > 0
            ? new Font(Font.HELVETICA, 12, Font.BOLD, ACCENT_COLOR)
            : new Font(Font.HELVETICA, 12, Font.BOLD, SUCCESS_COLOR);
        PdfPCell amountCell = new PdfPCell(new Phrase(formatCurrency(data.closingBalance()), balanceFont));
        amountCell.setBackgroundColor(LIGHT_BLUE_BG);
        amountCell.setPadding(10);
        amountCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        amountCell.setBorder(Rectangle.BOX);
        amountCell.setBorderColor(ACCENT_COLOR);
        closingTable.addCell(amountCell);

        document.add(closingTable);

        // Payment instructions
        PdfPTable paymentTable = new PdfPTable(1);
        paymentTable.setWidthPercentage(100);
        paymentTable.setSpacingBefore(10);

        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(LIGHT_BLUE_BG);
        cell.setPadding(15);
        cell.setBorderColor(ACCENT_COLOR);

        Paragraph paymentTitle = new Paragraph("Payment Information", HEADING_FONT);
        cell.addElement(paymentTitle);

        Paragraph instructions = new Paragraph();
        instructions.setSpacingBefore(10);
        instructions.add(new Chunk("Please quote your account number ", SMALL_FONT));
        instructions.add(new Chunk(data.contact().getCode(), SMALL_BOLD_FONT));
        instructions.add(new Chunk(" when making payment.", SMALL_FONT));
        cell.addElement(instructions);

        if (data.closingBalance().compareTo(BigDecimal.ZERO) > 0) {
            Paragraph totalDue = new Paragraph();
            totalDue.setSpacingBefore(10);
            totalDue.add(new Chunk("Amount Due: ", BOLD_FONT));
            totalDue.add(new Chunk(formatCurrency(data.closingBalance()),
                new Font(Font.HELVETICA, 12, Font.BOLD, ACCENT_COLOR)));
            cell.addElement(totalDue);
        }

        paymentTable.addCell(cell);
        document.add(paymentTable);

        // Footer text
        Paragraph footer = new Paragraph(
            "This statement was generated on " + LocalDate.now().format(dateFormatter) + ". " +
            "If you have any questions about this statement, please contact us.",
            SMALL_FONT
        );
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(20);
        document.add(footer);
    }

    private void addStatementContent(Document document, StatementData data) throws DocumentException {
        // Header
        addHeader(document, data);

        // Customer info
        addCustomerInfo(document, data);

        // Aging summary
        addAgingSummary(document, data);

        // Transaction list
        addTransactionList(document, data);

        // Footer
        addFooter(document, data);
    }

    private void addHeader(Document document, StatementData data) throws DocumentException {
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{60, 40});
        headerTable.setSpacingAfter(20);

        // Company info
        PdfPCell companyCell = new PdfPCell();
        companyCell.setBorder(Rectangle.NO_BORDER);

        Paragraph companyName = new Paragraph(data.company().getName(), LARGE_BOLD_FONT);
        companyCell.addElement(companyName);

        Paragraph statementType = new Paragraph("STATEMENT OF ACCOUNT", SMALL_BOLD_FONT);
        statementType.setSpacingBefore(5);
        companyCell.addElement(statementType);

        headerTable.addCell(companyCell);

        // Statement title
        PdfPCell titleCell = new PdfPCell();
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Paragraph title = new Paragraph("STATEMENT", TITLE_FONT);
        title.setAlignment(Element.ALIGN_RIGHT);
        titleCell.addElement(title);

        Paragraph dateInfo = new Paragraph("As at " + data.statementDate().format(dateFormatter), NORMAL_FONT);
        dateInfo.setAlignment(Element.ALIGN_RIGHT);
        titleCell.addElement(dateInfo);

        headerTable.addCell(titleCell);

        document.add(headerTable);
    }

    private void addCustomerInfo(Document document, StatementData data) throws DocumentException {
        Contact customer = data.contact();

        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(50);
        table.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.setSpacingAfter(20);

        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(LIGHT_BLUE_BG);
        cell.setPadding(10);
        cell.setBorderColor(ACCENT_COLOR);

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

        table.addCell(cell);
        document.add(table);
    }

    private void addAgingSummary(Document document, StatementData data) throws DocumentException {
        Paragraph heading = new Paragraph("Aging Summary", HEADING_FONT);
        heading.setSpacingAfter(10);
        document.add(heading);

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{20, 20, 20, 20, 20});
        table.setSpacingAfter(20);

        // Headers
        addAgingHeader(table, "Current");
        addAgingHeader(table, "1-30 Days");
        addAgingHeader(table, "31-60 Days");
        addAgingHeader(table, "61-90+ Days");
        addAgingHeader(table, "Total Due");

        // Values
        addAgingValue(table, data.currentBalance(), false);
        addAgingValue(table, data.days30Balance(), data.days30Balance().compareTo(BigDecimal.ZERO) > 0);
        addAgingValue(table, data.days60Balance(), data.days60Balance().compareTo(BigDecimal.ZERO) > 0);
        addAgingValue(table, data.days90PlusBalance(), data.days90PlusBalance().compareTo(BigDecimal.ZERO) > 0);

        // Total with emphasis
        PdfPCell totalCell = new PdfPCell(new Phrase(formatCurrency(data.totalBalance()),
            new Font(Font.HELVETICA, 11, Font.BOLD)));
        totalCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        totalCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        totalCell.setBackgroundColor(LIGHT_BLUE_BG);
        totalCell.setPadding(10);
        table.addCell(totalCell);

        document.add(table);
    }

    private void addAgingHeader(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, TABLE_HEADER_FONT));
        cell.setBackgroundColor(PRIMARY_COLOR);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(8);
        table.addCell(cell);
    }

    private void addAgingValue(PdfPTable table, BigDecimal amount, boolean warning) {
        Font font = warning
            ? new Font(Font.HELVETICA, 10, Font.BOLD, WARNING_COLOR)
            : NORMAL_FONT;
        PdfPCell cell = new PdfPCell(new Phrase(formatCurrency(amount), font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(10);
        table.addCell(cell);
    }

    private void addTransactionList(Document document, StatementData data) throws DocumentException {
        Paragraph heading = new Paragraph("Outstanding Invoices", HEADING_FONT);
        heading.setSpacingAfter(10);
        document.add(heading);

        if (data.lines().isEmpty()) {
            Paragraph noInvoices = new Paragraph("No outstanding invoices", NORMAL_FONT);
            noInvoices.setSpacingAfter(20);
            document.add(noInvoices);
            return;
        }

        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{15, 15, 25, 15, 15, 15});
        table.setSpacingAfter(20);

        // Headers
        addTableHeader(table, "Date");
        addTableHeader(table, "Invoice #");
        addTableHeader(table, "Description");
        addTableHeader(table, "Amount");
        addTableHeader(table, "Balance");
        addTableHeader(table, "Overdue");

        // Rows
        boolean alternate = false;
        for (StatementData.StatementLine line : data.lines()) {
            Color bgColor = alternate ? ALT_ROW_BG : Color.WHITE;
            alternate = !alternate;

            addTableCell(table, line.date().format(shortDateFormatter), bgColor, Element.ALIGN_CENTER);
            addTableCell(table, line.reference(), bgColor, Element.ALIGN_LEFT);
            addTableCell(table, line.description(), bgColor, Element.ALIGN_LEFT);
            addTableCell(table, formatCurrency(line.amount()), bgColor, Element.ALIGN_RIGHT);
            addTableCell(table, formatCurrency(line.balance()), bgColor, Element.ALIGN_RIGHT);

            // Overdue days with color coding
            String overdueText = line.daysOverdue() > 0 ? line.daysOverdue() + " days" : "Current";
            Font overdueFont = line.daysOverdue() > 30
                ? new Font(Font.HELVETICA, 9, Font.BOLD, WARNING_COLOR)
                : TABLE_CELL_FONT;
            PdfPCell overdueCell = new PdfPCell(new Phrase(overdueText, overdueFont));
            overdueCell.setBackgroundColor(bgColor);
            overdueCell.setPadding(6);
            overdueCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            overdueCell.setBorderColor(Color.LIGHT_GRAY);
            table.addCell(overdueCell);
        }

        document.add(table);
    }

    private void addFooter(Document document, StatementData data) throws DocumentException {
        document.add(Chunk.NEWLINE);

        // Payment instructions
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingBefore(20);

        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(LIGHT_BLUE_BG);
        cell.setPadding(15);
        cell.setBorderColor(ACCENT_COLOR);

        Paragraph paymentTitle = new Paragraph("Payment Information", HEADING_FONT);
        cell.addElement(paymentTitle);

        Paragraph instructions = new Paragraph();
        instructions.setSpacingBefore(10);
        instructions.add(new Chunk("Please quote your account number ", SMALL_FONT));
        instructions.add(new Chunk(data.contact().getCode(), SMALL_BOLD_FONT));
        instructions.add(new Chunk(" when making payment.", SMALL_FONT));
        cell.addElement(instructions);

        if (data.totalBalance().compareTo(BigDecimal.ZERO) > 0) {
            Paragraph totalDue = new Paragraph();
            totalDue.setSpacingBefore(10);
            totalDue.add(new Chunk("Total Amount Due: ", BOLD_FONT));
            totalDue.add(new Chunk(formatCurrency(data.totalBalance()),
                new Font(Font.HELVETICA, 12, Font.BOLD, ACCENT_COLOR)));
            cell.addElement(totalDue);
        }

        table.addCell(cell);
        document.add(table);

        // Footer text
        Paragraph footer = new Paragraph(
            "This statement was generated on " + LocalDate.now().format(dateFormatter) + ". " +
            "If you have any questions about this statement, please contact us.",
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
}
