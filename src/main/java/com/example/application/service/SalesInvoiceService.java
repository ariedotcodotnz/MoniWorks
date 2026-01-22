package com.example.application.service;

import com.example.application.domain.*;
import com.example.application.domain.SalesInvoice.InvoiceStatus;
import com.example.application.repository.SalesInvoiceLineRepository;
import com.example.application.repository.SalesInvoiceRepository;
import com.example.application.repository.TaxCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing sales invoices (A/R).
 *
 * Key workflows:
 * 1. Create draft invoice → Add lines → Issue (post to ledger)
 * 2. Void invoice (reversal) for issued invoices
 *
 * Issuing an invoice creates ledger entries:
 * - Debit AR control account (Asset)
 * - Credit income accounts (per line)
 * - Credit GST collected account (if applicable)
 */
@Service
@Transactional
public class SalesInvoiceService {

    private final SalesInvoiceRepository invoiceRepository;
    private final SalesInvoiceLineRepository lineRepository;
    private final TaxCodeRepository taxCodeRepository;
    private final AccountService accountService;
    private final TransactionService transactionService;
    private final PostingService postingService;
    private final AuditService auditService;

    // Default account codes (should be configurable per company in future)
    private static final String AR_ACCOUNT_CODE = "1200";  // Accounts Receivable
    private static final String GST_COLLECTED_CODE = "2200"; // GST Collected liability

    public SalesInvoiceService(SalesInvoiceRepository invoiceRepository,
                               SalesInvoiceLineRepository lineRepository,
                               TaxCodeRepository taxCodeRepository,
                               AccountService accountService,
                               TransactionService transactionService,
                               PostingService postingService,
                               AuditService auditService) {
        this.invoiceRepository = invoiceRepository;
        this.lineRepository = lineRepository;
        this.taxCodeRepository = taxCodeRepository;
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.postingService = postingService;
        this.auditService = auditService;
    }

    /**
     * Creates a new draft invoice.
     */
    public SalesInvoice createInvoice(Company company, Contact contact, LocalDate issueDate,
                                       LocalDate dueDate, User createdBy) {
        String invoiceNumber = generateInvoiceNumber(company);
        SalesInvoice invoice = new SalesInvoice(company, invoiceNumber, contact, issueDate, dueDate);
        invoice.setCreatedBy(createdBy);
        invoice = invoiceRepository.save(invoice);

        auditService.logEvent(company, createdBy, "INVOICE_CREATED", "SalesInvoice", invoice.getId(),
            "Created invoice " + invoiceNumber + " for " + contact.getName());

        return invoice;
    }

    /**
     * Creates a new draft invoice with a specific invoice number.
     */
    public SalesInvoice createInvoice(Company company, String invoiceNumber, Contact contact,
                                       LocalDate issueDate, LocalDate dueDate, User createdBy) {
        if (invoiceRepository.existsByCompanyAndInvoiceNumber(company, invoiceNumber)) {
            throw new IllegalArgumentException("Invoice number already exists: " + invoiceNumber);
        }

        SalesInvoice invoice = new SalesInvoice(company, invoiceNumber, contact, issueDate, dueDate);
        invoice.setCreatedBy(createdBy);
        invoice = invoiceRepository.save(invoice);

        auditService.logEvent(company, createdBy, "INVOICE_CREATED", "SalesInvoice", invoice.getId(),
            "Created invoice " + invoiceNumber + " for " + contact.getName());

        return invoice;
    }

    /**
     * Generates the next invoice number for a company.
     * Uses numeric incrementing: 1, 2, 3, etc.
     */
    public String generateInvoiceNumber(Company company) {
        List<String> existingNumbers = invoiceRepository.findAllInvoiceNumbersByCompany(company);
        int maxNumber = 0;
        for (String num : existingNumbers) {
            try {
                int parsed = Integer.parseInt(num);
                if (parsed > maxNumber) {
                    maxNumber = parsed;
                }
            } catch (NumberFormatException ignored) {
                // Skip non-numeric invoice numbers
            }
        }
        return String.valueOf(maxNumber + 1);
    }

    /**
     * Adds a line to a draft invoice.
     */
    public SalesInvoiceLine addLine(SalesInvoice invoice, Account account, String description,
                                     BigDecimal quantity, BigDecimal unitPrice, String taxCode) {
        if (!invoice.isDraft()) {
            throw new IllegalStateException("Cannot add lines to a non-draft invoice");
        }

        SalesInvoiceLine line = new SalesInvoiceLine(account, quantity, unitPrice);
        line.setDescription(description);
        line.setTaxCode(taxCode);

        // Look up tax rate if tax code provided
        if (taxCode != null && !taxCode.isBlank()) {
            Optional<TaxCode> taxCodeEntity = taxCodeRepository.findByCompanyAndCode(
                invoice.getCompany(), taxCode);
            if (taxCodeEntity.isPresent()) {
                // Convert rate from decimal (0.15) to percentage (15) for storage
                BigDecimal rate = taxCodeEntity.get().getRate().multiply(BigDecimal.valueOf(100));
                line.setTaxRate(rate);
            }
        }

        line.calculateTotals();
        invoice.addLine(line);
        invoice.recalculateTotals();
        invoiceRepository.save(invoice);

        return line;
    }

