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
import com.example.application.domain.SupplierBill.BillStatus;
import com.example.application.repository.PayableAllocationRepository;
import com.example.application.repository.SupplierBillRepository;

/** Unit tests for PayableAllocationService, focusing on overpayment handling. */
@ExtendWith(MockitoExtension.class)
class PayableAllocationServiceTest {

  @Mock private PayableAllocationRepository allocationRepository;
  @Mock private SupplierBillRepository billRepository;
  @Mock private AuditService auditService;

  private PayableAllocationService allocationService;

  private Company company;
  private Contact supplier;
  private Account bankAccount;
  private Account apAccount;
  private User user;
  private Transaction payment;
  private SupplierBill bill;

  @BeforeEach
  void setUp() {
    allocationService =
        new PayableAllocationService(allocationRepository, billRepository, auditService);

    company = new Company();
    company.setId(1L);
    company.setName("Test Company");

    supplier = new Contact();
    supplier.setId(1L);
    supplier.setCode("SUPP001");
    supplier.setName("Test Supplier");

    bankAccount = new Account();
    bankAccount.setId(1L);
    bankAccount.setCode("1000");
    bankAccount.setName("Bank Account");
    bankAccount.setType(Account.AccountType.ASSET);
    bankAccount.setBankAccount(true);

    apAccount = new Account();
    apAccount.setId(2L);
    apAccount.setCode("2100");
    apAccount.setName("Accounts Payable");
    apAccount.setType(Account.AccountType.LIABILITY);

    user = new User("admin@test.com", "Admin User");
    user.setId(1L);

    // Create a posted payment
    payment = new Transaction();
    payment.setId(1L);
    payment.setCompany(company);
    payment.setType(Transaction.TransactionType.PAYMENT);
    payment.setStatus(Transaction.Status.POSTED);
    payment.setTransactionDate(LocalDate.now());

    // Add a line for $500 debit to bank (payment out)
    TransactionLine bankLine =
        new TransactionLine(bankAccount, BigDecimal.valueOf(500), TransactionLine.Direction.CREDIT);
    payment.addLine(bankLine);
    TransactionLine apLine =
        new TransactionLine(apAccount, BigDecimal.valueOf(500), TransactionLine.Direction.DEBIT);
    payment.addLine(apLine);

    // Create a posted bill for $300
    bill = new SupplierBill();
    bill.setId(1L);
    bill.setCompany(company);
    bill.setBillNumber("BILL-001");
    bill.setContact(supplier);
    bill.setBillDate(LocalDate.now().minusDays(30));
    bill.setDueDate(LocalDate.now());
    bill.setStatus(BillStatus.POSTED);
    bill.setTotal(BigDecimal.valueOf(300));
    bill.setAmountPaid(BigDecimal.ZERO);
  }

  @Test
  void allocate_NormalAllocation_Success() {
    // Given
    when(allocationRepository.sumByPayment(payment)).thenReturn(BigDecimal.ZERO);
    when(allocationRepository.sumByBill(bill)).thenReturn(BigDecimal.valueOf(200));
    when(allocationRepository.save(any(PayableAllocation.class)))
        .thenAnswer(
            invocation -> {
              PayableAllocation alloc = invocation.getArgument(0);
              alloc.setId(1L);
              return alloc;
            });

    // When
    PayableAllocation allocation =
        allocationService.allocate(payment, bill, BigDecimal.valueOf(200), user);

    // Then
    assertNotNull(allocation);
    assertEquals(BigDecimal.valueOf(200), allocation.getAmount());
    verify(billRepository).save(bill);
    assertEquals(BigDecimal.valueOf(200), bill.getAmountPaid());
  }

  @Test
  void allocate_Overpayment_Success() {
    // Given - payment of $500, bill balance of $300, allocating $400 (overpayment of $100)
    when(allocationRepository.sumByPayment(payment)).thenReturn(BigDecimal.ZERO);
    when(allocationRepository.sumByBill(bill)).thenReturn(BigDecimal.valueOf(400));
    when(allocationRepository.save(any(PayableAllocation.class)))
        .thenAnswer(
            invocation -> {
              PayableAllocation alloc = invocation.getArgument(0);
              alloc.setId(1L);
              return alloc;
            });

    // When - allocate $400 to a $300 bill (overpayment of $100)
    PayableAllocation allocation =
        allocationService.allocate(payment, bill, BigDecimal.valueOf(400), user);

    // Then - overpayment should be allowed
    assertNotNull(allocation);
    assertEquals(BigDecimal.valueOf(400), allocation.getAmount());
    verify(billRepository).save(bill);
    // Bill amountPaid is now 400, which exceeds total of 300 (negative balance = supplier credit)
    assertEquals(BigDecimal.valueOf(400), bill.getAmountPaid());
    // Balance would be 300 - 400 = -100 (supplier credit/advance)
    assertEquals(BigDecimal.valueOf(-100), bill.getBalance());
  }

  @Test
  void allocate_ExactPayment_Success() {
    // Given - pay exactly the bill amount
    when(allocationRepository.sumByPayment(payment)).thenReturn(BigDecimal.ZERO);
    when(allocationRepository.sumByBill(bill)).thenReturn(BigDecimal.valueOf(300));
    when(allocationRepository.save(any(PayableAllocation.class)))
        .thenAnswer(
            invocation -> {
              PayableAllocation alloc = invocation.getArgument(0);
              alloc.setId(1L);
              return alloc;
            });

    // When
    PayableAllocation allocation =
        allocationService.allocate(payment, bill, BigDecimal.valueOf(300), user);

    // Then
    assertNotNull(allocation);
    assertEquals(BigDecimal.valueOf(300), allocation.getAmount());
    assertEquals(BigDecimal.ZERO, bill.getBalance());
    assertTrue(bill.isPaid());
  }

