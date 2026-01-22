package com.example.application.service;

import com.example.application.domain.Company;
import com.example.application.service.ReportingService.*;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Service for exporting financial reports to PDF and Excel formats.
 * Supports Trial Balance, Profit & Loss, Balance Sheet, and Budget vs Actual reports.
 */
@Service
public class ReportExportService {

    private static final Logger log = LoggerFactory.getLogger(ReportExportService.class);

    // PDF Fonts (using com.lowagie.text.Font explicitly)
    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 16, Font.BOLD);
    private static final Font SUBTITLE_FONT = new Font(Font.HELVETICA, 11, Font.NORMAL, Color.DARK_GRAY);
    private static final Font SECTION_FONT = new Font(Font.HELVETICA, 12, Font.BOLD);
    private static final Font TABLE_HEADER_FONT = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
    private static final Font TABLE_CELL_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL);
    private static final Font TABLE_CELL_BOLD = new Font(Font.HELVETICA, 9, Font.BOLD);
    private static final Font TOTAL_FONT = new Font(Font.HELVETICA, 10, Font.BOLD);
    private static final Font SMALL_FONT = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.GRAY);

    // PDF Colors
    private static final Color HEADER_BG = new Color(52, 73, 94);
    private static final Color ALT_ROW_BG = new Color(245, 247, 249);
    private static final Color TOTAL_BG = new Color(230, 230, 230);
    private static final Color PROFIT_COLOR = new Color(34, 139, 34);
    private static final Color LOSS_COLOR = new Color(178, 34, 34);

    private final NumberFormat currencyFormat;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy");

    public ReportExportService() {
        this.currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "NZ"));
    }

    // ==================== TRIAL BALANCE EXPORTS ====================

    /**
     * Exports Trial Balance report to PDF format.
     */
    public byte[] exportTrialBalanceToPdf(TrialBalance report, Company company) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
            document.open();

            // Title
            addReportTitle(document, company, "Trial Balance");
            addReportSubtitle(document, "Period: " + report.startDate().format(dateFormatter) +
                " to " + report.endDate().format(dateFormatter));

            // Balance status
            String balanceStatus = report.isBalanced() ? "BALANCED" : "OUT OF BALANCE";
            Paragraph status = new Paragraph(balanceStatus,
                new Font(Font.HELVETICA, 10, Font.BOLD,
                    report.isBalanced() ? PROFIT_COLOR : LOSS_COLOR));
            status.setAlignment(Element.ALIGN_CENTER);
            status.setSpacingAfter(15);
            document.add(status);

            // Table
            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1.5f, 4f, 2f, 2f});

            addTableHeader(table, "Code");
            addTableHeader(table, "Account");
            addTableHeader(table, "Debits");
            addTableHeader(table, "Credits");

            boolean alternate = false;
            for (TrialBalanceLine line : report.lines()) {
                Color bg = alternate ? ALT_ROW_BG : Color.WHITE;
                addTableCell(table, line.account().getCode(), bg, Element.ALIGN_LEFT);
                addTableCell(table, line.account().getName(), bg, Element.ALIGN_LEFT);
                addTableCell(table, formatCurrency(line.debits()), bg, Element.ALIGN_RIGHT);
                addTableCell(table, formatCurrency(line.credits()), bg, Element.ALIGN_RIGHT);
                alternate = !alternate;
            }

            // Totals row
            addTotalCell(table, "");
            addTotalCell(table, "Totals");
            addTotalCell(table, formatCurrency(report.totalDebits()));
            addTotalCell(table, formatCurrency(report.totalCredits()));

            document.add(table);
            addReportFooter(document);
            document.close();

            log.info("Generated Trial Balance PDF ({} bytes)", baos.size());
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Failed to generate Trial Balance PDF", e);
            throw new RuntimeException("Failed to generate Trial Balance PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Exports Trial Balance report to Excel format.
     */
    public byte[] exportTrialBalanceToExcel(TrialBalance report, Company company) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Trial Balance");
            int rowNum = 0;

            // Styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle totalStyle = createTotalStyle(workbook);

            // Title
            org.apache.poi.ss.usermodel.Row titleRow = sheet.createRow(rowNum++);
            org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(company.getName() + " - Trial Balance");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));

            // Date range
            org.apache.poi.ss.usermodel.Row dateRow = sheet.createRow(rowNum++);
            dateRow.createCell(0).setCellValue("Period: " + report.startDate().format(dateFormatter) +
                " to " + report.endDate().format(dateFormatter));
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 3));

            // Balance status
            org.apache.poi.ss.usermodel.Row statusRow = sheet.createRow(rowNum++);
            statusRow.createCell(0).setCellValue(report.isBalanced() ? "BALANCED" : "OUT OF BALANCE");

            rowNum++; // Empty row

            // Header row
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"Code", "Account", "Debits", "Credits"};
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            for (TrialBalanceLine line : report.lines()) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(line.account().getCode());
                row.createCell(1).setCellValue(line.account().getName());

                org.apache.poi.ss.usermodel.Cell debitCell = row.createCell(2);
                debitCell.setCellValue(line.debits().doubleValue());
                debitCell.setCellStyle(currencyStyle);

                org.apache.poi.ss.usermodel.Cell creditCell = row.createCell(3);
                creditCell.setCellValue(line.credits().doubleValue());
                creditCell.setCellStyle(currencyStyle);
            }

            // Totals row
            org.apache.poi.ss.usermodel.Row totalsRow = sheet.createRow(rowNum);
            totalsRow.createCell(0);
            org.apache.poi.ss.usermodel.Cell totalLabel = totalsRow.createCell(1);
            totalLabel.setCellValue("Totals");
            totalLabel.setCellStyle(totalStyle);

            org.apache.poi.ss.usermodel.Cell totalDebits = totalsRow.createCell(2);
            totalDebits.setCellValue(report.totalDebits().doubleValue());
            totalDebits.setCellStyle(totalStyle);

            org.apache.poi.ss.usermodel.Cell totalCredits = totalsRow.createCell(3);
            totalCredits.setCellValue(report.totalCredits().doubleValue());
            totalCredits.setCellStyle(totalStyle);

            // Auto-size columns
            for (int i = 0; i < 4; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(baos);
            log.info("Generated Trial Balance Excel ({} bytes)", baos.size());
            return baos.toByteArray();

        } catch (IOException e) {
            log.error("Failed to generate Trial Balance Excel", e);
            throw new RuntimeException("Failed to generate Trial Balance Excel: " + e.getMessage(), e);
        }
    }

    // ==================== PROFIT & LOSS EXPORTS ====================

    /**
     * Exports Profit & Loss report to PDF format.
     */
    public byte[] exportProfitAndLossToPdf(ProfitAndLoss report, Company company) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
            document.open();

            // Title
            addReportTitle(document, company, "Profit & Loss Statement");
            String subtitle = "Period: " + report.startDate().format(dateFormatter) +
                " to " + report.endDate().format(dateFormatter);
            if (report.department() != null) {
                subtitle += " | Department: " + report.department().getCode() +
                    " - " + report.department().getName();
            }
            addReportSubtitle(document, subtitle);

            // Income Section
            addSectionHeader(document, "Income");
            if (!report.incomeLines().isEmpty()) {
                PdfPTable incomeTable = createPLTable();
                for (ProfitAndLossLine line : report.incomeLines()) {
                    addPLTableRow(incomeTable, line, false);
                }
                addPLTotalRow(incomeTable, "Total Income", report.totalIncome());
                document.add(incomeTable);
            }

            // Expenses Section
            addSectionHeader(document, "Expenses");
            if (!report.expenseLines().isEmpty()) {
                PdfPTable expenseTable = createPLTable();
                for (ProfitAndLossLine line : report.expenseLines()) {
                    addPLTableRow(expenseTable, line, false);
                }
                addPLTotalRow(expenseTable, "Total Expenses", report.totalExpenses());
                document.add(expenseTable);
            }

            // Net Profit/Loss
            document.add(Chunk.NEWLINE);
            PdfPTable netTable = new PdfPTable(2);
            netTable.setWidthPercentage(100);
            netTable.setWidths(new float[]{4f, 2f});

            String label = report.netProfit().compareTo(BigDecimal.ZERO) >= 0 ? "Net Profit" : "Net Loss";
            Color amountColor = report.netProfit().compareTo(BigDecimal.ZERO) >= 0 ? PROFIT_COLOR : LOSS_COLOR;

            PdfPCell labelCell = new PdfPCell(new Phrase(label, TOTAL_FONT));
            labelCell.setBorder(Rectangle.TOP);
            labelCell.setBorderWidth(2);
            labelCell.setPadding(10);
            labelCell.setBackgroundColor(TOTAL_BG);
            netTable.addCell(labelCell);

            PdfPCell amountCell = new PdfPCell(new Phrase(formatCurrency(report.netProfit().abs()),
                new Font(Font.HELVETICA, 12, Font.BOLD, amountColor)));
            amountCell.setBorder(Rectangle.TOP);
            amountCell.setBorderWidth(2);
            amountCell.setPadding(10);
            amountCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            amountCell.setBackgroundColor(TOTAL_BG);
            netTable.addCell(amountCell);

            document.add(netTable);
            addReportFooter(document);
            document.close();

            log.info("Generated P&L PDF ({} bytes)", baos.size());
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Failed to generate P&L PDF", e);
            throw new RuntimeException("Failed to generate P&L PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Exports Profit & Loss report to Excel format.
     */
    public byte[] exportProfitAndLossToExcel(ProfitAndLoss report, Company company) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Profit & Loss");
            int rowNum = 0;

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle totalStyle = createTotalStyle(workbook);
            CellStyle sectionStyle = createSectionStyle(workbook);

            // Title
            org.apache.poi.ss.usermodel.Row titleRow = sheet.createRow(rowNum++);
            org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(company.getName() + " - Profit & Loss Statement");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 2));

            // Date range
            org.apache.poi.ss.usermodel.Row dateRow = sheet.createRow(rowNum++);
            String dateText = "Period: " + report.startDate().format(dateFormatter) +
                " to " + report.endDate().format(dateFormatter);
            if (report.department() != null) {
                dateText += " | Department: " + report.department().getCode();
            }
            dateRow.createCell(0).setCellValue(dateText);

            rowNum++; // Empty row

            // Income Section
            org.apache.poi.ss.usermodel.Row incomeHeader = sheet.createRow(rowNum++);
            org.apache.poi.ss.usermodel.Cell incomeCell = incomeHeader.createCell(0);
            incomeCell.setCellValue("Income");
            incomeCell.setCellStyle(sectionStyle);

            // Income header row
            org.apache.poi.ss.usermodel.Row incomeColHeaders = sheet.createRow(rowNum++);
            incomeColHeaders.createCell(0).setCellValue("Code");
            incomeColHeaders.createCell(1).setCellValue("Account");
            incomeColHeaders.createCell(2).setCellValue("Amount");
            for (int i = 0; i < 3; i++) {
                incomeColHeaders.getCell(i).setCellStyle(headerStyle);
            }

            // Income data
            for (ProfitAndLossLine line : report.incomeLines()) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(line.account().getCode());
                row.createCell(1).setCellValue(line.account().getName());
                org.apache.poi.ss.usermodel.Cell amtCell = row.createCell(2);
                amtCell.setCellValue(line.amount().doubleValue());
                amtCell.setCellStyle(currencyStyle);
            }

            // Total Income
            org.apache.poi.ss.usermodel.Row totalIncomeRow = sheet.createRow(rowNum++);
            totalIncomeRow.createCell(1).setCellValue("Total Income");
            totalIncomeRow.getCell(1).setCellStyle(totalStyle);
            org.apache.poi.ss.usermodel.Cell totalIncomeCell = totalIncomeRow.createCell(2);
            totalIncomeCell.setCellValue(report.totalIncome().doubleValue());
            totalIncomeCell.setCellStyle(totalStyle);

            rowNum++; // Empty row

            // Expenses Section
            org.apache.poi.ss.usermodel.Row expenseHeader = sheet.createRow(rowNum++);
            org.apache.poi.ss.usermodel.Cell expenseCell = expenseHeader.createCell(0);
            expenseCell.setCellValue("Expenses");
            expenseCell.setCellStyle(sectionStyle);

            // Expense header row
            org.apache.poi.ss.usermodel.Row expenseColHeaders = sheet.createRow(rowNum++);
            expenseColHeaders.createCell(0).setCellValue("Code");
            expenseColHeaders.createCell(1).setCellValue("Account");
            expenseColHeaders.createCell(2).setCellValue("Amount");
            for (int i = 0; i < 3; i++) {
                expenseColHeaders.getCell(i).setCellStyle(headerStyle);
            }

            // Expense data
            for (ProfitAndLossLine line : report.expenseLines()) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(line.account().getCode());
                row.createCell(1).setCellValue(line.account().getName());
                org.apache.poi.ss.usermodel.Cell amtCell = row.createCell(2);
                amtCell.setCellValue(line.amount().doubleValue());
                amtCell.setCellStyle(currencyStyle);
            }

            // Total Expenses
            org.apache.poi.ss.usermodel.Row totalExpenseRow = sheet.createRow(rowNum++);
            totalExpenseRow.createCell(1).setCellValue("Total Expenses");
            totalExpenseRow.getCell(1).setCellStyle(totalStyle);
            org.apache.poi.ss.usermodel.Cell totalExpenseCell = totalExpenseRow.createCell(2);
            totalExpenseCell.setCellValue(report.totalExpenses().doubleValue());
            totalExpenseCell.setCellStyle(totalStyle);

            rowNum++; // Empty row

            // Net Profit/Loss
            org.apache.poi.ss.usermodel.Row netRow = sheet.createRow(rowNum);
            String label = report.netProfit().compareTo(BigDecimal.ZERO) >= 0 ? "Net Profit" : "Net Loss";
            netRow.createCell(1).setCellValue(label);
            netRow.getCell(1).setCellStyle(totalStyle);
            org.apache.poi.ss.usermodel.Cell netCell = netRow.createCell(2);
            netCell.setCellValue(report.netProfit().doubleValue());
            netCell.setCellStyle(totalStyle);

            // Auto-size columns
            for (int i = 0; i < 3; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(baos);
            log.info("Generated P&L Excel ({} bytes)", baos.size());
            return baos.toByteArray();

        } catch (IOException e) {
            log.error("Failed to generate P&L Excel", e);
            throw new RuntimeException("Failed to generate P&L Excel: " + e.getMessage(), e);
        }
    }

    // ==================== BALANCE SHEET EXPORTS ====================

    /**
     * Exports Balance Sheet report to PDF format.
     */
    public byte[] exportBalanceSheetToPdf(BalanceSheet report, Company company) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
            document.open();

            // Title
            addReportTitle(document, company, "Balance Sheet");
            addReportSubtitle(document, "As of " + report.asOfDate().format(dateFormatter));

            // Balance status
            String balanceStatus = report.isBalanced() ? "BALANCED" : "OUT OF BALANCE";
            Paragraph status = new Paragraph(balanceStatus,
                new Font(Font.HELVETICA, 10, Font.BOLD,
                    report.isBalanced() ? PROFIT_COLOR : LOSS_COLOR));
            status.setAlignment(Element.ALIGN_CENTER);
            status.setSpacingAfter(15);
            document.add(status);

            // Assets Section
            addSectionHeader(document, "Assets");
            if (!report.assets().isEmpty()) {
                PdfPTable assetsTable = createBSTable();
                for (BalanceSheetLine line : report.assets()) {
                    addBSTableRow(assetsTable, line);
                }
                addBSTotalRow(assetsTable, "Total Assets", report.totalAssets());
                document.add(assetsTable);
            }

            // Liabilities Section
            addSectionHeader(document, "Liabilities");
            if (!report.liabilities().isEmpty()) {
                PdfPTable liabTable = createBSTable();
                for (BalanceSheetLine line : report.liabilities()) {
                    addBSTableRow(liabTable, line);
                }
                addBSTotalRow(liabTable, "Total Liabilities", report.totalLiabilities());
                document.add(liabTable);
            }

            // Equity Section
            addSectionHeader(document, "Equity");
            if (!report.equity().isEmpty()) {
                PdfPTable equityTable = createBSTable();
                for (BalanceSheetLine line : report.equity()) {
                    addBSTableRow(equityTable, line);
                }
                addBSTotalRow(equityTable, "Total Equity", report.totalEquity());
                document.add(equityTable);
            }

            // Total Liabilities + Equity
            document.add(Chunk.NEWLINE);
            PdfPTable totalTable = new PdfPTable(2);
            totalTable.setWidthPercentage(100);
            totalTable.setWidths(new float[]{4f, 2f});

            BigDecimal totalLiabEquity = report.totalLiabilities().add(report.totalEquity());

            PdfPCell labelCell = new PdfPCell(new Phrase("Total Liabilities + Equity", TOTAL_FONT));
            labelCell.setBorder(Rectangle.TOP);
            labelCell.setBorderWidth(2);
            labelCell.setPadding(10);
            labelCell.setBackgroundColor(TOTAL_BG);
            totalTable.addCell(labelCell);

            PdfPCell amountCell = new PdfPCell(new Phrase(formatCurrency(totalLiabEquity), TOTAL_FONT));
            amountCell.setBorder(Rectangle.TOP);
            amountCell.setBorderWidth(2);
            amountCell.setPadding(10);
            amountCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            amountCell.setBackgroundColor(TOTAL_BG);
            totalTable.addCell(amountCell);

            document.add(totalTable);
            addReportFooter(document);
            document.close();

            log.info("Generated Balance Sheet PDF ({} bytes)", baos.size());
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Failed to generate Balance Sheet PDF", e);
            throw new RuntimeException("Failed to generate Balance Sheet PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Exports Balance Sheet report to Excel format.
     */
    public byte[] exportBalanceSheetToExcel(BalanceSheet report, Company company) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Balance Sheet");
            int rowNum = 0;

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle totalStyle = createTotalStyle(workbook);
            CellStyle sectionStyle = createSectionStyle(workbook);

            // Title
            org.apache.poi.ss.usermodel.Row titleRow = sheet.createRow(rowNum++);
            org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(company.getName() + " - Balance Sheet");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 2));

            // As of date
            org.apache.poi.ss.usermodel.Row dateRow = sheet.createRow(rowNum++);
            dateRow.createCell(0).setCellValue("As of " + report.asOfDate().format(dateFormatter));

            // Balance status
            org.apache.poi.ss.usermodel.Row statusRow = sheet.createRow(rowNum++);
            statusRow.createCell(0).setCellValue(report.isBalanced() ? "BALANCED" : "OUT OF BALANCE");

            rowNum++; // Empty row

            // Assets Section
            rowNum = addBalanceSheetSection(sheet, rowNum, "Assets", report.assets(),
                "Total Assets", report.totalAssets(), headerStyle, currencyStyle, sectionStyle, totalStyle);

            rowNum++; // Empty row

            // Liabilities Section
            rowNum = addBalanceSheetSection(sheet, rowNum, "Liabilities", report.liabilities(),
                "Total Liabilities", report.totalLiabilities(), headerStyle, currencyStyle, sectionStyle, totalStyle);

            rowNum++; // Empty row

            // Equity Section
            rowNum = addBalanceSheetSection(sheet, rowNum, "Equity", report.equity(),
                "Total Equity", report.totalEquity(), headerStyle, currencyStyle, sectionStyle, totalStyle);

            rowNum++; // Empty row

            // Total Liabilities + Equity
            org.apache.poi.ss.usermodel.Row totalRow = sheet.createRow(rowNum);
            totalRow.createCell(1).setCellValue("Total Liabilities + Equity");
            totalRow.getCell(1).setCellStyle(totalStyle);
            org.apache.poi.ss.usermodel.Cell totalCell = totalRow.createCell(2);
            totalCell.setCellValue(report.totalLiabilities().add(report.totalEquity()).doubleValue());
            totalCell.setCellStyle(totalStyle);

            // Auto-size columns
            for (int i = 0; i < 3; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(baos);
            log.info("Generated Balance Sheet Excel ({} bytes)", baos.size());
            return baos.toByteArray();

        } catch (IOException e) {
            log.error("Failed to generate Balance Sheet Excel", e);
            throw new RuntimeException("Failed to generate Balance Sheet Excel: " + e.getMessage(), e);
        }
    }

    private int addBalanceSheetSection(Sheet sheet, int rowNum, String sectionName,
                                        java.util.List<BalanceSheetLine> lines, String totalLabel,
                                        BigDecimal total, CellStyle headerStyle, CellStyle currencyStyle,
                                        CellStyle sectionStyle, CellStyle totalStyle) {
        // Section header
        org.apache.poi.ss.usermodel.Row sectionRow = sheet.createRow(rowNum++);
        org.apache.poi.ss.usermodel.Cell sectionCell = sectionRow.createCell(0);
        sectionCell.setCellValue(sectionName);
        sectionCell.setCellStyle(sectionStyle);

        // Column headers
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(rowNum++);
        headerRow.createCell(0).setCellValue("Code");
        headerRow.createCell(1).setCellValue("Account");
        headerRow.createCell(2).setCellValue("Balance");
        for (int i = 0; i < 3; i++) {
            headerRow.getCell(i).setCellStyle(headerStyle);
        }

        // Data rows
        for (BalanceSheetLine line : lines) {
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(line.account().getCode());
            row.createCell(1).setCellValue(line.account().getName());
            org.apache.poi.ss.usermodel.Cell amtCell = row.createCell(2);
            amtCell.setCellValue(line.balance().doubleValue());
            amtCell.setCellStyle(currencyStyle);
        }

        // Total row
        org.apache.poi.ss.usermodel.Row totalRow = sheet.createRow(rowNum++);
        totalRow.createCell(1).setCellValue(totalLabel);
        totalRow.getCell(1).setCellStyle(totalStyle);
        org.apache.poi.ss.usermodel.Cell totalCell = totalRow.createCell(2);
        totalCell.setCellValue(total.doubleValue());
        totalCell.setCellStyle(totalStyle);

        return rowNum;
    }

    // ==================== BUDGET VS ACTUAL EXPORTS ====================

    /**
     * Exports Budget vs Actual report to PDF format.
     */
    public byte[] exportBudgetVsActualToPdf(BudgetVsActual report, Company company) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate()); // Landscape for more columns
            PdfWriter.getInstance(document, baos);
            document.open();

            // Title
            addReportTitle(document, company, "Budget vs Actual Report");
            String subtitle = "Budget: " + report.budget().getName() + " | Period: " +
                report.startDate().format(dateFormatter) + " to " + report.endDate().format(dateFormatter);
            if (report.department() != null) {
                subtitle += " | Department: " + report.department().getCode() +
                    " - " + report.department().getName();
            }
            addReportSubtitle(document, subtitle);

            // Table
            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1.5f, 3f, 2f, 2f, 2f, 1.5f});

            addTableHeader(table, "Code");
            addTableHeader(table, "Account");
            addTableHeader(table, "Budget");
            addTableHeader(table, "Actual");
            addTableHeader(table, "Variance");
            addTableHeader(table, "Var %");

            boolean alternate = false;
            for (BudgetVsActualLine line : report.lines()) {
                Color bg = alternate ? ALT_ROW_BG : Color.WHITE;
                addTableCell(table, line.account().getCode(), bg, Element.ALIGN_LEFT);
                addTableCell(table, line.account().getName(), bg, Element.ALIGN_LEFT);
                addTableCell(table, formatCurrency(line.budgetAmount()), bg, Element.ALIGN_RIGHT);
                addTableCell(table, formatCurrency(line.actualAmount()), bg, Element.ALIGN_RIGHT);
                addVarianceCell(table, line.variance(), bg);
                addTableCell(table, formatPercent(line.variancePercent()), bg, Element.ALIGN_RIGHT);
                alternate = !alternate;
            }

            // Totals row
            addTotalCell(table, "");
            addTotalCell(table, "Totals");
            addTotalCell(table, formatCurrency(report.totalBudget()));
            addTotalCell(table, formatCurrency(report.totalActual()));
            addTotalVarianceCell(table, report.totalVariance());
            addTotalCell(table, formatPercent(report.totalVariancePercent()));

            document.add(table);
            addReportFooter(document);
            document.close();

            log.info("Generated Budget vs Actual PDF ({} bytes)", baos.size());
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Failed to generate Budget vs Actual PDF", e);
            throw new RuntimeException("Failed to generate Budget vs Actual PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Exports Budget vs Actual report to Excel format.
     */
    public byte[] exportBudgetVsActualToExcel(BudgetVsActual report, Company company) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Budget vs Actual");
            int rowNum = 0;

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle totalStyle = createTotalStyle(workbook);
            CellStyle percentStyle = createPercentStyle(workbook);

            // Title
            org.apache.poi.ss.usermodel.Row titleRow = sheet.createRow(rowNum++);
            org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(company.getName() + " - Budget vs Actual Report");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));

            // Subtitle
            org.apache.poi.ss.usermodel.Row dateRow = sheet.createRow(rowNum++);
            String dateText = "Budget: " + report.budget().getName() + " | Period: " +
                report.startDate().format(dateFormatter) + " to " + report.endDate().format(dateFormatter);
            if (report.department() != null) {
                dateText += " | Department: " + report.department().getCode();
            }
            dateRow.createCell(0).setCellValue(dateText);

            rowNum++; // Empty row

            // Header row
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"Code", "Account", "Budget", "Actual", "Variance", "Var %"};
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            for (BudgetVsActualLine line : report.lines()) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(line.account().getCode());
                row.createCell(1).setCellValue(line.account().getName());

                org.apache.poi.ss.usermodel.Cell budgetCell = row.createCell(2);
                budgetCell.setCellValue(line.budgetAmount().doubleValue());
                budgetCell.setCellStyle(currencyStyle);

                org.apache.poi.ss.usermodel.Cell actualCell = row.createCell(3);
                actualCell.setCellValue(line.actualAmount().doubleValue());
                actualCell.setCellStyle(currencyStyle);

                org.apache.poi.ss.usermodel.Cell varianceCell = row.createCell(4);
                varianceCell.setCellValue(line.variance().doubleValue());
                varianceCell.setCellStyle(currencyStyle);

                org.apache.poi.ss.usermodel.Cell pctCell = row.createCell(5);
                pctCell.setCellValue(line.variancePercent().doubleValue() / 100);
                pctCell.setCellStyle(percentStyle);
            }

            // Totals row
            org.apache.poi.ss.usermodel.Row totalsRow = sheet.createRow(rowNum);
            totalsRow.createCell(0);
            org.apache.poi.ss.usermodel.Cell totalLabel = totalsRow.createCell(1);
            totalLabel.setCellValue("Totals");
            totalLabel.setCellStyle(totalStyle);

            org.apache.poi.ss.usermodel.Cell totalBudget = totalsRow.createCell(2);
            totalBudget.setCellValue(report.totalBudget().doubleValue());
            totalBudget.setCellStyle(totalStyle);

            org.apache.poi.ss.usermodel.Cell totalActual = totalsRow.createCell(3);
            totalActual.setCellValue(report.totalActual().doubleValue());
            totalActual.setCellStyle(totalStyle);

            org.apache.poi.ss.usermodel.Cell totalVariance = totalsRow.createCell(4);
            totalVariance.setCellValue(report.totalVariance().doubleValue());
            totalVariance.setCellStyle(totalStyle);

            org.apache.poi.ss.usermodel.Cell totalPct = totalsRow.createCell(5);
            totalPct.setCellValue(report.totalVariancePercent().doubleValue() / 100);
            totalPct.setCellStyle(totalStyle);

            // Auto-size columns
            for (int i = 0; i < 6; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(baos);
            log.info("Generated Budget vs Actual Excel ({} bytes)", baos.size());
            return baos.toByteArray();

        } catch (IOException e) {
            log.error("Failed to generate Budget vs Actual Excel", e);
            throw new RuntimeException("Failed to generate Budget vs Actual Excel: " + e.getMessage(), e);
        }
    }

    // ==================== PDF HELPER METHODS ====================

    private void addReportTitle(Document document, Company company, String title) throws DocumentException {
        Paragraph companyName = new Paragraph(company.getName(), TITLE_FONT);
        companyName.setAlignment(Element.ALIGN_CENTER);
        document.add(companyName);

        Paragraph reportTitle = new Paragraph(title, SECTION_FONT);
        reportTitle.setAlignment(Element.ALIGN_CENTER);
        reportTitle.setSpacingAfter(5);
        document.add(reportTitle);
    }

    private void addReportSubtitle(Document document, String text) throws DocumentException {
        Paragraph subtitle = new Paragraph(text, SUBTITLE_FONT);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        subtitle.setSpacingAfter(15);
        document.add(subtitle);
    }

    private void addSectionHeader(Document document, String text) throws DocumentException {
        Paragraph section = new Paragraph(text, SECTION_FONT);
        section.setSpacingBefore(15);
        section.setSpacingAfter(8);
        document.add(section);
    }

    private void addReportFooter(Document document) throws DocumentException {
        document.add(Chunk.NEWLINE);
        document.add(Chunk.NEWLINE);
        Paragraph footer = new Paragraph(
            "Generated on " + LocalDate.now().format(dateFormatter) + " by MoniWorks",
            SMALL_FONT);
        footer.setAlignment(Element.ALIGN_CENTER);
        document.add(footer);
    }

    private void addTableHeader(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, TABLE_HEADER_FONT));
        cell.setBackgroundColor(HEADER_BG);
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

    private void addTotalCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, TABLE_CELL_BOLD));
        cell.setBackgroundColor(TOTAL_BG);
        cell.setPadding(8);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cell.setBorder(Rectangle.TOP);
        cell.setBorderWidth(2);
        table.addCell(cell);
    }

    private void addVarianceCell(PdfPTable table, BigDecimal variance, Color bgColor) {
        String text = formatVariance(variance);
        Color textColor = variance.compareTo(BigDecimal.ZERO) < 0 ? LOSS_COLOR : Color.BLACK;
        PdfPCell cell = new PdfPCell(new Phrase(text, new Font(Font.HELVETICA, 9, Font.NORMAL, textColor)));
        cell.setBackgroundColor(bgColor);
        cell.setPadding(6);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cell.setBorderColor(Color.LIGHT_GRAY);
        table.addCell(cell);
    }

    private void addTotalVarianceCell(PdfPTable table, BigDecimal variance) {
        String text = formatVariance(variance);
        Color textColor = variance.compareTo(BigDecimal.ZERO) < 0 ? LOSS_COLOR : Color.BLACK;
        PdfPCell cell = new PdfPCell(new Phrase(text, new Font(Font.HELVETICA, 9, Font.BOLD, textColor)));
        cell.setBackgroundColor(TOTAL_BG);
        cell.setPadding(8);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cell.setBorder(Rectangle.TOP);
        cell.setBorderWidth(2);
        table.addCell(cell);
    }

    private PdfPTable createPLTable() throws DocumentException {
        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.5f, 4f, 2f});

        addTableHeader(table, "Code");
        addTableHeader(table, "Account");
        addTableHeader(table, "Amount");

        return table;
    }

    private void addPLTableRow(PdfPTable table, ProfitAndLossLine line, boolean alternate) {
        Color bg = alternate ? ALT_ROW_BG : Color.WHITE;
        addTableCell(table, line.account().getCode(), bg, Element.ALIGN_LEFT);
        addTableCell(table, line.account().getName(), bg, Element.ALIGN_LEFT);
        addTableCell(table, formatCurrency(line.amount()), bg, Element.ALIGN_RIGHT);
    }

    private void addPLTotalRow(PdfPTable table, String label, BigDecimal amount) {
        addTotalCell(table, "");
        addTotalCell(table, label);
        addTotalCell(table, formatCurrency(amount));
    }

    private PdfPTable createBSTable() throws DocumentException {
        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.5f, 4f, 2f});

        addTableHeader(table, "Code");
        addTableHeader(table, "Account");
        addTableHeader(table, "Balance");

        return table;
    }

    private void addBSTableRow(PdfPTable table, BalanceSheetLine line) {
        addTableCell(table, line.account().getCode(), Color.WHITE, Element.ALIGN_LEFT);
        addTableCell(table, line.account().getName(), Color.WHITE, Element.ALIGN_LEFT);
        addTableCell(table, formatCurrency(line.balance()), Color.WHITE, Element.ALIGN_RIGHT);
    }

    private void addBSTotalRow(PdfPTable table, String label, BigDecimal amount) {
        addTotalCell(table, "");
        addTotalCell(table, label);
        addTotalCell(table, formatCurrency(amount));
    }

    // ==================== EXCEL HELPER METHODS ====================

    private CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        return style;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);

        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);

        return style;
    }

    private CellStyle createCurrencyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("$#,##0.00"));
        return style;
    }

    private CellStyle createTotalStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);

        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        style.setBorderTop(BorderStyle.DOUBLE);

        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("$#,##0.00"));

        return style;
    }

    private CellStyle createSectionStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        return style;
    }

    private CellStyle createPercentStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("0.0%"));
        return style;
    }

    // ==================== FORMATTING HELPERS ====================

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return currencyFormat.format(BigDecimal.ZERO);
        return currencyFormat.format(amount);
    }

    private String formatVariance(BigDecimal variance) {
        if (variance == null) return "$0.00";
        if (variance.compareTo(BigDecimal.ZERO) < 0) {
            return "(" + currencyFormat.format(variance.abs()) + ")";
        }
        return currencyFormat.format(variance);
    }

    private String formatPercent(BigDecimal percent) {
        if (percent == null) return "0.0%";
        return percent.abs().setScale(1, java.math.RoundingMode.HALF_UP) + "%";
    }
}