    /**
     * Adds a line from a product to a draft invoice.
     */
    public SalesInvoiceLine addLineFromProduct(SalesInvoice invoice, Product product,
                                                BigDecimal quantity) {
        if (!invoice.isDraft()) {
            throw new IllegalStateException("Cannot add lines to a non-draft invoice");
        }

        Account account = product.getSalesAccount();
        if (account == null) {
            throw new IllegalStateException("Product has no sales account configured: " + product.getCode());
        }

        SalesInvoiceLine line = new SalesInvoiceLine(account, quantity, product.getSellPrice());
        line.setProduct(product);
        line.setDescription(product.getName());
        line.setTaxCode(product.getTaxCode());

        // Look up tax rate if product has tax code
        if (product.getTaxCode() != null && !product.getTaxCode().isBlank()) {
            Optional<TaxCode> taxCodeEntity = taxCodeRepository.findByCompanyAndCode(
                invoice.getCompany(), product.getTaxCode());
            if (taxCodeEntity.isPresent()) {
                BigDecimal rate = taxCodeEntity.get().getRate().multiply(BigDecimal.valueOf(100));
                line.setTaxRate(rate);
            }
        }

        line.calculateTotals();
        invoice.addLine(line);
        invoice.recalculateTotals();
        invoiceRepository.save(invoice);

        return line;
    }

    /**
     * Removes a line from a draft invoice.
     */
    public void removeLine(SalesInvoice invoice, SalesInvoiceLine line) {
        if (!invoice.isDraft()) {
            throw new IllegalStateException("Cannot remove lines from a non-draft invoice");
        }

        invoice.removeLine(line);
        invoice.recalculateTotals();
        invoiceRepository.save(invoice);
    }

