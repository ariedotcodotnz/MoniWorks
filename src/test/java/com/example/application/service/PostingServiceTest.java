package com.example.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.application.domain.*;
import com.example.application.repository.LedgerEntryRepository;
import com.example.application.repository.PeriodRepository;
import com.example.application.repository.ReversalLinkRepository;
import com.example.application.repository.TransactionRepository;

/**
 * Unit tests for PostingService. Tests the core accounting logic for posting transactions to the
 * ledger.
 */
@ExtendWith(MockitoExtension.class)
class PostingServiceTest {

  @Mock private TransactionRepository transactionRepository;

  @Mock private LedgerEntryRepository ledgerEntryRepository;

  @Mock private PeriodRepository periodRepository;

  @Mock private ReversalLinkRepository reversalLinkRepository;

  @Mock private AuditService auditService;

  @Mock private TaxCalculationService taxCalculationService;

  private PostingService postingService;

  private Company company;
  private Account bankAccount;
  private Account expenseAccount;
  private Period openPeriod;
  private User user;

  @BeforeEach
  void setUp() {
    postingService =
        new PostingService(
            transactionRepository,
            ledgerEntryRepository,
            periodRepository,
            reversalLinkRepository,
            auditService,
            taxCalculationService);

    company = new Company("Test Company", "NZ", "NZD", LocalDate.of(2024, 4, 1));
    company.setId(1L);

    bankAccount = new Account(company, "1000", "Bank Account", Account.AccountType.ASSET);
    bankAccount.setId(1L);

    expenseAccount = new Account(company, "5000", "Office Expenses", Account.AccountType.EXPENSE);
    expenseAccount.setId(2L);

    openPeriod = new Period();
    openPeriod.setId(1L);
    openPeriod.setStatus(Period.Status.OPEN);
    openPeriod.setStartDate(LocalDate.of(2024, 4, 1));
    openPeriod.setEndDate(LocalDate.of(2024, 4, 30));

    user = new User("test@example.com", "Test User");
    user.setId(1L);
  }

  @Test
  void postTransaction_whenBalanced_createsLedgerEntries() {
    // Arrange
    Transaction transaction = createBalancedTransaction();

    when(ledgerEntryRepository.existsByTransaction(transaction)).thenReturn(false);
    when(periodRepository.findByCompanyAndDate(company, transaction.getTransactionDate()))
        .thenReturn(Optional.of(openPeriod));
    when(transactionRepository.save(any(Transaction.class))).thenReturn(transaction);
    when(auditService.logEvent(any(), any(), any(), any(), any(), any()))
        .thenReturn(new AuditEvent());
    when(taxCalculationService.createTaxLinesForLedgerEntries(any(), anyList()))
        .thenReturn(new ArrayList<>());

    // Act
    Transaction result = postingService.postTransaction(transaction, user);

    // Assert
    assertEquals(Transaction.Status.POSTED, result.getStatus());
    assertNotNull(result.getPostedAt());
    verify(ledgerEntryRepository).saveAll(anyList());
    verify(taxCalculationService).createTaxLinesForLedgerEntries(any(), anyList());
    verify(auditService)
        .logEvent(any(), eq(user), eq("TRANSACTION_POSTED"), eq("Transaction"), any(), any());
  }

  @Test
  void postTransaction_whenUnbalanced_throwsException() {
    // Arrange
    Transaction transaction =
        new Transaction(company, Transaction.TransactionType.PAYMENT, LocalDate.of(2024, 4, 15));
    transaction.setId(1L);

    // Add only a debit line (unbalanced)
    TransactionLine debitLine =
        new TransactionLine(bankAccount, new BigDecimal("100.00"), TransactionLine.Direction.DEBIT);
    transaction.addLine(debitLine);

    when(ledgerEntryRepository.existsByTransaction(transaction)).thenReturn(false);

    // Act & Assert
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class, () -> postingService.validateForPosting(transaction));

