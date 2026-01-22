package com.example.application.service;

import com.example.application.domain.*;
import com.example.application.domain.SupplierBill.BillStatus;
import com.example.application.repository.SupplierBillLineRepository;
import com.example.application.repository.SupplierBillRepository;
import com.example.application.repository.TaxCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing supplier bills (A/P).
 *
 * Key workflows:
 * 1. Create draft bill → Add lines → Post (post to ledger)
 * 2. Void bill (reversal) for posted bills
 *
 * Posting a bill creates ledger entries:
 * - Credit AP control account (Liability)
 * - Debit expense/asset accounts (per line)
 * - Debit GST paid/Input Tax account (if applicable)
 */
@Service
@Transactional
public class SupplierBillService {

    private final SupplierBillRepository billRepository;
    private final SupplierBillLineRepository lineRepository;
    private final TaxCodeRepository taxCodeRepository;
    private final AccountService accountService;
    private final TransactionService transactionService;
    private final PostingService postingService;
    private final AuditService auditService;

    // Default account codes (should be configurable per company in future)
    private static final String AP_ACCOUNT_CODE = "2100";  // Accounts Payable
    private static final String GST_PAID_CODE = "1150"; // GST Paid (Input Tax)

    public SupplierBillService(SupplierBillRepository billRepository,
                               SupplierBillLineRepository lineRepository,
                               TaxCodeRepository taxCodeRepository,
                               AccountService accountService,
                               TransactionService transactionService,
                               PostingService postingService,
                               AuditService auditService) {
        this.billRepository = billRepository;
        this.lineRepository = lineRepository;
        this.taxCodeRepository = taxCodeRepository;
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.postingService = postingService;
        this.auditService = auditService;
    }

    /**
     * Creates a new draft bill.
     */
    public SupplierBill createBill(Company company, Contact contact, LocalDate billDate,
                                    LocalDate dueDate, User createdBy) {
        String billNumber = generateBillNumber(company);
        SupplierBill bill = new SupplierBill(company, billNumber, contact, billDate, dueDate);
        bill.setCreatedBy(createdBy);
        bill = billRepository.save(bill);

        auditService.logEvent(company, createdBy, "BILL_CREATED", "SupplierBill", bill.getId(),
            "Created bill " + billNumber + " from " + contact.getName());

        return bill;
    }

    /**
     * Creates a new draft bill with a specific bill number.
     */
    public SupplierBill createBill(Company company, String billNumber, Contact contact,
                                    LocalDate billDate, LocalDate dueDate, User createdBy) {
        if (billRepository.existsByCompanyAndBillNumber(company, billNumber)) {
            throw new IllegalArgumentException("Bill number already exists: " + billNumber);
        }

        SupplierBill bill = new SupplierBill(company, billNumber, contact, billDate, dueDate);
        bill.setCreatedBy(createdBy);
        bill = billRepository.save(bill);

        auditService.logEvent(company, createdBy, "BILL_CREATED", "SupplierBill", bill.getId(),
            "Created bill " + billNumber + " from " + contact.getName());

        return bill;
    }

    /**
     * Generates the next bill number for a company.
     * Uses prefix "BILL-" with numeric incrementing: BILL-1, BILL-2, etc.
     */
    public String generateBillNumber(Company company) {
        List<String> existingNumbers = billRepository.findAllBillNumbersByCompany(company);
        int maxNumber = 0;
        for (String num : existingNumbers) {
            if (num != null && num.startsWith("BILL-")) {
                try {
                    int parsed = Integer.parseInt(num.substring(5));
                    if (parsed > maxNumber) {
                        maxNumber = parsed;
                    }
                } catch (NumberFormatException ignored) {
                    // Skip non-numeric suffixes
                }
            }
        }
        return "BILL-" + (maxNumber + 1);
    }

    /**
     * Adds a line to a draft bill.
     */
    public SupplierBillLine addLine(SupplierBill bill, Account account, String description,
                                     BigDecimal quantity, BigDecimal unitPrice, String taxCode) {
        if (!bill.isDraft()) {
            throw new IllegalStateException("Cannot add lines to a non-draft bill");
        }

        SupplierBillLine line = new SupplierBillLine(account, quantity, unitPrice);
        line.setDescription(description);
        line.setTaxCode(taxCode);

        // Look up tax rate if tax code provided
        if (taxCode != null && !taxCode.isBlank()) {
            Optional<TaxCode> taxCodeEntity = taxCodeRepository.findByCompanyAndCode(
                bill.getCompany(), taxCode);
            if (taxCodeEntity.isPresent()) {
                // Convert rate from decimal (0.15) to percentage (15) for storage
                BigDecimal rate = taxCodeEntity.get().getRate().multiply(BigDecimal.valueOf(100));
                line.setTaxRate(rate);
            }
        }

        line.calculateTotals();
        bill.addLine(line);
        bill.recalculateTotals();
        billRepository.save(bill);

        return line;
    }

