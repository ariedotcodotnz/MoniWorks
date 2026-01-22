package com.example.application.service;

import com.example.application.domain.*;
import com.example.application.domain.TaxReturn.Basis;
import com.example.application.domain.TaxReturn.Status;
import com.example.application.repository.TaxLineRepository;
import com.example.application.repository.TaxReturnRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service for generating and managing GST/VAT returns.
 *
 * Generates returns from TaxLine data, producing standardized
 * box totals for NZ GST reporting (IR-03 form).
 *
 * NZ GST Return Structure:
 * - Box 5: Total sales and income (taxable amount for sales)
 * - Box 6: Zero-rated supplies
 * - Box 7: Total purchases and expenses (taxable amount for purchases)
 * - Box 9: GST collected on sales (output tax)
 * - Box 11: GST paid on purchases (input tax)
 * - Box 12: Tax to pay (output - input, if positive) or refund (if negative)
 */
@Service
@Transactional
public class TaxReturnService {

    private final TaxReturnRepository taxReturnRepository;
    private final TaxLineRepository taxLineRepository;
    private final AuditService auditService;

    public TaxReturnService(TaxReturnRepository taxReturnRepository,
                            TaxLineRepository taxLineRepository,
                            AuditService auditService) {
        this.taxReturnRepository = taxReturnRepository;
        this.taxLineRepository = taxLineRepository;
        this.auditService = auditService;
    }

    /**
     * Generates a new GST return for the specified period.
     *
     * @param company The company
     * @param startDate Period start date
     * @param endDate Period end date
     * @param basis Cash or Invoice basis
     * @param actor User generating the return
     * @return The generated TaxReturn
     */
    public TaxReturn generateReturn(Company company, LocalDate startDate, LocalDate endDate,
                                     Basis basis, User actor) {
        // Note: For now, we only support Invoice basis as we don't have
        // payment/receipt tracking for cash basis. Cash basis would require
        // tracking when invoices are paid rather than when they're issued.

        // Create the return
        TaxReturn taxReturn = new TaxReturn(company, startDate, endDate, basis);
        taxReturn.setGeneratedAt(Instant.now());
        taxReturn.setGeneratedBy(actor);
        taxReturn.setStatus(Status.DRAFT);

        // Get all tax lines for the period
        List<TaxLine> taxLines = taxLineRepository.findByCompanyAndDateRange(
            company, startDate, endDate);

        // Calculate totals
        BigDecimal totalSales = BigDecimal.ZERO;          // Box 5
        BigDecimal zeroRatedSales = BigDecimal.ZERO;      // Box 6
        BigDecimal totalPurchases = BigDecimal.ZERO;      // Box 7
        BigDecimal outputTax = BigDecimal.ZERO;           // Box 9
        BigDecimal inputTax = BigDecimal.ZERO;            // Box 11

        int salesCount = 0;
        int purchaseCount = 0;
        int zeroRatedCount = 0;

        for (TaxLine line : taxLines) {
            BigDecimal taxableAmount = line.getTaxableAmount();
            BigDecimal taxAmount = line.getTaxAmount();

            // Positive amounts are typically sales/income (credit to revenue accounts)
            // Negative amounts are typically purchases/expenses (debit to expense accounts)
            // The sign convention from LedgerEntry.getNetAmount() is: debit positive, credit negative

            if (taxableAmount.compareTo(BigDecimal.ZERO) < 0) {
                // Sales (credits to income accounts show as negative in net amount)
                totalSales = totalSales.add(taxableAmount.abs());

                if (taxAmount.compareTo(BigDecimal.ZERO) == 0 &&
                    line.getTaxRate().compareTo(BigDecimal.ZERO) == 0) {
                    // Zero-rated
                    zeroRatedSales = zeroRatedSales.add(taxableAmount.abs());
                    zeroRatedCount++;
                } else {
                    outputTax = outputTax.add(taxAmount.abs());
                }
                salesCount++;
            } else {
                // Purchases (debits to expense accounts show as positive)
                totalPurchases = totalPurchases.add(taxableAmount);
                inputTax = inputTax.add(taxAmount);
                purchaseCount++;
            }
        }

        // Tax payable = output tax - input tax
        BigDecimal taxPayable = outputTax.subtract(inputTax);

        // Set summary totals
        taxReturn.setTotalSales(totalSales);
        taxReturn.setTotalPurchases(totalPurchases);
        taxReturn.setOutputTax(outputTax);
        taxReturn.setInputTax(inputTax);
        taxReturn.setTaxPayable(taxPayable);

        // Save before adding lines (need ID)
        taxReturn = taxReturnRepository.save(taxReturn);

        // Create detailed lines
        addLine(taxReturn, "5", "Total sales and income", totalSales, salesCount);
        addLine(taxReturn, "6", "Zero-rated supplies", zeroRatedSales, zeroRatedCount);
        addLine(taxReturn, "7", "Total purchases and expenses", totalPurchases, purchaseCount);
        addLine(taxReturn, "9", "GST collected on sales", outputTax, salesCount);
        addLine(taxReturn, "11", "GST paid on purchases", inputTax, purchaseCount);
        addLine(taxReturn, "12", taxPayable.compareTo(BigDecimal.ZERO) >= 0 ?
            "GST to pay" : "GST refund due", taxPayable.abs(), 0);

        taxReturn = taxReturnRepository.save(taxReturn);

        // Log audit event
        auditService.logEvent(
            company,
            actor,
            "TAX_RETURN_GENERATED",
            "TaxReturn",
            taxReturn.getId(),
            "Generated GST return for " + startDate + " to " + endDate
        );

        return taxReturn;
    }

