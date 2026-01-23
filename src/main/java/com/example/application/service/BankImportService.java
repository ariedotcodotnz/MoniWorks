package com.example.application.service;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.domain.*;
import com.example.application.domain.BankFeedItem.FeedItemStatus;
import com.example.application.domain.BankStatementImport.SourceType;
import com.example.application.domain.ReconciliationMatch.MatchType;
import com.example.application.repository.*;

/**
 * Service for importing bank statements from various formats. Supports QIF (Quicken Interchange
 * Format) and OFX (Open Financial Exchange).
 */
@Service
@Transactional
public class BankImportService {

  private static final Logger log = LoggerFactory.getLogger(BankImportService.class);

  private final BankStatementImportRepository importRepository;
  private final BankFeedItemRepository feedItemRepository;
  private final AccountRepository accountRepository;
  private final AllocationRuleRepository ruleRepository;
  private final ReconciliationMatchRepository reconciliationMatchRepository;

  public BankImportService(
      BankStatementImportRepository importRepository,
      BankFeedItemRepository feedItemRepository,
      AccountRepository accountRepository,
      AllocationRuleRepository ruleRepository,
      ReconciliationMatchRepository reconciliationMatchRepository) {
    this.importRepository = importRepository;
    this.feedItemRepository = feedItemRepository;
    this.accountRepository = accountRepository;
    this.ruleRepository = ruleRepository;
    this.reconciliationMatchRepository = reconciliationMatchRepository;
  }

  /**
   * Imports a bank statement file for the given bank account. Returns the import record with
   * statistics.
   *
   * @param company The company context
   * @param bankAccount The bank account to import into
   * @param fileName Original filename
   * @param content File content as string
   * @return The import record
   * @throws IllegalArgumentException if account is not a bank account or duplicate import
   */
  public BankStatementImport importStatement(
      Company company, Account bankAccount, String fileName, String content) {
    if (!bankAccount.isBankAccount()) {
      throw new IllegalArgumentException(
          "Account is not marked as a bank account: " + bankAccount.getCode());
    }

    // Calculate file hash for deduplication
    String fileHash = calculateHash(content);

    // Check for duplicate import
    if (importRepository.existsByCompanyAndAccountAndFileHash(company, bankAccount, fileHash)) {
      throw new IllegalArgumentException("This file has already been imported");
    }

    // Determine file type from extension
    SourceType sourceType = detectSourceType(fileName);

    // Create import record
    BankStatementImport importRecord =
        new BankStatementImport(company, bankAccount, sourceType, fileName, fileHash);
    importRecord = importRepository.save(importRecord);

    // Parse and create feed items
    List<BankFeedItem> items = parseStatement(importRecord, content, sourceType);

    // Filter out duplicates by FITID
    int duplicateCount = 0;
    List<BankFeedItem> newItems = new ArrayList<>();
    for (BankFeedItem item : items) {
      if (item.getFitId() != null
          && feedItemRepository.existsByBankStatementImportAndFitId(
              importRecord, item.getFitId())) {
        duplicateCount++;
        continue;
      }
      newItems.add(item);
    }

    // Save new items
    feedItemRepository.saveAll(newItems);

    // Update import statistics
    importRecord.setTotalItems(newItems.size());
    importRepository.save(importRecord);

    log.info(
        "Imported {} items from {} ({} duplicates skipped)",
        newItems.size(),
        fileName,
        duplicateCount);

    return importRecord;
  }

  /** Parses a bank statement based on source type. */
  private List<BankFeedItem> parseStatement(
      BankStatementImport importRecord, String content, SourceType sourceType) {
    return switch (sourceType) {
      case QIF -> parseQIF(importRecord, content);
      case OFX, QFX, QBO -> parseOFX(importRecord, content);
      case CSV -> parseCSV(importRecord, content);
    };
  }

