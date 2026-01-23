package com.example.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.application.domain.*;
import com.example.application.domain.SalesInvoice.InvoiceStatus;
import com.example.application.repository.ReceivableAllocationRepository;
import com.example.application.repository.SalesInvoiceRepository;

/** Unit tests for ReceivableAllocationService, focusing on overpayment handling. */
@ExtendWith(MockitoExtension.class)
class ReceivableAllocationServiceTest {

  @Mock private ReceivableAllocationRepository allocationRepository;
  @Mock private SalesInvoiceRepository invoiceRepository;
  @Mock private AuditService auditService;

  private ReceivableAllocationService allocationService;

  private Company company;
  private Contact customer;
  private Account bankAccount;
  private Account arAccount;
  private User user;
  private Transaction receipt;
  private SalesInvoice invoice;

  @BeforeEach
  void setUp() {
    allocationService =
        new ReceivableAllocationService(allocationRepository, invoiceRepository, auditService);

    company = new Company();
    company.setId(1L);
    company.setName("Test Company");

    customer = new Contact();
    customer.setId(1L);
    customer.setCode("CUST001");
    customer.setName("Test Customer");

    bankAccount = new Account();
    bankAccount.setId(1L);
    bankAccount.setCode("1000");
    bankAccount.setName("Bank Account");
    bankAccount.setType(Account.AccountType.ASSET);
    bankAccount.setBankAccount(true);

    arAccount = new Account();
    arAccount.setId(2L);
    arAccount.setCode("1200");
    arAccount.setName("Accounts Receivable");
    arAccount.setType(Account.AccountType.ASSET);

    user = new User("admin@test.com", "Admin User");
    user.setId(1L);

    // Create a posted receipt
    receipt = new Transaction();
    receipt.setId(1L);
    receipt.setCompany(company);
    receipt.setType(Transaction.TransactionType.RECEIPT);
    receipt.setStatus(Transaction.Status.POSTED);
    receipt.setTransactionDate(LocalDate.now());

    // Add a line for $500 credit to bank (receipt)
    TransactionLine bankLine =
        new TransactionLine(bankAccount, BigDecimal.valueOf(500), TransactionLine.Direction.DEBIT);
    receipt.addLine(bankLine);
    TransactionLine arLine =
        new TransactionLine(arAccount, BigDecimal.valueOf(500), TransactionLine.Direction.CREDIT);
    receipt.addLine(arLine);

    // Create an issued invoice for $300
    invoice = new SalesInvoice();
    invoice.setId(1L);
    invoice.setCompany(company);
    invoice.setInvoiceNumber("INV-001");
    invoice.setContact(customer);
    invoice.setIssueDate(LocalDate.now().minusDays(30));
    invoice.setDueDate(LocalDate.now());
    invoice.setStatus(InvoiceStatus.ISSUED);
    invoice.setTotal(BigDecimal.valueOf(300));
    invoice.setAmountPaid(BigDecimal.ZERO);
  }

  @Test
  void allocate_NormalAllocation_Success() {
    // Given
    when(allocationRepository.sumByReceipt(receipt)).thenReturn(BigDecimal.ZERO);
    when(allocationRepository.sumByInvoice(invoice)).thenReturn(BigDecimal.valueOf(200));
    when(allocationRepository.save(any(ReceivableAllocation.class)))
        .thenAnswer(
            invocation -> {
              ReceivableAllocation alloc = invocation.getArgument(0);
              alloc.setId(1L);
              return alloc;
            });

    // When
    ReceivableAllocation allocation =
        allocationService.allocate(receipt, invoice, BigDecimal.valueOf(200), user);

    // Then
    assertNotNull(allocation);
    assertEquals(BigDecimal.valueOf(200), allocation.getAmount());
    verify(invoiceRepository).save(invoice);
    assertEquals(BigDecimal.valueOf(200), invoice.getAmountPaid());
  }

