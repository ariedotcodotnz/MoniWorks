package com.example.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.application.domain.*;
import com.example.application.domain.BankFeedItem.FeedItemStatus;
import com.example.application.domain.TransactionLine.Direction;
import com.example.application.repository.*;

/**
 * Unit tests for BankImportService, focusing on the split transaction functionality per spec 05
 * bank reconciliation requirements.
 */
@ExtendWith(MockitoExtension.class)
class BankImportServiceTest {

  @Mock private BankStatementImportRepository importRepository;
  @Mock private BankFeedItemRepository feedItemRepository;
  @Mock private AccountRepository accountRepository;
  @Mock private AllocationRuleRepository ruleRepository;
  @Mock private ReconciliationMatchRepository reconciliationMatchRepository;
  @Mock private LedgerEntryRepository ledgerEntryRepository;

  private BankImportService bankImportService;

  private Company company;
  private User user;
  private Account bankAccount;
  private Account expenseAccount1;
  private Account expenseAccount2;
  private BankStatementImport bankImport;
  private BankFeedItem feedItem;

  @BeforeEach
  void setUp() {
    bankImportService =
        new BankImportService(
            importRepository,
            feedItemRepository,
            accountRepository,
            ruleRepository,
            reconciliationMatchRepository,
            ledgerEntryRepository);

    company = new Company();
    company.setId(1L);
    company.setName("Test Company");

    user = new User();
    user.setId(1L);
    user.setEmail("test@example.com");

    bankAccount = new Account(company, "1000", "Bank Account", Account.AccountType.ASSET);
    bankAccount.setId(1L);
    bankAccount.setBankAccount(true);

    expenseAccount1 = new Account(company, "5000", "Office Expenses", Account.AccountType.EXPENSE);
    expenseAccount1.setId(2L);

    expenseAccount2 = new Account(company, "5100", "Utilities", Account.AccountType.EXPENSE);
    expenseAccount2.setId(3L);

    bankImport = new BankStatementImport();
    bankImport.setId(1L);
    bankImport.setCompany(company);
    bankImport.setAccount(bankAccount);

    feedItem =
        new BankFeedItem(bankImport, LocalDate.now(), new BigDecimal("-100.00"), "Test payment");
    feedItem.setId(1L);
  }

  @Test
  void splitItem_PaymentWithTwoAllocations_CreatesBalancedTransaction() {
    // Given a payment (negative amount) bank feed item
    feedItem.setAmount(new BigDecimal("-100.00"));

    List<BankImportService.SplitAllocation> allocations =
        List.of(
            new BankImportService.SplitAllocation(
                expenseAccount1, new BigDecimal("60.00"), "GST", "Office supplies"),
            new BankImportService.SplitAllocation(
                expenseAccount2, new BigDecimal("40.00"), null, "Power bill"));

    // When splitting the item
    Transaction result =
        bankImportService.splitItem(
            feedItem, bankAccount, allocations, LocalDate.now(), "Split test", user);

    // Then transaction is created with correct structure
    assertNotNull(result);
    assertEquals(Transaction.TransactionType.PAYMENT, result.getType());
    assertEquals("Split test", result.getDescription());
    assertEquals(3, result.getLines().size()); // 1 bank line + 2 allocation lines

    // Bank line should be credit (payment reduces bank balance)
    TransactionLine bankLine =
        result.getLines().stream()
            .filter(l -> l.getAccount().equals(bankAccount))
            .findFirst()
            .orElseThrow();
    assertEquals(Direction.CREDIT, bankLine.getDirection());
    assertEquals(new BigDecimal("100.00"), bankLine.getAmount());

    // Expense lines should be debits
    List<TransactionLine> expenseLines =
        result.getLines().stream().filter(l -> !l.getAccount().equals(bankAccount)).toList();
    assertEquals(2, expenseLines.size());
    assertTrue(expenseLines.stream().allMatch(l -> l.getDirection() == Direction.DEBIT));

    // Verify feed item status updated
    assertEquals(FeedItemStatus.SPLIT, feedItem.getStatus());
    assertEquals(result, feedItem.getMatchedTransaction());

    // Verify reconciliation match was saved
    verify(reconciliationMatchRepository).save(any(ReconciliationMatch.class));
    verify(feedItemRepository).save(feedItem);
  }

