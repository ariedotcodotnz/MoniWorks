package com.example.application.service;

import com.example.application.domain.*;
import com.example.application.repository.ReceivableAllocationRepository;
import com.example.application.repository.SalesInvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Service for managing receivable allocations (matching receipts to invoices).
 *
 * Supports:
 * - Allocating a receipt to one or multiple invoices
 * - Partial payments (allocate less than invoice balance)
 * - Auto-allocation suggestions based on amount matching
 * - Tracking unallocated receipt amounts
 */
@Service
@Transactional
public class ReceivableAllocationService {

    private final ReceivableAllocationRepository allocationRepository;
    private final SalesInvoiceRepository invoiceRepository;
    private final AuditService auditService;

    public ReceivableAllocationService(ReceivableAllocationRepository allocationRepository,
                                        SalesInvoiceRepository invoiceRepository,
                                        AuditService auditService) {
        this.allocationRepository = allocationRepository;
        this.invoiceRepository = invoiceRepository;
        this.auditService = auditService;
    }

    /**
     * Allocates a receipt to an invoice.
     * Updates the invoice's amount paid.
     *
     * @param receipt The receipt transaction
     * @param invoice The invoice to allocate to
     * @param amount The amount to allocate
     * @param allocatedBy The user performing the allocation
     * @return The created allocation
     */
    public ReceivableAllocation allocate(Transaction receipt, SalesInvoice invoice,
                                          BigDecimal amount, User allocatedBy) {
        validateAllocation(receipt, invoice, amount);

        Company company = invoice.getCompany();

        ReceivableAllocation allocation = new ReceivableAllocation(
            company, receipt, invoice, amount);
        allocation.setAllocatedBy(allocatedBy);
        allocation = allocationRepository.save(allocation);

        // Update invoice amount paid
        BigDecimal totalAllocated = allocationRepository.sumByInvoice(invoice);
        invoice.setAmountPaid(totalAllocated);
        invoiceRepository.save(invoice);

        auditService.logEvent(company, allocatedBy, "ALLOCATION_CREATED", "ReceivableAllocation",
            allocation.getId(), "Allocated $" + amount + " from receipt " + receipt.getId() +
            " to invoice " + invoice.getInvoiceNumber());

        return allocation;
    }

    /**
     * Allocates a receipt to multiple invoices.
     *
     * @param receipt The receipt transaction
     * @param allocations List of invoice-amount pairs to allocate
     * @param allocatedBy The user performing the allocation
     * @return List of created allocations
     */
    public List<ReceivableAllocation> allocateToMultiple(Transaction receipt,
                                                          List<AllocationRequest> allocations,
                                                          User allocatedBy) {
        List<ReceivableAllocation> created = new ArrayList<>();

        for (AllocationRequest request : allocations) {
            ReceivableAllocation allocation = allocate(
                receipt, request.invoice(), request.amount(), allocatedBy);
            created.add(allocation);
        }

        return created;
    }

    /**
     * Removes an allocation.
     * Updates the invoice's amount paid.
     */
    public void removeAllocation(ReceivableAllocation allocation, User actor) {
        SalesInvoice invoice = allocation.getSalesInvoice();
        Company company = invoice.getCompany();

        allocationRepository.delete(allocation);

        // Recalculate invoice amount paid
        BigDecimal totalAllocated = allocationRepository.sumByInvoice(invoice);
        invoice.setAmountPaid(totalAllocated);
        invoiceRepository.save(invoice);

        auditService.logEvent(company, actor, "ALLOCATION_REMOVED", "ReceivableAllocation",
            allocation.getId(), "Removed allocation from invoice " + invoice.getInvoiceNumber());
    }