  @Test
  void allocate_Overpayment_Success() {
    // Given - receipt of $500, invoice balance of $300, allocating $400 (overpayment of $100)
    when(allocationRepository.sumByReceipt(receipt)).thenReturn(BigDecimal.ZERO);
    when(allocationRepository.sumByInvoice(invoice)).thenReturn(BigDecimal.valueOf(400));
    when(allocationRepository.save(any(ReceivableAllocation.class)))
        .thenAnswer(
            invocation -> {
              ReceivableAllocation alloc = invocation.getArgument(0);
              alloc.setId(1L);
              return alloc;
            });

    // When - allocate $400 to a $300 invoice (overpayment of $100)
    ReceivableAllocation allocation =
        allocationService.allocate(receipt, invoice, BigDecimal.valueOf(400), user);

    // Then - overpayment should be allowed
    assertNotNull(allocation);
    assertEquals(BigDecimal.valueOf(400), allocation.getAmount());
    verify(invoiceRepository).save(invoice);
    // Invoice amountPaid is now 400, which exceeds total of 300 (negative balance = credit)
    assertEquals(BigDecimal.valueOf(400), invoice.getAmountPaid());
    // Balance would be 300 - 400 = -100 (customer credit)
    assertEquals(BigDecimal.valueOf(-100), invoice.getBalance());
  }

  @Test
  void allocate_ExactPayment_Success() {
    // Given - pay exactly the invoice amount
    when(allocationRepository.sumByReceipt(receipt)).thenReturn(BigDecimal.ZERO);
    when(allocationRepository.sumByInvoice(invoice)).thenReturn(BigDecimal.valueOf(300));
    when(allocationRepository.save(any(ReceivableAllocation.class)))
        .thenAnswer(
            invocation -> {
              ReceivableAllocation alloc = invocation.getArgument(0);
              alloc.setId(1L);
              return alloc;
            });

    // When
    ReceivableAllocation allocation =
        allocationService.allocate(receipt, invoice, BigDecimal.valueOf(300), user);

    // Then
    assertNotNull(allocation);
    assertEquals(BigDecimal.valueOf(300), allocation.getAmount());
    assertEquals(BigDecimal.ZERO, invoice.getBalance());
    assertTrue(invoice.isPaid());
  }

