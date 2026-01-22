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
 * Represents a sales invoice or credit note issued to a customer.
 * Invoices follow a workflow: DRAFT → ISSUED → VOID
 * Issuing an invoice creates ledger entries (AR debit, Income credit, Tax).
 * Credit notes are created against original invoices with reversed entries.
 * Voiding is done via reversal/credit note patterns.
 */
@Entity
@Table(name = "sales_invoice", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"company_id", "invoice_number"})
})
public class SalesInvoice {

    public enum InvoiceStatus {
        DRAFT,   // Can be edited
        ISSUED,  // Posted to ledger, immutable
        VOID     // Reversed/cancelled
    }

    public enum InvoiceType {
        INVOICE,     // Standard sales invoice
        CREDIT_NOTE  // Credit note/credit memo
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @NotBlank
    @Size(max = 20)
    @Column(name = "invoice_number", nullable = false, length = 20)
    private String invoiceNumber;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", nullable = false)
    private Contact contact;

    @NotNull
    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @NotNull
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_type", nullable = false, length = 15)
    private InvoiceType type = InvoiceType.INVOICE;

    // For credit notes, reference to the original invoice being credited
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_invoice_id")
    private SalesInvoice originalInvoice;

    @Size(max = 3)
    @Column(length = 3)
    private String currency;

    @Size(max = 255)
    @Column(length = 255)
    private String reference;

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

    // Link to posted transaction when invoice is issued
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posted_transaction_id")
    private Transaction postedTransaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineIndex ASC")
    private List<SalesInvoiceLine> lines = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "issued_at")
    private Instant issuedAt;

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
    public SalesInvoice() {
    }

    public SalesInvoice(Company company, String invoiceNumber, Contact contact,
                        LocalDate issueDate, LocalDate dueDate) {
        this.company = company;
        this.invoiceNumber = invoiceNumber;
        this.contact = contact;
        this.issueDate = issueDate;
        this.dueDate = dueDate;
    }

    // Helper methods
    public void addLine(SalesInvoiceLine line) {
        lines.add(line);
        line.setInvoice(this);
        line.setLineIndex(lines.size());
    }

    public void removeLine(SalesInvoiceLine line) {
        lines.remove(line);
        line.setInvoice(null);
    }

    public boolean isDraft() {
        return status == InvoiceStatus.DRAFT;
    }

    public boolean isIssued() {
        return status == InvoiceStatus.ISSUED;
    }

    public boolean isVoid() {
        return status == InvoiceStatus.VOID;
    }

    public boolean isCreditNote() {
        return type == InvoiceType.CREDIT_NOTE;
    }

    public boolean isInvoice() {
        return type == InvoiceType.INVOICE;
    }

    /**
     * Recalculates totals from line items.
     * Should be called after modifying lines.
     */
    public void recalculateTotals() {
        subtotal = BigDecimal.ZERO;
        taxTotal = BigDecimal.ZERO;
        for (SalesInvoiceLine line : lines) {
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
     * Returns true if the invoice is fully paid.
     */
    public boolean isPaid() {
        return getBalance().compareTo(BigDecimal.ZERO) <= 0;
    }

    /**
     * Returns true if the invoice is overdue (unpaid and past due date).
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

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public Contact getContact() {
        return contact;
    }

    public void setContact(Contact contact) {
        this.contact = contact;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(LocalDate issueDate) {
        this.issueDate = issueDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public void setStatus(InvoiceStatus status) {
        this.status = status;
    }

    public InvoiceType getType() {
        return type;
    }

    public void setType(InvoiceType type) {
        this.type = type;
    }

    public SalesInvoice getOriginalInvoice() {
        return originalInvoice;
    }

    public void setOriginalInvoice(SalesInvoice originalInvoice) {
        this.originalInvoice = originalInvoice;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
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

    public List<SalesInvoiceLine> getLines() {
        return lines;
    }

    public void setLines(List<SalesInvoiceLine> lines) {
        this.lines = lines;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(Instant issuedAt) {
        this.issuedAt = issuedAt;
    }
}
