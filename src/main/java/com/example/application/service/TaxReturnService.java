package com.example.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.domain.*;
import com.example.application.domain.TaxReturn.Basis;
import com.example.application.domain.TaxReturn.Status;
import com.example.application.repository.PayableAllocationRepository;
import com.example.application.repository.ReceivableAllocationRepository;
import com.example.application.repository.TaxLineRepository;
import com.example.application.repository.TaxReturnRepository;

/**
 * Service for generating and managing GST/VAT returns.
 *
 * <p>Generates returns from TaxLine data (invoice basis) or allocation data (cash basis), producing
 * standardized box totals for NZ GST reporting (IR-03 form).
 *
 * <p>NZ GST Return Structure: - Box 5: Total sales and income (taxable amount for sales) - Box 6:
 * Zero-rated supplies - Box 7: Total purchases and expenses (taxable amount for purchases) - Box 9:
 * GST collected on sales (output tax) - Box 11: GST paid on purchases (input tax) - Box 12: Tax to
 * pay (output - input, if positive) or refund (if negative)
 *
 * <p>Supports two tax bases per spec 06:
 *
 * <ul>
 *   <li><b>Invoice/Accrual basis</b>: Tax recognized when invoice is issued/bill is posted
 *   <li><b>Cash basis</b>: Tax recognized when payment is received/made (proportional to payment
 *       amount)
 * </ul>
 */
@Service
@Transactional
public class TaxReturnService {

  private final TaxReturnRepository taxReturnRepository;
  private final TaxLineRepository taxLineRepository;
  private final ReceivableAllocationRepository receivableAllocationRepository;
  private final PayableAllocationRepository payableAllocationRepository;
  private final AuditService auditService;

