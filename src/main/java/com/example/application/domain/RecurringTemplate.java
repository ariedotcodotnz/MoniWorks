package com.example.application.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Represents a recurring transaction template.
 * Stores the template configuration for generating recurring transactions,
 * invoices, or bills on a schedule.
 */
@Entity
@Table(name = "recurring_template", indexes = {
    @Index(name = "idx_recurring_template_company", columnList = "company_id"),
    @Index(name = "idx_recurring_template_next_run", columnList = "company_id, status, next_run_date")
})
public class RecurringTemplate {

    /**
     * Type of entity this template creates.
     */
    public enum TemplateType {
        PAYMENT,    // Creates a payment transaction
        RECEIPT,    // Creates a receipt transaction
        JOURNAL,    // Creates a journal entry
        INVOICE,    // Creates a sales invoice
        BILL        // Creates a supplier bill
    }

    /**
     * Frequency pattern for recurrence.
     */
    public enum Frequency {
        DAILY,
        WEEKLY,
        FORTNIGHTLY,
        MONTHLY,
        QUARTERLY,
        YEARLY
    }

    /**
     * Status of the recurring template.
     */
    public enum Status {
        ACTIVE,     // Template is active and will run on schedule
        PAUSED,     // Template is paused, will not run
        COMPLETED,  // Template has reached its end condition
        CANCELLED   // Template has been cancelled
    }

    /**
     * What to do when the recurrence runs.
     */
    public enum ExecutionMode {
        AUTO_POST,      // Create and post immediately
        CREATE_DRAFT    // Create as draft for manual review
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "template_type", nullable = false, length = 20)
    private TemplateType templateType;

    // Reference to source entity (e.g., copy from existing transaction/invoice)
    @Column(name = "source_entity_id")
    private Long sourceEntityId;

    // JSON payload containing the template data (lines, amounts, accounts, etc.)
    @NotNull
    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    // Schedule configuration
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Frequency frequency;

    @Column(name = "frequency_interval")
    private int frequencyInterval = 1; // Every N frequency units

    @NotNull
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate; // null = forever

    @Column(name = "max_occurrences")
    private Integer maxOccurrences; // null = unlimited

    @Column(name = "occurrences_count")
    private int occurrencesCount = 0; // How many times it has run

    @NotNull
    @Column(name = "next_run_date", nullable = false)
    private LocalDate nextRunDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "execution_mode", nullable = false, length = 20)
    private ExecutionMode executionMode = ExecutionMode.CREATE_DRAFT;

    // For invoices/bills - contact to use
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    // Default bank account for payments/receipts
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_account_id")
    private Account bankAccount;

    @Size(max = 255)
    @Column(length = 255)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Constructors
    public RecurringTemplate() {
    }

    public RecurringTemplate(Company company, String name, TemplateType templateType,
                             String payloadJson, Frequency frequency, LocalDate startDate) {
        this.company = company;
        this.name = name;
        this.templateType = templateType;
        this.payloadJson = payloadJson;
        this.frequency = frequency;
        this.startDate = startDate;
        this.nextRunDate = startDate;
    }

    // Business methods

    /**
     * Checks if this template is ready to run (active and next run date is today or earlier).
     */
    public boolean isReadyToRun(LocalDate today) {
        return status == Status.ACTIVE && !nextRunDate.isAfter(today);
    }

    /**
     * Checks if this template has reached its end condition.
     */
    public boolean hasReachedEnd(LocalDate today) {
        if (endDate != null && today.isAfter(endDate)) {
            return true;
        }
        if (maxOccurrences != null && occurrencesCount >= maxOccurrences) {
            return true;
        }
        return false;
    }

    /**
     * Calculates the next run date after a successful execution.
     */
    public LocalDate calculateNextRunDate() {
        LocalDate current = nextRunDate;
        for (int i = 0; i < frequencyInterval; i++) {
            current = switch (frequency) {
                case DAILY -> current.plusDays(1);
                case WEEKLY -> current.plusWeeks(1);
                case FORTNIGHTLY -> current.plusWeeks(2);
                case MONTHLY -> current.plusMonths(1);
                case QUARTERLY -> current.plusMonths(3);
                case YEARLY -> current.plusYears(1);
            };
        }
        return current;
    }

    /**
     * Called after successful execution to advance the schedule.
     */
    public void recordExecution(LocalDate today) {
        occurrencesCount++;
        nextRunDate = calculateNextRunDate();

        if (hasReachedEnd(today)) {
            status = Status.COMPLETED;
        }
    }

    /**
     * Pause the template.
     */
    public void pause() {
        if (status == Status.ACTIVE) {
            status = Status.PAUSED;
        }
    }

    /**
     * Resume a paused template.
     */
    public void resume(LocalDate today) {
        if (status == Status.PAUSED) {
            status = Status.ACTIVE;
            // If next run date is in the past, set it to today
            if (nextRunDate.isBefore(today)) {
                nextRunDate = today;
            }
        }
    }

    /**
     * Cancel the template permanently.
     */
    public void cancel() {
        status = Status.CANCELLED;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TemplateType getTemplateType() {
        return templateType;
    }

    public void setTemplateType(TemplateType templateType) {
        this.templateType = templateType;
    }

    public Long getSourceEntityId() {
        return sourceEntityId;
    }

    public void setSourceEntityId(Long sourceEntityId) {
        this.sourceEntityId = sourceEntityId;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public Frequency getFrequency() {
        return frequency;
    }

    public void setFrequency(Frequency frequency) {
        this.frequency = frequency;
    }

    public int getFrequencyInterval() {
        return frequencyInterval;
    }

    public void setFrequencyInterval(int frequencyInterval) {
        this.frequencyInterval = frequencyInterval;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Integer getMaxOccurrences() {
        return maxOccurrences;
    }

    public void setMaxOccurrences(Integer maxOccurrences) {
        this.maxOccurrences = maxOccurrences;
    }

    public int getOccurrencesCount() {
        return occurrencesCount;
    }

    public void setOccurrencesCount(int occurrencesCount) {
        this.occurrencesCount = occurrencesCount;
    }

    public LocalDate getNextRunDate() {
        return nextRunDate;
    }

    public void setNextRunDate(LocalDate nextRunDate) {
        this.nextRunDate = nextRunDate;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public ExecutionMode getExecutionMode() {
        return executionMode;
    }

    public void setExecutionMode(ExecutionMode executionMode) {
        this.executionMode = executionMode;
    }

    public Contact getContact() {
        return contact;
    }

    public void setContact(Contact contact) {
        this.contact = contact;
    }

    public Account getBankAccount() {
        return bankAccount;
    }

    public void setBankAccount(Account bankAccount) {
        this.bankAccount = bankAccount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    /**
     * Returns a human-readable schedule description.
     */
    public String getScheduleDescription() {
        String freq = switch (frequency) {
            case DAILY -> frequencyInterval == 1 ? "Daily" : "Every " + frequencyInterval + " days";
            case WEEKLY -> frequencyInterval == 1 ? "Weekly" : "Every " + frequencyInterval + " weeks";
            case FORTNIGHTLY -> "Fortnightly";
            case MONTHLY -> frequencyInterval == 1 ? "Monthly" : "Every " + frequencyInterval + " months";
            case QUARTERLY -> "Quarterly";
            case YEARLY -> frequencyInterval == 1 ? "Yearly" : "Every " + frequencyInterval + " years";
        };

        if (endDate != null) {
            return freq + " until " + endDate;
        } else if (maxOccurrences != null) {
            return freq + " (" + occurrencesCount + "/" + maxOccurrences + " occurrences)";
        }
        return freq;
    }
}