    /**
     * Adds a line from a product to a draft bill.
     */
    public SupplierBillLine addLineFromProduct(SupplierBill bill, Product product,
                                                BigDecimal quantity) {
        if (!bill.isDraft()) {
            throw new IllegalStateException("Cannot add lines to a non-draft bill");
        }

        Account account = product.getPurchaseAccount();
        if (account == null) {
            throw new IllegalStateException("Product has no purchase account configured: " + product.getCode());
        }

        SupplierBillLine line = new SupplierBillLine(account, quantity, product.getBuyPrice());
        line.setProduct(product);
        line.setDescription(product.getName());
        line.setTaxCode(product.getTaxCode());

        // Look up tax rate if product has tax code
        if (product.getTaxCode() != null && !product.getTaxCode().isBlank()) {
            Optional<TaxCode> taxCodeEntity = taxCodeRepository.findByCompanyAndCode(
                bill.getCompany(), product.getTaxCode());
            if (taxCodeEntity.isPresent()) {
                BigDecimal rate = taxCodeEntity.get().getRate().multiply(BigDecimal.valueOf(100));
                line.setTaxRate(rate);
            }
        }

        line.calculateTotals();
        bill.addLine(line);
        bill.recalculateTotals();
        billRepository.save(bill);

        return line;
    }

    /**
     * Removes a line from a draft bill.
     */
    public void removeLine(SupplierBill bill, SupplierBillLine line) {
        if (!bill.isDraft()) {
            throw new IllegalStateException("Cannot remove lines from a non-draft bill");
        }

        bill.removeLine(line);
        bill.recalculateTotals();
        billRepository.save(bill);
    }

    /**
     * Updates an existing line on a draft bill.
     */
    public void updateLine(SupplierBillLine line, String description, BigDecimal quantity,
                           BigDecimal unitPrice, Account account, String taxCode) {
        SupplierBill bill = line.getBill();
        if (!bill.isDraft()) {
            throw new IllegalStateException("Cannot update lines on a non-draft bill");
        }

        line.setDescription(description);
        line.setQuantity(quantity);
        line.setUnitPrice(unitPrice);
        line.setAccount(account);
        line.setTaxCode(taxCode);

        // Update tax rate
        if (taxCode != null && !taxCode.isBlank()) {
            Optional<TaxCode> taxCodeEntity = taxCodeRepository.findByCompanyAndCode(
                bill.getCompany(), taxCode);
            if (taxCodeEntity.isPresent()) {
                BigDecimal rate = taxCodeEntity.get().getRate().multiply(BigDecimal.valueOf(100));
                line.setTaxRate(rate);
            } else {
                line.setTaxRate(null);
            }
        } else {
            line.setTaxRate(null);
        }

        line.calculateTotals();
        bill.recalculateTotals();
        billRepository.save(bill);
    }

    /**
     * Posts a draft bill, posting it to the ledger.
     * Creates a JOURNAL transaction with:
     * - Credit: AP control account for total amount
     * - Debit: Expense/asset accounts for each line's net amount
     * - Debit: GST paid for total tax
     */
    public SupplierBill postBill(SupplierBill bill, User actor) {
        if (!bill.isDraft()) {
            throw new IllegalStateException("Bill is not in draft status");
        }

        if (bill.getLines().isEmpty()) {
            throw new IllegalStateException("Bill has no lines");
        }

        Company company = bill.getCompany();

        // Find AP control account
        Account apAccount = accountService.findByCompanyAndCode(company, AP_ACCOUNT_CODE)
            .orElseThrow(() -> new IllegalStateException(
                "AP control account not found: " + AP_ACCOUNT_CODE));

        // Create the posting transaction
        String description = "Bill " + bill.getBillNumber() + " - " +
            bill.getContact().getName();
        Transaction transaction = transactionService.createTransaction(
            company,
            Transaction.TransactionType.JOURNAL,
            bill.getBillDate(),
            description,
            actor
        );
        transaction.setReference(bill.getBillNumber());

        // Credit AP for the total amount (including tax)
        TransactionLine apLine = new TransactionLine(
            apAccount,
            bill.getTotal(),
            TransactionLine.Direction.CREDIT
        );
        apLine.setMemo("AP for bill " + bill.getBillNumber());
        transaction.addLine(apLine);

        // Debit expense/asset accounts for each line (net amounts)
        for (SupplierBillLine billLine : bill.getLines()) {
            TransactionLine expenseLine = new TransactionLine(
                billLine.getAccount(),
                billLine.getLineTotal(),
                TransactionLine.Direction.DEBIT
            );
            expenseLine.setTaxCode(billLine.getTaxCode());
            expenseLine.setDepartment(billLine.getDepartment());
            expenseLine.setMemo(billLine.getDescription());
            transaction.addLine(expenseLine);
        }

        // Debit GST paid if there's any tax
        if (bill.getTaxTotal().compareTo(BigDecimal.ZERO) > 0) {
            Account gstAccount = accountService.findByCompanyAndCode(company, GST_PAID_CODE)
                .orElseThrow(() -> new IllegalStateException(
                    "GST paid account not found: " + GST_PAID_CODE));

            TransactionLine gstLine = new TransactionLine(
                gstAccount,
                bill.getTaxTotal(),
                TransactionLine.Direction.DEBIT
            );
            gstLine.setMemo("GST on bill " + bill.getBillNumber());
            transaction.addLine(gstLine);
        }

        transactionService.save(transaction);

        // Post the transaction to create ledger entries
        transaction = postingService.postTransaction(transaction, actor);

        // Update bill status
        bill.setStatus(BillStatus.POSTED);
        bill.setPostedAt(Instant.now());
        bill.setPostedTransaction(transaction);
        bill = billRepository.save(bill);

        auditService.logEvent(company, actor, "BILL_POSTED", "SupplierBill", bill.getId(),
            "Posted bill " + bill.getBillNumber() + " for $" + bill.getTotal());

        return bill;
    }

