package com.example.application.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.domain.Company;
import com.example.application.domain.Contact;
import com.example.application.domain.PaymentRun;
import com.example.application.service.PaymentRunService.PaymentRunBill;

/**
 * Service for exporting payment runs to direct credit file formats for bank submission.
 *
 * <p>Supports multiple formats: - CSV: Generic comma-separated format compatible with most NZ banks
 * - ABA: Australian Banking Association format (also used by ANZ NZ)
 *
 * <p>File formats follow common banking standards for batch payment submission.
 */
@Service
@Transactional(readOnly = true)
public class DirectCreditExportService {

  private final PaymentRunService paymentRunService;
  private final CompanyService companyService;

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final DateTimeFormatter ABA_DATE_FORMAT = DateTimeFormatter.ofPattern("ddMMyy");

  /** Supported export formats. */
  public enum ExportFormat {
    CSV("CSV (Generic)", "csv"),
    ABA("ABA (ANZ/Westpac)", "aba");

    private final String displayName;
    private final String fileExtension;

    ExportFormat(String displayName, String fileExtension) {
      this.displayName = displayName;
      this.fileExtension = fileExtension;
    }

    public String getDisplayName() {
      return displayName;
    }

    public String getFileExtension() {
      return fileExtension;
    }
  }

  /** Result of a direct credit export operation. */
  public record ExportResult(
      byte[] content,
      String filename,
      String contentType,
      int paymentCount,
      BigDecimal totalAmount,
      List<String> warnings) {}

  public DirectCreditExportService(
      PaymentRunService paymentRunService, CompanyService companyService) {
    this.paymentRunService = paymentRunService;
    this.companyService = companyService;
  }

  /**
   * Exports a payment run to the specified format.
   *
   * @param paymentRun The completed payment run to export
   * @param format The desired export format
   * @return ExportResult containing the file content and metadata
   */
  public ExportResult exportPaymentRun(PaymentRun paymentRun, ExportFormat format) {
    if (!paymentRun.isCompleted()) {
      throw new IllegalStateException("Cannot export draft payment run");
    }

    List<PaymentRunBill> runBills = paymentRunService.getRunBills(paymentRun);
    if (runBills.isEmpty()) {
      throw new IllegalStateException("Payment run has no bills");
    }

    return switch (format) {
      case CSV -> exportToCsv(paymentRun, runBills);
      case ABA -> exportToAba(paymentRun, runBills);
    };
  }

  /**
   * Exports to generic CSV format suitable for most bank portals.
   *
   * <p>Format: Payee Name, Bank Account Number, Bank Code (BSB/Branch), Amount, Reference,
   * Particulars, Code
   */
  private ExportResult exportToCsv(PaymentRun paymentRun, List<PaymentRunBill> runBills) {
    StringBuilder csv = new StringBuilder();
    List<String> warnings = new ArrayList<>();

    // UTF-8 BOM for Excel compatibility
    csv.append("\uFEFF");

    // Header row
    csv.append(
        "Payee Name,Bank Account Number,Bank Code,Amount,Reference,Particulars,Code,Bill Numbers\n");

    // Group bills by supplier
    Map<Contact, List<PaymentRunBill>> bySupplier =
        runBills.stream().collect(Collectors.groupingBy(rb -> rb.bill().getContact()));

    int paymentCount = 0;
    BigDecimal totalAmount = BigDecimal.ZERO;

    for (Map.Entry<Contact, List<PaymentRunBill>> entry : bySupplier.entrySet()) {
      Contact supplier = entry.getKey();
      List<PaymentRunBill> supplierBills = entry.getValue();

      // Validate bank details
      if (supplier.getBankAccountNumber() == null || supplier.getBankAccountNumber().isBlank()) {
        warnings.add("Supplier '" + supplier.getName() + "' has no bank account number configured");
        continue;
      }

      BigDecimal supplierTotal =
          supplierBills.stream()
              .map(PaymentRunBill::amount)
              .reduce(BigDecimal.ZERO, BigDecimal::add);

      String billNumbers =
          supplierBills.stream()
              .map(rb -> rb.bill().getBillNumber())
              .collect(Collectors.joining("; "));

      csv.append(escapeCsvField(supplier.getName())).append(",");
      csv.append(escapeCsvField(cleanAccountNumber(supplier.getBankAccountNumber()))).append(",");
      csv.append(escapeCsvField(supplier.getBankRouting() != null ? supplier.getBankRouting() : ""))
          .append(",");
      csv.append(supplierTotal.setScale(2).toPlainString()).append(",");
      csv.append(escapeCsvField("PR" + paymentRun.getId())).append(","); // Reference
      csv.append(escapeCsvField(paymentRun.getCompany().getName())).append(","); // Particulars
      csv.append(escapeCsvField("PAYMENT")).append(","); // Code
      csv.append(escapeCsvField(billNumbers)).append("\n");

      paymentCount++;
      totalAmount = totalAmount.add(supplierTotal);
    }

    String filename =
        "direct-credit-"
            + paymentRun.getCompany().getId()
            + "-"
            + DATE_FORMAT.format(paymentRun.getRunDate())
            + ".csv";

    return new ExportResult(
        csv.toString().getBytes(StandardCharsets.UTF_8),
        filename,
        "text/csv",
        paymentCount,
        totalAmount,
        warnings);
  }