    /**
     * Updates an existing line on a draft invoice.
     */
    public void updateLine(SalesInvoiceLine line, String description, BigDecimal quantity,
                           BigDecimal unitPrice, Account account, String taxCode) {
        SalesInvoice invoice = line.getInvoice();
        if (!invoice.isDraft()) {
            throw new IllegalStateException("Cannot update lines on a non-draft invoice");
        }

        line.setDescription(description);
        line.setQuantity(quantity);
        line.setUnitPrice(unitPrice);
        line.setAccount(account);
        line.setTaxCode(taxCode);

        // Update tax rate
        if (taxCode != null && !taxCode.isBlank()) {
            Optional<TaxCode> taxCodeEntity = taxCodeRepository.findByCompanyAndCode(
                invoice.getCompany(), taxCode);
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
        invoice.recalculateTotals();
        invoiceRepository.save(invoice);
    }

    /**
     * Issues a draft invoice, posting it to the ledger.
     * Creates a JOURNAL transaction with:
     * - Debit: AR control account for total amount
     * - Credit: Income accounts for each line's net amount
     * - Credit: GST collected for total tax
     */
    public SalesInvoice issueInvoice(SalesInvoice invoice, User actor) {
        if (!invoice.isDraft()) {
            throw new IllegalStateException("Invoice is not in draft status");
        }

        if (invoice.getLines().isEmpty()) {
            throw new IllegalStateException("Invoice has no lines");
        }

        Company company = invoice.getCompany();

        // Find AR control account
        Account arAccount = accountService.findByCompanyAndCode(company, AR_ACCOUNT_CODE)
            .orElseThrow(() -> new IllegalStateException(
                "AR control account not found: " + AR_ACCOUNT_CODE));

        // Create the posting transaction
        String description = "Invoice " + invoice.getInvoiceNumber() + " - " +
            invoice.getContact().getName();
        Transaction transaction = transactionService.createTransaction(
            company,
            Transaction.TransactionType.JOURNAL,
            invoice.getIssueDate(),
            description,
            actor
        );
        transaction.setReference(invoice.getInvoiceNumber());

        // Debit AR for the total amount (including tax)
        TransactionLine arLine = new TransactionLine(
            arAccount,
            invoice.getTotal(),
            TransactionLine.Direction.DEBIT
        );
        arLine.setMemo("AR for invoice " + invoice.getInvoiceNumber());
        transaction.addLine(arLine);

        // Credit income accounts for each line (net amounts)
        for (SalesInvoiceLine invoiceLine : invoice.getLines()) {
            TransactionLine incomeLine = new TransactionLine(
                invoiceLine.getAccount(),
                invoiceLine.getLineTotal(),
                TransactionLine.Direction.CREDIT
            );
            incomeLine.setTaxCode(invoiceLine.getTaxCode());
            incomeLine.setDepartment(invoiceLine.getDepartment());
            incomeLine.setMemo(invoiceLine.getDescription());
            transaction.addLine(incomeLine);
        }

        // Credit GST collected if there's any tax
        if (invoice.getTaxTotal().compareTo(BigDecimal.ZERO) > 0) {
            Account gstAccount = accountService.findByCompanyAndCode(company, GST_COLLECTED_CODE)
                .orElseThrow(() -> new IllegalStateException(
                    "GST collected account not found: " + GST_COLLECTED_CODE));

            TransactionLine gstLine = new TransactionLine(
                gstAccount,
                invoice.getTaxTotal(),
                TransactionLine.Direction.CREDIT
            );
            gstLine.setMemo("GST on invoice " + invoice.getInvoiceNumber());
            transaction.addLine(gstLine);
        }

        transactionService.save(transaction);

        // Post the transaction to create ledger entries
        transaction = postingService.postTransaction(transaction, actor);

        // Update invoice status
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setIssuedAt(Instant.now());
        invoice.setPostedTransaction(transaction);
        invoice = invoiceRepository.save(invoice);

        auditService.logEvent(company, actor, "INVOICE_ISSUED", "SalesInvoice", invoice.getId(),
            "Issued invoice " + invoice.getInvoiceNumber() + " for $" + invoice.getTotal());

        return invoice;
    }

    /**
     * Voids an issued invoice by creating a reversal transaction.
     * Sets the invoice status to VOID.
     */
    public SalesInvoice voidInvoice(SalesInvoice invoice, User actor, String reason) {
        if (!invoice.isIssued()) {
            throw new IllegalStateException("Can only void issued invoices");
        }

        if (invoice.getAmountPaid().compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException("Cannot void invoice with payments allocated");
        }

        // Create reversal of the posted transaction
        Transaction postedTransaction = invoice.getPostedTransaction();
        if (postedTransaction != null) {
            postingService.reverseTransaction(postedTransaction, actor, reason);
        }

        // Update invoice status
        invoice.setStatus(InvoiceStatus.VOID);
        invoice = invoiceRepository.save(invoice);

        auditService.logEvent(invoice.getCompany(), actor, "INVOICE_VOIDED", "SalesInvoice",
            invoice.getId(), "Voided invoice " + invoice.getInvoiceNumber() +
            (reason != null ? ": " + reason : ""));

        return invoice;
    }

    /**
     * Updates the amount paid on an invoice.
     * Called by allocation service when payments are allocated.
     */
    public void updateAmountPaid(SalesInvoice invoice, BigDecimal totalPaid) {
        invoice.setAmountPaid(totalPaid);
        invoiceRepository.save(invoice);
    }

    /**
     * Saves an invoice.
     */
    public SalesInvoice save(SalesInvoice invoice) {
        return invoiceRepository.save(invoice);
    }

    // Query methods

    @Transactional(readOnly = true)
    public Optional<SalesInvoice> findById(Long id) {
        return invoiceRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<SalesInvoice> findByCompanyAndNumber(Company company, String invoiceNumber) {
        return invoiceRepository.findByCompanyAndInvoiceNumber(company, invoiceNumber);
    }

    @Transactional(readOnly = true)
    public List<SalesInvoice> findByCompany(Company company) {
        return invoiceRepository.findByCompanyOrderByIssueDateDescInvoiceNumberDesc(company);
    }

    @Transactional(readOnly = true)
    public List<SalesInvoice> findByCompanyAndStatus(Company company, InvoiceStatus status) {
        return invoiceRepository.findByCompanyAndStatusOrderByIssueDateDesc(company, status);
    }

    @Transactional(readOnly = true)
    public List<SalesInvoice> findByCompanyAndContact(Company company, Contact contact) {
        return invoiceRepository.findByCompanyAndContactOrderByIssueDateDesc(company, contact);
    }

    @Transactional(readOnly = true)
    public List<SalesInvoice> findOutstandingByCompany(Company company) {
        return invoiceRepository.findOutstandingByCompany(company);
    }

    @Transactional(readOnly = true)
    public List<SalesInvoice> findOutstandingByContact(Company company, Contact contact) {
        return invoiceRepository.findOutstandingByCompanyAndContact(company, contact);
    }

    @Transactional(readOnly = true)
    public List<SalesInvoice> findOverdueByCompany(Company company) {
        return invoiceRepository.findOverdueByCompany(company, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<SalesInvoice> searchByCompany(Company company, String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            return findByCompany(company);
        }
        return invoiceRepository.searchByCompany(company, searchTerm.trim());
    }

    @Transactional(readOnly = true)
    public List<SalesInvoice> findByDateRange(Company company, LocalDate startDate, LocalDate endDate) {
        return invoiceRepository.findByCompanyAndDateRange(company, startDate, endDate);
    }

    // Dashboard/reporting methods

    @Transactional(readOnly = true)
    public BigDecimal getTotalOutstanding(Company company) {
        return invoiceRepository.sumOutstandingByCompany(company);
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalOverdue(Company company) {
        return invoiceRepository.sumOverdueByCompany(company, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public long countByStatus(Company company, InvoiceStatus status) {
        return invoiceRepository.countByCompanyAndStatus(company, status);
    }
}
