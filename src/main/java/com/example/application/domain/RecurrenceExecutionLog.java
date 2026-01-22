package com.example.application.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Logs each execution of a recurring template.
 * Tracks success/failure and maintains audit trail of generated entities.
 */
@Entity
@Table(name = "recurrence_execution_log", indexes = {
    @Index(name = "idx_recurrence_log_template", columnList = "template_id"),
    @Index(name = "idx_recurrence_log_run_at", columnList = "template_id, run_at")
})
public class RecurrenceExecutionLog {

    /**
     * Result of the execution attempt.
     */
    public enum Result {
        CREATED,    // Successfully created the entity
        FAILED      // Failed to create - see error field
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private RecurringTemplate template;

    @NotNull
    @Column(name = "run_at", nullable = false)
    private Instant runAt;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Result result;

    // ID of the created entity (Transaction, SalesInvoice, SupplierBill)
    @Column(name = "created_entity_id")
    private Long createdEntityId;

    // Type of entity created (for reference when looking up)
    @Enumerated(EnumType.STRING)
    @Column(name = "created_entity_type", length = 20)
    private RecurringTemplate.TemplateType createdEntityType;

    // Error message if failed
    @Size(max = 1000)
    @Column(length = 1000)
    private String error;

    // Constructors
    public RecurrenceExecutionLog() {
    }

    /**
     * Create a success log entry.
     */
    public static RecurrenceExecutionLog success(RecurringTemplate template,
                                                  Long createdEntityId,
                                                  RecurringTemplate.TemplateType entityType) {
        RecurrenceExecutionLog log = new RecurrenceExecutionLog();
        log.template = template;
        log.runAt = Instant.now();
        log.result = Result.CREATED;
        log.createdEntityId = createdEntityId;
        log.createdEntityType = entityType;
        return log;
    }

    /**
     * Create a failure log entry.
     */
    public static RecurrenceExecutionLog failure(RecurringTemplate template, String error) {
        RecurrenceExecutionLog log = new RecurrenceExecutionLog();
        log.template = template;
        log.runAt = Instant.now();
        log.result = Result.FAILED;
        log.error = error != null && error.length() > 1000 ? error.substring(0, 1000) : error;
        return log;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public RecurringTemplate getTemplate() {
        return template;
    }

    public void setTemplate(RecurringTemplate template) {
        this.template = template;
    }

    public Instant getRunAt() {
        return runAt;
    }

    public void setRunAt(Instant runAt) {
        this.runAt = runAt;
    }

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    public Long getCreatedEntityId() {
        return createdEntityId;
    }

    public void setCreatedEntityId(Long createdEntityId) {
        this.createdEntityId = createdEntityId;
    }

    public RecurringTemplate.TemplateType getCreatedEntityType() {
        return createdEntityType;
    }

    public void setCreatedEntityType(RecurringTemplate.TemplateType createdEntityType) {
        this.createdEntityType = createdEntityType;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public boolean isSuccess() {
        return result == Result.CREATED;
    }

    public boolean isFailed() {
        return result == Result.FAILED;
    }
}