  /**
   * Exports to ABA (Australian Banking Association) format.
   *
   * <p>This format is used by ANZ, Westpac, and other banks in Australia and NZ. Fixed-width format
   * with 120-byte records.
   *
   * <p>Record types: 0 = Header, 1 = Detail, 7 = Trailer
   */
  private ExportResult exportToAba(PaymentRun paymentRun, List<PaymentRunBill> runBills) {
    StringBuilder aba = new StringBuilder();
    List<String> warnings = new ArrayList<>();

    Company company = paymentRun.getCompany();

    // Group bills by supplier
    Map<Contact, List<PaymentRunBill>> bySupplier =
        runBills.stream().collect(Collectors.groupingBy(rb -> rb.bill().getContact()));

    int recordCount = 0;
    BigDecimal totalAmount = BigDecimal.ZERO;
    int paymentCount = 0;

    // Prepare detail records first to calculate totals
    List<String> detailRecords = new ArrayList<>();

    for (Map.Entry<Contact, List<PaymentRunBill>> entry : bySupplier.entrySet()) {
      Contact supplier = entry.getKey();
      List<PaymentRunBill> supplierBills = entry.getValue();

      // Validate bank details
      if (supplier.getBankAccountNumber() == null || supplier.getBankAccountNumber().isBlank()) {
        warnings.add("Supplier '" + supplier.getName() + "' has no bank account number configured");
        continue;
      }

      BigDecimal supplierTotal =
          supplierBills.stream()
              .map(PaymentRunBill::amount)
              .reduce(BigDecimal.ZERO, BigDecimal::add);

      // Build detail record (Type 1)
      String detailRecord = buildAbaDetailRecord(supplier, supplierTotal, paymentRun, company);
      detailRecords.add(detailRecord);

      paymentCount++;
      totalAmount = totalAmount.add(supplierTotal);
      recordCount++;
    }

    // Header record (Type 0)
    aba.append(buildAbaHeaderRecord(company, paymentRun.getRunDate()));
    aba.append("\r\n");

    // Detail records
    for (String record : detailRecords) {
      aba.append(record);
      aba.append("\r\n");
    }

    // Trailer record (Type 7)
    aba.append(buildAbaTrailerRecord(totalAmount, recordCount));
    aba.append("\r\n");

    String filename =
        "direct-credit-"
            + company.getId()
            + "-"
            + DATE_FORMAT.format(paymentRun.getRunDate())
            + ".aba";

    return new ExportResult(
        aba.toString().getBytes(StandardCharsets.US_ASCII),
        filename,
        "application/octet-stream",
        paymentCount,
        totalAmount,
        warnings);
  }

  /**
   * Builds ABA header record (Type 0).
   *
   * <p>Format: Pos 1: '0' (record type) Pos 2-18: blank Pos 19-20: sequence number (01) Pos 21-23:
   * financial institution (e.g., ANZ) Pos 24-30: blank Pos 31-56: company name Pos 57-62: user ID
   * Pos 63-74: description Pos 75-80: date (DDMMYY) Pos 81-120: blank
   */
  private String buildAbaHeaderRecord(Company company, LocalDate paymentDate) {
    StringBuilder record = new StringBuilder();

    record.append("0"); // Record type
    record.append(padRight("", 17)); // Blank
    record.append("01"); // Sequence number
    record.append(padRight("ANZ", 3)); // Financial institution (default)
    record.append(padRight("", 7)); // Blank
    record.append(padRight(truncate(company.getName(), 26), 26)); // Company name
    record.append(padRight(String.valueOf(company.getId()), 6)); // User ID
    record.append(padRight("PAYMENTS", 12)); // Description
    record.append(ABA_DATE_FORMAT.format(paymentDate)); // Date
    record.append(padRight("", 40)); // Blank to fill 120 chars

    return record.toString();
  }

