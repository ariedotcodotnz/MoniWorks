package com.example.application.domain;

import java.time.Instant;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

/**
 * Links an original transaction to its reversal transaction. Per spec 04 domain model: "A 'Reverse'
 * action creates a new transaction with inverted lines and links it." This entity provides a formal
 * audit trail for reversals, making it easy to query reversal relationships.
 */
@Entity
@Table(
    name = "reversal_link",
    indexes = {
      @Index(name = "idx_reversal_link_original", columnList = "original_transaction_id"),
      @Index(name = "idx_reversal_link_reversing", columnList = "reversing_transaction_id")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_reversal_link_pair",
          columnNames = {"original_transaction_id", "reversing_transaction_id"})
    })
public class ReversalLink {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "original_transaction_id", nullable = false)
  private Transaction originalTransaction;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reversing_transaction_id", nullable = false)
  private Transaction reversingTransaction;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by")
  private User createdBy;

  @Column(length = 500)
  private String reason;

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
  }

  // Constructors
  public ReversalLink() {}

  public ReversalLink(Transaction originalTransaction, Transaction reversingTransaction) {
    this.originalTransaction = originalTransaction;
    this.reversingTransaction = reversingTransaction;
  }

  public ReversalLink(
      Transaction originalTransaction,
      Transaction reversingTransaction,
      User createdBy,
      String reason) {
    this.originalTransaction = originalTransaction;
    this.reversingTransaction = reversingTransaction;
    this.createdBy = createdBy;
    this.reason = reason;
  }

  // Getters and Setters
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Transaction getOriginalTransaction() {
    return originalTransaction;
  }

  public void setOriginalTransaction(Transaction originalTransaction) {
    this.originalTransaction = originalTransaction;
  }

  public Transaction getReversingTransaction() {
    return reversingTransaction;
  }

  public void setReversingTransaction(Transaction reversingTransaction) {
    this.reversingTransaction = reversingTransaction;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public User getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(User createdBy) {
    this.createdBy = createdBy;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }
}
