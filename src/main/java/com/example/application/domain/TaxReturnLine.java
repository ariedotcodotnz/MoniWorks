package com.example.application.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Represents a single line/box in a GST/VAT return.
 *
 * Each line corresponds to a reporting box (e.g., Box 5 for total sales,
 * Box 6 for zero-rated sales, Box 9 for total GST collected, etc.)
 *
 * NZ GST Return boxes (IR-03):
 * - Box 5: Total sales and income
 * - Box 6: Zero-rated supplies
 * - Box 7: Total purchases and expenses
 * - Box 8: Credit adjustments
 * - Box 9: Debit adjustments
 * - Box 10: GST collected (output tax)
 * - Box 11: GST paid (input tax)
 * - Box 12: Tax payable/refund
 */
@Entity
@Table(name = "tax_return_line")
public class TaxReturnLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tax_return_id", nullable = false)
    private TaxReturn taxReturn;

    @NotBlank
    @Size(max = 20)
    @Column(name = "box_code", nullable = false, length = 20)
    private String boxCode;

    @Size(max = 100)
    @Column(name = "box_description", length = 100)
    private String boxDescription;

    @NotNull
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "transaction_count")
    private int transactionCount = 0;

    // Constructors
    public TaxReturnLine() {
    }

    public TaxReturnLine(String boxCode, String boxDescription, BigDecimal amount) {
        this.boxCode = boxCode;
        this.boxDescription = boxDescription;
        this.amount = amount;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TaxReturn getTaxReturn() {
        return taxReturn;
    }

    public void setTaxReturn(TaxReturn taxReturn) {
        this.taxReturn = taxReturn;
    }

    public String getBoxCode() {
        return boxCode;
    }

    public void setBoxCode(String boxCode) {
        this.boxCode = boxCode;
    }

    public String getBoxDescription() {
        return boxDescription;
    }

    public void setBoxDescription(String boxDescription) {
        this.boxDescription = boxDescription;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public int getTransactionCount() {
        return transactionCount;
    }

    public void setTransactionCount(int transactionCount) {
        this.transactionCount = transactionCount;
    }
}
