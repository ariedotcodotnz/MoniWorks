package com.example.application.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Tracks allocation of payment transactions to supplier bills.
 * Supports partial payments (one payment split across multiple bills)
 * and overpayments (allocated amount exceeds bill balance - creates credit).
 */
@Entity
@Table(name = "payable_allocation")
public class PayableAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    // The payment transaction being allocated
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_transaction_id", nullable = false)
    private Transaction paymentTransaction;

    // The bill receiving the payment
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_bill_id", nullable = false)
    private SupplierBill supplierBill;

    // Amount allocated from this payment to this bill
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
    public PayableAllocation() {
    }

    public PayableAllocation(Company company, Transaction paymentTransaction,
                             SupplierBill supplierBill, BigDecimal amount) {
        this.company = company;
        this.paymentTransaction = paymentTransaction;
        this.supplierBill = supplierBill;
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

    public Transaction getPaymentTransaction() {
        return paymentTransaction;
    }

    public void setPaymentTransaction(Transaction paymentTransaction) {
        this.paymentTransaction = paymentTransaction;
    }

    public SupplierBill getSupplierBill() {
        return supplierBill;
    }

    public void setSupplierBill(SupplierBill supplierBill) {
        this.supplierBill = supplierBill;
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
