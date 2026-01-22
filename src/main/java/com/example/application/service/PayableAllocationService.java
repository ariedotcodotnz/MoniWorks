package com.example.application.service;

import com.example.application.domain.*;
import com.example.application.repository.PayableAllocationRepository;
import com.example.application.repository.SupplierBillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Service for managing payable allocations (matching payments to supplier bills).
 *
 * Supports:
 * - Allocating a payment to one or multiple bills
 * - Partial payments (allocate less than bill balance)
 * - Auto-allocation suggestions based on amount matching
 * - Tracking unallocated payment amounts
 */
@Service
@Transactional
public class PayableAllocationService {

    private final PayableAllocationRepository allocationRepository;
    private final SupplierBillRepository billRepository;
    private final AuditService auditService;

    public PayableAllocationService(PayableAllocationRepository allocationRepository,
                                     SupplierBillRepository billRepository,
                                     AuditService auditService) {
        this.allocationRepository = allocationRepository;
        this.billRepository = billRepository;
        this.auditService = auditService;
    }

    /**
     * Allocates a payment to a bill.
     * Updates the bill's amount paid.
     *
     * @param payment The payment transaction
     * @param bill The bill to allocate to
     * @param amount The amount to allocate
     * @param allocatedBy The user performing the allocation
     * @return The created allocation
     */
    public PayableAllocation allocate(Transaction payment, SupplierBill bill,
                                       BigDecimal amount, User allocatedBy) {
        validateAllocation(payment, bill, amount);

        Company company = bill.getCompany();

        PayableAllocation allocation = new PayableAllocation(
            company, payment, bill, amount);
        allocation.setAllocatedBy(allocatedBy);
        allocation = allocationRepository.save(allocation);

        // Update bill amount paid
        BigDecimal totalAllocated = allocationRepository.sumByBill(bill);
        bill.setAmountPaid(totalAllocated);
        billRepository.save(bill);

        auditService.logEvent(company, allocatedBy, "ALLOCATION_CREATED", "PayableAllocation",
            allocation.getId(), "Allocated $" + amount + " from payment " + payment.getId() +
            " to bill " + bill.getBillNumber());

        return allocation;
    }

    /**
     * Allocates a payment to multiple bills.
     *
     * @param payment The payment transaction
     * @param allocations List of bill-amount pairs to allocate
     * @param allocatedBy The user performing the allocation
     * @return List of created allocations
     */
    public List<PayableAllocation> allocateToMultiple(Transaction payment,
                                                       List<AllocationRequest> allocations,
                                                       User allocatedBy) {
        List<PayableAllocation> created = new ArrayList<>();

        for (AllocationRequest request : allocations) {
            PayableAllocation allocation = allocate(
                payment, request.bill(), request.amount(), allocatedBy);
            created.add(allocation);
        }

        return created;
    }

    /**
     * Removes an allocation.
     * Updates the bill's amount paid.
     */
    public void removeAllocation(PayableAllocation allocation, User actor) {
        SupplierBill bill = allocation.getSupplierBill();
        Company company = bill.getCompany();

        allocationRepository.delete(allocation);

        // Recalculate bill amount paid
        BigDecimal totalAllocated = allocationRepository.sumByBill(bill);
        bill.setAmountPaid(totalAllocated);
        billRepository.save(bill);

        auditService.logEvent(company, actor, "ALLOCATION_REMOVED", "PayableAllocation",
            allocation.getId(), "Removed allocation from bill " + bill.getBillNumber());
    }

