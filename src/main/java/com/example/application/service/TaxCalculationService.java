package com.example.application.service;

import com.example.application.domain.*;
import com.example.application.repository.TaxCodeRepository;
import com.example.application.repository.TaxLineRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for calculating tax amounts on transactions.
 *
 * Handles:
 * - Tax calculation based on tax codes
 * - Tax-inclusive and tax-exclusive amounts
 * - Creating TaxLine records for ledger entries
 * - Rounding rules (half-up to 2 decimal places)
 *
 * GST/VAT Calculation:
 * - Tax-exclusive: amount * rate = tax
 * - Tax-inclusive: amount * rate / (1 + rate) = tax
 */
@Service
@Transactional
public class TaxCalculationService {

    private final TaxCodeRepository taxCodeRepository;
    private final TaxLineRepository taxLineRepository;

    public TaxCalculationService(TaxCodeRepository taxCodeRepository,
                                  TaxLineRepository taxLineRepository) {
        this.taxCodeRepository = taxCodeRepository;
        this.taxLineRepository = taxLineRepository;
    }

    /**
     * Calculates tax for a given amount and tax code.
     * Assumes tax-exclusive amount (add tax on top).
     *
     * @param company The company (for tax code lookup)
     * @param taxCodeStr The tax code string (e.g., "GST")
     * @param amount The taxable amount (tax-exclusive)
     * @return Calculated tax amount, or ZERO if no tax code or tax code not found
     */
    public BigDecimal calculateTax(Company company, String taxCodeStr, BigDecimal amount) {
        if (taxCodeStr == null || taxCodeStr.isBlank()) {
            return BigDecimal.ZERO;
        }

        Optional<TaxCode> taxCodeOpt = taxCodeRepository.findByCompanyAndCode(company, taxCodeStr);
        if (taxCodeOpt.isEmpty()) {
            return BigDecimal.ZERO;
        }

        TaxCode taxCode = taxCodeOpt.get();

        // For EXEMPT and OUT_OF_SCOPE, no tax
        if (taxCode.getType() == TaxCode.TaxType.EXEMPT ||
            taxCode.getType() == TaxCode.TaxType.OUT_OF_SCOPE) {
            return BigDecimal.ZERO;
        }

        // Calculate tax: amount * rate, rounded half-up to 2 decimal places
        return amount.multiply(taxCode.getRate())
                     .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates tax for a tax-inclusive amount.
     * Extracts the tax component from an amount that already includes tax.
     *
     * Formula: tax = amount * rate / (1 + rate)
     *
     * @param company The company (for tax code lookup)
     * @param taxCodeStr The tax code string
     * @param inclusiveAmount The total amount including tax
     * @return The tax component, or ZERO if no tax code
     */
    public BigDecimal calculateTaxFromInclusive(Company company, String taxCodeStr,
                                                 BigDecimal inclusiveAmount) {
        if (taxCodeStr == null || taxCodeStr.isBlank()) {
            return BigDecimal.ZERO;
        }

        Optional<TaxCode> taxCodeOpt = taxCodeRepository.findByCompanyAndCode(company, taxCodeStr);
        if (taxCodeOpt.isEmpty()) {
            return BigDecimal.ZERO;
        }

        TaxCode taxCode = taxCodeOpt.get();

        // For EXEMPT and OUT_OF_SCOPE, no tax
        if (taxCode.getType() == TaxCode.TaxType.EXEMPT ||
            taxCode.getType() == TaxCode.TaxType.OUT_OF_SCOPE) {
            return BigDecimal.ZERO;
        }

        // tax = amount * rate / (1 + rate)
        BigDecimal rate = taxCode.getRate();
        BigDecimal divisor = BigDecimal.ONE.add(rate);

        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return inclusiveAmount.multiply(rate)
                             .divide(divisor, 2, RoundingMode.HALF_UP);
    }

    /**
     * Extracts the taxable (tax-exclusive) amount from a tax-inclusive amount.
     *
     * Formula: taxable = amount / (1 + rate)
     *
     * @param company The company
     * @param taxCodeStr The tax code string
     * @param inclusiveAmount The total amount including tax
     * @return The taxable (pre-tax) amount
     */
    public BigDecimal extractTaxableAmount(Company company, String taxCodeStr,
                                           BigDecimal inclusiveAmount) {
        if (taxCodeStr == null || taxCodeStr.isBlank()) {
            return inclusiveAmount;
        }

        Optional<TaxCode> taxCodeOpt = taxCodeRepository.findByCompanyAndCode(company, taxCodeStr);
        if (taxCodeOpt.isEmpty()) {
            return inclusiveAmount;
        }

        TaxCode taxCode = taxCodeOpt.get();

        // For EXEMPT and OUT_OF_SCOPE, the full amount is taxable (or non-taxable)
        if (taxCode.getType() == TaxCode.TaxType.EXEMPT ||
            taxCode.getType() == TaxCode.TaxType.OUT_OF_SCOPE) {
            return inclusiveAmount;
        }

        // taxable = amount / (1 + rate)
        BigDecimal rate = taxCode.getRate();
        BigDecimal divisor = BigDecimal.ONE.add(rate);

        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
            return inclusiveAmount;
        }

        return inclusiveAmount.divide(divisor, 2, RoundingMode.HALF_UP);
    }