  /**
   * Parses QIF (Quicken Interchange Format) files. QIF format uses single-character field codes: D
   * = Date, T = Amount, P = Payee, M = Memo, N = Number/Reference
   */
  private List<BankFeedItem> parseQIF(BankStatementImport importRecord, String content) {
    List<BankFeedItem> items = new ArrayList<>();

    // Common date formats in QIF files
    List<DateTimeFormatter> dateFormats =
        List.of(
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yy"),
            DateTimeFormatter.ofPattern("M/d/yy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"));

    LocalDate currentDate = null;
    BigDecimal currentAmount = null;
    String currentPayee = null;
    String currentMemo = null;
    String currentNumber = null;

    for (String line : content.split("\n")) {
      line = line.trim();
      if (line.isEmpty()) continue;

      char code = line.charAt(0);
      String value = line.length() > 1 ? line.substring(1).trim() : "";

      switch (code) {
        case 'D' -> currentDate = parseDate(value, dateFormats);
        case 'T' -> currentAmount = parseAmount(value);
        case 'P' -> currentPayee = value;
        case 'M' -> currentMemo = value;
        case 'N' -> currentNumber = value;
        case '^' -> {
          // End of record
          if (currentDate != null && currentAmount != null) {
            String description = buildDescription(currentPayee, currentMemo);
            BankFeedItem item =
                new BankFeedItem(importRecord, currentDate, currentAmount, description);
            item.setFitId(currentNumber);
            item.setRawJson(
                buildRawJson(currentDate, currentAmount, currentPayee, currentMemo, currentNumber));
            items.add(item);
          }
          // Reset for next record
          currentDate = null;
          currentAmount = null;
          currentPayee = null;
          currentMemo = null;
          currentNumber = null;
        }
      }
    }

    return items;
  }

  /**
   * Parses OFX/QFX/QBO (Open Financial Exchange) files. Basic SGML-style parser for common OFX
   * elements.
   */
  private List<BankFeedItem> parseOFX(BankStatementImport importRecord, String content) {
    List<BankFeedItem> items = new ArrayList<>();

    // OFX date format: YYYYMMDDHHMMSS or YYYYMMDD
    DateTimeFormatter ofxDateFormat = DateTimeFormatter.ofPattern("yyyyMMdd");

    // Find all STMTTRN (statement transaction) blocks
    int pos = 0;
    while ((pos = content.indexOf("<STMTTRN>", pos)) != -1) {
      int endPos = content.indexOf("</STMTTRN>", pos);
      if (endPos == -1) {
        endPos = content.indexOf("<STMTTRN>", pos + 9);
        if (endPos == -1) endPos = content.length();
      }

      String block = content.substring(pos, endPos);

      LocalDate date = extractOFXDate(block, "<DTPOSTED>", ofxDateFormat);
      BigDecimal amount = extractOFXAmount(block, "<TRNAMT>");
      String name = extractOFXValue(block, "<NAME>");
      String memo = extractOFXValue(block, "<MEMO>");
      String fitId = extractOFXValue(block, "<FITID>");

      if (date != null && amount != null) {
        String description = buildDescription(name, memo);
        BankFeedItem item = new BankFeedItem(importRecord, date, amount, description);
        item.setFitId(fitId);
        item.setRawJson(block.trim());
        items.add(item);
      }

      pos = endPos;
    }

    return items;
  }

  /**
   * Parses CSV files with common bank export formats. Expects headers: Date, Description/Payee,
   * Amount (or Debit/Credit columns)
   */
  private List<BankFeedItem> parseCSV(BankStatementImport importRecord, String content) {
    List<BankFeedItem> items = new ArrayList<>();
    String[] lines = content.split("\n");

    if (lines.length < 2) return items;

    // Parse header to find column indices
    String[] headers = parseCSVLine(lines[0]);
    int dateCol = findColumn(headers, "date", "transaction date", "posted");
    int descCol = findColumn(headers, "description", "payee", "name", "memo", "details");
    int amountCol = findColumn(headers, "amount", "value");
    int debitCol = findColumn(headers, "debit", "withdrawal", "out");
    int creditCol = findColumn(headers, "credit", "deposit", "in");

    if (dateCol < 0 || (amountCol < 0 && debitCol < 0 && creditCol < 0)) {
      log.warn("CSV format not recognized - missing required columns");
      return items;
    }

    List<DateTimeFormatter> dateFormats =
        List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("M/d/yyyy"));

    for (int i = 1; i < lines.length; i++) {
      String[] values = parseCSVLine(lines[i]);
      if (values.length <= dateCol) continue;

      LocalDate date = parseDate(values[dateCol], dateFormats);
      if (date == null) continue;

      BigDecimal amount;
      if (amountCol >= 0 && amountCol < values.length) {
        amount = parseAmount(values[amountCol]);
      } else {
        BigDecimal debit =
            debitCol >= 0 && debitCol < values.length
                ? parseAmount(values[debitCol])
                : BigDecimal.ZERO;
        BigDecimal credit =
            creditCol >= 0 && creditCol < values.length
                ? parseAmount(values[creditCol])
                : BigDecimal.ZERO;
        amount = credit.subtract(debit);
      }

      if (amount != null && amount.compareTo(BigDecimal.ZERO) != 0) {
        String description = descCol >= 0 && descCol < values.length ? values[descCol].trim() : "";
        BankFeedItem item = new BankFeedItem(importRecord, date, amount, description);
        item.setRawJson(lines[i]);
        items.add(item);
      }
    }

