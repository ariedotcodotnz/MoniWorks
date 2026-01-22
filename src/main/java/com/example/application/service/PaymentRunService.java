package com.example.application.service;

import com.example.application.domain.*;
import com.example.application.domain.PaymentRun.PaymentRunStatus;
import com.example.application.repository.PaymentRunRepository;
import com.example.application.repository.SupplierBillRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing payment runs (batch supplier payments).
 *
 * Key workflows:
 * 1. Create payment run → Select bills to pay → Complete run (creates payment transactions)
 * 2. Group payments by supplier for efficient bank transfers
 *
 * Completing a payment run:
 * - Creates payment transactions for each supplier
 * - Allocates payments to bills
 * - Generates remittance advice (PDF output)
 */
@Service
@Transactional
public class PaymentRunService {

    private final PaymentRunRepository paymentRunRepository;
    private final SupplierBillRepository billRepository;
    private final SupplierBillService billService;
    private final AccountService accountService;
    private final TransactionService transactionService;
    private final PostingService postingService;
    private final PayableAllocationService allocationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    // Default AP account code
    private static final String AP_ACCOUNT_CODE = "2100";

    public PaymentRunService(PaymentRunRepository paymentRunRepository,
                             SupplierBillRepository billRepository,
                             SupplierBillService billService,
                             AccountService accountService,
                             TransactionService transactionService,
                             PostingService postingService,
                             PayableAllocationService allocationService,
                             AuditService auditService) {
        this.paymentRunRepository = paymentRunRepository;
        this.billRepository = billRepository;
        this.billService = billService;
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.postingService = postingService;
        this.allocationService = allocationService;
        this.auditService = auditService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Creates a new payment run.
     */
    public PaymentRun createPaymentRun(Company company, LocalDate runDate,
                                        Account bankAccount, User createdBy) {
        if (!bankAccount.isBankAccount()) {
            throw new IllegalArgumentException("Account must be a bank account");
        }

        PaymentRun run = new PaymentRun(company, runDate, bankAccount);
        run.setCreatedBy(createdBy);
        run = paymentRunRepository.save(run);

        auditService.logEvent(company, createdBy, "PAYMENT_RUN_CREATED", "PaymentRun",
            run.getId(), "Created payment run for " + runDate);

        return run;
    }

    /**
     * Adds bills to a payment run.
     * Bills are grouped by supplier for efficient processing.
     */
    public void addBillsToRun(PaymentRun run, List<SupplierBill> bills) {
        if (!run.isDraft()) {
            throw new IllegalStateException("Cannot modify a completed payment run");
        }

        List<PaymentItem> items = getPaymentItems(run);

        for (SupplierBill bill : bills) {
            if (!bill.isPosted()) {
                throw new IllegalArgumentException("Bill " + bill.getBillNumber() + " is not posted");
            }
            if (bill.getBalance().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Bill " + bill.getBillNumber() + " has no outstanding balance");
            }

            PaymentItem item = new PaymentItem();
            item.billId = bill.getId();
            item.contactId = bill.getContact().getId();
            item.amount = bill.getBalance();
            items.add(item);
        }

        savePaymentItems(run, items);
        paymentRunRepository.save(run);
    }

    /**
     * Removes a bill from a payment run.
     */
    public void removeBillFromRun(PaymentRun run, SupplierBill bill) {
        if (!run.isDraft()) {
            throw new IllegalStateException("Cannot modify a completed payment run");
        }

        List<PaymentItem> items = getPaymentItems(run);
        items.removeIf(item -> item.billId.equals(bill.getId()));
        savePaymentItems(run, items);
        paymentRunRepository.save(run);
    }

    /**
     * Completes a payment run.
     * Creates payment transactions grouped by supplier and allocates to bills.
     */
    public PaymentRun completePaymentRun(PaymentRun run, User actor) {
        if (!run.isDraft()) {
            throw new IllegalStateException("Payment run is not in draft status");
        }

        List<PaymentItem> items = getPaymentItems(run);
        if (items.isEmpty()) {
            throw new IllegalStateException("Payment run has no bills selected");
        }

        Company company = run.getCompany();
        Account bankAccount = run.getBankAccount();

        // Group items by supplier
        Map<Long, List<PaymentItem>> bySupplier = items.stream()
            .collect(Collectors.groupingBy(item -> item.contactId));

        // Process each supplier's payments
        for (Map.Entry<Long, List<PaymentItem>> entry : bySupplier.entrySet()) {
            List<PaymentItem> supplierItems = entry.getValue();
            BigDecimal totalAmount = supplierItems.stream()
                .map(item -> item.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Get first bill to get contact
            SupplierBill firstBill = billRepository.findById(supplierItems.get(0).billId)
                .orElseThrow(() -> new IllegalStateException("Bill not found"));
            Contact supplier = firstBill.getContact();

            // Create payment transaction
            String description = "Payment to " + supplier.getName() +
                " (" + supplierItems.size() + " bills)";
            Transaction payment = transactionService.createTransaction(
                company,
                Transaction.TransactionType.PAYMENT,
                run.getRunDate(),
                description,
                actor
            );

            // Credit bank account
            TransactionLine bankLine = new TransactionLine(
                bankAccount,
                totalAmount,
                TransactionLine.Direction.CREDIT
            );
            bankLine.setMemo("Payment run " + run.getId() + " - " + supplier.getName());
            payment.addLine(bankLine);

            // Debit AP account
            Account apAccount = accountService.findByCompanyAndCode(company, AP_ACCOUNT_CODE)
                .orElseThrow(() -> new IllegalStateException("AP account not found: " + AP_ACCOUNT_CODE));

            TransactionLine apLine = new TransactionLine(
                apAccount,
                totalAmount,
                TransactionLine.Direction.DEBIT
            );
            apLine.setMemo("Payment to " + supplier.getName());
            payment.addLine(apLine);

            transactionService.save(payment);
            payment = postingService.postTransaction(payment, actor);

            // Allocate payment to bills
            for (PaymentItem item : supplierItems) {
                SupplierBill bill = billRepository.findById(item.billId)
                    .orElseThrow(() -> new IllegalStateException("Bill not found: " + item.billId));
                allocationService.allocate(payment, bill, item.amount, actor);
                item.transactionId = payment.getId();
            }
        }

        // Update payment run status
        run.setStatus(PaymentRunStatus.COMPLETED);
        run.setCompletedAt(Instant.now());
        savePaymentItems(run, items);
        run = paymentRunRepository.save(run);

        auditService.logEvent(company, actor, "PAYMENT_RUN_COMPLETED", "PaymentRun",
            run.getId(), "Completed payment run with " + items.size() + " bills");

        return run;
    }

    /**
     * Gets the total amount of a payment run.
     */
    @Transactional(readOnly = true)
    public BigDecimal getRunTotal(PaymentRun run) {
        List<PaymentItem> items = getPaymentItems(run);
        return items.stream()
            .map(item -> item.amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Gets bills in a payment run with their amounts.
     */
    @Transactional(readOnly = true)
    public List<PaymentRunBill> getRunBills(PaymentRun run) {
        List<PaymentItem> items = getPaymentItems(run);
        List<PaymentRunBill> result = new ArrayList<>();

        for (PaymentItem item : items) {
            Optional<SupplierBill> bill = billRepository.findById(item.billId);
            if (bill.isPresent()) {
                result.add(new PaymentRunBill(bill.get(), item.amount));
            }
        }

        return result;
    }

    // Query methods

    @Transactional(readOnly = true)
    public Optional<PaymentRun> findById(Long id) {
        return paymentRunRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<PaymentRun> findByCompany(Company company) {
        return paymentRunRepository.findByCompanyOrderByCreatedAtDesc(company);
    }

    @Transactional(readOnly = true)
    public List<PaymentRun> findByCompanyAndStatus(Company company, PaymentRunStatus status) {
        return paymentRunRepository.findByCompanyAndStatusOrderByCreatedAtDesc(company, status);
    }

    // Helper methods for JSON serialization

    private List<PaymentItem> getPaymentItems(PaymentRun run) {
        if (run.getItemsJson() == null || run.getItemsJson().isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(run.getItemsJson(),
                new TypeReference<List<PaymentItem>>() {});
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }

    private void savePaymentItems(PaymentRun run, List<PaymentItem> items) {
        try {
            run.setItemsJson(objectMapper.writeValueAsString(items));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize payment items", e);
        }
    }

    /**
     * Internal class for payment item serialization.
     */
    public static class PaymentItem {
        public Long billId;
        public Long contactId;
        public BigDecimal amount;
        public Long transactionId;
    }

    /**
     * DTO for returning bill details in a payment run.
     */
    public record PaymentRunBill(SupplierBill bill, BigDecimal amount) {}
}