  /**
   * Builds ABA detail record (Type 1).
   *
   * <p>Format: Pos 1: '1' (record type) Pos 2-8: BSB (with hyphen) Pos 9-17: Account number Pos 18:
   * Indicator (blank for credit) Pos 19-20: Transaction code (53 = Credit) Pos 21-30: Amount
   * (cents, right aligned) Pos 31-62: Account name Pos 63-80: Lodgement reference Pos 81-87: Trace
   * BSB Pos 88-96: Trace account Pos 97-112: Remitter name Pos 113-120: Withholding tax
   */
  private String buildAbaDetailRecord(
      Contact supplier, BigDecimal amount, PaymentRun paymentRun, Company company) {
    StringBuilder record = new StringBuilder();

    // Parse bank account - NZ format is typically BB-bbbb-AAAAAAA-SSS or similar
    String[] bankParts = parseNzBankAccount(supplier.getBankAccountNumber());
    String bsb = bankParts[0]; // Bank-Branch code
    String accountNumber = bankParts[1]; // Account number

    // Amount in cents
    long amountCents = amount.movePointRight(2).longValue();

    record.append("1"); // Record type
    record.append(padRight(formatBsb(bsb), 7)); // BSB
    record.append(padRight(truncate(accountNumber, 9), 9)); // Account number
    record.append(" "); // Indicator (blank)
    record.append("53"); // Transaction code (credit)
    record.append(padLeft(String.valueOf(amountCents), 10, '0')); // Amount
    record.append(padRight(truncate(supplier.getName(), 32), 32)); // Account name
    record.append(padRight("PR" + paymentRun.getId(), 18)); // Lodgement reference
    record.append(padRight("000-000", 7)); // Trace BSB (our bank)
    record.append(padRight("", 9)); // Trace account
    record.append(padRight(truncate(company.getName(), 16), 16)); // Remitter name
    record.append(padLeft("0", 8, '0')); // Withholding tax

    return record.toString();
  }

  /**
   * Builds ABA trailer record (Type 7).
   *
   * <p>Format: Pos 1: '7' (record type) Pos 2-8: BSB (999-999) Pos 9-20: blank Pos 21-30: Net total
   * Pos 31-40: Credit total Pos 41-50: Debit total Pos 51-56: blank Pos 57-62: Record count Pos
   * 63-120: blank
   */
  private String buildAbaTrailerRecord(BigDecimal totalAmount, int recordCount) {
    StringBuilder record = new StringBuilder();

    long totalCents = totalAmount.movePointRight(2).longValue();

    record.append("7"); // Record type
    record.append("999-999"); // BSB filler
    record.append(padRight("", 12)); // Blank
    record.append(
        padLeft(String.valueOf(totalCents), 10, '0')); // Net total (same as credit for payments)
    record.append(padLeft(String.valueOf(totalCents), 10, '0')); // Credit total
    record.append(padLeft("0", 10, '0')); // Debit total
    record.append(padRight("", 6)); // Blank
    record.append(padLeft(String.valueOf(recordCount), 6, '0')); // Record count
    record.append(padRight("", 40)); // Blank to fill 120 chars

    return record.toString();
  }

  /**
   * Parses NZ bank account number into BSB-like code and account number.
   *
   * <p>NZ format: BB-bbbb-AAAAAAA-SSS (bank, branch, account, suffix) Returns [bank-branch,
   * account-suffix]
   */
  private String[] parseNzBankAccount(String accountNumber) {
    if (accountNumber == null) {
      return new String[] {"000-000", "000000000"};
    }

    // Remove spaces and standardize separators
    String cleaned = accountNumber.replaceAll("\\s+", "").replace(".", "-");

    // Try to parse NZ format: BB-bbbb-AAAAAAA-SSS
    String[] parts = cleaned.split("-");
    if (parts.length >= 2) {
      // Bank-branch (first two parts)
      String bankBranch = parts[0] + "-" + (parts.length > 1 ? parts[1] : "0000");
      // Account-suffix (remaining parts)
      String account = parts.length > 2 ? parts[2] : "";
      if (parts.length > 3) {
        account = account + parts[3];
      }
      return new String[] {truncate(bankBranch, 7), truncate(account, 9)};
    }

    // Fallback: treat as single account number
    return new String[] {"000-000", truncate(cleaned, 9)};
  }

  private String formatBsb(String bsb) {
    if (bsb == null || bsb.length() < 3) {
      return "000-000";
    }
    // Ensure hyphen is in correct position
    bsb = bsb.replace("-", "");
    if (bsb.length() >= 6) {
      return bsb.substring(0, 3) + "-" + bsb.substring(3, 6);
    }
    return padRight(bsb, 6).substring(0, 3) + "-" + padRight(bsb, 6).substring(3, 6);
  }

  private String cleanAccountNumber(String accountNumber) {
    if (accountNumber == null) {
      return "";
    }
    // Remove common formatting characters for CSV export
    return accountNumber.replaceAll("[^0-9a-zA-Z-]", "");
  }

  private String escapeCsvField(String field) {
    if (field == null) {
      return "";
    }
    if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
      return "\"" + field.replace("\"", "\"\"") + "\"";
    }
    return field;
  }

  private String padRight(String str, int length) {
    if (str == null) {
      str = "";
    }
    if (str.length() >= length) {
      return str.substring(0, length);
    }
    return String.format("%-" + length + "s", str);
  }

  private String padLeft(String str, int length, char padChar) {
    if (str == null) {
      str = "";
    }
    if (str.length() >= length) {
      return str.substring(str.length() - length);
    }
    StringBuilder result = new StringBuilder();
    for (int i = str.length(); i < length; i++) {
      result.append(padChar);
    }
    result.append(str);
    return result.toString();
  }

  private String truncate(String str, int maxLength) {
    if (str == null) {
      return "";
    }
    return str.length() <= maxLength ? str : str.substring(0, maxLength);
  }
}
