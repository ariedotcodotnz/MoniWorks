package com.example.application.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Represents a single line item on a supplier bill.
 * Lines can reference a Product for auto-fill or use free-form descriptions.
 * Tax is calculated based on tax code and supports tax-inclusive pricing.
 */
@Entity
@Table(name = "supplier_bill_line")
public class SupplierBillLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_bill_id", nullable = false)
    private SupplierBill bill;

    @Column(name = "line_index", nullable = false)
    private int lineIndex;

    // Optional product reference for auto-fill
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    // Free-form description (or product name if from product)
    @Size(max = 500)
    @Column(length = 500)
    private String description;

    @NotNull
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity = BigDecimal.ONE;

    @NotNull
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    // Expense/asset account to debit
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    // Tax code for this line
    @Size(max = 10)
    @Column(name = "tax_code", length = 10)
    private String taxCode;

    // Tax rate stored for historical accuracy (from TaxCode at time of creation)
    @Column(name = "tax_rate", precision = 5, scale = 2)
    private BigDecimal taxRate;

    // Calculated tax amount (extracted from unit price if tax-inclusive)
    @Column(name = "tax_amount", precision = 19, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    // Line total before tax (qty * unit price - tax if inclusive)
    @Column(name = "line_total", precision = 19, scale = 2)
    private BigDecimal lineTotal = BigDecimal.ZERO;

    // Optional department/cost center
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    // Constructors
    public SupplierBillLine() {
    }

    public SupplierBillLine(Account account, BigDecimal quantity, BigDecimal unitPrice) {
        this.account = account;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        calculateTotals();
    }

    /**
     * Calculates line total and tax amount.
     * Assumes tax-inclusive pricing (NZ standard) - extracts tax from total.
     */
    public void calculateTotals() {
        BigDecimal grossTotal = quantity.multiply(unitPrice)
            .setScale(2, RoundingMode.HALF_UP);

        if (taxRate != null && taxRate.compareTo(BigDecimal.ZERO) > 0) {
            // Tax-inclusive: extract tax from gross amount
            // Tax = Gross - (Gross / (1 + rate))
            BigDecimal divisor = BigDecimal.ONE.add(taxRate.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
            BigDecimal netAmount = grossTotal.divide(divisor, 2, RoundingMode.HALF_UP);
            taxAmount = grossTotal.subtract(netAmount);
            lineTotal = netAmount;
        } else {
            // No tax or zero rate
            taxAmount = BigDecimal.ZERO;
            lineTotal = grossTotal;
        }
    }

    /**
     * Returns the gross total (line total + tax).
     */
    public BigDecimal getGrossTotal() {
        return lineTotal.add(taxAmount != null ? taxAmount : BigDecimal.ZERO);
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SupplierBill getBill() {
        return bill;
    }

    public void setBill(SupplierBill bill) {
        this.bill = bill;
    }

    public int getLineIndex() {
        return lineIndex;
    }

    public void setLineIndex(int lineIndex) {
        this.lineIndex = lineIndex;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public String getTaxCode() {
        return taxCode;
    }

    public void setTaxCode(String taxCode) {
        this.taxCode = taxCode;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public void setLineTotal(BigDecimal lineTotal) {
        this.lineTotal = lineTotal;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }
}
