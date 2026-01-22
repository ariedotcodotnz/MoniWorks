package com.example.application.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Tracks a batch statement generation run.
 * Stores criteria used and references the generated output.
 * Supports filtering by customer, date range, and minimum balance.
 */
@Entity
@Table(name = "statement_run")
public class StatementRun {

    public enum RunStatus {
        PENDING,    // Created but not yet processed
        PROCESSING, // Currently generating statements
        COMPLETED,  // All statements generated successfully
        FAILED      // Generation failed
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

    @NotNull
    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RunStatus status = RunStatus.PENDING;

    // Filter criteria - stored as JSON for flexibility
    @Size(max = 4000)
    @Column(name = "criteria_json", length = 4000)
    private String criteriaJson;

    // Number of statements generated
    @Column(name = "statement_count")
    private Integer statementCount;

    // Reference to combined PDF output (if generated)
    @Column(name = "output_attachment_id")
    private Long outputAttachmentId;

    // Error message if status is FAILED
    @Size(max = 500)
    @Column(name = "error_message", length = 500)
    private String errorMessage;

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
        if (runDate == null) {
            runDate = LocalDate.now();
        }
    }

    // Constructors
    public StatementRun() {
    }

    public StatementRun(Company company, LocalDate asOfDate) {
        this.company = company;
        this.asOfDate = asOfDate;
        this.runDate = LocalDate.now();
    }

    // Helper methods
    public boolean isCompleted() {
        return status == RunStatus.COMPLETED;
    }

    public boolean isFailed() {
        return status == RunStatus.FAILED;
    }

    public boolean isPending() {
        return status == RunStatus.PENDING;
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

    public LocalDate getAsOfDate() {
        return asOfDate;
    }

    public void setAsOfDate(LocalDate asOfDate) {
        this.asOfDate = asOfDate;
    }

    public RunStatus getStatus() {
        return status;
    }

    public void setStatus(RunStatus status) {
        this.status = status;
    }

    public String getCriteriaJson() {
        return criteriaJson;
    }

    public void setCriteriaJson(String criteriaJson) {
        this.criteriaJson = criteriaJson;
    }

    public Integer getStatementCount() {
        return statementCount;
    }

    public void setStatementCount(Integer statementCount) {
        this.statementCount = statementCount;
    }

    public Long getOutputAttachmentId() {
        return outputAttachmentId;
    }

    public void setOutputAttachmentId(Long outputAttachmentId) {
        this.outputAttachmentId = outputAttachmentId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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
