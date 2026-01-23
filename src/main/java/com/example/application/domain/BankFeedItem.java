package com.example.application.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Represents a single line item from an imported bank statement. Bank feed items can be matched to
 * existing transactions or used to create new ones.
 */
@Entity
@Table(
    name = "bank_feed_item",
    indexes = {
      @Index(name = "idx_bank_feed_item_status", columnList = "import_id, status"),
      @Index(name = "idx_bank_feed_item_fitid", columnList = "import_id, fit_id")
    })
public class BankFeedItem {

  public enum FeedItemStatus {
    NEW, // Unprocessed item
    MATCHED, // Matched to existing transaction
    CREATED, // New transaction created from this item
    SPLIT, // Split across multiple accounts in a single transaction
    IGNORED // Manually ignored by user
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "import_id", nullable = false)
  private BankStatementImport bankStatementImport;

  @NotNull
  @Column(name = "posted_date", nullable = false)
  private LocalDate postedDate;

  @NotNull
  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Size(max = 500)
  @Column(length = 500)
  private String description;

  @Size(max = 100)
  @Column(name = "fit_id", length = 100)
  private String fitId;

  @Column(name = "raw_json", columnDefinition = "TEXT")
  private String rawJson;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private FeedItemStatus status = FeedItemStatus.NEW;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "matched_transaction_id")
  private Transaction matchedTransaction;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
  }

  // Constructors
  public BankFeedItem() {}

  public BankFeedItem(
      BankStatementImport bankStatementImport,
      LocalDate postedDate,
      BigDecimal amount,
      String description) {
    this.bankStatementImport = bankStatementImport;
    this.postedDate = postedDate;
    this.amount = amount;
    this.description = description;
  }

  // Getters and Setters
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public BankStatementImport getBankStatementImport() {
    return bankStatementImport;
  }

  public void setBankStatementImport(BankStatementImport bankStatementImport) {
    this.bankStatementImport = bankStatementImport;
  }

  public LocalDate getPostedDate() {
    return postedDate;
  }

  public void setPostedDate(LocalDate postedDate) {
    this.postedDate = postedDate;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getFitId() {
    return fitId;
  }

  public void setFitId(String fitId) {
    this.fitId = fitId;
  }

  public String getRawJson() {
    return rawJson;
  }

  public void setRawJson(String rawJson) {
    this.rawJson = rawJson;
  }

  public FeedItemStatus getStatus() {
    return status;
  }

  public void setStatus(FeedItemStatus status) {
    this.status = status;
  }

  public Transaction getMatchedTransaction() {
    return matchedTransaction;
  }

  public void setMatchedTransaction(Transaction matchedTransaction) {
    this.matchedTransaction = matchedTransaction;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  /** Returns true if this is an inflow (deposit/receipt). */
  public boolean isInflow() {
    return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
  }

  /** Returns true if this is an outflow (withdrawal/payment). */
  public boolean isOutflow() {
    return amount != null && amount.compareTo(BigDecimal.ZERO) < 0;
  }
}