  @Test
  void allocate_InsufficientPaymentFunds_ThrowsException() {
    // Given - payment of $500, but $400 already allocated
    when(allocationRepository.sumByPayment(payment)).thenReturn(BigDecimal.valueOf(400));

    // When & Then - trying to allocate another $200 should fail
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> allocationService.allocate(payment, bill, BigDecimal.valueOf(200), user));
    assertTrue(exception.getMessage().contains("exceeds unallocated payment amount"));
  }

  @Test
  void allocate_NullAmount_ThrowsException() {
    // When & Then
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> allocationService.allocate(payment, bill, null, user));
    assertEquals("Amount must be positive", exception.getMessage());
  }

  @Test
  void allocate_ZeroAmount_ThrowsException() {
    // When & Then
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> allocationService.allocate(payment, bill, BigDecimal.ZERO, user));
    assertEquals("Amount must be positive", exception.getMessage());
  }

  @Test
  void allocate_UnpostedPayment_ThrowsException() {
    // Given
    payment.setStatus(Transaction.Status.DRAFT);

    // When & Then
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> allocationService.allocate(payment, bill, BigDecimal.valueOf(100), user));
    assertEquals("Payment must be posted", exception.getMessage());
  }

  @Test
  void allocate_NotAPayment_ThrowsException() {
    // Given
    payment.setType(Transaction.TransactionType.RECEIPT);

    // When & Then
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> allocationService.allocate(payment, bill, BigDecimal.valueOf(100), user));
    assertEquals("Transaction must be a payment", exception.getMessage());
  }

  @Test
  void allocate_UnpostedBill_ThrowsException() {
    // Given
    bill.setStatus(BillStatus.DRAFT);

    // When & Then
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> allocationService.allocate(payment, bill, BigDecimal.valueOf(100), user));
    assertEquals("Bill must be posted", exception.getMessage());
  }

  @Test
  void getUnallocatedAmount_NoAllocations_ReturnsFullAmount() {
    // Given
    when(allocationRepository.sumByPayment(payment)).thenReturn(BigDecimal.ZERO);

    // When
    BigDecimal unallocated = allocationService.getUnallocatedAmount(payment);

    // Then - payment has $500 credit to bank
    assertEquals(BigDecimal.valueOf(500), unallocated);
  }

  @Test
  void getUnallocatedAmount_WithAllocations_ReturnsRemainder() {
    // Given
    when(allocationRepository.sumByPayment(payment)).thenReturn(BigDecimal.valueOf(200));

    // When
    BigDecimal unallocated = allocationService.getUnallocatedAmount(payment);

    // Then
    assertEquals(BigDecimal.valueOf(300), unallocated);
  }

  @Test
  void suggestAllocations_ExactMatch_ReturnsOneBill() {
    // Given
    bill.setTotal(BigDecimal.valueOf(500));
    bill.setAmountPaid(BigDecimal.ZERO);
    when(billRepository.findOutstandingByCompanyAndContact(company, supplier))
        .thenReturn(List.of(bill));

    // When
    List<PayableAllocationService.AllocationSuggestion> suggestions =
        allocationService.suggestAllocations(company, supplier, BigDecimal.valueOf(500));

    // Then
    assertEquals(1, suggestions.size());
    assertTrue(suggestions.get(0).exactMatch());
    assertEquals(BigDecimal.valueOf(500), suggestions.get(0).suggestedAmount());
  }

  @Test
  void suggestAllocations_MultipleBills_OldestFirst() {
    // Given - multiple bills sorted by due date
    SupplierBill bill1 =
        createBill(1L, "BILL-001", BigDecimal.valueOf(200), LocalDate.now().minusDays(30));
    SupplierBill bill2 =
        createBill(2L, "BILL-002", BigDecimal.valueOf(300), LocalDate.now().minusDays(10));

    List<SupplierBill> bills = new ArrayList<>();
    bills.add(bill2); // Add in wrong order to verify sorting
    bills.add(bill1);
    when(billRepository.findOutstandingByCompany(company)).thenReturn(bills);

    // When - allocating $350 should cover bill1 (200) and part of bill2 (150)
    List<PayableAllocationService.AllocationSuggestion> suggestions =
        allocationService.suggestAllocations(company, null, BigDecimal.valueOf(350));

    // Then
    assertEquals(2, suggestions.size());
    // First should be the older bill
    assertEquals("BILL-001", suggestions.get(0).bill().getBillNumber());
    assertEquals(BigDecimal.valueOf(200), suggestions.get(0).suggestedAmount());
    // Second should be the newer bill with partial amount
    assertEquals("BILL-002", suggestions.get(1).bill().getBillNumber());
    assertEquals(BigDecimal.valueOf(150), suggestions.get(1).suggestedAmount());
  }

  private SupplierBill createBill(Long id, String number, BigDecimal total, LocalDate dueDate) {
    SupplierBill b = new SupplierBill();
    b.setId(id);
    b.setCompany(company);
    b.setBillNumber(number);
    b.setContact(supplier);
    b.setBillDate(dueDate.minusDays(30));
    b.setDueDate(dueDate);
    b.setStatus(BillStatus.POSTED);
    b.setTotal(total);
    b.setAmountPaid(BigDecimal.ZERO);
    return b;
  }
}