    return items;
  }

  // Helper methods

  private SourceType detectSourceType(String fileName) {
    String lower = fileName.toLowerCase();
    if (lower.endsWith(".qif")) return SourceType.QIF;
    if (lower.endsWith(".ofx")) return SourceType.OFX;
    if (lower.endsWith(".qfx")) return SourceType.QFX;
    if (lower.endsWith(".qbo")) return SourceType.QBO;
    if (lower.endsWith(".csv")) return SourceType.CSV;
    // Default to QIF for unknown
    return SourceType.QIF;
  }

  private String calculateHash(String content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : hash) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 not available", e);
    }
  }

  private LocalDate parseDate(String value, List<DateTimeFormatter> formats) {
    if (value == null || value.isBlank()) return null;
    value = value.trim();

    for (DateTimeFormatter format : formats) {
      try {
        return LocalDate.parse(value, format);
      } catch (DateTimeParseException ignored) {
      }
    }

    log.warn("Could not parse date: {}", value);
    return null;
  }

  private BigDecimal parseAmount(String value) {
    if (value == null || value.isBlank()) return null;
    // Remove currency symbols, commas, and whitespace
    value = value.replaceAll("[^0-9.\\-]", "").trim();
    if (value.isEmpty()) return null;
    try {
      return new BigDecimal(value);
    } catch (NumberFormatException e) {
      log.warn("Could not parse amount: {}", value);
      return null;
    }
  }

  private String buildDescription(String payee, String memo) {
    if (payee != null && !payee.isBlank()) {
      if (memo != null && !memo.isBlank() && !memo.equals(payee)) {
        return payee + " - " + memo;
      }
      return payee;
    }
    return memo != null ? memo : "";
  }

  private String buildRawJson(
      LocalDate date, BigDecimal amount, String payee, String memo, String number) {
    return String.format(
        "{\"date\":\"%s\",\"amount\":%s,\"payee\":\"%s\",\"memo\":\"%s\",\"number\":\"%s\"}",
        date,
        amount,
        payee != null ? payee.replace("\"", "\\\"") : "",
        memo != null ? memo.replace("\"", "\\\"") : "",
        number != null ? number : "");
  }

  private String extractOFXValue(String block, String tag) {
    int start = block.indexOf(tag);
    if (start < 0) return null;
    start += tag.length();

    int end = block.indexOf("<", start);
    if (end < 0) end = block.indexOf("\n", start);
    if (end < 0) end = block.length();

    return block.substring(start, end).trim();
  }

  private LocalDate extractOFXDate(String block, String tag, DateTimeFormatter format) {
    String value = extractOFXValue(block, tag);
    if (value == null) return null;
    // OFX dates may include time - take first 8 characters
    if (value.length() >= 8) {
      value = value.substring(0, 8);
    }
    try {
      return LocalDate.parse(value, format);
    } catch (DateTimeParseException e) {
      log.warn("Could not parse OFX date: {}", value);
      return null;
    }
  }

  private BigDecimal extractOFXAmount(String block, String tag) {
    String value = extractOFXValue(block, tag);
    return parseAmount(value);
  }

  private String[] parseCSVLine(String line) {
    // Simple CSV parsing (doesn't handle all edge cases)
    List<String> values = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inQuotes = false;

    for (char c : line.toCharArray()) {
      if (c == '"') {
        inQuotes = !inQuotes;
      } else if (c == ',' && !inQuotes) {
        values.add(current.toString().trim());
        current = new StringBuilder();
      } else {
        current.append(c);
      }
    }
    values.add(current.toString().trim());

    return values.toArray(new String[0]);
  }

  private int findColumn(String[] headers, String... names) {
    for (int i = 0; i < headers.length; i++) {
      String header = headers[i].toLowerCase().trim();
      for (String name : names) {
        if (header.contains(name.toLowerCase())) {
          return i;
        }
      }
    }
    return -1;
  }

  /** Finds all unmatched feed items for a bank account. */
  @Transactional(readOnly = true)
  public List<BankFeedItem> findUnmatchedItems(Account bankAccount) {
    return feedItemRepository.findUnmatchedByAccountId(bankAccount.getId());
  }

  /** Finds bank accounts for a company. */
  @Transactional(readOnly = true)
  public List<Account> findBankAccounts(Company company) {
    return accountRepository.findBankAccountsByCompany(company);
  }

  /** Finds all imports for a bank account. */
  @Transactional(readOnly = true)
  public List<BankStatementImport> findImportsByAccount(Account account) {
    return importRepository.findByAccountOrderByImportedAtDesc(account);
  }

  /**
   * Marks a feed item as matched to a transaction. Creates a ReconciliationMatch record for audit
   * trail per spec 05.
   *
   * @param item The bank feed item to match
   * @param transaction The transaction to match to
   * @param matchType Whether this was an AUTO or MANUAL match
   * @param user The user performing the match (can be null for system matches)
   */
  public void matchItem(
      BankFeedItem item, Transaction transaction, MatchType matchType, User user) {
    // Update the BankFeedItem with the match
    item.setMatchedTransaction(transaction);
    item.setStatus(FeedItemStatus.MATCHED);
    feedItemRepository.save(item);

    // Create ReconciliationMatch record for audit trail
    Company company = item.getBankStatementImport().getCompany();
    ReconciliationMatch match =
        new ReconciliationMatch(company, item, transaction, matchType, user);
    reconciliationMatchRepository.save(match);

    log.info(
        "Matched bank feed item {} to transaction {} ({} match by {})",
        item.getId(),
        transaction.getId(),
        matchType,
        user != null ? user.getEmail() : "system");
  }

  /**
   * Marks a feed item as matched to a transaction (manual match). Creates a ReconciliationMatch
   * record for audit trail per spec 05.
   *
   * @param item The bank feed item to match
   * @param transaction The transaction to match to
   * @param user The user performing the match
   */
  public void matchItem(BankFeedItem item, Transaction transaction, User user) {
    matchItem(item, transaction, MatchType.MANUAL, user);
  }

  /**
   * Marks a feed item as matched to a transaction (legacy method for backwards compatibility).
   * Creates a ReconciliationMatch record with MANUAL match type.
   */
  public void matchItem(BankFeedItem item, Transaction transaction) {
    matchItem(item, transaction, MatchType.MANUAL, null);
  }

  /**
   * Unmatches a previously matched bank feed item. The ReconciliationMatch record is kept for audit
   * purposes but marked as inactive.
   *
   * @param item The bank feed item to unmatch
   * @param user The user performing the unmatch
   */
  public void unmatchItem(BankFeedItem item, User user) {
    // Find and deactivate the active reconciliation match
    reconciliationMatchRepository
        .findByBankFeedItemAndActiveTrue(item)
        .ifPresent(
            match -> {
              match.unmatch(user);
              reconciliationMatchRepository.save(match);
            });

    // Reset the bank feed item status
    item.setMatchedTransaction(null);
    item.setStatus(FeedItemStatus.NEW);
    feedItemRepository.save(item);

    log.info(
        "Unmatched bank feed item {} by {}",
        item.getId(),
        user != null ? user.getEmail() : "system");
  }

  /** Marks a feed item as ignored. */
  public void ignoreItem(BankFeedItem item) {
    item.setStatus(FeedItemStatus.IGNORED);
    feedItemRepository.save(item);
  }

  /** Finds the active reconciliation match for a bank feed item. */
  @Transactional(readOnly = true)
  public Optional<ReconciliationMatch> findActiveMatch(BankFeedItem item) {
    return reconciliationMatchRepository.findByBankFeedItemAndActiveTrue(item);
  }

  /** Finds all reconciliation matches (including inactive) for audit history. */
  @Transactional(readOnly = true)
  public List<ReconciliationMatch> findMatchHistory(BankFeedItem item) {
    return reconciliationMatchRepository.findByBankFeedItemOrderByMatchedAtDesc(item);
  }

  /** Finds allocation rules that match the given description. */
  @Transactional(readOnly = true)
  public Optional<AllocationRule> findMatchingRule(Company company, String description) {
    List<AllocationRule> rules = ruleRepository.findEnabledByCompanyOrderByPriority(company);

    for (AllocationRule rule : rules) {
      if (rule.matches(description)) {
        return Optional.of(rule);
      }
    }

    return Optional.empty();
  }
}