  public TaxReturnService(
      TaxReturnRepository taxReturnRepository,
      TaxLineRepository taxLineRepository,
      ReceivableAllocationRepository receivableAllocationRepository,
      PayableAllocationRepository payableAllocationRepository,
      AuditService auditService) {
    this.taxReturnRepository = taxReturnRepository;
    this.taxLineRepository = taxLineRepository;
    this.receivableAllocationRepository = receivableAllocationRepository;
    this.payableAllocationRepository = payableAllocationRepository;
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
  public TaxReturn generateReturn(
      Company company, LocalDate startDate, LocalDate endDate, Basis basis, User actor) {

    // Create the return
    TaxReturn taxReturn = new TaxReturn(company, startDate, endDate, basis);
    taxReturn.setGeneratedAt(Instant.now());
    taxReturn.setGeneratedBy(actor);
    taxReturn.setStatus(Status.DRAFT);

    // Calculate totals based on the selected basis
    TaxTotals totals;
    if (basis == Basis.CASH) {
      totals = calculateCashBasisTotals(company, startDate, endDate);
    } else {
      totals = calculateInvoiceBasisTotals(company, startDate, endDate);
    }

    // Tax payable = output tax - input tax
    BigDecimal taxPayable = totals.outputTax.subtract(totals.inputTax);

    // Set summary totals
    taxReturn.setTotalSales(totals.totalSales);
    taxReturn.setTotalPurchases(totals.totalPurchases);
    taxReturn.setOutputTax(totals.outputTax);
    taxReturn.setInputTax(totals.inputTax);
    taxReturn.setTaxPayable(taxPayable);

    // Save before adding lines (need ID)
    taxReturn = taxReturnRepository.save(taxReturn);

    // Create detailed lines
    addLine(taxReturn, "5", "Total sales and income", totals.totalSales, totals.salesCount);
    addLine(taxReturn, "6", "Zero-rated supplies", totals.zeroRatedSales, totals.zeroRatedCount);
    addLine(
        taxReturn,
        "7",
        "Total purchases and expenses",
        totals.totalPurchases,
        totals.purchaseCount);
    addLine(taxReturn, "9", "GST collected on sales", totals.outputTax, totals.salesCount);
    addLine(taxReturn, "11", "GST paid on purchases", totals.inputTax, totals.purchaseCount);
    addLine(
        taxReturn,
        "12",
        taxPayable.compareTo(BigDecimal.ZERO) >= 0 ? "GST to pay" : "GST refund due",
        taxPayable.abs(),
        0);

    taxReturn = taxReturnRepository.save(taxReturn);

    // Log audit event
    String basisDescription = basis == Basis.CASH ? "Cash" : "Invoice";
    auditService.logEvent(
        company,
        actor,
        "TAX_RETURN_GENERATED",
        "TaxReturn",
        taxReturn.getId(),
        "Generated GST return ("
            + basisDescription
            + " basis) for "
            + startDate
            + " to "
            + endDate);

    return taxReturn;
  }

  /**
   * Calculates tax totals using invoice/accrual basis. Tax is recognized when transactions are
   * posted to the ledger, based on the transaction date.
   */
  private TaxTotals calculateInvoiceBasisTotals(
      Company company, LocalDate startDate, LocalDate endDate) {
    TaxTotals totals = new TaxTotals();

    // Get all tax lines for the period
    List<TaxLine> taxLines =
        taxLineRepository.findByCompanyAndDateRange(company, startDate, endDate);

    for (TaxLine line : taxLines) {
      BigDecimal taxableAmount = line.getTaxableAmount();
      BigDecimal taxAmount = line.getTaxAmount();

      // Positive amounts are typically sales/income (credit to revenue accounts)
      // Negative amounts are typically purchases/expenses (debit to expense accounts)
      // The sign convention from LedgerEntry.getNetAmount() is: debit positive, credit negative

      if (taxableAmount.compareTo(BigDecimal.ZERO) < 0) {
        // Sales (credits to income accounts show as negative in net amount)
        totals.totalSales = totals.totalSales.add(taxableAmount.abs());

        if (taxAmount.compareTo(BigDecimal.ZERO) == 0
            && line.getTaxRate().compareTo(BigDecimal.ZERO) == 0) {
          // Zero-rated
          totals.zeroRatedSales = totals.zeroRatedSales.add(taxableAmount.abs());
          totals.zeroRatedCount++;
        } else {
          totals.outputTax = totals.outputTax.add(taxAmount.abs());
        }
        totals.salesCount++;
      } else {
        // Purchases (debits to expense accounts show as positive)
        totals.totalPurchases = totals.totalPurchases.add(taxableAmount);
        totals.inputTax = totals.inputTax.add(taxAmount);
        totals.purchaseCount++;
      }
    }

    return totals;
  }

  /**
   * Calculates tax totals using cash basis. Tax is recognized when payments are received (for
   * sales) or made (for purchases), proportional to the payment amount vs the invoice/bill total.
   *
   * <p>For partial payments, the tax is calculated proportionally: (payment amount / invoice total)
   * * invoice tax total
   */
  private TaxTotals calculateCashBasisTotals(
      Company company, LocalDate startDate, LocalDate endDate) {
    TaxTotals totals = new TaxTotals();

    // Convert LocalDate to Instant for allocation queries
    Instant startInstant = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
    Instant endInstant = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

    // Process receipts (payments received for sales invoices)
    List<ReceivableAllocation> receivableAllocations =
        receivableAllocationRepository.findByCompanyAndAllocatedAtRange(
            company, startInstant, endInstant);

    for (ReceivableAllocation allocation : receivableAllocations) {
      SalesInvoice invoice = allocation.getSalesInvoice();
      BigDecimal allocationAmount = allocation.getAmount();

      // Skip if invoice has no total (shouldn't happen but guard against it)
      if (invoice.getTotal() == null || invoice.getTotal().compareTo(BigDecimal.ZERO) == 0) {
        continue;
      }

      // Calculate proportion of payment vs invoice total
      BigDecimal proportion = allocationAmount.divide(invoice.getTotal(), 10, RoundingMode.HALF_UP);

      // Calculate proportional amounts
      BigDecimal proportionalSales =
          invoice.getSubtotal().multiply(proportion).setScale(2, RoundingMode.HALF_UP);
      BigDecimal proportionalTax =
          invoice.getTaxTotal().multiply(proportion).setScale(2, RoundingMode.HALF_UP);

      // Add to totals
      totals.totalSales = totals.totalSales.add(proportionalSales);

      // Check if zero-rated (tax total is zero)
      if (invoice.getTaxTotal() == null || invoice.getTaxTotal().compareTo(BigDecimal.ZERO) == 0) {
        totals.zeroRatedSales = totals.zeroRatedSales.add(proportionalSales);
        totals.zeroRatedCount++;
      } else {
        totals.outputTax = totals.outputTax.add(proportionalTax);
      }
      totals.salesCount++;
    }

    // Process payments (payments made for supplier bills)
    List<PayableAllocation> payableAllocations =
        payableAllocationRepository.findByCompanyAndAllocatedAtRange(
            company, startInstant, endInstant);

    for (PayableAllocation allocation : payableAllocations) {
      SupplierBill bill = allocation.getSupplierBill();
      BigDecimal allocationAmount = allocation.getAmount();

      // Skip if bill has no total
      if (bill.getTotal() == null || bill.getTotal().compareTo(BigDecimal.ZERO) == 0) {
        continue;
      }

      // Calculate proportion of payment vs bill total
      BigDecimal proportion = allocationAmount.divide(bill.getTotal(), 10, RoundingMode.HALF_UP);

      // Calculate proportional amounts
      BigDecimal proportionalPurchases =
          bill.getSubtotal().multiply(proportion).setScale(2, RoundingMode.HALF_UP);
      BigDecimal proportionalTax =
          bill.getTaxTotal().multiply(proportion).setScale(2, RoundingMode.HALF_UP);

      // Add to totals
      totals.totalPurchases = totals.totalPurchases.add(proportionalPurchases);
      totals.inputTax = totals.inputTax.add(proportionalTax);
      totals.purchaseCount++;
    }

    return totals;
  }

  /** Helper class to hold calculated tax totals. */
  private static class TaxTotals {
    BigDecimal totalSales = BigDecimal.ZERO;
    BigDecimal zeroRatedSales = BigDecimal.ZERO;
    BigDecimal totalPurchases = BigDecimal.ZERO;
    BigDecimal outputTax = BigDecimal.ZERO;
    BigDecimal inputTax = BigDecimal.ZERO;
    int salesCount = 0;
    int purchaseCount = 0;
    int zeroRatedCount = 0;
  }

  private void addLine(
      TaxReturn taxReturn, String boxCode, String description, BigDecimal amount, int count) {
    TaxReturnLine line = new TaxReturnLine(boxCode, description, amount);
    line.setTransactionCount(count);
    taxReturn.addLine(line);
  }

  /** Finalizes a return, preventing further changes. */
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
        "Finalized GST return for " + taxReturn.getStartDate() + " to " + taxReturn.getEndDate());

