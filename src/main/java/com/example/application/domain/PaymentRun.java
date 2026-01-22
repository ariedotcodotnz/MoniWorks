package com.example.application.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Represents a batch payment run for paying multiple supplier bills.
 * Payment runs go through: DRAFT → COMPLETED workflow.
 * DRAFT: Bills selected, can be edited
 * COMPLETED: Payments posted to ledger, remittance advice generated
 */
@Entity
@Table(name = "payment_run")
public class PaymentRun {

    public enum PaymentRunStatus {
        DRAFT,      // Bills selected, pending review
        COMPLETED   // Payments processed, remittance advice generated
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @NotNull
    @Column(name = "run_date", nullable = false)
    private LocalDate runDate;

    // Bank account to pay from
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_account_id", nullable = false)
    private Account bankAccount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private PaymentRunStatus status = PaymentRunStatus.DRAFT;

    // JSON array of payment items: [{billId, contactId, amount, transactionId}]
    @Column(name = "items_json", columnDefinition = "TEXT")
    private String itemsJson;

    // Link to generated remittance advice PDF attachment
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "output_attachment_id")
    private Attachment outputAttachment;

    @Size(max = 500)
    @Column(length = 500)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    // Constructors
    public PaymentRun() {
    }

    public PaymentRun(Company company, LocalDate runDate, Account bankAccount) {
        this.company = company;
        this.runDate = runDate;
        this.bankAccount = bankAccount;
    }

    // Helper methods
    public boolean isDraft() {
        return status == PaymentRunStatus.DRAFT;
    }

    public boolean isCompleted() {
        return status == PaymentRunStatus.COMPLETED;
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

    public LocalDate getRunDate() {
        return runDate;
    }

    public void setRunDate(LocalDate runDate) {
        this.runDate = runDate;
    }

    public Account getBankAccount() {
        return bankAccount;
    }

    public void setBankAccount(Account bankAccount) {
        this.bankAccount = bankAccount;
    }

    public PaymentRunStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentRunStatus status) {
        this.status = status;
    }

    public String getItemsJson() {
        return itemsJson;
    }

    public void setItemsJson(String itemsJson) {
        this.itemsJson = itemsJson;
    }

    public Attachment getOutputAttachment() {
        return outputAttachment;
    }

    public void setOutputAttachment(Attachment outputAttachment) {
        this.outputAttachment = outputAttachment;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
