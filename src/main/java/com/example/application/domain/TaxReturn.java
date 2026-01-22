package com.example.application.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a GST/VAT return for a tax period.
 *
 * A tax return is a snapshot of tax calculations for a date range,
 * containing box totals that can be reported to tax authorities.
 *
 * Features:
 * - Generated from TaxLine data for the period
 * - Supports cash or invoice/accrual basis
 * - Stores totals by report box
 * - Immutable once finalized (corrections via new returns)
 */
@Entity
@Table(name = "tax_return", indexes = {
    @Index(name = "idx_tax_return_company_period", columnList = "company_id, start_date, end_date")
})
public class TaxReturn {

    public enum Basis {
        CASH,       // Tax based on when payments/receipts occur
        INVOICE     // Tax based on when invoices/bills are issued (accrual)
    }

    public enum Status {
        DRAFT,      // Can be edited/regenerated
        FINALIZED,  // Ready to file
        FILED       // Submitted to tax authority
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @NotNull
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @NotNull
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Basis basis = Basis.INVOICE;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Status status = Status.DRAFT;

    @Column(name = "total_sales", precision = 19, scale = 2)
    private BigDecimal totalSales = BigDecimal.ZERO;

    @Column(name = "total_purchases", precision = 19, scale = 2)
    private BigDecimal totalPurchases = BigDecimal.ZERO;

    @Column(name = "output_tax", precision = 19, scale = 2)
    private BigDecimal outputTax = BigDecimal.ZERO;

    @Column(name = "input_tax", precision = 19, scale = 2)
    private BigDecimal inputTax = BigDecimal.ZERO;

    @Column(name = "tax_payable", precision = 19, scale = 2)
    private BigDecimal taxPayable = BigDecimal.ZERO;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generated_by")
    private User generatedBy;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "taxReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TaxReturnLine> lines = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
    public TaxReturn() {
    }

    public TaxReturn(Company company, LocalDate startDate, LocalDate endDate, Basis basis) {
        this.company = company;
        this.startDate = startDate;
        this.endDate = endDate;
        this.basis = basis;
    }

    // Helper methods
    public void addLine(TaxReturnLine line) {
        lines.add(line);
        line.setTaxReturn(this);
    }

    public void removeLine(TaxReturnLine line) {
        lines.remove(line);
        line.setTaxReturn(null);
    }

    public boolean isDraft() {
        return status == Status.DRAFT;
    }

    public boolean isFinalized() {
        return status == Status.FINALIZED || status == Status.FILED;
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

    public Basis getBasis() {
        return basis;
    }

    public void setBasis(Basis basis) {
        this.basis = basis;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public BigDecimal getTotalSales() {
        return totalSales;
    }

    public void setTotalSales(BigDecimal totalSales) {
        this.totalSales = totalSales;
    }

    public BigDecimal getTotalPurchases() {
        return totalPurchases;
    }

    public void setTotalPurchases(BigDecimal totalPurchases) {
        this.totalPurchases = totalPurchases;
    }

    public BigDecimal getOutputTax() {
        return outputTax;
    }

    public void setOutputTax(BigDecimal outputTax) {
        this.outputTax = outputTax;
    }

    public BigDecimal getInputTax() {
        return inputTax;
    }

    public void setInputTax(BigDecimal inputTax) {
        this.inputTax = inputTax;
    }

    public BigDecimal getTaxPayable() {
        return taxPayable;
    }

    public void setTaxPayable(BigDecimal taxPayable) {
        this.taxPayable = taxPayable;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public User getGeneratedBy() {
        return generatedBy;
    }

    public void setGeneratedBy(User generatedBy) {
        this.generatedBy = generatedBy;
    }

    public Instant getFinalizedAt() {
        return finalizedAt;
    }

    public void setFinalizedAt(Instant finalizedAt) {
        this.finalizedAt = finalizedAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<TaxReturnLine> getLines() {
        return lines;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
