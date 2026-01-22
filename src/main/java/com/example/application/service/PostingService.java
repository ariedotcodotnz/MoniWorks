package com.example.application.service;

import com.example.application.domain.*;
import com.example.application.repository.LedgerEntryRepository;
import com.example.application.repository.PeriodRepository;
import com.example.application.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for posting transactions to the general ledger.
 * Ensures all accounting rules are enforced:
 * - Debits must equal credits
 * - Transaction date must be in an open period
 * - Posted transactions are immutable
 * - Tax calculations are recorded via TaxLine entities
 */
@Service
@Transactional
public class PostingService {

    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final PeriodRepository periodRepository;
    private final AuditService auditService;
    private final TaxCalculationService taxCalculationService;

    public PostingService(TransactionRepository transactionRepository,
                          LedgerEntryRepository ledgerEntryRepository,
                          PeriodRepository periodRepository,
                          AuditService auditService,
                          TaxCalculationService taxCalculationService) {
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.periodRepository = periodRepository;
        this.auditService = auditService;
        this.taxCalculationService = taxCalculationService;
    }

    /**
     * Posts a transaction, creating immutable ledger entries.
     *
     * @param transaction The transaction to post
     * @param actor The user performing the posting
     * @return The posted transaction
     * @throws IllegalStateException if validation fails
     */
    public Transaction postTransaction(Transaction transaction, User actor) {
        validateForPosting(transaction);

        // Create ledger entries for each line
        List<LedgerEntry> entries = new ArrayList<>();
        for (TransactionLine line : transaction.getLines()) {
            LedgerEntry entry = new LedgerEntry(transaction.getCompany(), transaction, line);
            entries.add(entry);
        }

        // Save all ledger entries
        ledgerEntryRepository.saveAll(entries);

        // Create tax lines for entries with tax codes
        taxCalculationService.createTaxLinesForLedgerEntries(transaction.getCompany(), entries);

        // Update transaction status
        transaction.setStatus(Transaction.Status.POSTED);
        transaction.setPostedAt(Instant.now());
        transaction = transactionRepository.save(transaction);

        // Record audit event
        auditService.logEvent(
            transaction.getCompany(),
            actor,
            "TRANSACTION_POSTED",
            "Transaction",
            transaction.getId(),
            "Posted transaction: " + transaction.getDescription()
        );

        return transaction;
    }

    /**
     * Validates a transaction before posting.
     */
    public void validateForPosting(Transaction transaction) {
        // Check transaction is in draft status
        if (transaction.isPosted()) {
            throw new IllegalStateException("Transaction is already posted");
        }

        // Check for duplicate posting (idempotency)
        if (ledgerEntryRepository.existsByTransaction(transaction)) {
            throw new IllegalStateException("Ledger entries already exist for this transaction");
        }

        // Check transaction has lines
        if (transaction.getLines().isEmpty()) {
            throw new IllegalStateException("Transaction has no lines");
        }

        // Check all accounts are active
        for (TransactionLine line : transaction.getLines()) {
            if (!line.getAccount().isActive()) {
                throw new IllegalStateException("Account is inactive: " +
                    line.getAccount().getCode());
            }
        }

        // Check debits equal credits
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;

        for (TransactionLine line : transaction.getLines()) {
            if (line.isDebit()) {
                totalDebits = totalDebits.add(line.getAmount());
            } else {
                totalCredits = totalCredits.add(line.getAmount());
            }
        }

        if (totalDebits.compareTo(totalCredits) != 0) {
            throw new IllegalStateException(
                "Transaction is unbalanced: debits=" + totalDebits +
                ", credits=" + totalCredits);
        }

        // Check period is open
        Period period = periodRepository.findByCompanyAndDate(
            transaction.getCompany(),
            transaction.getTransactionDate()
        ).orElseThrow(() -> new IllegalStateException(
            "No period found for date: " + transaction.getTransactionDate()));

        if (period.isLocked()) {
            throw new IllegalStateException(
                "Period is locked: " + period.getStartDate() + " to " + period.getEndDate());
        }
    }

    /**
     * Creates a reversal transaction for a posted transaction.
     * The reversal inverts all debit/credit directions.
     */
    public Transaction reverseTransaction(Transaction original, User actor, String reason) {
        if (!original.isPosted()) {
            throw new IllegalStateException("Can only reverse posted transactions");
        }

        Transaction reversal = new Transaction(
            original.getCompany(),
            original.getType(),
            original.getTransactionDate()
        );
        reversal.setDescription("Reversal: " + original.getDescription() +
            (reason != null ? " - " + reason : ""));
        reversal.setReference("REV-" + original.getId());
        reversal.setCreatedBy(actor);

        // Create reversed lines
        for (TransactionLine originalLine : original.getLines()) {
            TransactionLine reversedLine = new TransactionLine(
                originalLine.getAccount(),
                originalLine.getAmount(),
                originalLine.isDebit() ?
                    TransactionLine.Direction.CREDIT :
                    TransactionLine.Direction.DEBIT
            );
            reversedLine.setTaxCode(originalLine.getTaxCode());
            reversedLine.setDepartment(originalLine.getDepartment());
            reversedLine.setMemo("Reversal of line " + originalLine.getLineIndex());
            reversal.addLine(reversedLine);
        }

        reversal = transactionRepository.save(reversal);

        // Post the reversal
        return postTransaction(reversal, actor);
    }
}
