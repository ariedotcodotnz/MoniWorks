package com.example.application.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Represents an immutable ledger entry created when a transaction is posted. These entries form the
 * official record of all accounting activity. Ledger entries cannot be modified - corrections
 * require reversals.
 *
 * <p>Per spec 05, bank reconciliation status is tracked per ledger entry for entries affecting bank
 * accounts. The reconciliation fields are operational metadata and do not affect the immutable
 * accounting data.
 */
@Entity
@Table(
    name = "ledger_entry",
    indexes = {
      @Index(name = "idx_ledger_company_date", columnList = "company_id, entry_date"),
      @Index(name = "idx_ledger_account", columnList = "account_id"),
      @Index(name = "idx_ledger_reconciled", columnList = "account_id, is_reconciled")
    })
public class LedgerEntry {

  /** Reconciliation status for bank account ledger entries. */
  public enum ReconciliationStatus {
    UNRECONCILED, // Not yet matched to a bank feed item
    RECONCILED, // Matched to a bank feed item
    MANUAL_CLEARED // Manually marked as cleared without bank feed match
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "company_id", nullable = false)
  private Company company;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "transaction_id", nullable = false)
  private Transaction transaction;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "transaction_line_id", nullable = false)
  private TransactionLine transactionLine;

  @NotNull
  @Column(name = "entry_date", nullable = false)
  private LocalDate entryDate;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "account_id", nullable = false)
  private Account account;

  @NotNull
  @Column(name = "amount_dr", nullable = false, precision = 19, scale = 2)
  private BigDecimal amountDr = BigDecimal.ZERO;

  @NotNull
  @Column(name = "amount_cr", nullable = false, precision = 19, scale = 2)
  private BigDecimal amountCr = BigDecimal.ZERO;

  @Size(max = 10)
  @Column(name = "tax_code", length = 10)
  private String taxCode;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "department_id")
  private Department department;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  // Bank reconciliation tracking (mutable operational metadata, not affecting accounting data)

  /** Whether this entry has been reconciled (only applicable for bank account entries). */
  @Column(name = "is_reconciled", nullable = false)
  private boolean reconciled = false;

  /** The reconciliation status for bank account entries. */
  @Enumerated(EnumType.STRING)
  @Column(name = "reconciliation_status", length = 20)
  private ReconciliationStatus reconciliationStatus = ReconciliationStatus.UNRECONCILED;

  /** Reference to the bank feed item this entry was reconciled against (optional). */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reconciled_bank_feed_item_id")
  private BankFeedItem reconciledBankFeedItem;

  /** When this entry was reconciled. */
  @Column(name = "reconciled_at")
  private Instant reconciledAt;

  /** User who reconciled this entry. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reconciled_by_id")
  private User reconciledBy;

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
  }

  // Constructors
  public LedgerEntry() {}

  public LedgerEntry(Company company, Transaction transaction, TransactionLine line) {
    this.company = company;
    this.transaction = transaction;
    this.transactionLine = line;
    this.entryDate = transaction.getTransactionDate();
    this.account = line.getAccount();
    this.taxCode = line.getTaxCode();
    this.department = line.getDepartment();

    if (line.isDebit()) {
      this.amountDr = line.getAmount();
      this.amountCr = BigDecimal.ZERO;
    } else {
      this.amountDr = BigDecimal.ZERO;
      this.amountCr = line.getAmount();
    }
  }

  // Getters only - ledger entries are immutable after creation
  public Long getId() {
    return id;
  }

  public Company getCompany() {
    return company;
  }

  public Transaction getTransaction() {
    return transaction;
  }

  public TransactionLine getTransactionLine() {
    return transactionLine;
  }

  public LocalDate getEntryDate() {
    return entryDate;
  }

  public Account getAccount() {
    return account;
  }

  public BigDecimal getAmountDr() {
    return amountDr;
  }

  public BigDecimal getAmountCr() {
    return amountCr;
  }

  public String getTaxCode() {
    return taxCode;
  }

  public Department getDepartment() {
    return department;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  // Helper method to get net amount (debit positive, credit negative)
  public BigDecimal getNetAmount() {
    return amountDr.subtract(amountCr);
  }

  // Bank reconciliation methods (mutable operational metadata)

  public boolean isReconciled() {
    return reconciled;
  }

  public ReconciliationStatus getReconciliationStatus() {
    return reconciliationStatus;
  }

  public BankFeedItem getReconciledBankFeedItem() {
    return reconciledBankFeedItem;
  }

  public Instant getReconciledAt() {
    return reconciledAt;
  }

  public User getReconciledBy() {
    return reconciledBy;
  }

  /**
   * Marks this ledger entry as reconciled against a bank feed item.
   *
   * @param bankFeedItem The bank feed item this entry was matched to
   * @param user The user who performed the reconciliation
   */
  public void markReconciled(BankFeedItem bankFeedItem, User user) {
    this.reconciled = true;
    this.reconciliationStatus = ReconciliationStatus.RECONCILED;
    this.reconciledBankFeedItem = bankFeedItem;
    this.reconciledBy = user;
    this.reconciledAt = Instant.now();
  }

  /**
   * Marks this ledger entry as manually cleared (without bank feed match).
   *
   * @param user The user who performed the clearing
   */
  public void markManuallyCleared(User user) {
    this.reconciled = true;
    this.reconciliationStatus = ReconciliationStatus.MANUAL_CLEARED;
    this.reconciledBy = user;
    this.reconciledAt = Instant.now();
  }

  /** Unreconciles this ledger entry. */
  public void unreconcile() {
    this.reconciled = false;
    this.reconciliationStatus = ReconciliationStatus.UNRECONCILED;
    this.reconciledBankFeedItem = null;
    this.reconciledBy = null;
    this.reconciledAt = null;
  }
}