    /**
     * Suggests allocations for a payment based on outstanding bills.
     * Uses a simple matching strategy:
     * 1. Exact amount match to a single bill
     * 2. Oldest bills first until payment amount is exhausted
     *
     * @param company The company
     * @param contact The supplier (optional, filters bills)
     * @param paymentAmount The payment amount to allocate
     * @return Suggested allocations
     */
    @Transactional(readOnly = true)
    public List<AllocationSuggestion> suggestAllocations(Company company, Contact contact,
                                                          BigDecimal paymentAmount) {
        List<SupplierBill> outstanding;
        if (contact != null) {
            outstanding = billRepository.findOutstandingByCompanyAndContact(company, contact);
        } else {
            outstanding = billRepository.findOutstandingByCompany(company);
        }

        List<AllocationSuggestion> suggestions = new ArrayList<>();
        BigDecimal remaining = paymentAmount;

        // First, look for exact match
        for (SupplierBill bill : outstanding) {
            BigDecimal balance = bill.getBalance();
            if (balance.compareTo(paymentAmount) == 0) {
                suggestions.add(new AllocationSuggestion(bill, balance, true));
                return suggestions;
            }
        }

        // Otherwise, allocate oldest first
        outstanding.sort(Comparator.comparing(SupplierBill::getDueDate));

        for (SupplierBill bill : outstanding) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal balance = bill.getBalance();
            BigDecimal toAllocate = remaining.min(balance);
            suggestions.add(new AllocationSuggestion(bill, toAllocate, false));
            remaining = remaining.subtract(toAllocate);
        }

        return suggestions;
    }

    /**
     * Gets the unallocated amount for a payment transaction.
     */
    @Transactional(readOnly = true)
    public BigDecimal getUnallocatedAmount(Transaction payment) {
        // Get total payment amount (sum of debits to bank/cash account)
        BigDecimal paymentTotal = BigDecimal.ZERO;
        for (TransactionLine line : payment.getLines()) {
            if (line.isDebit() && line.getAccount().isBankAccount()) {
                paymentTotal = paymentTotal.add(line.getAmount());
            }
        }
        // Alternative: use the first credit line amount as the payment total
        if (paymentTotal.compareTo(BigDecimal.ZERO) == 0) {
            for (TransactionLine line : payment.getLines()) {
                if (line.isCredit()) {
                    paymentTotal = paymentTotal.add(line.getAmount());
                    break;
                }
            }
        }

        BigDecimal allocated = allocationRepository.sumByPayment(payment);
        return paymentTotal.subtract(allocated);
    }

    /**
     * Gets all allocations for a bill.
     */
    @Transactional(readOnly = true)
    public List<PayableAllocation> findByBill(SupplierBill bill) {
        return allocationRepository.findBySupplierBill(bill);
    }

    /**
     * Gets all allocations for a payment.
     */
    @Transactional(readOnly = true)
    public List<PayableAllocation> findByPayment(Transaction payment) {
        return allocationRepository.findByPaymentTransaction(payment);
    }

    /**
     * Gets all allocations for a company.
     */
    @Transactional(readOnly = true)
    public List<PayableAllocation> findByCompany(Company company) {
        return allocationRepository.findByCompanyOrderByAllocatedAtDesc(company);
    }

    /**
     * Validates an allocation request.
     */
    private void validateAllocation(Transaction payment, SupplierBill bill, BigDecimal amount) {
        if (payment == null) {
            throw new IllegalArgumentException("Payment cannot be null");
        }

        if (bill == null) {
            throw new IllegalArgumentException("Bill cannot be null");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        if (!payment.isPosted()) {
            throw new IllegalStateException("Payment must be posted");
        }

        if (payment.getType() != Transaction.TransactionType.PAYMENT) {
            throw new IllegalStateException("Transaction must be a payment");
        }

        if (!bill.isPosted()) {
            throw new IllegalStateException("Bill must be posted");
        }

        // Check company match
        if (!payment.getCompany().getId().equals(bill.getCompany().getId())) {
            throw new IllegalStateException("Payment and bill must be for the same company");
        }

        // Check allocation doesn't exceed bill balance
        BigDecimal balance = bill.getBalance();
        if (amount.compareTo(balance) > 0) {
            throw new IllegalArgumentException(
                "Allocation amount ($" + amount + ") exceeds bill balance ($" + balance + ")");
        }

        // Check payment has enough unallocated funds
        BigDecimal unallocated = getUnallocatedAmount(payment);
        if (amount.compareTo(unallocated) > 0) {
            throw new IllegalArgumentException(
                "Allocation amount ($" + amount + ") exceeds unallocated payment amount ($" + unallocated + ")");
        }
    }

    /**
     * Request object for batch allocation.
     */
    public record AllocationRequest(SupplierBill bill, BigDecimal amount) {}

    /**
     * Suggestion object for auto-allocation.
     */
    public record AllocationSuggestion(SupplierBill bill, BigDecimal suggestedAmount,
                                       boolean exactMatch) {}
}