    /**
     * Suggests allocations for a receipt based on outstanding invoices.
     * Uses a simple matching strategy:
     * 1. Exact amount match to a single invoice
     * 2. Oldest invoices first until receipt amount is exhausted
     *
     * @param company The company
     * @param contact The customer (optional, filters invoices)
     * @param receiptAmount The receipt amount to allocate
     * @return Suggested allocations
     */
    @Transactional(readOnly = true)
    public List<AllocationSuggestion> suggestAllocations(Company company, Contact contact,
                                                          BigDecimal receiptAmount) {
        List<SalesInvoice> outstanding;
        if (contact != null) {
            outstanding = invoiceRepository.findOutstandingByCompanyAndContact(company, contact);
        } else {
            outstanding = invoiceRepository.findOutstandingByCompany(company);
        }

        List<AllocationSuggestion> suggestions = new ArrayList<>();
        BigDecimal remaining = receiptAmount;

        // First, look for exact match
        for (SalesInvoice invoice : outstanding) {
            BigDecimal balance = invoice.getBalance();
            if (balance.compareTo(receiptAmount) == 0) {
                suggestions.add(new AllocationSuggestion(invoice, balance, true));
                return suggestions;
            }
        }

        // Otherwise, allocate oldest first
        outstanding.sort(Comparator.comparing(SalesInvoice::getDueDate));

        for (SalesInvoice invoice : outstanding) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal balance = invoice.getBalance();
            BigDecimal toAllocate = remaining.min(balance);
            suggestions.add(new AllocationSuggestion(invoice, toAllocate, false));
            remaining = remaining.subtract(toAllocate);
        }

        return suggestions;
    }

    /**
     * Gets the unallocated amount for a receipt transaction.
     */
    @Transactional(readOnly = true)
    public BigDecimal getUnallocatedAmount(Transaction receipt) {
        // Get total receipt amount (sum of credits to bank/cash account)
        BigDecimal receiptTotal = BigDecimal.ZERO;
        for (TransactionLine line : receipt.getLines()) {
            if (line.isCredit() && line.getAccount().isBankAccount()) {
                receiptTotal = receiptTotal.add(line.getAmount());
            }
        }
        // Alternative: use the first debit line amount as the receipt total
        if (receiptTotal.compareTo(BigDecimal.ZERO) == 0) {
            for (TransactionLine line : receipt.getLines()) {
                if (line.isDebit()) {
                    receiptTotal = receiptTotal.add(line.getAmount());
                    break;
                }
            }
        }

        BigDecimal allocated = allocationRepository.sumByReceipt(receipt);
        return receiptTotal.subtract(allocated);
    }

    /**
     * Gets all allocations for an invoice.
     */
    @Transactional(readOnly = true)
    public List<ReceivableAllocation> findByInvoice(SalesInvoice invoice) {
        return allocationRepository.findBySalesInvoice(invoice);
    }

    /**
     * Gets all allocations for a receipt.
     */
    @Transactional(readOnly = true)
    public List<ReceivableAllocation> findByReceipt(Transaction receipt) {
        return allocationRepository.findByReceiptTransaction(receipt);
    }

    /**
     * Gets all allocations for a company.
     */
    @Transactional(readOnly = true)
    public List<ReceivableAllocation> findByCompany(Company company) {
        return allocationRepository.findByCompanyOrderByAllocatedAtDesc(company);
    }

    /**
     * Validates an allocation request.
     */
    private void validateAllocation(Transaction receipt, SalesInvoice invoice, BigDecimal amount) {
        if (receipt == null) {
            throw new IllegalArgumentException("Receipt cannot be null");
        }

        if (invoice == null) {
            throw new IllegalArgumentException("Invoice cannot be null");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        if (!receipt.isPosted()) {
            throw new IllegalStateException("Receipt must be posted");
        }

        if (receipt.getType() != Transaction.TransactionType.RECEIPT) {
            throw new IllegalStateException("Transaction must be a receipt");
        }

        if (!invoice.isIssued()) {
            throw new IllegalStateException("Invoice must be issued");
        }

        // Check company match
        if (!receipt.getCompany().getId().equals(invoice.getCompany().getId())) {
            throw new IllegalStateException("Receipt and invoice must be for the same company");
        }

        // Check allocation doesn't exceed invoice balance
        BigDecimal balance = invoice.getBalance();
        if (amount.compareTo(balance) > 0) {
            throw new IllegalArgumentException(
                "Allocation amount ($" + amount + ") exceeds invoice balance ($" + balance + ")");
        }

        // Check receipt has enough unallocated funds
        BigDecimal unallocated = getUnallocatedAmount(receipt);
        if (amount.compareTo(unallocated) > 0) {
            throw new IllegalArgumentException(
                "Allocation amount ($" + amount + ") exceeds unallocated receipt amount ($" + unallocated + ")");
        }
    }

    /**
     * Request object for batch allocation.
     */
    public record AllocationRequest(SalesInvoice invoice, BigDecimal amount) {}

    /**
     * Suggestion object for auto-allocation.
     */
    public record AllocationSuggestion(SalesInvoice invoice, BigDecimal suggestedAmount,
                                       boolean exactMatch) {}
}