  @Test
  void splitItem_ReceiptWithTwoAllocations_CreatesBalancedTransaction() {
    // Given a receipt (positive amount) bank feed item
    feedItem.setAmount(new BigDecimal("500.00"));

    List<BankImportService.SplitAllocation> allocations =
        List.of(
            new BankImportService.SplitAllocation(
                expenseAccount1, new BigDecimal("300.00"), null, "Service A"),
            new BankImportService.SplitAllocation(
                expenseAccount2, new BigDecimal("200.00"), null, "Service B"));

    // When splitting the item
    Transaction result =
        bankImportService.splitItem(
            feedItem, bankAccount, allocations, LocalDate.now(), "Receipt split", user);

    // Then transaction is created as receipt type
    assertEquals(Transaction.TransactionType.RECEIPT, result.getType());

    // Bank line should be debit (receipt increases bank balance)
    TransactionLine bankLine =
        result.getLines().stream()
            .filter(l -> l.getAccount().equals(bankAccount))
            .findFirst()
            .orElseThrow();
    assertEquals(Direction.DEBIT, bankLine.getDirection());
    assertEquals(new BigDecimal("500.00"), bankLine.getAmount());

    // Income lines should be credits
    List<TransactionLine> incomeLines =
        result.getLines().stream().filter(l -> !l.getAccount().equals(bankAccount)).toList();
    assertTrue(incomeLines.stream().allMatch(l -> l.getDirection() == Direction.CREDIT));
  }

  @Test
  void splitItem_AllocationsDoNotMatchTotal_ThrowsException() {
    // Given allocations that don't match the total
    feedItem.setAmount(new BigDecimal("-100.00"));

    List<BankImportService.SplitAllocation> allocations =
        List.of(
            new BankImportService.SplitAllocation(
                expenseAccount1, new BigDecimal("50.00"), null, "Partial"));

    // When/Then
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                bankImportService.splitItem(
                    feedItem, bankAccount, allocations, LocalDate.now(), "Test", user));

