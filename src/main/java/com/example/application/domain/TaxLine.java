package com.example.application.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents a calculated tax amount for a ledger entry.
 * Each posted transaction line with a tax code generates a TaxLine
 * that records the taxable amount and calculated tax.
 *
 * TaxLines are used for:
 * - GST/VAT return generation
 * - Tax reporting by box mapping
 * - Audit trail of tax calculations
 */
@Entity
@Table(name = "tax_line", indexes = {
    @Index(name = "idx_tax_line_company_date", columnList = "company_id, entry_date"),
    @Index(name = "idx_tax_line_tax_code", columnList = "company_id, tax_code")
})
public class TaxLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ledger_entry_id", nullable = false)
    private LedgerEntry ledgerEntry;

    @NotNull
    @Column(name = "entry_date", nullable = false)
    private java.time.LocalDate entryDate;

    @NotNull
    @Size(max = 10)
    @Column(name = "tax_code", nullable = false, length = 10)
    private String taxCode;

    @NotNull
    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal taxRate;

    @NotNull
    @Column(name = "taxable_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal taxableAmount;

    @NotNull
    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal taxAmount;

    @Size(max = 20)
    @Column(name = "report_box", length = 20)
    private String reportBox;

    @Size(max = 10)
    @Column(length = 10)
    private String jurisdiction;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    // Constructors
    public TaxLine() {
    }

    public TaxLine(Company company, LedgerEntry ledgerEntry, String taxCode,
                   BigDecimal taxRate, BigDecimal taxableAmount, BigDecimal taxAmount) {
        this.company = company;
        this.ledgerEntry = ledgerEntry;
        this.entryDate = ledgerEntry.getEntryDate();
        this.taxCode = taxCode;
        this.taxRate = taxRate;
        this.taxableAmount = taxableAmount;
        this.taxAmount = taxAmount;
    }

    // Getters (immutable after creation)
    public Long getId() {
        return id;
    }

    public Company getCompany() {
        return company;
    }

    public LedgerEntry getLedgerEntry() {
        return ledgerEntry;
    }

    public java.time.LocalDate getEntryDate() {
        return entryDate;
    }

    public String getTaxCode() {
        return taxCode;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public BigDecimal getTaxableAmount() {
        return taxableAmount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public String getReportBox() {
        return reportBox;
    }

    public void setReportBox(String reportBox) {
        this.reportBox = reportBox;
    }

    public String getJurisdiction() {
        return jurisdiction;
    }

    public void setJurisdiction(String jurisdiction) {
        this.jurisdiction = jurisdiction;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
