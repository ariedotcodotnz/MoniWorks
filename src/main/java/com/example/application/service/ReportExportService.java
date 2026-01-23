package com.example.application.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

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

/**
 * Service for exporting financial reports to PDF and Excel formats. Supports Trial Balance, Profit
 * & Loss, Balance Sheet, and Budget vs Actual reports.
 */
@Service
public class ReportExportService {

  private static final Logger log = LoggerFactory.getLogger(ReportExportService.class);

  // PDF Fonts (using com.lowagie.text.Font explicitly)
  private static final Font TITLE_FONT = new Font(Font.HELVETICA, 16, Font.BOLD);
  private static final Font SUBTITLE_FONT =
      new Font(Font.HELVETICA, 11, Font.NORMAL, Color.DARK_GRAY);
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

  /** Exports Trial Balance report to PDF format. */
  public byte[] exportTrialBalanceToPdf(TrialBalance report, Company company) {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      Document document = new Document(PageSize.A4);
      PdfWriter.getInstance(document, baos);
      document.open();

      // Title
      addReportTitle(document, company, "Trial Balance");
      addReportSubtitle(
          document,
          "Period: "
              + report.startDate().format(dateFormatter)
              + " to "
              + report.endDate().format(dateFormatter));

      // Balance status
      String balanceStatus = report.isBalanced() ? "BALANCED" : "OUT OF BALANCE";
      Paragraph status =
          new Paragraph(
              balanceStatus,
              new Font(
                  Font.HELVETICA, 10, Font.BOLD, report.isBalanced() ? PROFIT_COLOR : LOSS_COLOR));
      status.setAlignment(Element.ALIGN_CENTER);
      status.setSpacingAfter(15);
      document.add(status);

      // Table
      PdfPTable table = new PdfPTable(4);
      table.setWidthPercentage(100);
      table.setWidths(new float[] {1.5f, 4f, 2f, 2f});

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

  /** Exports Trial Balance report to Excel format. */
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
      dateRow
          .createCell(0)
          .setCellValue(
              "Period: "
                  + report.startDate().format(dateFormatter)
                  + " to "
                  + report.endDate().format(dateFormatter));
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

  /** Exports Trial Balance report to CSV format. */
  public byte[] exportTrialBalanceToCsv(TrialBalance report, Company company) {
    StringBuilder csv = new StringBuilder();

    // Add UTF-8 BOM for Excel compatibility
    csv.append('\uFEFF');

    // Title row
    csv.append(escapeCsvField(company.getName())).append(" - Trial Balance\n");
    csv.append("Period: ")
        .append(report.startDate().format(dateFormatter))
        .append(" to ")
        .append(report.endDate().format(dateFormatter))
        .append("\n");
    csv.append("Status: ").append(report.isBalanced() ? "BALANCED" : "OUT OF BALANCE").append("\n");
    csv.append("\n");

    // Header row
    csv.append("Code,Account,Debits,Credits\n");

    // Data rows
    for (TrialBalanceLine line : report.lines()) {
      csv.append(escapeCsvField(line.account().getCode())).append(",");
      csv.append(escapeCsvField(line.account().getName())).append(",");
      csv.append(line.debits().toPlainString()).append(",");
      csv.append(line.credits().toPlainString()).append("\n");
    }

    // Totals row
    csv.append(",Totals,");
    csv.append(report.totalDebits().toPlainString()).append(",");
    csv.append(report.totalCredits().toPlainString()).append("\n");

    log.info("Generated Trial Balance CSV ({} bytes)", csv.length());
    return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }

  // ==================== PROFIT & LOSS EXPORTS ====================

  /** Exports Profit & Loss report to PDF format. */
  public byte[] exportProfitAndLossToPdf(ProfitAndLoss report, Company company) {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      Document document = new Document(PageSize.A4);
      PdfWriter.getInstance(document, baos);
      document.open();

      // Title
      addReportTitle(document, company, "Profit & Loss Statement");
      String subtitle =
          "Period: "
              + report.startDate().format(dateFormatter)
              + " to "
              + report.endDate().format(dateFormatter);
      if (report.department() != null) {
        subtitle +=
            " | Department: "
                + report.department().getCode()
                + " - "
                + report.department().getName();
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
      netTable.setWidths(new float[] {4f, 2f});

      String label = report.netProfit().compareTo(BigDecimal.ZERO) >= 0 ? "Net Profit" : "Net Loss";
      Color amountColor =
          report.netProfit().compareTo(BigDecimal.ZERO) >= 0 ? PROFIT_COLOR : LOSS_COLOR;

      PdfPCell labelCell = new PdfPCell(new Phrase(label, TOTAL_FONT));
      labelCell.setBorder(Rectangle.TOP);
      labelCell.setBorderWidth(2);
      labelCell.setPadding(10);
      labelCell.setBackgroundColor(TOTAL_BG);
      netTable.addCell(labelCell);

      PdfPCell amountCell =
          new PdfPCell(
              new Phrase(
                  formatCurrency(report.netProfit().abs()),
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

  /** Exports Profit & Loss report to Excel format. */
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
      String dateText =
          "Period: "
              + report.startDate().format(dateFormatter)
              + " to "
              + report.endDate().format(dateFormatter);
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

  /** Exports Profit & Loss report to CSV format. */
  public byte[] exportProfitAndLossToCsv(ProfitAndLoss report, Company company) {
    StringBuilder csv = new StringBuilder();
    csv.append('\uFEFF'); // UTF-8 BOM

    // Title
    csv.append(escapeCsvField(company.getName())).append(" - Profit & Loss Statement\n");
    String subtitle =
        "Period: "
            + report.startDate().format(dateFormatter)
            + " to "
            + report.endDate().format(dateFormatter);
    if (report.department() != null) {
      subtitle +=
          " | Department: " + report.department().getCode() + " - " + report.department().getName();
    }
    csv.append(subtitle).append("\n\n");

    // Header
    csv.append("Section,Code,Account,Amount\n");

    // Income lines
    for (ProfitAndLossLine line : report.incomeLines()) {
      csv.append("Income,");
      csv.append(escapeCsvField(line.account().getCode())).append(",");
      csv.append(escapeCsvField(line.account().getName())).append(",");
      csv.append(line.amount().toPlainString()).append("\n");
    }
    csv.append("Income,,Total Income,").append(report.totalIncome().toPlainString()).append("\n");

    // Expense lines
    for (ProfitAndLossLine line : report.expenseLines()) {
      csv.append("Expenses,");
      csv.append(escapeCsvField(line.account().getCode())).append(",");
      csv.append(escapeCsvField(line.account().getName())).append(",");
      csv.append(line.amount().toPlainString()).append("\n");
    }
    csv.append("Expenses,,Total Expenses,")
        .append(report.totalExpenses().toPlainString())
        .append("\n");

    // Net Profit/Loss
    String label = report.netProfit().compareTo(BigDecimal.ZERO) >= 0 ? "Net Profit" : "Net Loss";
    csv.append("Summary,,").append(label).append(",");
    csv.append(report.netProfit().toPlainString()).append("\n");

    log.info("Generated P&L CSV ({} bytes)", csv.length());
    return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }

  // ==================== BALANCE SHEET EXPORTS ====================

  /** Exports Balance Sheet report to PDF format. */
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
      Paragraph status =
          new Paragraph(
              balanceStatus,
              new Font(
                  Font.HELVETICA, 10, Font.BOLD, report.isBalanced() ? PROFIT_COLOR : LOSS_COLOR));
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
      totalTable.setWidths(new float[] {4f, 2f});

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

  /** Exports Balance Sheet report to Excel format. */
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
      rowNum =
          addBalanceSheetSection(
              sheet,
              rowNum,
              "Assets",
              report.assets(),
              "Total Assets",
              report.totalAssets(),
              headerStyle,
              currencyStyle,
              sectionStyle,
              totalStyle);

      rowNum++; // Empty row

      // Liabilities Section
      rowNum =
          addBalanceSheetSection(
              sheet,
              rowNum,
              "Liabilities",
              report.liabilities(),
              "Total Liabilities",
              report.totalLiabilities(),
              headerStyle,
              currencyStyle,
              sectionStyle,
              totalStyle);

      rowNum++; // Empty row

      // Equity Section
      rowNum =
          addBalanceSheetSection(
              sheet,
              rowNum,
              "Equity",
              report.equity(),
              "Total Equity",
              report.totalEquity(),
              headerStyle,
              currencyStyle,
              sectionStyle,
              totalStyle);

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

  /** Exports Balance Sheet report to CSV format. */
  public byte[] exportBalanceSheetToCsv(BalanceSheet report, Company company) {
    StringBuilder csv = new StringBuilder();
    csv.append('\uFEFF'); // UTF-8 BOM

    // Title
    csv.append(escapeCsvField(company.getName())).append(" - Balance Sheet\n");
    csv.append("As of ").append(report.asOfDate().format(dateFormatter)).append("\n");
    csv.append("Status: ").append(report.isBalanced() ? "BALANCED" : "OUT OF BALANCE").append("\n");
    csv.append("\n");

    // Header
    csv.append("Section,Code,Account,Balance\n");

    // Assets
    for (BalanceSheetLine line : report.assets()) {
      csv.append("Assets,");
      csv.append(escapeCsvField(line.account().getCode())).append(",");
      csv.append(escapeCsvField(line.account().getName())).append(",");
      csv.append(line.balance().toPlainString()).append("\n");
    }
    csv.append("Assets,,Total Assets,").append(report.totalAssets().toPlainString()).append("\n");

    // Liabilities
    for (BalanceSheetLine line : report.liabilities()) {
      csv.append("Liabilities,");
      csv.append(escapeCsvField(line.account().getCode())).append(",");
      csv.append(escapeCsvField(line.account().getName())).append(",");
      csv.append(line.balance().toPlainString()).append("\n");
    }
    csv.append("Liabilities,,Total Liabilities,")
        .append(report.totalLiabilities().toPlainString())
        .append("\n");

    // Equity
    for (BalanceSheetLine line : report.equity()) {
      csv.append("Equity,");
      csv.append(escapeCsvField(line.account().getCode())).append(",");
      csv.append(escapeCsvField(line.account().getName())).append(",");
      csv.append(line.balance().toPlainString()).append("\n");
    }
    csv.append("Equity,,Total Equity,").append(report.totalEquity().toPlainString()).append("\n");

    // Summary
    csv.append("Summary,,Total Liabilities + Equity,")
        .append(report.totalLiabilities().add(report.totalEquity()).toPlainString())
        .append("\n");

    log.info("Generated Balance Sheet CSV ({} bytes)", csv.length());
    return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }

  private int addBalanceSheetSection(
      Sheet sheet,
      int rowNum,
      String sectionName,
      java.util.List<BalanceSheetLine> lines,
      String totalLabel,
      BigDecimal total,
      CellStyle headerStyle,
      CellStyle currencyStyle,
      CellStyle sectionStyle,
      CellStyle totalStyle) {
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

  /** Exports Budget vs Actual report to PDF format. */
  public byte[] exportBudgetVsActualToPdf(BudgetVsActual report, Company company) {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      Document document = new Document(PageSize.A4.rotate()); // Landscape for more columns
      PdfWriter.getInstance(document, baos);
      document.open();

      // Title
      addReportTitle(document, company, "Budget vs Actual Report");
      String subtitle =
          "Budget: "
              + report.budget().getName()
              + " | Period: "
              + report.startDate().format(dateFormatter)
              + " to "
              + report.endDate().format(dateFormatter);
      if (report.department() != null) {
        subtitle +=
            " | Department: "
                + report.department().getCode()
                + " - "
                + report.department().getName();
      }
      addReportSubtitle(document, subtitle);

      // Table
      PdfPTable table = new PdfPTable(6);
      table.setWidthPercentage(100);
      table.setWidths(new float[] {1.5f, 3f, 2f, 2f, 2f, 1.5f});

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

  /** Exports Budget vs Actual report to Excel format. */
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
      String dateText =
          "Budget: "
              + report.budget().getName()
              + " | Period: "
              + report.startDate().format(dateFormatter)
              + " to "
              + report.endDate().format(dateFormatter);
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

  /** Exports Budget vs Actual report to CSV format. */
  public byte[] exportBudgetVsActualToCsv(BudgetVsActual report, Company company) {
    StringBuilder csv = new StringBuilder();
    csv.append('\uFEFF'); // UTF-8 BOM

    // Title
    csv.append(escapeCsvField(company.getName())).append(" - Budget vs Actual\n");
    csv.append("Budget: ")
        .append(escapeCsvField(report.budget() != null ? report.budget().getName() : ""))
        .append("\n");
    csv.append("Period: ")
        .append(report.startDate().format(dateFormatter))
        .append(" to ")
        .append(report.endDate().format(dateFormatter))
        .append("\n\n");

    // Header
    csv.append("Code,Account,Budget,Actual,Variance,Variance %\n");

    // Data rows
    for (BudgetVsActualLine line : report.lines()) {
      csv.append(escapeCsvField(line.account().getCode())).append(",");
      csv.append(escapeCsvField(line.account().getName())).append(",");
      csv.append(line.budgetAmount().toPlainString()).append(",");
      csv.append(line.actualAmount().toPlainString()).append(",");
      csv.append(line.variance().toPlainString()).append(",");
      if (line.variancePercent() != null) {
        csv.append(line.variancePercent().toPlainString()).append("%");
      }
      csv.append("\n");
    }

    // Totals row
    csv.append(",Totals,");
    csv.append(report.totalBudget().toPlainString()).append(",");
    csv.append(report.totalActual().toPlainString()).append(",");
    csv.append(report.totalVariance().toPlainString()).append(",");
    if (report.totalVariancePercent() != null) {
      csv.append(report.totalVariancePercent().toPlainString()).append("%");
    }
    csv.append("\n");

    log.info("Generated Budget vs Actual CSV ({} bytes)", csv.length());
    return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }

  // ==================== PDF HELPER METHODS ====================

  private void addReportTitle(Document document, Company company, String title)
      throws DocumentException {
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
    Paragraph footer =
        new Paragraph(
            "Generated on " + LocalDate.now().format(dateFormatter) + " by MoniWorks", SMALL_FONT);
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
    PdfPCell cell =
        new PdfPCell(new Phrase(text, new Font(Font.HELVETICA, 9, Font.NORMAL, textColor)));
    cell.setBackgroundColor(bgColor);
    cell.setPadding(6);
    cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
    cell.setBorderColor(Color.LIGHT_GRAY);
    table.addCell(cell);
  }

  private void addTotalVarianceCell(PdfPTable table, BigDecimal variance) {
    String text = formatVariance(variance);
    Color textColor = variance.compareTo(BigDecimal.ZERO) < 0 ? LOSS_COLOR : Color.BLACK;
    PdfPCell cell =
        new PdfPCell(new Phrase(text, new Font(Font.HELVETICA, 9, Font.BOLD, textColor)));
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
    table.setWidths(new float[] {1.5f, 4f, 2f});

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
    table.setWidths(new float[] {1.5f, 4f, 2f});

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

  // ==================== CSV HELPER METHODS ====================

  /**
   * Escapes a field value for CSV output. Fields containing commas, quotes, or newlines are wrapped
   * in quotes, and embedded quotes are doubled.
   */
  private String escapeCsvField(String value) {
    if (value == null) return "";
    // If field contains comma, quote, or newline, wrap in quotes and escape quotes
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }

  // ==================== AR AGING EXPORTS ====================

  /**
   * Exports AR Aging report to PDF format. Shows outstanding receivables categorized by aging
   * buckets (Current, 1-30, 31-60, 61-90, 90+ days).
   */
  public byte[] exportArAgingToPdf(ArAgingReport report, Company company) {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      Document document = new Document(PageSize.A4.rotate()); // Landscape for more columns
      PdfWriter.getInstance(document, baos);
      document.open();

      // Title
      addReportTitle(document, company, "Accounts Receivable Aging Report");
      addReportSubtitle(document, "As of " + report.asOfDate().format(dateFormatter));

      // Summary section
      addSectionHeader(document, "Aging Summary by Customer");

      PdfPTable summaryTable = new PdfPTable(7);
      summaryTable.setWidthPercentage(100);
      summaryTable.setWidths(new float[] {3f, 1.5f, 1.5f, 1.5f, 1.5f, 1.5f, 2f});

      addTableHeader(summaryTable, "Customer");
      addTableHeader(summaryTable, "Current");
      addTableHeader(summaryTable, "1-30 Days");
      addTableHeader(summaryTable, "31-60 Days");
      addTableHeader(summaryTable, "61-90 Days");
      addTableHeader(summaryTable, "90+ Days");
      addTableHeader(summaryTable, "Total");

      boolean alternate = false;
      for (ArAgingCustomerSummary summary : report.customerSummaries()) {
        Color bg = alternate ? ALT_ROW_BG : Color.WHITE;
        addTableCell(summaryTable, summary.customer().getName(), bg, Element.ALIGN_LEFT);
        addTableCell(summaryTable, formatCurrency(summary.current()), bg, Element.ALIGN_RIGHT);
        addAgingCell(summaryTable, summary.days1to30(), bg, 1);
        addAgingCell(summaryTable, summary.days31to60(), bg, 31);
        addAgingCell(summaryTable, summary.days61to90(), bg, 61);
        addAgingCell(summaryTable, summary.days90Plus(), bg, 91);
        addTableCell(summaryTable, formatCurrency(summary.total()), bg, Element.ALIGN_RIGHT);
        alternate = !alternate;
      }

      // Totals row
      addTotalCell(summaryTable, "Totals");
      addTotalCell(summaryTable, formatCurrency(report.totalCurrent()));
      addTotalCell(summaryTable, formatCurrency(report.total1to30()));
      addTotalCell(summaryTable, formatCurrency(report.total31to60()));
      addTotalCell(summaryTable, formatCurrency(report.total61to90()));
      addTotalCell(summaryTable, formatCurrency(report.total90Plus()));
      addTotalCell(summaryTable, formatCurrency(report.grandTotal()));

      document.add(summaryTable);

      // Grand total highlight
      document.add(Chunk.NEWLINE);
      PdfPTable grandTotalTable = new PdfPTable(2);
      grandTotalTable.setWidthPercentage(50);
      grandTotalTable.setHorizontalAlignment(Element.ALIGN_RIGHT);

      PdfPCell labelCell = new PdfPCell(new Phrase("Total Outstanding Receivables", TOTAL_FONT));
      labelCell.setBorder(Rectangle.TOP);
      labelCell.setBorderWidth(2);
      labelCell.setPadding(10);
      labelCell.setBackgroundColor(TOTAL_BG);
      grandTotalTable.addCell(labelCell);

      PdfPCell amountCell =
          new PdfPCell(new Phrase(formatCurrency(report.grandTotal()), TOTAL_FONT));
      amountCell.setBorder(Rectangle.TOP);
      amountCell.setBorderWidth(2);
      amountCell.setPadding(10);
      amountCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
      amountCell.setBackgroundColor(TOTAL_BG);
      grandTotalTable.addCell(amountCell);

      document.add(grandTotalTable);

      addReportFooter(document);
      document.close();

      log.info("Generated AR Aging PDF ({} bytes)", baos.size());
      return baos.toByteArray();

    } catch (Exception e) {
      log.error("Failed to generate AR Aging PDF", e);
      throw new RuntimeException("Failed to generate AR Aging PDF: " + e.getMessage(), e);
    }
  }

  /** Exports AR Aging report to Excel format. */
  public byte[] exportArAgingToExcel(ArAgingReport report, Company company) {
    try (XSSFWorkbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

      Sheet sheet = workbook.createSheet("AR Aging");
      int rowNum = 0;

      CellStyle headerStyle = createHeaderStyle(workbook);
      CellStyle currencyStyle = createCurrencyStyle(workbook);
      CellStyle titleStyle = createTitleStyle(workbook);
      CellStyle totalStyle = createTotalStyle(workbook);

      // Title
      org.apache.poi.ss.usermodel.Row titleRow = sheet.createRow(rowNum++);
      org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
      titleCell.setCellValue(company.getName() + " - Accounts Receivable Aging Report");
      titleCell.setCellStyle(titleStyle);
      sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));

      // As of date
      org.apache.poi.ss.usermodel.Row dateRow = sheet.createRow(rowNum++);
      dateRow.createCell(0).setCellValue("As of " + report.asOfDate().format(dateFormatter));

      rowNum++; // Empty row

      // Header row
      org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(rowNum++);
      String[] headers = {
        "Customer", "Current", "1-30 Days", "31-60 Days", "61-90 Days", "90+ Days", "Total"
      };
      for (int i = 0; i < headers.length; i++) {
        org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
        cell.setCellValue(headers[i]);
        cell.setCellStyle(headerStyle);
      }

      // Data rows
      for (ArAgingCustomerSummary summary : report.customerSummaries()) {
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
        row.createCell(0).setCellValue(summary.customer().getName());

        org.apache.poi.ss.usermodel.Cell currentCell = row.createCell(1);
        currentCell.setCellValue(summary.current().doubleValue());
        currentCell.setCellStyle(currencyStyle);

        org.apache.poi.ss.usermodel.Cell days1to30Cell = row.createCell(2);
        days1to30Cell.setCellValue(summary.days1to30().doubleValue());
        days1to30Cell.setCellStyle(currencyStyle);

        org.apache.poi.ss.usermodel.Cell days31to60Cell = row.createCell(3);
        days31to60Cell.setCellValue(summary.days31to60().doubleValue());
        days31to60Cell.setCellStyle(currencyStyle);

        org.apache.poi.ss.usermodel.Cell days61to90Cell = row.createCell(4);
        days61to90Cell.setCellValue(summary.days61to90().doubleValue());
        days61to90Cell.setCellStyle(currencyStyle);

        org.apache.poi.ss.usermodel.Cell days90PlusCell = row.createCell(5);
        days90PlusCell.setCellValue(summary.days90Plus().doubleValue());
        days90PlusCell.setCellStyle(currencyStyle);

        org.apache.poi.ss.usermodel.Cell totalCell = row.createCell(6);
        totalCell.setCellValue(summary.total().doubleValue());
        totalCell.setCellStyle(currencyStyle);
      }

      // Totals row
      org.apache.poi.ss.usermodel.Row totalsRow = sheet.createRow(rowNum);
      org.apache.poi.ss.usermodel.Cell totalLabel = totalsRow.createCell(0);
      totalLabel.setCellValue("Totals");
      totalLabel.setCellStyle(totalStyle);

      org.apache.poi.ss.usermodel.Cell totalCurrent = totalsRow.createCell(1);
      totalCurrent.setCellValue(report.totalCurrent().doubleValue());
      totalCurrent.setCellStyle(totalStyle);

      org.apache.poi.ss.usermodel.Cell total1to30 = totalsRow.createCell(2);
      total1to30.setCellValue(report.total1to30().doubleValue());
      total1to30.setCellStyle(totalStyle);

      org.apache.poi.ss.usermodel.Cell total31to60 = totalsRow.createCell(3);
      total31to60.setCellValue(report.total31to60().doubleValue());
      total31to60.setCellStyle(totalStyle);

      org.apache.poi.ss.usermodel.Cell total61to90 = totalsRow.createCell(4);
      total61to90.setCellValue(report.total61to90().doubleValue());
      total61to90.setCellStyle(totalStyle);

      org.apache.poi.ss.usermodel.Cell total90Plus = totalsRow.createCell(5);
      total90Plus.setCellValue(report.total90Plus().doubleValue());
      total90Plus.setCellStyle(totalStyle);

      org.apache.poi.ss.usermodel.Cell grandTotal = totalsRow.createCell(6);
      grandTotal.setCellValue(report.grandTotal().doubleValue());
      grandTotal.setCellStyle(totalStyle);

      // Auto-size columns
      for (int i = 0; i < 7; i++) {
        sheet.autoSizeColumn(i);
      }

      workbook.write(baos);
      log.info("Generated AR Aging Excel ({} bytes)", baos.size());
      return baos.toByteArray();

    } catch (IOException e) {
      log.error("Failed to generate AR Aging Excel", e);
      throw new RuntimeException("Failed to generate AR Aging Excel: " + e.getMessage(), e);
    }
  }

  /** Exports AR Aging report to CSV format. */
  public byte[] exportArAgingToCsv(ArAgingReport report, Company company) {
    StringBuilder csv = new StringBuilder();
    csv.append('\uFEFF'); // UTF-8 BOM

    csv.append(escapeCsvField(company.getName())).append(" - Accounts Receivable Aging Report\n");
    csv.append("As of ").append(report.asOfDate().format(dateFormatter)).append("\n\n");

    // Header
    csv.append("Customer,Current,1-30 Days,31-60 Days,61-90 Days,90+ Days,Total\n");

    // Customer summaries
    for (ArAgingCustomerSummary summary : report.customerSummaries()) {
      csv.append(escapeCsvField(summary.customer().getName())).append(",");
      csv.append(summary.current().toPlainString()).append(",");
      csv.append(summary.days1to30().toPlainString()).append(",");
      csv.append(summary.days31to60().toPlainString()).append(",");
      csv.append(summary.days61to90().toPlainString()).append(",");
      csv.append(summary.days90Plus().toPlainString()).append(",");
      csv.append(summary.total().toPlainString()).append("\n");
    }

    // Totals
    csv.append("Totals,");
    csv.append(report.totalCurrent().toPlainString()).append(",");
    csv.append(report.total1to30().toPlainString()).append(",");
    csv.append(report.total31to60().toPlainString()).append(",");
    csv.append(report.total61to90().toPlainString()).append(",");
    csv.append(report.total90Plus().toPlainString()).append(",");
    csv.append(report.grandTotal().toPlainString()).append("\n");

    log.info("Generated AR Aging CSV ({} bytes)", csv.length());
    return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }

  // ==================== AP AGING EXPORTS ====================

  /**
   * Exports AP Aging report to PDF format. Shows outstanding payables categorized by aging buckets
   * (Current, 1-30, 31-60, 61-90, 90+ days).
   */
  public byte[] exportApAgingToPdf(ApAgingReport report, Company company) {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      Document document = new Document(PageSize.A4.rotate()); // Landscape for more columns
      PdfWriter.getInstance(document, baos);
      document.open();

      // Title
      addReportTitle(document, company, "Accounts Payable Aging Report");
      addReportSubtitle(document, "As of " + report.asOfDate().format(dateFormatter));

      // Summary section
      addSectionHeader(document, "Aging Summary by Supplier");

      PdfPTable summaryTable = new PdfPTable(7);
      summaryTable.setWidthPercentage(100);
      summaryTable.setWidths(new float[] {3f, 1.5f, 1.5f, 1.5f, 1.5f, 1.5f, 2f});

      addTableHeader(summaryTable, "Supplier");
      addTableHeader(summaryTable, "Current");
      addTableHeader(summaryTable, "1-30 Days");
      addTableHeader(summaryTable, "31-60 Days");
      addTableHeader(summaryTable, "61-90 Days");
      addTableHeader(summaryTable, "90+ Days");
      addTableHeader(summaryTable, "Total");

      boolean alternate = false;
      for (ApAgingSupplierSummary summary : report.supplierSummaries()) {
        Color bg = alternate ? ALT_ROW_BG : Color.WHITE;
        addTableCell(summaryTable, summary.supplier().getName(), bg, Element.ALIGN_LEFT);
        addTableCell(summaryTable, formatCurrency(summary.current()), bg, Element.ALIGN_RIGHT);
        addAgingCell(summaryTable, summary.days1to30(), bg, 1);
        addAgingCell(summaryTable, summary.days31to60(), bg, 31);
        addAgingCell(summaryTable, summary.days61to90(), bg, 61);
        addAgingCell(summaryTable, summary.days90Plus(), bg, 91);
        addTableCell(summaryTable, formatCurrency(summary.total()), bg, Element.ALIGN_RIGHT);
        alternate = !alternate;
      }

      // Totals row
      addTotalCell(summaryTable, "Totals");
      addTotalCell(summaryTable, formatCurrency(report.totalCurrent()));
      addTotalCell(summaryTable, formatCurrency(report.total1to30()));
      addTotalCell(summaryTable, formatCurrency(report.total31to60()));
      addTotalCell(summaryTable, formatCurrency(report.total61to90()));
      addTotalCell(summaryTable, formatCurrency(report.total90Plus()));
      addTotalCell(summaryTable, formatCurrency(report.grandTotal()));

      document.add(summaryTable);

      // Grand total highlight
      document.add(Chunk.NEWLINE);
      PdfPTable grandTotalTable = new PdfPTable(2);
      grandTotalTable.setWidthPercentage(50);
      grandTotalTable.setHorizontalAlignment(Element.ALIGN_RIGHT);

      PdfPCell labelCell = new PdfPCell(new Phrase("Total Outstanding Payables", TOTAL_FONT));
      labelCell.setBorder(Rectangle.TOP);
      labelCell.setBorderWidth(2);
      labelCell.setPadding(10);
      labelCell.setBackgroundColor(TOTAL_BG);
      grandTotalTable.addCell(labelCell);

      PdfPCell amountCell =
          new PdfPCell(new Phrase(formatCurrency(report.grandTotal()), TOTAL_FONT));
      amountCell.setBorder(Rectangle.TOP);
      amountCell.setBorderWidth(2);
      amountCell.setPadding(10);
      amountCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
      amountCell.setBackgroundColor(TOTAL_BG);
      grandTotalTable.addCell(amountCell);

      document.add(grandTotalTable);

      addReportFooter(document);
      document.close();

      log.info("Generated AP Aging PDF ({} bytes)", baos.size());
      return baos.toByteArray();

    } catch (Exception e) {
      log.error("Failed to generate AP Aging PDF", e);
      throw new RuntimeException("Failed to generate AP Aging PDF: " + e.getMessage(), e);
    }
  }

  /** Exports AP Aging report to Excel format. */
  public byte[] exportApAgingToExcel(ApAgingReport report, Company company) {
    try (XSSFWorkbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

      Sheet sheet = workbook.createSheet("AP Aging");
      int rowNum = 0;

      CellStyle headerStyle = createHeaderStyle(workbook);
      CellStyle currencyStyle = createCurrencyStyle(workbook);
      CellStyle titleStyle = createTitleStyle(workbook);
      CellStyle totalStyle = createTotalStyle(workbook);

      // Title
      org.apache.poi.ss.usermodel.Row titleRow = sheet.createRow(rowNum++);
      org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
      titleCell.setCellValue(company.getName() + " - Accounts Payable Aging Report");
      titleCell.setCellStyle(titleStyle);
      sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));

      // As of date
      org.apache.poi.ss.usermodel.Row dateRow = sheet.createRow(rowNum++);
      dateRow.createCell(0).setCellValue("As of " + report.asOfDate().format(dateFormatter));

      rowNum++; // Empty row

      // Header row
      org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(rowNum++);
      String[] headers = {
        "Supplier", "Current", "1-30 Days", "31-60 Days", "61-90 Days", "90+ Days", "Total"
      };
      for (int i = 0; i < headers.length; i++) {
        org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
        cell.setCellValue(headers[i]);
        cell.setCellStyle(headerStyle);
      }

      // Data rows
      for (ApAgingSupplierSummary summary : report.supplierSummaries()) {
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
        row.createCell(0).setCellValue(summary.supplier().getName());

        org.apache.poi.ss.usermodel.Cell currentCell = row.createCell(1);
        currentCell.setCellValue(summary.current().doubleValue());
        currentCell.setCellStyle(currencyStyle);

        org.apache.poi.ss.usermodel.Cell days1to30Cell = row.createCell(2);
        days1to30Cell.setCellValue(summary.days1to30().doubleValue());
        days1to30Cell.setCellStyle(currencyStyle);

        org.apache.poi.ss.usermodel.Cell days31to60Cell = row.createCell(3);
        days31to60Cell.setCellValue(summary.days31to60().doubleValue());
        days31to60Cell.setCellStyle(currencyStyle);

        org.apache.poi.ss.usermodel.Cell days61to90Cell = row.createCell(4);
        days61to90Cell.setCellValue(summary.days61to90().doubleValue());
        days61to90Cell.setCellStyle(currencyStyle);

        org.apache.poi.ss.usermodel.Cell days90PlusCell = row.createCell(5);
        days90PlusCell.setCellValue(summary.days90Plus().doubleValue());
        days90PlusCell.setCellStyle(currencyStyle);

        org.apache.poi.ss.usermodel.Cell totalCell = row.createCell(6);
        totalCell.setCellValue(summary.total().doubleValue());
        totalCell.setCellStyle(currencyStyle);
      }

      // Totals row
      org.apache.poi.ss.usermodel.Row totalsRow = sheet.createRow(rowNum);
      org.apache.poi.ss.usermodel.Cell totalLabel = totalsRow.createCell(0);
      totalLabel.setCellValue("Totals");
      totalLabel.setCellStyle(totalStyle);

      org.apache.poi.ss.usermodel.Cell totalCurrent = totalsRow.createCell(1);
      totalCurrent.setCellValue(report.totalCurrent().doubleValue());
      totalCurrent.setCellStyle(totalStyle);

      org.apache.poi.ss.usermodel.Cell total1to30 = totalsRow.createCell(2);
      total1to30.setCellValue(report.total1to30().doubleValue());
      total1to30.setCellStyle(totalStyle);

      org.apache.poi.ss.usermodel.Cell total31to60 = totalsRow.createCell(3);
      total31to60.setCellValue(report.total31to60().doubleValue());
      total31to60.setCellStyle(totalStyle);

      org.apache.poi.ss.usermodel.Cell total61to90 = totalsRow.createCell(4);
      total61to90.setCellValue(report.total61to90().doubleValue());
      total61to90.setCellStyle(totalStyle);

      org.apache.poi.ss.usermodel.Cell total90Plus = totalsRow.createCell(5);
      total90Plus.setCellValue(report.total90Plus().doubleValue());
      total90Plus.setCellStyle(totalStyle);

      org.apache.poi.ss.usermodel.Cell grandTotal = totalsRow.createCell(6);
      grandTotal.setCellValue(report.grandTotal().doubleValue());
      grandTotal.setCellStyle(totalStyle);

      // Auto-size columns
      for (int i = 0; i < 7; i++) {
        sheet.autoSizeColumn(i);
      }

      workbook.write(baos);
      log.info("Generated AP Aging Excel ({} bytes)", baos.size());
      return baos.toByteArray();

    } catch (IOException e) {
      log.error("Failed to generate AP Aging Excel", e);
      throw new RuntimeException("Failed to generate AP Aging Excel: " + e.getMessage(), e);
    }
  }

  /** Exports AP Aging report to CSV format. */
  public byte[] exportApAgingToCsv(ApAgingReport report, Company company) {
    StringBuilder csv = new StringBuilder();
    csv.append('\uFEFF'); // UTF-8 BOM

    csv.append(escapeCsvField(company.getName())).append(" - Accounts Payable Aging Report\n");
    csv.append("As of ").append(report.asOfDate().format(dateFormatter)).append("\n\n");

    // Header
    csv.append("Supplier,Current,1-30 Days,31-60 Days,61-90 Days,90+ Days,Total\n");

    // Supplier summaries
    for (ApAgingSupplierSummary summary : report.supplierSummaries()) {
      csv.append(escapeCsvField(summary.supplier().getName())).append(",");
      csv.append(summary.current().toPlainString()).append(",");
      csv.append(summary.days1to30().toPlainString()).append(",");
      csv.append(summary.days31to60().toPlainString()).append(",");
      csv.append(summary.days61to90().toPlainString()).append(",");
      csv.append(summary.days90Plus().toPlainString()).append(",");
      csv.append(summary.total().toPlainString()).append("\n");
    }

    // Totals
    csv.append("Totals,");
    csv.append(report.totalCurrent().toPlainString()).append(",");
    csv.append(report.total1to30().toPlainString()).append(",");
    csv.append(report.total31to60().toPlainString()).append(",");
    csv.append(report.total61to90().toPlainString()).append(",");
    csv.append(report.total90Plus().toPlainString()).append(",");
    csv.append(report.grandTotal().toPlainString()).append("\n");

    log.info("Generated AP Aging CSV ({} bytes)", csv.length());
    return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }

  /**
   * Helper method to add colored aging cells based on days overdue. Current amounts are black,
   * overdue amounts are shown in red shades.
   */
  private void addAgingCell(PdfPTable table, BigDecimal amount, Color bgColor, int daysOverdue) {
    Color textColor = Color.BLACK;
    if (amount.compareTo(BigDecimal.ZERO) > 0 && daysOverdue > 0) {
      // Show overdue amounts in red
      textColor = LOSS_COLOR;
    }
    PdfPCell cell =
        new PdfPCell(
            new Phrase(
                formatCurrency(amount), new Font(Font.HELVETICA, 9, Font.NORMAL, textColor)));
    cell.setBackgroundColor(bgColor);
    cell.setPadding(6);
    cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
    cell.setBorderColor(Color.LIGHT_GRAY);
    table.addCell(cell);
  }

  // ==================== CASHFLOW EXPORTS ====================

  /**
   * Exports Cashflow Statement report to PDF format. Shows cash inflows, outflows, and net cash
   * movement for bank accounts.
   */
  public byte[] exportCashflowToPdf(ReportingService.CashflowStatement report, Company company) {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      Document document = new Document(PageSize.A4);
      PdfWriter.getInstance(document, baos);
      document.open();

      // Title
      addReportTitle(document, company, "Cashflow Statement");
      addReportSubtitle(
          document,
          "Period: "
              + report.startDate().format(dateFormatter)
              + " to "
              + report.endDate().format(dateFormatter));

      // Reconciliation status
      String reconcileStatus = report.isReconciled() ? "RECONCILED" : "UNRECONCILED";
      Paragraph status =
          new Paragraph(
              reconcileStatus,
              new Font(
                  Font.HELVETICA,
                  10,
                  Font.BOLD,
                  report.isReconciled() ? PROFIT_COLOR : LOSS_COLOR));
      status.setAlignment(Element.ALIGN_CENTER);
      status.setSpacingAfter(15);
      document.add(status);

      // Summary section
      addSectionHeader(document, "Cash Summary");

      PdfPTable summaryTable = new PdfPTable(2);
      summaryTable.setWidthPercentage(60);
      summaryTable.setHorizontalAlignment(Element.ALIGN_LEFT);
      summaryTable.setWidths(new float[] {3f, 2f});

      addSummaryRow(summaryTable, "Opening Cash Balance", report.openingBalance());
      addSummaryRow(summaryTable, "Total Cash Inflows", report.totalInflows());
      addSummaryRow(summaryTable, "Total Cash Outflows", report.totalOutflows().negate());
      addNetCashFlowRow(summaryTable, "Net Cash Flow", report.netCashFlow());
      addSummaryRow(summaryTable, "Closing Cash Balance", report.closingBalance());

      document.add(summaryTable);

      // Account summaries section
      if (!report.accountSummaries().isEmpty()) {
        addSectionHeader(document, "Summary by Bank Account");

        PdfPTable accountTable = new PdfPTable(5);
        accountTable.setWidthPercentage(100);
        accountTable.setWidths(new float[] {3f, 2f, 2f, 2f, 2f});

        addTableHeader(accountTable, "Account");
        addTableHeader(accountTable, "Opening");
        addTableHeader(accountTable, "Inflows");
        addTableHeader(accountTable, "Outflows");
        addTableHeader(accountTable, "Closing");

        boolean alternate = false;
        for (ReportingService.CashflowAccountSummary summary : report.accountSummaries()) {
          Color bg = alternate ? ALT_ROW_BG : Color.WHITE;
          addTableCell(
              accountTable,
              summary.account().getCode() + " - " + summary.account().getName(),
              bg,
              Element.ALIGN_LEFT);
          addTableCell(
              accountTable, formatCurrency(summary.openingBalance()), bg, Element.ALIGN_RIGHT);
          addInflowCell(accountTable, summary.inflows(), bg);
          addOutflowCell(accountTable, summary.outflows(), bg);
          addTableCell(
              accountTable, formatCurrency(summary.closingBalance()), bg, Element.ALIGN_RIGHT);
          alternate = !alternate;
        }

        // Totals
        addTotalCell(accountTable, "Totals");
        addTotalCell(accountTable, formatCurrency(report.openingBalance()));
        addTotalCell(accountTable, formatCurrency(report.totalInflows()));
        addTotalCell(accountTable, formatCurrency(report.totalOutflows()));
        addTotalCell(accountTable, formatCurrency(report.closingBalance()));

        document.add(accountTable);
      }

      addReportFooter(document);
      document.close();

      log.info("Generated Cashflow PDF ({} bytes)", baos.size());
      return baos.toByteArray();

    } catch (Exception e) {
      log.error("Failed to generate Cashflow PDF", e);
      throw new RuntimeException("Failed to generate Cashflow PDF: " + e.getMessage(), e);
    }
  }

  /** Exports Cashflow Statement report to Excel format. */
  public byte[] exportCashflowToExcel(ReportingService.CashflowStatement report, Company company) {
    try (XSSFWorkbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

      Sheet sheet = workbook.createSheet("Cashflow Statement");
      int rowNum = 0;

      CellStyle headerStyle = createHeaderStyle(workbook);
      CellStyle currencyStyle = createCurrencyStyle(workbook);
      CellStyle titleStyle = createTitleStyle(workbook);
      CellStyle totalStyle = createTotalStyle(workbook);
      CellStyle sectionStyle = createSectionStyle(workbook);

      // Title
      org.apache.poi.ss.usermodel.Row titleRow = sheet.createRow(rowNum++);
      org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
      titleCell.setCellValue(company.getName() + " - Cashflow Statement");
      titleCell.setCellStyle(titleStyle);
      sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));

      // Period
      org.apache.poi.ss.usermodel.Row dateRow = sheet.createRow(rowNum++);
      dateRow
          .createCell(0)
          .setCellValue(
              "Period: "
                  + report.startDate().format(dateFormatter)
                  + " to "
                  + report.endDate().format(dateFormatter));

      // Status
      org.apache.poi.ss.usermodel.Row statusRow = sheet.createRow(rowNum++);
      statusRow.createCell(0).setCellValue(report.isReconciled() ? "RECONCILED" : "UNRECONCILED");

      rowNum++; // Empty row

      // Summary section
      org.apache.poi.ss.usermodel.Row summaryHeader = sheet.createRow(rowNum++);
      summaryHeader.createCell(0).setCellValue("Cash Summary");
      summaryHeader.getCell(0).setCellStyle(sectionStyle);

      rowNum++; // Empty row

      // Summary data
      addExcelSummaryRow(
          sheet, rowNum++, "Opening Cash Balance", report.openingBalance(), currencyStyle);
      addExcelSummaryRow(
          sheet, rowNum++, "Total Cash Inflows", report.totalInflows(), currencyStyle);
      addExcelSummaryRow(
          sheet, rowNum++, "Total Cash Outflows", report.totalOutflows().negate(), currencyStyle);
      org.apache.poi.ss.usermodel.Row netRow = sheet.createRow(rowNum++);
      netRow.createCell(0).setCellValue("Net Cash Flow");
      netRow.getCell(0).setCellStyle(totalStyle);
      org.apache.poi.ss.usermodel.Cell netCell = netRow.createCell(1);
      netCell.setCellValue(report.netCashFlow().doubleValue());
      netCell.setCellStyle(totalStyle);
      addExcelSummaryRow(
          sheet, rowNum++, "Closing Cash Balance", report.closingBalance(), currencyStyle);

      rowNum++; // Empty row

      // Account summaries
      if (!report.accountSummaries().isEmpty()) {
        org.apache.poi.ss.usermodel.Row accountHeader = sheet.createRow(rowNum++);
        accountHeader.createCell(0).setCellValue("Summary by Bank Account");
        accountHeader.getCell(0).setCellStyle(sectionStyle);

        // Column headers
        org.apache.poi.ss.usermodel.Row colHeaders = sheet.createRow(rowNum++);
        String[] headers = {"Account", "Opening", "Inflows", "Outflows", "Closing"};
        for (int i = 0; i < headers.length; i++) {
          org.apache.poi.ss.usermodel.Cell cell = colHeaders.createCell(i);
          cell.setCellValue(headers[i]);
          cell.setCellStyle(headerStyle);
        }

        // Account data
        for (ReportingService.CashflowAccountSummary summary : report.accountSummaries()) {
          org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
          row.createCell(0)
              .setCellValue(summary.account().getCode() + " - " + summary.account().getName());

          org.apache.poi.ss.usermodel.Cell openingCell = row.createCell(1);
          openingCell.setCellValue(summary.openingBalance().doubleValue());
          openingCell.setCellStyle(currencyStyle);

          org.apache.poi.ss.usermodel.Cell inflowsCell = row.createCell(2);
          inflowsCell.setCellValue(summary.inflows().doubleValue());
          inflowsCell.setCellStyle(currencyStyle);

          org.apache.poi.ss.usermodel.Cell outflowsCell = row.createCell(3);
          outflowsCell.setCellValue(summary.outflows().doubleValue());
          outflowsCell.setCellStyle(currencyStyle);

          org.apache.poi.ss.usermodel.Cell closingCell = row.createCell(4);
          closingCell.setCellValue(summary.closingBalance().doubleValue());
          closingCell.setCellStyle(currencyStyle);
        }

        // Totals row
        org.apache.poi.ss.usermodel.Row totalsRow = sheet.createRow(rowNum);
        org.apache.poi.ss.usermodel.Cell totalLabel = totalsRow.createCell(0);
        totalLabel.setCellValue("Totals");
        totalLabel.setCellStyle(totalStyle);

        org.apache.poi.ss.usermodel.Cell totalOpening = totalsRow.createCell(1);
        totalOpening.setCellValue(report.openingBalance().doubleValue());
        totalOpening.setCellStyle(totalStyle);

        org.apache.poi.ss.usermodel.Cell totalInflows = totalsRow.createCell(2);
        totalInflows.setCellValue(report.totalInflows().doubleValue());
        totalInflows.setCellStyle(totalStyle);

        org.apache.poi.ss.usermodel.Cell totalOutflows = totalsRow.createCell(3);
        totalOutflows.setCellValue(report.totalOutflows().doubleValue());
        totalOutflows.setCellStyle(totalStyle);

        org.apache.poi.ss.usermodel.Cell totalClosing = totalsRow.createCell(4);
        totalClosing.setCellValue(report.closingBalance().doubleValue());
        totalClosing.setCellStyle(totalStyle);
      }

      // Auto-size columns
      for (int i = 0; i < 5; i++) {
        sheet.autoSizeColumn(i);
      }

      workbook.write(baos);
      log.info("Generated Cashflow Excel ({} bytes)", baos.size());
      return baos.toByteArray();

    } catch (IOException e) {
      log.error("Failed to generate Cashflow Excel", e);
      throw new RuntimeException("Failed to generate Cashflow Excel: " + e.getMessage(), e);
    }
  }

  /** Exports Cashflow Statement report to CSV format. */
  public byte[] exportCashflowToCsv(ReportingService.CashflowStatement report, Company company) {
    StringBuilder csv = new StringBuilder();
    csv.append('\uFEFF'); // UTF-8 BOM

    csv.append(escapeCsvField(company.getName())).append(" - Cashflow Statement\n");
    csv.append("Period: ")
        .append(report.startDate().format(dateFormatter))
        .append(" to ")
        .append(report.endDate().format(dateFormatter))
        .append("\n\n");

    // Summary section
    csv.append("Summary\n");
    csv.append("Opening Balance,").append(report.openingBalance().toPlainString()).append("\n");
    csv.append("Total Inflows,").append(report.totalInflows().toPlainString()).append("\n");
    csv.append("Total Outflows,").append(report.totalOutflows().toPlainString()).append("\n");
    csv.append("Net Cash Flow,").append(report.netCashFlow().toPlainString()).append("\n");
    csv.append("Closing Balance,").append(report.closingBalance().toPlainString()).append("\n\n");

    // Account breakdown
    csv.append("Bank Account Breakdown\n");
    csv.append("Account,Opening,Inflows,Outflows,Net,Closing\n");

    for (ReportingService.CashflowAccountSummary summary : report.accountSummaries()) {
      csv.append(escapeCsvField(summary.account().getName())).append(",");
      csv.append(summary.openingBalance().toPlainString()).append(",");
      csv.append(summary.inflows().toPlainString()).append(",");
      csv.append(summary.outflows().toPlainString()).append(",");
      csv.append(summary.netChange().toPlainString()).append(",");
      csv.append(summary.closingBalance().toPlainString()).append("\n");
    }

    log.info("Generated Cashflow CSV ({} bytes)", csv.length());
    return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }

  // Cashflow PDF helper methods
  private void addSummaryRow(PdfPTable table, String label, BigDecimal amount) {
    PdfPCell labelCell = new PdfPCell(new Phrase(label, TABLE_CELL_FONT));
    labelCell.setBorder(Rectangle.NO_BORDER);
    labelCell.setPadding(6);
    table.addCell(labelCell);

    PdfPCell amountCell = new PdfPCell(new Phrase(formatCurrency(amount), TABLE_CELL_FONT));
    amountCell.setBorder(Rectangle.NO_BORDER);
    amountCell.setPadding(6);
    amountCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
    table.addCell(amountCell);
  }

  private void addNetCashFlowRow(PdfPTable table, String label, BigDecimal amount) {
    Color textColor = amount.compareTo(BigDecimal.ZERO) >= 0 ? PROFIT_COLOR : LOSS_COLOR;

    PdfPCell labelCell = new PdfPCell(new Phrase(label, TABLE_CELL_BOLD));
    labelCell.setBorder(Rectangle.TOP | Rectangle.BOTTOM);
    labelCell.setBorderWidth(1);
    labelCell.setPadding(8);
    labelCell.setBackgroundColor(TOTAL_BG);
    table.addCell(labelCell);

    PdfPCell amountCell =
        new PdfPCell(
            new Phrase(formatCurrency(amount), new Font(Font.HELVETICA, 10, Font.BOLD, textColor)));
    amountCell.setBorder(Rectangle.TOP | Rectangle.BOTTOM);
    amountCell.setBorderWidth(1);
    amountCell.setPadding(8);
    amountCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
    amountCell.setBackgroundColor(TOTAL_BG);
    table.addCell(amountCell);
  }

  private void addInflowCell(PdfPTable table, BigDecimal amount, Color bgColor) {
    PdfPCell cell =
        new PdfPCell(
            new Phrase(
                formatCurrency(amount), new Font(Font.HELVETICA, 9, Font.NORMAL, PROFIT_COLOR)));
    cell.setBackgroundColor(bgColor);
    cell.setPadding(6);
    cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
    cell.setBorderColor(Color.LIGHT_GRAY);
    table.addCell(cell);
  }

  private void addOutflowCell(PdfPTable table, BigDecimal amount, Color bgColor) {
    PdfPCell cell =
        new PdfPCell(
            new Phrase(
                formatCurrency(amount), new Font(Font.HELVETICA, 9, Font.NORMAL, LOSS_COLOR)));
    cell.setBackgroundColor(bgColor);
    cell.setPadding(6);
    cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
    cell.setBorderColor(Color.LIGHT_GRAY);
    table.addCell(cell);
  }

  // Cashflow Excel helper methods
  private void addExcelSummaryRow(
      Sheet sheet, int rowNum, String label, BigDecimal amount, CellStyle currencyStyle) {
    org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum);
    row.createCell(0).setCellValue(label);
    org.apache.poi.ss.usermodel.Cell amountCell = row.createCell(1);
    amountCell.setCellValue(amount.doubleValue());
    amountCell.setCellStyle(currencyStyle);
  }

  // ==================== BANK REGISTER EXPORTS ====================

  /**
   * Exports Bank Register report to PDF format. Shows a chronological list of transactions with
   * running balance for a bank account.
   *
   * <p>This report provides an audit trail of all cash movements through a specific bank account,
   * essential for bank reconciliation and financial audits.
   */
  public byte[] exportBankRegisterToPdf(ReportingService.BankRegister report, Company company) {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      Document document = new Document(PageSize.A4.rotate()); // Landscape for more columns
      PdfWriter.getInstance(document, baos);
      document.open();

      // Title
      addReportTitle(document, company, "Bank Register");
      addReportSubtitle(
          document,
          "Account: " + report.bankAccount().getCode() + " - " + report.bankAccount().getName());
      addReportSubtitle(
          document,
          "Period: "
              + report.startDate().format(dateFormatter)
              + " to "
              + report.endDate().format(dateFormatter));

      // Reconciliation status
      String reconcileStatus = report.isReconciled() ? "RECONCILED" : "UNRECONCILED";
      Paragraph status =
          new Paragraph(
              reconcileStatus,
              new Font(
                  Font.HELVETICA,
                  10,
                  Font.BOLD,
                  report.isReconciled() ? PROFIT_COLOR : LOSS_COLOR));
      status.setAlignment(Element.ALIGN_CENTER);
      status.setSpacingAfter(10);
      document.add(status);

      // Opening balance
      Paragraph openingBal =
          new Paragraph(
              "Opening Balance: " + formatCurrency(report.openingBalance()), TABLE_CELL_BOLD);
      openingBal.setSpacingAfter(10);
      document.add(openingBal);

      // Transaction table
      PdfPTable table = new PdfPTable(6);
      table.setWidthPercentage(100);
      table.setWidths(new float[] {1.5f, 1.5f, 3f, 1.5f, 1.5f, 2f});

      addTableHeader(table, "Date");
      addTableHeader(table, "Reference");
      addTableHeader(table, "Description");
      addTableHeader(table, "Debit");
      addTableHeader(table, "Credit");
      addTableHeader(table, "Balance");

      boolean alternate = false;
      for (ReportingService.BankRegisterLine line : report.lines()) {
        Color bg = alternate ? ALT_ROW_BG : Color.WHITE;
        addTableCell(table, line.date().format(dateFormatter), bg, Element.ALIGN_LEFT);
        addTableCell(
            table, line.reference() != null ? line.reference() : "", bg, Element.ALIGN_LEFT);
        addTableCell(
            table, line.description() != null ? line.description() : "", bg, Element.ALIGN_LEFT);
        addBankRegisterAmountCell(table, line.debit(), bg, true);
        addBankRegisterAmountCell(table, line.credit(), bg, false);
        addTableCell(table, formatCurrency(line.runningBalance()), bg, Element.ALIGN_RIGHT);
        alternate = !alternate;
      }

      // Totals row
      addTotalCell(table, "");
      addTotalCell(table, "");
      addTotalCell(table, "Totals");
      addTotalCell(table, formatCurrency(report.totalDebits()));
      addTotalCell(table, formatCurrency(report.totalCredits()));
      addTotalCell(table, formatCurrency(report.closingBalance()));

      document.add(table);

      // Summary section
      document.add(Chunk.NEWLINE);
      PdfPTable summaryTable = new PdfPTable(2);
      summaryTable.setWidthPercentage(40);
      summaryTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
      summaryTable.setWidths(new float[] {2f, 1.5f});

      addBankRegisterSummaryRow(summaryTable, "Opening Balance", report.openingBalance());
      addBankRegisterSummaryRow(summaryTable, "Total Debits (Deposits)", report.totalDebits());
      addBankRegisterSummaryRow(summaryTable, "Total Credits (Withdrawals)", report.totalCredits());
      addBankRegisterSummaryRow(summaryTable, "Net Change", report.netChange());

      // Closing balance with emphasis
      PdfPCell closingLabel = new PdfPCell(new Phrase("Closing Balance", TABLE_CELL_BOLD));
      closingLabel.setBorder(Rectangle.TOP);
      closingLabel.setBorderWidth(2);
      closingLabel.setPadding(8);
      closingLabel.setBackgroundColor(TOTAL_BG);
      summaryTable.addCell(closingLabel);

      PdfPCell closingAmount =
          new PdfPCell(new Phrase(formatCurrency(report.closingBalance()), TABLE_CELL_BOLD));
      closingAmount.setBorder(Rectangle.TOP);
      closingAmount.setBorderWidth(2);
      closingAmount.setPadding(8);
      closingAmount.setHorizontalAlignment(Element.ALIGN_RIGHT);
      closingAmount.setBackgroundColor(TOTAL_BG);
      summaryTable.addCell(closingAmount);

      document.add(summaryTable);

      addReportFooter(document);
      document.close();

      log.info("Generated Bank Register PDF ({} bytes)", baos.size());
      return baos.toByteArray();

    } catch (Exception e) {
      log.error("Failed to generate Bank Register PDF", e);
      throw new RuntimeException("Failed to generate Bank Register PDF: " + e.getMessage(), e);
    }
  }

  /** Exports Bank Register report to Excel format. */
  public byte[] exportBankRegisterToExcel(ReportingService.BankRegister report, Company company) {
    try (XSSFWorkbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

      Sheet sheet = workbook.createSheet("Bank Register");
      int rowNum = 0;

      CellStyle headerStyle = createHeaderStyle(workbook);
      CellStyle currencyStyle = createCurrencyStyle(workbook);
      CellStyle titleStyle = createTitleStyle(workbook);
      CellStyle totalStyle = createTotalStyle(workbook);

      // Title
      org.apache.poi.ss.usermodel.Row titleRow = sheet.createRow(rowNum++);
      org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
      titleCell.setCellValue(company.getName() + " - Bank Register");
      titleCell.setCellStyle(titleStyle);
      sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));

      // Account info
      org.apache.poi.ss.usermodel.Row accountRow = sheet.createRow(rowNum++);
      accountRow
          .createCell(0)
          .setCellValue(
              "Account: "
                  + report.bankAccount().getCode()
                  + " - "
                  + report.bankAccount().getName());

      // Period
      org.apache.poi.ss.usermodel.Row dateRow = sheet.createRow(rowNum++);
      dateRow
          .createCell(0)
          .setCellValue(
              "Period: "
                  + report.startDate().format(dateFormatter)
                  + " to "
                  + report.endDate().format(dateFormatter));

      // Status
      org.apache.poi.ss.usermodel.Row statusRow = sheet.createRow(rowNum++);
      statusRow.createCell(0).setCellValue(report.isReconciled() ? "RECONCILED" : "UNRECONCILED");

      // Opening balance
      org.apache.poi.ss.usermodel.Row openingRow = sheet.createRow(rowNum++);
      openingRow.createCell(0).setCellValue("Opening Balance");
      org.apache.poi.ss.usermodel.Cell openingCell = openingRow.createCell(5);
      openingCell.setCellValue(report.openingBalance().doubleValue());
      openingCell.setCellStyle(currencyStyle);

      rowNum++; // Empty row

      // Header row
      org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(rowNum++);
      String[] headers = {"Date", "Reference", "Description", "Debit", "Credit", "Balance"};
      for (int i = 0; i < headers.length; i++) {
        org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
        cell.setCellValue(headers[i]);
        cell.setCellStyle(headerStyle);
      }

      // Data rows
      for (ReportingService.BankRegisterLine line : report.lines()) {
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
        row.createCell(0).setCellValue(line.date().format(dateFormatter));
        row.createCell(1).setCellValue(line.reference() != null ? line.reference() : "");
        row.createCell(2).setCellValue(line.description() != null ? line.description() : "");

        if (line.debit() != null && line.debit().compareTo(BigDecimal.ZERO) > 0) {
          org.apache.poi.ss.usermodel.Cell debitCell = row.createCell(3);
          debitCell.setCellValue(line.debit().doubleValue());
          debitCell.setCellStyle(currencyStyle);
        } else {
          row.createCell(3).setCellValue("");
        }

        if (line.credit() != null && line.credit().compareTo(BigDecimal.ZERO) > 0) {
          org.apache.poi.ss.usermodel.Cell creditCell = row.createCell(4);
          creditCell.setCellValue(line.credit().doubleValue());
          creditCell.setCellStyle(currencyStyle);
        } else {
          row.createCell(4).setCellValue("");
        }

        org.apache.poi.ss.usermodel.Cell balanceCell = row.createCell(5);
        balanceCell.setCellValue(line.runningBalance().doubleValue());
        balanceCell.setCellStyle(currencyStyle);
      }

      // Totals row
      org.apache.poi.ss.usermodel.Row totalsRow = sheet.createRow(rowNum++);
      totalsRow.createCell(0);
      totalsRow.createCell(1);
      org.apache.poi.ss.usermodel.Cell totalLabel = totalsRow.createCell(2);
      totalLabel.setCellValue("Totals");
      totalLabel.setCellStyle(totalStyle);

      org.apache.poi.ss.usermodel.Cell totalDebits = totalsRow.createCell(3);
      totalDebits.setCellValue(report.totalDebits().doubleValue());
      totalDebits.setCellStyle(totalStyle);

      org.apache.poi.ss.usermodel.Cell totalCredits = totalsRow.createCell(4);
      totalCredits.setCellValue(report.totalCredits().doubleValue());
      totalCredits.setCellStyle(totalStyle);

      org.apache.poi.ss.usermodel.Cell closingBalance = totalsRow.createCell(5);
      closingBalance.setCellValue(report.closingBalance().doubleValue());
      closingBalance.setCellStyle(totalStyle);

      rowNum++; // Empty row

      // Summary section
      org.apache.poi.ss.usermodel.Row summaryOpeningRow = sheet.createRow(rowNum++);
      summaryOpeningRow.createCell(4).setCellValue("Opening Balance");
      org.apache.poi.ss.usermodel.Cell summaryOpeningCell = summaryOpeningRow.createCell(5);
      summaryOpeningCell.setCellValue(report.openingBalance().doubleValue());
      summaryOpeningCell.setCellStyle(currencyStyle);

      org.apache.poi.ss.usermodel.Row summaryDebitsRow = sheet.createRow(rowNum++);
      summaryDebitsRow.createCell(4).setCellValue("Total Debits");
      org.apache.poi.ss.usermodel.Cell summaryDebitsCell = summaryDebitsRow.createCell(5);
      summaryDebitsCell.setCellValue(report.totalDebits().doubleValue());
      summaryDebitsCell.setCellStyle(currencyStyle);

      org.apache.poi.ss.usermodel.Row summaryCreditsRow = sheet.createRow(rowNum++);
      summaryCreditsRow.createCell(4).setCellValue("Total Credits");
      org.apache.poi.ss.usermodel.Cell summaryCreditsCell = summaryCreditsRow.createCell(5);
      summaryCreditsCell.setCellValue(report.totalCredits().doubleValue());
      summaryCreditsCell.setCellStyle(currencyStyle);

      org.apache.poi.ss.usermodel.Row summaryNetRow = sheet.createRow(rowNum++);
      summaryNetRow.createCell(4).setCellValue("Net Change");
      org.apache.poi.ss.usermodel.Cell summaryNetCell = summaryNetRow.createCell(5);
      summaryNetCell.setCellValue(report.netChange().doubleValue());
      summaryNetCell.setCellStyle(currencyStyle);

      org.apache.poi.ss.usermodel.Row summaryClosingRow = sheet.createRow(rowNum);
      org.apache.poi.ss.usermodel.Cell closingLabelCell = summaryClosingRow.createCell(4);
      closingLabelCell.setCellValue("Closing Balance");
      closingLabelCell.setCellStyle(totalStyle);
      org.apache.poi.ss.usermodel.Cell summaryClosingCell = summaryClosingRow.createCell(5);
      summaryClosingCell.setCellValue(report.closingBalance().doubleValue());
      summaryClosingCell.setCellStyle(totalStyle);

      // Auto-size columns
      for (int i = 0; i < 6; i++) {
        sheet.autoSizeColumn(i);
      }

      workbook.write(baos);
      log.info("Generated Bank Register Excel ({} bytes)", baos.size());
      return baos.toByteArray();

    } catch (IOException e) {
      log.error("Failed to generate Bank Register Excel", e);
      throw new RuntimeException("Failed to generate Bank Register Excel: " + e.getMessage(), e);
    }
  }

  /** Exports Bank Register report to CSV format. */
  public byte[] exportBankRegisterToCsv(ReportingService.BankRegister report, Company company) {
    StringBuilder csv = new StringBuilder();
    csv.append('\uFEFF'); // UTF-8 BOM

    csv.append(escapeCsvField(company.getName())).append(" - Bank Register\n");
    csv.append("Account: ")
        .append(escapeCsvField(report.bankAccount().getCode()))
        .append(" - ")
        .append(escapeCsvField(report.bankAccount().getName()))
        .append("\n");
    csv.append("Period: ")
        .append(report.startDate().format(dateFormatter))
        .append(" to ")
        .append(report.endDate().format(dateFormatter))
        .append("\n");
    csv.append("Status: ")
        .append(report.isReconciled() ? "Reconciled" : "Unreconciled")
        .append("\n\n");

    // Header
    csv.append("Date,Reference,Description,Type,Debit,Credit,Running Balance\n");

    // Transaction lines
    for (ReportingService.BankRegisterLine line : report.lines()) {
      csv.append(line.date().format(dateFormatter)).append(",");
      csv.append(escapeCsvField(line.reference() != null ? line.reference() : "")).append(",");
      csv.append(escapeCsvField(line.description() != null ? line.description() : "")).append(",");
      csv.append(line.transactionType() != null ? line.transactionType() : "").append(",");
      csv.append(
              line.debit() != null && line.debit().compareTo(BigDecimal.ZERO) > 0
                  ? line.debit().toPlainString()
                  : "")
          .append(",");
      csv.append(
              line.credit() != null && line.credit().compareTo(BigDecimal.ZERO) > 0
                  ? line.credit().toPlainString()
                  : "")
          .append(",");
      csv.append(line.runningBalance().toPlainString()).append("\n");
    }

    // Summary
    csv.append("\nSummary\n");
    csv.append("Opening Balance,").append(report.openingBalance().toPlainString()).append("\n");
    csv.append("Total Debits,").append(report.totalDebits().toPlainString()).append("\n");
    csv.append("Total Credits,").append(report.totalCredits().toPlainString()).append("\n");
    csv.append("Net Change,").append(report.netChange().toPlainString()).append("\n");
    csv.append("Closing Balance,").append(report.closingBalance().toPlainString()).append("\n");

    log.info("Generated Bank Register CSV ({} bytes)", csv.length());
    return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }

  // Bank Register PDF helper methods
  private void addBankRegisterAmountCell(
      PdfPTable table, BigDecimal amount, Color bgColor, boolean isDebit) {
    String text = "";
    if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
      text = formatCurrency(amount);
    }
    Color textColor =
        isDebit
            ? PROFIT_COLOR
            : (amount != null && amount.compareTo(BigDecimal.ZERO) > 0 ? LOSS_COLOR : Color.BLACK);
    PdfPCell cell =
        new PdfPCell(new Phrase(text, new Font(Font.HELVETICA, 9, Font.NORMAL, textColor)));
    cell.setBackgroundColor(bgColor);
    cell.setPadding(6);
    cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
    cell.setBorderColor(Color.LIGHT_GRAY);
    table.addCell(cell);
  }

  private void addBankRegisterSummaryRow(PdfPTable table, String label, BigDecimal amount) {
    PdfPCell labelCell = new PdfPCell(new Phrase(label, TABLE_CELL_FONT));
    labelCell.setBorder(Rectangle.NO_BORDER);
    labelCell.setPadding(4);
    table.addCell(labelCell);

    PdfPCell amountCell = new PdfPCell(new Phrase(formatCurrency(amount), TABLE_CELL_FONT));
    amountCell.setBorder(Rectangle.NO_BORDER);
    amountCell.setPadding(4);
    amountCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
    table.addCell(amountCell);
  }

  // ==================== RECONCILIATION STATUS EXPORTS ====================

  /**
   * Exports Bank Reconciliation Status report to PDF format. Shows the reconciliation status of all
   * bank accounts with item counts and unreconciled amounts.
   */
  public byte[] exportReconciliationStatusToPdf(
      ReportingService.ReconciliationStatus report, Company company) {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      Document document = new Document(PageSize.A4.rotate()); // Landscape for more columns
      PdfWriter.getInstance(document, baos);
      document.open();

      // Title
      addReportTitle(document, company, "Bank Reconciliation Status");
      addReportSubtitle(document, "As of: " + report.asOfDate().format(dateFormatter));

      // Overall status
      boolean isFullyReconciled =
          report.grandTotal() > 0
              && report.overallReconciledPercent().compareTo(new BigDecimal("100")) >= 0;
      String statusText = isFullyReconciled ? "FULLY RECONCILED" : "ITEMS PENDING";
      Paragraph status =
          new Paragraph(
              statusText,
              new Font(
                  Font.HELVETICA, 10, Font.BOLD, isFullyReconciled ? PROFIT_COLOR : LOSS_COLOR));
      status.setAlignment(Element.ALIGN_CENTER);
      status.setSpacingAfter(10);
      document.add(status);

      // Summary statistics table
      PdfPTable summaryTable = new PdfPTable(7);
      summaryTable.setWidthPercentage(100);
      summaryTable.setSpacingBefore(10);
      summaryTable.setSpacingAfter(15);

      String[] summaryHeaders = {
        "Total Items", "New", "Matched", "Created", "Ignored", "Unreconciled $", "Reconciled %"
      };
      for (String header : summaryHeaders) {
        PdfPCell cell = new PdfPCell(new Phrase(header, TABLE_HEADER_FONT));
        cell.setBackgroundColor(HEADER_BG);
        cell.setPadding(8);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        summaryTable.addCell(cell);
      }

      addSummaryCellCenter(summaryTable, String.valueOf(report.grandTotal()));
      addSummaryCellCenter(summaryTable, String.valueOf(report.totalNew()));
      addSummaryCellCenter(summaryTable, String.valueOf(report.totalMatched()));
      addSummaryCellCenter(summaryTable, String.valueOf(report.totalCreated()));
      addSummaryCellCenter(summaryTable, String.valueOf(report.totalIgnored()));
      addSummaryCellCenter(summaryTable, formatCurrency(report.totalUnreconciledAmount()));
      addSummaryCellCenter(
          summaryTable,
          report.overallReconciledPercent().setScale(1, java.math.RoundingMode.HALF_UP) + "%");

      document.add(summaryTable);

      // Account details section
      Paragraph detailsTitle = new Paragraph("Account Details", SECTION_FONT);
      detailsTitle.setSpacingBefore(10);
      detailsTitle.setSpacingAfter(10);
      document.add(detailsTitle);

      // Account details table
      PdfPTable table = new PdfPTable(9);
      table.setWidthPercentage(100);
      float[] columnWidths = {60f, 120f, 40f, 50f, 50f, 45f, 40f, 70f, 60f};
      table.setWidths(columnWidths);

      String[] headers = {
        "Account",
        "Name",
        "New",
        "Matched",
        "Created",
        "Ignored",
        "Total",
        "Unreconciled $",
        "Reconciled %"
      };
      for (String header : headers) {
        PdfPCell cell = new PdfPCell(new Phrase(header, TABLE_HEADER_FONT));
        cell.setBackgroundColor(HEADER_BG);
        cell.setPadding(6);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
      }

      int rowIndex = 0;
      for (ReportingService.ReconciliationAccountSummary summary : report.accountSummaries()) {
        Color bgColor = rowIndex % 2 == 0 ? Color.WHITE : ALT_ROW_BG;
        boolean isReconciled = summary.reconciledPercent().compareTo(new BigDecimal("100")) >= 0;

        addTableCell(table, summary.account().getCode(), bgColor, Element.ALIGN_LEFT);
        addTableCell(table, summary.account().getName(), bgColor, Element.ALIGN_LEFT);
        addTableCell(table, String.valueOf(summary.newCount()), bgColor, Element.ALIGN_RIGHT);
        addTableCell(table, String.valueOf(summary.matchedCount()), bgColor, Element.ALIGN_RIGHT);
        addTableCell(table, String.valueOf(summary.createdCount()), bgColor, Element.ALIGN_RIGHT);
        addTableCell(table, String.valueOf(summary.ignoredCount()), bgColor, Element.ALIGN_RIGHT);
        addTableCell(table, String.valueOf(summary.totalItems()), bgColor, Element.ALIGN_RIGHT);
        addTableCell(
            table, formatCurrency(summary.unreconciledAmount()), bgColor, Element.ALIGN_RIGHT);

        // Color-code reconciled percentage
        Font percentFont =
            new Font(Font.HELVETICA, 9, Font.BOLD, isReconciled ? PROFIT_COLOR : LOSS_COLOR);
        String percentText =
            summary.reconciledPercent().setScale(1, java.math.RoundingMode.HALF_UP) + "%";
        PdfPCell percentCell = new PdfPCell(new Phrase(percentText, percentFont));
        percentCell.setBackgroundColor(bgColor);
        percentCell.setPadding(6);
        percentCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        percentCell.setBorderColor(Color.LIGHT_GRAY);
        table.addCell(percentCell);

        rowIndex++;
      }

      document.add(table);

      // Footer with generation timestamp
      Paragraph footer =
          new Paragraph(
              "Generated: " + LocalDate.now().format(dateFormatter) + " | MoniWorks", SMALL_FONT);
      footer.setAlignment(Element.ALIGN_CENTER);
      footer.setSpacingBefore(20);
      document.add(footer);

      document.close();
      log.info("Generated Reconciliation Status PDF ({} bytes)", baos.size());
      return baos.toByteArray();

    } catch (DocumentException | IOException e) {
      log.error("Failed to generate Reconciliation Status PDF", e);
      throw new RuntimeException(
          "Failed to generate Reconciliation Status PDF: " + e.getMessage(), e);
    }
  }

  /** Exports Bank Reconciliation Status report to Excel format. */
  public byte[] exportReconciliationStatusToExcel(
      ReportingService.ReconciliationStatus report, Company company) {
    try (XSSFWorkbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

      Sheet sheet = workbook.createSheet("Reconciliation Status");
      int rowNum = 0;

      CellStyle headerStyle = createHeaderStyle(workbook);
      CellStyle currencyStyle = createCurrencyStyle(workbook);
      CellStyle titleStyle = createTitleStyle(workbook);
      CellStyle percentStyle = createPercentStyle(workbook);

      // Title
      org.apache.poi.ss.usermodel.Row titleRow = sheet.createRow(rowNum++);
      org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
      titleCell.setCellValue(company.getName() + " - Bank Reconciliation Status");
      titleCell.setCellStyle(titleStyle);
      sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 8));

      // As of date
      org.apache.poi.ss.usermodel.Row dateRow = sheet.createRow(rowNum++);
      dateRow.createCell(0).setCellValue("As of: " + report.asOfDate().format(dateFormatter));

      // Status
      org.apache.poi.ss.usermodel.Row statusRow = sheet.createRow(rowNum++);
      boolean isFullyReconciled =
          report.grandTotal() > 0
              && report.overallReconciledPercent().compareTo(new BigDecimal("100")) >= 0;
      statusRow
          .createCell(0)
          .setCellValue(isFullyReconciled ? "FULLY RECONCILED" : "ITEMS PENDING");

      rowNum++; // Empty row

      // Summary section
      org.apache.poi.ss.usermodel.Row summaryLabelRow = sheet.createRow(rowNum++);
      summaryLabelRow.createCell(0).setCellValue("Summary");

      org.apache.poi.ss.usermodel.Row summaryHeaderRow = sheet.createRow(rowNum++);
      String[] summaryHeaders = {
        "Total Items", "New", "Matched", "Created", "Ignored", "Unreconciled $", "Reconciled %"
      };
      for (int i = 0; i < summaryHeaders.length; i++) {
        org.apache.poi.ss.usermodel.Cell cell = summaryHeaderRow.createCell(i);
        cell.setCellValue(summaryHeaders[i]);
        cell.setCellStyle(headerStyle);
      }

      org.apache.poi.ss.usermodel.Row summaryDataRow = sheet.createRow(rowNum++);
      summaryDataRow.createCell(0).setCellValue(report.grandTotal());
      summaryDataRow.createCell(1).setCellValue(report.totalNew());
      summaryDataRow.createCell(2).setCellValue(report.totalMatched());
      summaryDataRow.createCell(3).setCellValue(report.totalCreated());
      summaryDataRow.createCell(4).setCellValue(report.totalIgnored());

      org.apache.poi.ss.usermodel.Cell unreconciledCell = summaryDataRow.createCell(5);
      unreconciledCell.setCellValue(report.totalUnreconciledAmount().doubleValue());
      unreconciledCell.setCellStyle(currencyStyle);

      org.apache.poi.ss.usermodel.Cell percentCell = summaryDataRow.createCell(6);
      percentCell.setCellValue(report.overallReconciledPercent().doubleValue() / 100);
      percentCell.setCellStyle(percentStyle);

      rowNum++; // Empty row

      // Account details section
      org.apache.poi.ss.usermodel.Row detailsLabelRow = sheet.createRow(rowNum++);
      detailsLabelRow.createCell(0).setCellValue("Account Details");

      // Header row for account details
      org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(rowNum++);
      String[] headers = {
        "Account",
        "Name",
        "New",
        "Matched",
        "Created",
        "Ignored",
        "Total",
        "Unreconciled $",
        "Reconciled %",
        "Oldest Pending"
      };
      for (int i = 0; i < headers.length; i++) {
        org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
        cell.setCellValue(headers[i]);
        cell.setCellStyle(headerStyle);
      }

      // Data rows
      for (ReportingService.ReconciliationAccountSummary summary : report.accountSummaries()) {
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
        row.createCell(0).setCellValue(summary.account().getCode());
        row.createCell(1).setCellValue(summary.account().getName());
        row.createCell(2).setCellValue(summary.newCount());
        row.createCell(3).setCellValue(summary.matchedCount());
        row.createCell(4).setCellValue(summary.createdCount());
        row.createCell(5).setCellValue(summary.ignoredCount());
        row.createCell(6).setCellValue(summary.totalItems());

        org.apache.poi.ss.usermodel.Cell amountCell = row.createCell(7);
        amountCell.setCellValue(summary.unreconciledAmount().doubleValue());
        amountCell.setCellStyle(currencyStyle);

        org.apache.poi.ss.usermodel.Cell pctCell = row.createCell(8);
        pctCell.setCellValue(summary.reconciledPercent().doubleValue() / 100);
        pctCell.setCellStyle(percentStyle);

        row.createCell(9)
            .setCellValue(
                summary.oldestUnmatchedDate() != null
                    ? summary.oldestUnmatchedDate().format(dateFormatter)
                    : "");
      }

      // Auto-size columns
      for (int i = 0; i < 10; i++) {
        sheet.autoSizeColumn(i);
      }

      workbook.write(baos);
      log.info("Generated Reconciliation Status Excel ({} bytes)", baos.size());
      return baos.toByteArray();

    } catch (IOException e) {
      log.error("Failed to generate Reconciliation Status Excel", e);
      throw new RuntimeException(
          "Failed to generate Reconciliation Status Excel: " + e.getMessage(), e);
    }
  }

  /** Exports Reconciliation Status report to CSV format. */
  public byte[] exportReconciliationStatusToCsv(
      ReportingService.ReconciliationStatus report, Company company) {
    StringBuilder csv = new StringBuilder();
    csv.append('\uFEFF'); // UTF-8 BOM

    csv.append(escapeCsvField(company.getName())).append(" - Reconciliation Status\n");
    csv.append("As of ").append(report.asOfDate().format(dateFormatter)).append("\n\n");

    // Summary
    csv.append("Summary\n");
    csv.append("Total Items,").append(report.grandTotal()).append("\n");
    csv.append("Matched Items,").append(report.totalMatched()).append("\n");
    csv.append("New (Unmatched) Items,").append(report.totalNew()).append("\n");
    csv.append("Total Unreconciled Amount,")
        .append(report.totalUnreconciledAmount().toPlainString())
        .append("\n\n");

    // Account breakdown
    csv.append("Account Breakdown\n");
    csv.append(
        "Code,Account,New,Matched,Created,Ignored,Total,Unreconciled $,Reconciled %,Oldest Pending\n");

    for (ReportingService.ReconciliationAccountSummary summary : report.accountSummaries()) {
      csv.append(escapeCsvField(summary.account().getCode())).append(",");
      csv.append(escapeCsvField(summary.account().getName())).append(",");
      csv.append(summary.newCount()).append(",");
      csv.append(summary.matchedCount()).append(",");
      csv.append(summary.createdCount()).append(",");
      csv.append(summary.ignoredCount()).append(",");
      csv.append(summary.totalItems()).append(",");
      csv.append(summary.unreconciledAmount().toPlainString()).append(",");
      csv.append(summary.reconciledPercent().toPlainString()).append("%,");
      csv.append(
              summary.oldestUnmatchedDate() != null
                  ? summary.oldestUnmatchedDate().format(dateFormatter)
                  : "")
          .append("\n");
    }

    log.info("Generated Reconciliation Status CSV ({} bytes)", csv.length());
    return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }

  private void addSummaryCellCenter(PdfPTable table, String text) {
    PdfPCell cell = new PdfPCell(new Phrase(text, TABLE_CELL_BOLD));
    cell.setPadding(8);
    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
    cell.setBorderColor(Color.LIGHT_GRAY);
    table.addCell(cell);
  }
}