    return taxReturn;
  }

  /** Marks a return as filed. */
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
        "Marked GST return as filed");

    return taxReturn;
  }

  /** Deletes a draft return. */
  public void deleteReturn(TaxReturn taxReturn) {
    if (!taxReturn.isDraft()) {
      throw new IllegalStateException("Can only delete draft returns");
    }
    taxReturnRepository.delete(taxReturn);
  }

  /** Finds all returns for a company. */
  @Transactional(readOnly = true)
  public List<TaxReturn> findByCompany(Company company) {
    return taxReturnRepository.findByCompanyOrderByEndDateDesc(company);
  }

  /** Finds a return by ID. */
  @Transactional(readOnly = true)
  public Optional<TaxReturn> findById(Long id) {
    return taxReturnRepository.findById(id);
  }

  /**
   * Gets the tax lines that contributed to a return (for drilldown). For invoice basis returns,
   * returns the tax lines from ledger entries. For cash basis returns, returns tax lines from
   * invoices/bills that had payments in the period.
   */
  @Transactional(readOnly = true)
  public List<TaxLine> getTaxLinesForReturn(TaxReturn taxReturn) {
    // For now, return tax lines based on entry date (works for invoice basis)
    // Cash basis drilldown would need different approach to show proportional amounts
    return taxLineRepository.findByCompanyAndDateRange(
        taxReturn.getCompany(), taxReturn.getStartDate(), taxReturn.getEndDate());
  }

  /** Gets tax lines filtered by report box for drilldown. */
  @Transactional(readOnly = true)
  public List<TaxLine> getTaxLinesForBox(TaxReturn taxReturn, String boxCode) {
    return taxLineRepository.findByCompanyAndReportBoxAndDateRange(
        taxReturn.getCompany(), boxCode, taxReturn.getStartDate(), taxReturn.getEndDate());
  }
}
