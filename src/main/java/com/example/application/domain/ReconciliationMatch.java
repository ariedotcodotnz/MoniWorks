package com.example.application.domain;

import java.time.Instant;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

/**
 * Represents a match between a bank feed item and a transaction. This entity provides an audit
 * trail for reconciliation actions, tracking who matched what and when, as well as whether the
 * match was automatic or manual.
 *
 * <p>Per spec 05 (Bank Import and Reconciliation), this entity tracks the reconciliation state and
 * provides accountability for reconciliation actions.
 */
@Entity
@Table(
    name = "reconciliation_match",
    indexes = {
      @Index(name = "idx_recon_match_bank_feed", columnList = "bank_feed_item_id"),
      @Index(name = "idx_recon_match_transaction", columnList = "transaction_id"),
      @Index(name = "idx_recon_match_company", columnList = "company_id")
    })
public class ReconciliationMatch {

  /** Type of match - whether it was automatically suggested or manually selected by user. */
  public enum MatchType {
    AUTO, // System-suggested match based on amount/date/description
    MANUAL // User manually selected this match
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
  @JoinColumn(name = "bank_feed_item_id", nullable = false)
  private BankFeedItem bankFeedItem;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "transaction_id", nullable = false)
  private Transaction transaction;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "match_type", nullable = false, length = 20)
  private MatchType matchType;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "matched_by_id")
  private User matchedBy;

  @NotNull
  @Column(name = "matched_at", nullable = false)
  private Instant matchedAt;

  /** Optional notes explaining the match decision. */
  @Column(name = "match_notes", length = 500)
  private String matchNotes;

  /**
   * Flag indicating if this match has been unmatched. We keep the record for audit trail but mark
   * it inactive.
   */
  @Column(name = "is_active", nullable = false)
  private boolean active = true;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "unmatched_by_id")
  private User unmatchedBy;

  @Column(name = "unmatched_at")
  private Instant unmatchedAt;

  @PrePersist
  protected void onCreate() {
    if (matchedAt == null) {
      matchedAt = Instant.now();
    }
  }

  // Constructors
  public ReconciliationMatch() {}

  public ReconciliationMatch(
      Company company,
      BankFeedItem bankFeedItem,
      Transaction transaction,
      MatchType matchType,
      User matchedBy) {
    this.company = company;
    this.bankFeedItem = bankFeedItem;
    this.transaction = transaction;
    this.matchType = matchType;
    this.matchedBy = matchedBy;
    this.matchedAt = Instant.now();
  }

  // Getters and Setters
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Company getCompany() {
    return company;
  }

  public void setCompany(Company company) {
    this.company = company;
  }

  public BankFeedItem getBankFeedItem() {
    return bankFeedItem;
  }

  public void setBankFeedItem(BankFeedItem bankFeedItem) {
    this.bankFeedItem = bankFeedItem;
  }

  public Transaction getTransaction() {
    return transaction;
  }

  public void setTransaction(Transaction transaction) {
    this.transaction = transaction;
  }

  public MatchType getMatchType() {
    return matchType;
  }

  public void setMatchType(MatchType matchType) {
    this.matchType = matchType;
  }

  public User getMatchedBy() {
    return matchedBy;
  }

  public void setMatchedBy(User matchedBy) {
    this.matchedBy = matchedBy;
  }

  public Instant getMatchedAt() {
    return matchedAt;
  }

  public void setMatchedAt(Instant matchedAt) {
    this.matchedAt = matchedAt;
  }

  public String getMatchNotes() {
    return matchNotes;
  }

  public void setMatchNotes(String matchNotes) {
    this.matchNotes = matchNotes;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public User getUnmatchedBy() {
    return unmatchedBy;
  }

  public void setUnmatchedBy(User unmatchedBy) {
    this.unmatchedBy = unmatchedBy;
  }

  public Instant getUnmatchedAt() {
    return unmatchedAt;
  }

  public void setUnmatchedAt(Instant unmatchedAt) {
    this.unmatchedAt = unmatchedAt;
  }

  /** Unmatches this reconciliation, keeping the record for audit purposes. */
  public void unmatch(User user) {
    this.active = false;
    this.unmatchedBy = user;
    this.unmatchedAt = Instant.now();
  }
}