    private void addLine(TaxReturn taxReturn, String boxCode, String description,
                         BigDecimal amount, int count) {
        TaxReturnLine line = new TaxReturnLine(boxCode, description, amount);
        line.setTransactionCount(count);
        taxReturn.addLine(line);
    }

    /**
     * Finalizes a return, preventing further changes.
     */
    public TaxReturn finalizeReturn(TaxReturn taxReturn, User actor) {
        if (taxReturn.isFinalized()) {
            throw new IllegalStateException("Return is already finalized");
        }

        taxReturn.setStatus(Status.FINALIZED);
        taxReturn.setFinalizedAt(Instant.now());
        taxReturn = taxReturnRepository.save(taxReturn);

        auditService.logEvent(
            taxReturn.getCompany(),
            actor,
            "TAX_RETURN_FINALIZED",
            "TaxReturn",
            taxReturn.getId(),
            "Finalized GST return for " + taxReturn.getStartDate() + " to " + taxReturn.getEndDate()
        );

        return taxReturn;
    }

    /**
     * Marks a return as filed.
     */
    public TaxReturn markAsFiled(TaxReturn taxReturn, User actor) {
        if (taxReturn.getStatus() != Status.FINALIZED) {
            throw new IllegalStateException("Return must be finalized before filing");
        }

        taxReturn.setStatus(Status.FILED);
        taxReturn = taxReturnRepository.save(taxReturn);

        auditService.logEvent(
            taxReturn.getCompany(),
            actor,
            "TAX_RETURN_FILED",
            "TaxReturn",
            taxReturn.getId(),
            "Marked GST return as filed"
        );

        return taxReturn;
    }

    /**
     * Deletes a draft return.
     */
    public void deleteReturn(TaxReturn taxReturn) {
        if (!taxReturn.isDraft()) {
            throw new IllegalStateException("Can only delete draft returns");
        }
        taxReturnRepository.delete(taxReturn);
    }

    /**
     * Finds all returns for a company.
     */
    @Transactional(readOnly = true)
    public List<TaxReturn> findByCompany(Company company) {
        return taxReturnRepository.findByCompanyOrderByEndDateDesc(company);
    }

    /**
     * Finds a return by ID.
     */
    @Transactional(readOnly = true)
    public Optional<TaxReturn> findById(Long id) {
        return taxReturnRepository.findById(id);
    }

    /**
     * Gets the tax lines that contributed to a return (for drilldown).
     */
    @Transactional(readOnly = true)
    public List<TaxLine> getTaxLinesForReturn(TaxReturn taxReturn) {
        return taxLineRepository.findByCompanyAndDateRange(
            taxReturn.getCompany(),
            taxReturn.getStartDate(),
            taxReturn.getEndDate()
        );
    }

    /**
     * Gets tax lines filtered by report box for drilldown.
     */
    @Transactional(readOnly = true)
    public List<TaxLine> getTaxLinesForBox(TaxReturn taxReturn, String boxCode) {
        return taxLineRepository.findByCompanyAndReportBoxAndDateRange(
            taxReturn.getCompany(),
            boxCode,
            taxReturn.getStartDate(),
            taxReturn.getEndDate()
        );
    }
}