  @Test
  void allocate_InsufficientReceiptFunds_ThrowsException() {
    // Given - receipt of $500, but $400 already allocated
    when(allocationRepository.sumByReceipt(receipt)).thenReturn(BigDecimal.valueOf(400));

    // When & Then - trying to allocate another $200 should fail
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> allocationService.allocate(receipt, invoice, BigDecimal.valueOf(200), user));
    assertTrue(exception.getMessage().contains("exceeds unallocated receipt amount"));
  }

  @Test
  void allocate_NullAmount_ThrowsException() {
    // When & Then
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> allocationService.allocate(receipt, invoice, null, user));
    assertEquals("Amount must be positive", exception.getMessage());
  }

  @Test
  void allocate_ZeroAmount_ThrowsException() {
    // When & Then
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> allocationService.allocate(receipt, invoice, BigDecimal.ZERO, user));
    assertEquals("Amount must be positive", exception.getMessage());
  }

  @Test
  void allocate_UnpostedReceipt_ThrowsException() {
    // Given
    receipt.setStatus(Transaction.Status.DRAFT);

    // When & Then
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> allocationService.allocate(receipt, invoice, BigDecimal.valueOf(100), user));
    assertEquals("Receipt must be posted", exception.getMessage());
  }

  @Test
  void allocate_NotAReceipt_ThrowsException() {
    // Given
    receipt.setType(Transaction.TransactionType.PAYMENT);

    // When & Then
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> allocationService.allocate(receipt, invoice, BigDecimal.valueOf(100), user));
    assertEquals("Transaction must be a receipt", exception.getMessage());
  }

  @Test
  void allocate_UnissuedInvoice_ThrowsException() {
    // Given
    invoice.setStatus(InvoiceStatus.DRAFT);

    // When & Then
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> allocationService.allocate(receipt, invoice, BigDecimal.valueOf(100), user));
    assertEquals("Invoice must be issued", exception.getMessage());
  }

  @Test
  void getUnallocatedAmount_NoAllocations_ReturnsFullAmount() {
    // Given
    when(allocationRepository.sumByReceipt(receipt)).thenReturn(BigDecimal.ZERO);

    // When
    BigDecimal unallocated = allocationService.getUnallocatedAmount(receipt);

    // Then - receipt has $500 debit to bank
    assertEquals(BigDecimal.valueOf(500), unallocated);
  }

  @Test
  void getUnallocatedAmount_WithAllocations_ReturnsRemainder() {
    // Given
    when(allocationRepository.sumByReceipt(receipt)).thenReturn(BigDecimal.valueOf(200));

    // When
    BigDecimal unallocated = allocationService.getUnallocatedAmount(receipt);

    // Then
    assertEquals(BigDecimal.valueOf(300), unallocated);
  }

  @Test
  void suggestAllocations_ExactMatch_ReturnsOneInvoice() {
    // Given
    invoice.setTotal(BigDecimal.valueOf(500));
    invoice.setAmountPaid(BigDecimal.ZERO);
    when(invoiceRepository.findOutstandingByCompanyAndContact(company, customer))
        .thenReturn(List.of(invoice));

    // When
    List<ReceivableAllocationService.AllocationSuggestion> suggestions =
        allocationService.suggestAllocations(company, customer, BigDecimal.valueOf(500));

    // Then
    assertEquals(1, suggestions.size());
    assertTrue(suggestions.get(0).exactMatch());
    assertEquals(BigDecimal.valueOf(500), suggestions.get(0).suggestedAmount());
  }

  @Test
  void suggestAllocations_MultipleInvoices_OldestFirst() {
    // Given - multiple invoices sorted by due date
    SalesInvoice invoice1 =
        createInvoice(1L, "INV-001", BigDecimal.valueOf(200), LocalDate.now().minusDays(30));
    SalesInvoice invoice2 =
        createInvoice(2L, "INV-002", BigDecimal.valueOf(300), LocalDate.now().minusDays(10));

    List<SalesInvoice> invoices = new ArrayList<>();
    invoices.add(invoice2); // Add in wrong order to verify sorting
    invoices.add(invoice1);
    when(invoiceRepository.findOutstandingByCompany(company)).thenReturn(invoices);

    // When - allocating $350 should cover invoice1 (200) and part of invoice2 (150)
    List<ReceivableAllocationService.AllocationSuggestion> suggestions =
        allocationService.suggestAllocations(company, null, BigDecimal.valueOf(350));

    // Then
    assertEquals(2, suggestions.size());
    // First should be the older invoice
    assertEquals("INV-001", suggestions.get(0).invoice().getInvoiceNumber());
    assertEquals(BigDecimal.valueOf(200), suggestions.get(0).suggestedAmount());
    // Second should be the newer invoice with partial amount
    assertEquals("INV-002", suggestions.get(1).invoice().getInvoiceNumber());
    assertEquals(BigDecimal.valueOf(150), suggestions.get(1).suggestedAmount());
  }

  private SalesInvoice createInvoice(Long id, String number, BigDecimal total, LocalDate dueDate) {
    SalesInvoice inv = new SalesInvoice();
    inv.setId(id);
    inv.setCompany(company);
    inv.setInvoiceNumber(number);
    inv.setContact(customer);
    inv.setIssueDate(dueDate.minusDays(30));
    inv.setDueDate(dueDate);
    inv.setStatus(InvoiceStatus.ISSUED);
    inv.setTotal(total);
    inv.setAmountPaid(BigDecimal.ZERO);
    return inv;
  }
}