    /**
     * Voids a posted bill by creating a reversal transaction.
     * Sets the bill status to VOID.
     */
    public SupplierBill voidBill(SupplierBill bill, User actor, String reason) {
        if (!bill.isPosted()) {
            throw new IllegalStateException("Can only void posted bills");
        }

        if (bill.getAmountPaid().compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException("Cannot void bill with payments allocated");
        }

        // Create reversal of the posted transaction
        Transaction postedTransaction = bill.getPostedTransaction();
        if (postedTransaction != null) {
            postingService.reverseTransaction(postedTransaction, actor, reason);
        }

        // Update bill status
        bill.setStatus(BillStatus.VOID);
        bill = billRepository.save(bill);

        auditService.logEvent(bill.getCompany(), actor, "BILL_VOIDED", "SupplierBill",
            bill.getId(), "Voided bill " + bill.getBillNumber() +
            (reason != null ? ": " + reason : ""));

        return bill;
    }

    /**
     * Updates the amount paid on a bill.
     * Called by allocation service when payments are allocated.
     */
    public void updateAmountPaid(SupplierBill bill, BigDecimal totalPaid) {
        bill.setAmountPaid(totalPaid);
        billRepository.save(bill);
    }

    /**
     * Saves a bill.
     */
    public SupplierBill save(SupplierBill bill) {
        return billRepository.save(bill);
    }

    // Query methods

    @Transactional(readOnly = true)
    public Optional<SupplierBill> findById(Long id) {
        return billRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<SupplierBill> findByCompanyAndNumber(Company company, String billNumber) {
        return billRepository.findByCompanyAndBillNumber(company, billNumber);
    }

    @Transactional(readOnly = true)
    public List<SupplierBill> findByCompany(Company company) {
        return billRepository.findByCompanyOrderByBillDateDescBillNumberDesc(company);
    }

    @Transactional(readOnly = true)
    public List<SupplierBill> findByCompanyAndStatus(Company company, BillStatus status) {
        return billRepository.findByCompanyAndStatusOrderByBillDateDesc(company, status);
    }

    @Transactional(readOnly = true)
    public List<SupplierBill> findByCompanyAndContact(Company company, Contact contact) {
        return billRepository.findByCompanyAndContactOrderByBillDateDesc(company, contact);
    }

    @Transactional(readOnly = true)
    public List<SupplierBill> findOutstandingByCompany(Company company) {
        return billRepository.findOutstandingByCompany(company);
    }

    @Transactional(readOnly = true)
    public List<SupplierBill> findOutstandingByContact(Company company, Contact contact) {
        return billRepository.findOutstandingByCompanyAndContact(company, contact);
    }

    @Transactional(readOnly = true)
    public List<SupplierBill> findOverdueByCompany(Company company) {
        return billRepository.findOverdueByCompany(company, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<SupplierBill> searchByCompany(Company company, String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            return findByCompany(company);
        }
        return billRepository.searchByCompany(company, searchTerm.trim());
    }

    @Transactional(readOnly = true)
    public List<SupplierBill> findByDateRange(Company company, LocalDate startDate, LocalDate endDate) {
        return billRepository.findByCompanyAndDateRange(company, startDate, endDate);
    }

    @Transactional(readOnly = true)
    public List<SupplierBill> findPayableBillsDueBy(Company company, LocalDate dueBy) {
        return billRepository.findPayableBillsDueBy(company, dueBy);
    }

    // Dashboard/reporting methods

    @Transactional(readOnly = true)
    public BigDecimal getTotalOutstanding(Company company) {
        return billRepository.sumOutstandingByCompany(company);
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalOverdue(Company company) {
        return billRepository.sumOverdueByCompany(company, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public long countByStatus(Company company, BillStatus status) {
        return billRepository.countByCompanyAndStatus(company, status);
    }
}