    assertTrue(exception.getMessage().contains("unbalanced"));
  }

  @Test
  void postTransaction_whenAlreadyPosted_throwsException() {
    // Arrange
    Transaction transaction = createBalancedTransaction();
    transaction.setStatus(Transaction.Status.POSTED);

    // Act & Assert
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class, () -> postingService.validateForPosting(transaction));

    assertTrue(exception.getMessage().contains("already posted"));
  }

  @Test
  void postTransaction_whenPeriodLocked_throwsException() {
    // Arrange
    Transaction transaction = createBalancedTransaction();
    Period lockedPeriod = new Period();
    lockedPeriod.setStatus(Period.Status.LOCKED);
    lockedPeriod.setStartDate(LocalDate.of(2024, 4, 1));
    lockedPeriod.setEndDate(LocalDate.of(2024, 4, 30));

    when(ledgerEntryRepository.existsByTransaction(transaction)).thenReturn(false);
    when(periodRepository.findByCompanyAndDate(company, transaction.getTransactionDate()))
        .thenReturn(Optional.of(lockedPeriod));

    // Act & Assert
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class, () -> postingService.validateForPosting(transaction));

    assertTrue(exception.getMessage().contains("locked"));
  }

  @Test
  void postTransaction_whenAccountInactive_throwsException() {
    // Arrange
    bankAccount.setActive(false);
    Transaction transaction = createBalancedTransaction();

    when(ledgerEntryRepository.existsByTransaction(transaction)).thenReturn(false);

    // Act & Assert
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class, () -> postingService.validateForPosting(transaction));

    assertTrue(exception.getMessage().contains("inactive"));
  }

  @Test
  void postTransaction_whenNoLines_throwsException() {
    // Arrange
    Transaction transaction =
        new Transaction(company, Transaction.TransactionType.PAYMENT, LocalDate.of(2024, 4, 15));
    transaction.setId(1L);

    when(ledgerEntryRepository.existsByTransaction(transaction)).thenReturn(false);

    // Act & Assert
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class, () -> postingService.validateForPosting(transaction));

    assertTrue(exception.getMessage().contains("no lines"));
  }

  @Test
  void reverseTransaction_createsInvertedEntries() {
    // Arrange
    Transaction original = createBalancedTransaction();
    original.setStatus(Transaction.Status.POSTED);

    when(transactionRepository.save(any(Transaction.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(ledgerEntryRepository.existsByTransaction(any())).thenReturn(false);
    when(periodRepository.findByCompanyAndDate(any(), any())).thenReturn(Optional.of(openPeriod));
    when(auditService.logEvent(any(), any(), any(), any(), any(), any()))
        .thenReturn(new AuditEvent());
    when(taxCalculationService.createTaxLinesForLedgerEntries(any(), anyList()))
        .thenReturn(new ArrayList<>());
    when(reversalLinkRepository.save(any(ReversalLink.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    Transaction reversal = postingService.reverseTransaction(original, user, "Error correction");

    // Assert
    assertEquals(2, reversal.getLines().size());
    // Verify ReversalLink was created
    verify(reversalLinkRepository).save(any(ReversalLink.class));
    // Original was: bank credit, expense debit
    // Reversal should be: bank debit, expense credit
    TransactionLine reversedBankLine =
        reversal.getLines().stream()
            .filter(l -> l.getAccount().equals(bankAccount))
            .findFirst()
            .orElseThrow();
    assertEquals(TransactionLine.Direction.DEBIT, reversedBankLine.getDirection());
  }

  private Transaction createBalancedTransaction() {
    Transaction transaction =
        new Transaction(company, Transaction.TransactionType.PAYMENT, LocalDate.of(2024, 4, 15));
    transaction.setId(1L);
    transaction.setDescription("Office supplies");

    // Credit bank (money going out)
    TransactionLine creditLine =
        new TransactionLine(
            bankAccount, new BigDecimal("100.00"), TransactionLine.Direction.CREDIT);
    transaction.addLine(creditLine);

    // Debit expense (expense increasing)
    TransactionLine debitLine =
        new TransactionLine(
            expenseAccount, new BigDecimal("100.00"), TransactionLine.Direction.DEBIT);
    transaction.addLine(debitLine);

    return transaction;
  }
}