    assertTrue(exception.getMessage().contains("must equal"));
  }

  @Test
  void splitItem_EmptyAllocations_ThrowsException() {
    // Given empty allocations
    List<BankImportService.SplitAllocation> allocations = List.of();

    // When/Then
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                bankImportService.splitItem(
                    feedItem, bankAccount, allocations, LocalDate.now(), "Test", user));

    assertEquals("At least one allocation is required", exception.getMessage());
  }

  @Test
  void splitItem_NullAllocations_ThrowsException() {
    // When/Then
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                bankImportService.splitItem(
                    feedItem, bankAccount, null, LocalDate.now(), "Test", user));

    assertEquals("At least one allocation is required", exception.getMessage());
  }

  @Test
  void splitItem_SingleAllocation_CreatesSimpleTransaction() {
    // Given a single allocation (equivalent to create transaction)
    feedItem.setAmount(new BigDecimal("-75.50"));

    List<BankImportService.SplitAllocation> allocations =
        List.of(
            new BankImportService.SplitAllocation(
                expenseAccount1, new BigDecimal("75.50"), "GST", "Single allocation"));

    // When
    Transaction result =
        bankImportService.splitItem(
            feedItem, bankAccount, allocations, LocalDate.now(), "Single split", user);

    // Then
    assertEquals(2, result.getLines().size()); // 1 bank + 1 allocation
    assertEquals(FeedItemStatus.SPLIT, feedItem.getStatus());
  }

  @Test
  void splitItem_PreservesTaxCodeOnAllocationLines() {
    // Given allocations with tax codes
    feedItem.setAmount(new BigDecimal("-100.00"));

    List<BankImportService.SplitAllocation> allocations =
        List.of(
            new BankImportService.SplitAllocation(
                expenseAccount1, new BigDecimal("50.00"), "GST", "With tax"),
            new BankImportService.SplitAllocation(
                expenseAccount2, new BigDecimal("50.00"), "ZERO", "Zero rated"));

    // When
    Transaction result =
        bankImportService.splitItem(
            feedItem, bankAccount, allocations, LocalDate.now(), "Tax test", user);

    // Then tax codes are preserved
    List<TransactionLine> allocLines =
        result.getLines().stream().filter(l -> !l.getAccount().equals(bankAccount)).toList();

    assertTrue(allocLines.stream().anyMatch(l -> "GST".equals(l.getTaxCode())));
    assertTrue(allocLines.stream().anyMatch(l -> "ZERO".equals(l.getTaxCode())));
  }

  @Test
  void splitItem_ReconciliationMatchCreatedWithNotes() {
    // Given
    feedItem.setAmount(new BigDecimal("-100.00"));

    List<BankImportService.SplitAllocation> allocations =
        List.of(
            new BankImportService.SplitAllocation(
                expenseAccount1, new BigDecimal("50.00"), null, null),
            new BankImportService.SplitAllocation(
                expenseAccount2, new BigDecimal("50.00"), null, null));

    // When
    bankImportService.splitItem(feedItem, bankAccount, allocations, LocalDate.now(), "Test", user);

    // Then reconciliation match is saved with notes
    ArgumentCaptor<ReconciliationMatch> matchCaptor =
        ArgumentCaptor.forClass(ReconciliationMatch.class);
    verify(reconciliationMatchRepository).save(matchCaptor.capture());

    ReconciliationMatch savedMatch = matchCaptor.getValue();
    assertEquals("Split across 2 accounts", savedMatch.getMatchNotes());
    assertEquals(ReconciliationMatch.MatchType.MANUAL, savedMatch.getMatchType());
  }

  @Test
  void splitAllocation_NullAccount_ThrowsException() {
    assertThrows(
        NullPointerException.class,
        () -> new BankImportService.SplitAllocation(null, BigDecimal.TEN, null, null));
  }

  @Test
  void splitAllocation_NullAmount_ThrowsException() {
    assertThrows(
        NullPointerException.class,
        () -> new BankImportService.SplitAllocation(expenseAccount1, null, null, null));
  }

  @Test
  void splitAllocation_ZeroAmount_ThrowsException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new BankImportService.SplitAllocation(expenseAccount1, BigDecimal.ZERO, null, null));
  }

  @Test
  void splitAllocation_NegativeAmount_ThrowsException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BankImportService.SplitAllocation(
                expenseAccount1, new BigDecimal("-10.00"), null, null));
  }

  @Test
  void reconcileSplitTransaction_MarksLedgerEntriesAsReconciled() {
    // Given a transaction with ledger entries
    Transaction transaction =
        new Transaction(company, Transaction.TransactionType.PAYMENT, LocalDate.now());
    transaction.setDescription("Test transaction");

    // Create transaction lines
    TransactionLine bankLine =
        new TransactionLine(bankAccount, new BigDecimal("100.00"), Direction.CREDIT);
    TransactionLine expenseLine =
        new TransactionLine(expenseAccount1, new BigDecimal("100.00"), Direction.DEBIT);
    transaction.addLine(bankLine);
    transaction.addLine(expenseLine);

    // Create ledger entries from the transaction lines
    LedgerEntry bankEntry = new LedgerEntry(company, transaction, bankLine);
    LedgerEntry expenseEntry = new LedgerEntry(company, transaction, expenseLine);

    when(ledgerEntryRepository.findByTransaction(transaction))
        .thenReturn(List.of(bankEntry, expenseEntry));

    // When
    bankImportService.reconcileSplitTransaction(feedItem, transaction, bankAccount, user);

    // Then only the bank account entry is reconciled
    verify(ledgerEntryRepository, times(1)).save(any(LedgerEntry.class));
    assertTrue(bankEntry.isReconciled());
    assertFalse(expenseEntry.isReconciled());
  }
}
