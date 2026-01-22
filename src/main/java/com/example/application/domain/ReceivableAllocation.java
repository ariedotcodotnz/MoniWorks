package com.example.application.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Tracks allocation of receipt transactions to sales invoices.
 * Supports partial payments (one receipt split across multiple invoices)
 * and overpayments (allocated amount exceeds invoice balance - creates credit).
 */
@Entity
@Table(name = "receivable_allocation")
public class ReceivableAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    // The receipt transaction being allocated
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_transaction_id", nullable = false)
    private Transaction receiptTransaction;

    // The invoice receiving the payment
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_invoice_id", nullable = false)
    private SalesInvoice salesInvoice;

    // Amount allocated from this receipt to this invoice
    @NotNull
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "allocated_at", nullable = false)
    private Instant allocatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allocated_by")
    private User allocatedBy;

    @PrePersist
    protected void onCreate() {
        allocatedAt = Instant.now();
    }

    // Constructors
    public ReceivableAllocation() {
    }

    public ReceivableAllocation(Company company, Transaction receiptTransaction,
                                SalesInvoice salesInvoice, BigDecimal amount) {
        this.company = company;
        this.receiptTransaction = receiptTransaction;
        this.salesInvoice = salesInvoice;
        this.amount = amount;
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

    public Transaction getReceiptTransaction() {
        return receiptTransaction;
    }

    public void setReceiptTransaction(Transaction receiptTransaction) {
        this.receiptTransaction = receiptTransaction;
    }

    public SalesInvoice getSalesInvoice() {
        return salesInvoice;
    }

    public void setSalesInvoice(SalesInvoice salesInvoice) {
        this.salesInvoice = salesInvoice;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Instant getAllocatedAt() {
        return allocatedAt;
    }

    public void setAllocatedAt(Instant allocatedAt) {
        this.allocatedAt = allocatedAt;
    }

    public User getAllocatedBy() {
        return allocatedBy;
    }

    public void setAllocatedBy(User allocatedBy) {
        this.allocatedBy = allocatedBy;
    }
}