    /**
     * Creates TaxLine records for all ledger entries in a posted transaction.
     * Called during the posting process to record tax calculations.
     *
     * @param company The company
     * @param ledgerEntries The ledger entries created during posting
     * @return List of created TaxLine records
     */
    public List<TaxLine> createTaxLinesForLedgerEntries(Company company,
                                                         List<LedgerEntry> ledgerEntries) {
        List<TaxLine> taxLines = new ArrayList<>();

        for (LedgerEntry entry : ledgerEntries) {
            String taxCodeStr = entry.getTaxCode();
            if (taxCodeStr == null || taxCodeStr.isBlank()) {
                continue;
            }

            Optional<TaxCode> taxCodeOpt = taxCodeRepository.findByCompanyAndCode(company, taxCodeStr);
            if (taxCodeOpt.isEmpty()) {
                continue;
            }

            TaxCode taxCode = taxCodeOpt.get();

            // Skip exempt and out of scope
            if (taxCode.getType() == TaxCode.TaxType.EXEMPT ||
                taxCode.getType() == TaxCode.TaxType.OUT_OF_SCOPE) {
                continue;
            }

            // Get the net amount (positive for debits, negative for credits)
            BigDecimal netAmount = entry.getNetAmount();

            // For tax calculation, we treat the ledger amount as tax-inclusive
            // This is the NZ standard approach where the entered amount includes GST
            BigDecimal taxableAmount = extractTaxableAmountInternal(netAmount, taxCode.getRate());
            BigDecimal taxAmount = netAmount.subtract(taxableAmount);

            TaxLine taxLine = new TaxLine(
                company,
                entry,
                taxCodeStr,
                taxCode.getRate(),
                taxableAmount,
                taxAmount
            );
            taxLine.setReportBox(taxCode.getReportBox());

            taxLines.add(taxLine);
        }

        if (!taxLines.isEmpty()) {
            taxLineRepository.saveAll(taxLines);
        }

        return taxLines;
    }

    /**
     * Internal method to extract taxable amount without DB lookup.
     */
    private BigDecimal extractTaxableAmountInternal(BigDecimal inclusiveAmount, BigDecimal rate) {
        if (rate.compareTo(BigDecimal.ZERO) == 0) {
            return inclusiveAmount;
        }

        BigDecimal divisor = BigDecimal.ONE.add(rate);
        return inclusiveAmount.divide(divisor, 2, RoundingMode.HALF_UP);
    }

    /**
     * Gets the TaxCode entity for a tax code string.
     */
    @Transactional(readOnly = true)
    public Optional<TaxCode> getTaxCode(Company company, String taxCodeStr) {
        if (taxCodeStr == null || taxCodeStr.isBlank()) {
            return Optional.empty();
        }
        return taxCodeRepository.findByCompanyAndCode(company, taxCodeStr);
    }

    /**
     * Validates that a tax code exists and is active.
     *
     * @param company The company
     * @param taxCodeStr The tax code string
     * @return true if valid, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isValidTaxCode(Company company, String taxCodeStr) {
        if (taxCodeStr == null || taxCodeStr.isBlank()) {
            return true; // Empty tax code is valid (means no tax)
        }

        Optional<TaxCode> taxCodeOpt = taxCodeRepository.findByCompanyAndCode(company, taxCodeStr);
        return taxCodeOpt.isPresent() && taxCodeOpt.get().isActive();
    }
}
