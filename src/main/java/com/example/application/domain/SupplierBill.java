package com.example.application.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a supplier bill (purchase invoice) received from a supplier.
 * Bills follow a workflow: DRAFT → POSTED → VOID
 * Posting a bill creates ledger entries (AP credit, Expense debit, Input Tax debit).
 * Voiding is done via reversal patterns.
 */
@Entity
@Table(name = "supplier_bill", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"company_id", "bill_number"})
})
public class SupplierBill {

    public enum BillStatus {
        DRAFT,   // Can be edited
        POSTED,  // Posted to ledger, immutable
        VOID     // Reversed/cancelled
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @NotBlank
    @Size(max = 50)
    @Column(name = "bill_number", nullable = false, length = 50)
    private String billNumber;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", nullable = false)
    private Contact contact;

    @NotNull
    @Column(name = "bill_date", nullable = false)
    private LocalDate billDate;

    @NotNull
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BillStatus status = BillStatus.DRAFT;

    @Size(max = 3)
    @Column(length = 3)
    private String currency;

    // Supplier's invoice reference number
    @Size(max = 100)
    @Column(name = "supplier_reference", length = 100)
    private String supplierReference;

    @Size(max = 500)
    @Column(length = 500)
    private String notes;

    // Cached totals for display/queries (recalculated from lines)
    @Column(name = "subtotal", precision = 19, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tax_total", precision = 19, scale = 2)
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(name = "total", precision = 19, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "amount_paid", precision = 19, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    // Link to posted transaction when bill is posted
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posted_transaction_id")
    private Transaction postedTransaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineIndex ASC")
    private List<SupplierBillLine> lines = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "posted_at")
    private Instant postedAt;

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
    public SupplierBill() {
    }

    public SupplierBill(Company company, String billNumber, Contact contact,
                        LocalDate billDate, LocalDate dueDate) {
        this.company = company;
        this.billNumber = billNumber;
        this.contact = contact;
        this.billDate = billDate;
        this.dueDate = dueDate;
    }

    // Helper methods
    public void addLine(SupplierBillLine line) {
        lines.add(line);
        line.setBill(this);
        line.setLineIndex(lines.size());
    }

    public void removeLine(SupplierBillLine line) {
        lines.remove(line);
        line.setBill(null);
    }

    public boolean isDraft() {
        return status == BillStatus.DRAFT;
    }

    public boolean isPosted() {
        return status == BillStatus.POSTED;
    }

    public boolean isVoid() {
        return status == BillStatus.VOID;
    }

    /**
     * Recalculates totals from line items.
     * Should be called after modifying lines.
     */
    public void recalculateTotals() {
        subtotal = BigDecimal.ZERO;
        taxTotal = BigDecimal.ZERO;
        for (SupplierBillLine line : lines) {
            subtotal = subtotal.add(line.getLineTotal() != null ? line.getLineTotal() : BigDecimal.ZERO);
            taxTotal = taxTotal.add(line.getTaxAmount() != null ? line.getTaxAmount() : BigDecimal.ZERO);
        }
        total = subtotal.add(taxTotal);
    }

    /**
     * Returns the outstanding balance (total minus amount paid).
     */
    public BigDecimal getBalance() {
        return total.subtract(amountPaid != null ? amountPaid : BigDecimal.ZERO);
    }

    /**
     * Returns true if the bill is fully paid.
     */
    public boolean isPaid() {
        return getBalance().compareTo(BigDecimal.ZERO) <= 0;
    }

    /**
     * Returns true if the bill is overdue (unpaid and past due date).
     */
    public boolean isOverdue() {
        return !isPaid() && dueDate != null && LocalDate.now().isAfter(dueDate);
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

    public String getBillNumber() {
        return billNumber;
    }

    public void setBillNumber(String billNumber) {
        this.billNumber = billNumber;
    }

    public Contact getContact() {
        return contact;
    }

    public void setContact(Contact contact) {
        this.contact = contact;
    }

    public LocalDate getBillDate() {
        return billDate;
    }

    public void setBillDate(LocalDate billDate) {
        this.billDate = billDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public BillStatus getStatus() {
        return status;
    }

    public void setStatus(BillStatus status) {
        this.status = status;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getSupplierReference() {
        return supplierReference;
    }

    public void setSupplierReference(String supplierReference) {
        this.supplierReference = supplierReference;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getTaxTotal() {
        return taxTotal;
    }

    public void setTaxTotal(BigDecimal taxTotal) {
        this.taxTotal = taxTotal;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public void setAmountPaid(BigDecimal amountPaid) {
        this.amountPaid = amountPaid;
    }

    public Transaction getPostedTransaction() {
        return postedTransaction;
    }

    public void setPostedTransaction(Transaction postedTransaction) {
        this.postedTransaction = postedTransaction;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public List<SupplierBillLine> getLines() {
        return lines;
    }

    public void setLines(List<SupplierBillLine> lines) {
        this.lines = lines;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public void setPostedAt(Instant postedAt) {
        this.postedAt = postedAt;
    }
}
