package com.example.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.application.domain.*;
import com.example.application.domain.TaxReturn.Basis;
import com.example.application.repository.PayableAllocationRepository;
import com.example.application.repository.ReceivableAllocationRepository;
import com.example.application.repository.TaxLineRepository;
import com.example.application.repository.TaxReturnRepository;

/**
 * Unit tests for TaxReturnService. Tests GST return generation for both Invoice (accrual) and Cash
 * bases per spec 06:
 *
 * <ul>
 *   <li>Invoice basis: Tax recognized when invoice is issued/bill is posted
 *   <li>Cash basis: Tax recognized when payment is received/made (proportional to payment amount)
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TaxReturnServiceTest {

  @Mock private TaxReturnRepository taxReturnRepository;
  @Mock private TaxLineRepository taxLineRepository;
  @Mock private ReceivableAllocationRepository receivableAllocationRepository;
  @Mock private PayableAllocationRepository payableAllocationRepository;
  @Mock private AuditService auditService;

  private TaxReturnService taxReturnService;

  private Company company;
  private User actor;
  private LocalDate startDate;
  private LocalDate endDate;

  @BeforeEach
  void setUp() {
    taxReturnService =
        new TaxReturnService(
            taxReturnRepository,
            taxLineRepository,
            receivableAllocationRepository,
            payableAllocationRepository,
            auditService);

    company = new Company("Test Company", "NZ", "NZD", LocalDate.of(2024, 4, 1));
    company.setId(1L);

    actor = new User("test@example.com", "Test User");
    actor.setId(1L);

    startDate = LocalDate.of(2024, 1, 1);
    endDate = LocalDate.of(2024, 1, 31);
  }

  // ==================== Invoice Basis Tests ====================

  @Test
  void generateReturn_invoiceBasis_calculatesFromTaxLines() {
    // Given: Tax lines from ledger entries in the period
    List<TaxLine> taxLines = createSampleTaxLines();
    when(taxLineRepository.findByCompanyAndDateRange(company, startDate, endDate))
        .thenReturn(taxLines);
    when(taxReturnRepository.save(any(TaxReturn.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When: Generate return with Invoice basis
    TaxReturn result =
        taxReturnService.generateReturn(company, startDate, endDate, Basis.INVOICE, actor);

    // Then: Totals are calculated from tax lines
    assertNotNull(result);
    assertEquals(Basis.INVOICE, result.getBasis());
    // Sales: 100.00 (standard) + 50.00 (zero-rated) = 150.00
    assertEquals(new BigDecimal("150.00"), result.getTotalSales());
    // Zero-rated: 50.00
    // Purchases: 80.00
    assertEquals(new BigDecimal("80.00"), result.getTotalPurchases());
    // Output tax: 15.00 (15% of 100)
    assertEquals(new BigDecimal("15.00"), result.getOutputTax());
    // Input tax: 12.00 (15% of 80)
    assertEquals(new BigDecimal("12.00"), result.getInputTax());
    // Tax payable: 15 - 12 = 3.00
    assertEquals(new BigDecimal("3.00"), result.getTaxPayable());
  }

  @Test
  void generateReturn_invoiceBasis_createsCorrectLines() {
    // Given
    List<TaxLine> taxLines = createSampleTaxLines();
    when(taxLineRepository.findByCompanyAndDateRange(company, startDate, endDate))
        .thenReturn(taxLines);
    when(taxReturnRepository.save(any(TaxReturn.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    TaxReturn result =
        taxReturnService.generateReturn(company, startDate, endDate, Basis.INVOICE, actor);

    // Then: Check report lines
    assertEquals(6, result.getLines().size());

    // Box 5: Total sales
    TaxReturnLine box5 =
        result.getLines().stream().filter(l -> l.getBoxCode().equals("5")).findFirst().orElse(null);
    assertNotNull(box5);
    assertEquals(new BigDecimal("150.00"), box5.getAmount());

    // Box 9: GST collected
    TaxReturnLine box9 =
        result.getLines().stream().filter(l -> l.getBoxCode().equals("9")).findFirst().orElse(null);
    assertNotNull(box9);
    assertEquals(new BigDecimal("15.00"), box9.getAmount());

    // Box 11: GST paid
    TaxReturnLine box11 =
        result.getLines().stream()
            .filter(l -> l.getBoxCode().equals("11"))
            .findFirst()
            .orElse(null);
    assertNotNull(box11);
    assertEquals(new BigDecimal("12.00"), box11.getAmount());
  }

  // ==================== Cash Basis Tests ====================

  @Test
  void generateReturn_cashBasis_calculatesFromAllocations() {
    // Given: Allocations (payments received/made) in the period
    List<ReceivableAllocation> receivableAllocations = createSampleReceivableAllocations();
    List<PayableAllocation> payableAllocations = createSamplePayableAllocations();

    when(receivableAllocationRepository.findByCompanyAndAllocatedAtRange(any(), any(), any()))
        .thenReturn(receivableAllocations);
    when(payableAllocationRepository.findByCompanyAndAllocatedAtRange(any(), any(), any()))
        .thenReturn(payableAllocations);
    when(taxReturnRepository.save(any(TaxReturn.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When: Generate return with Cash basis
    TaxReturn result =
        taxReturnService.generateReturn(company, startDate, endDate, Basis.CASH, actor);

    // Then: Totals are calculated from allocations
    assertNotNull(result);
    assertEquals(Basis.CASH, result.getBasis());
    // Invoice total: 115.00, subtotal: 100.00, tax: 15.00
    // Payment received: 57.50 (50% of total)
    // Proportional sales: 100.00 * 0.5 = 50.00
    assertEquals(new BigDecimal("50.00"), result.getTotalSales());
    // Proportional output tax: 15.00 * 0.5 = 7.50
    assertEquals(new BigDecimal("7.50"), result.getOutputTax());
    // Bill total: 92.00, subtotal: 80.00, tax: 12.00
    // Payment made: 46.00 (50% of total)
    // Proportional purchases: 80.00 * 0.5 = 40.00
    assertEquals(new BigDecimal("40.00"), result.getTotalPurchases());
    // Proportional input tax: 12.00 * 0.5 = 6.00
    assertEquals(new BigDecimal("6.00"), result.getInputTax());
    // Tax payable: 7.50 - 6.00 = 1.50
    assertEquals(new BigDecimal("1.50"), result.getTaxPayable());
  }

  @Test
  void generateReturn_cashBasis_handlesPartialPayments() {
    // Given: A partial payment of 25% of an invoice
    SalesInvoice invoice = createInvoice(new BigDecimal("100.00"), new BigDecimal("15.00"));
    ReceivableAllocation allocation =
        new ReceivableAllocation(
            company, createReceiptTransaction(), invoice, new BigDecimal("28.75")); // 25% of 115.00
    allocation.setAllocatedAt(Instant.now());

    when(receivableAllocationRepository.findByCompanyAndAllocatedAtRange(any(), any(), any()))
        .thenReturn(List.of(allocation));
    when(payableAllocationRepository.findByCompanyAndAllocatedAtRange(any(), any(), any()))
        .thenReturn(List.of());
    when(taxReturnRepository.save(any(TaxReturn.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    TaxReturn result =
        taxReturnService.generateReturn(company, startDate, endDate, Basis.CASH, actor);

    // Then: 25% of sales and tax is recognized
    assertEquals(new BigDecimal("25.00"), result.getTotalSales()); // 100 * 0.25
    assertEquals(new BigDecimal("3.75"), result.getOutputTax()); // 15 * 0.25
  }

  @Test
  void generateReturn_cashBasis_handlesZeroTaxInvoices() {
    // Given: A zero-rated invoice with payment
    SalesInvoice invoice = createInvoice(new BigDecimal("100.00"), BigDecimal.ZERO);
    ReceivableAllocation allocation =
        new ReceivableAllocation(
            company, createReceiptTransaction(), invoice, new BigDecimal("100.00"));
    allocation.setAllocatedAt(Instant.now());

    when(receivableAllocationRepository.findByCompanyAndAllocatedAtRange(any(), any(), any()))
        .thenReturn(List.of(allocation));
    when(payableAllocationRepository.findByCompanyAndAllocatedAtRange(any(), any(), any()))
        .thenReturn(List.of());
    when(taxReturnRepository.save(any(TaxReturn.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    TaxReturn result =
        taxReturnService.generateReturn(company, startDate, endDate, Basis.CASH, actor);

    // Then: Sales counted as zero-rated
    assertEquals(new BigDecimal("100.00"), result.getTotalSales());
    assertEquals(BigDecimal.ZERO, result.getOutputTax());
  }

  @Test
  void generateReturn_cashBasis_noAllocations_returnsZeroTotals() {
    // Given: No allocations in period
    when(receivableAllocationRepository.findByCompanyAndAllocatedAtRange(any(), any(), any()))
        .thenReturn(List.of());
    when(payableAllocationRepository.findByCompanyAndAllocatedAtRange(any(), any(), any()))
        .thenReturn(List.of());
    when(taxReturnRepository.save(any(TaxReturn.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    TaxReturn result =
        taxReturnService.generateReturn(company, startDate, endDate, Basis.CASH, actor);

    // Then: All totals are zero
    assertEquals(BigDecimal.ZERO, result.getTotalSales());
    assertEquals(BigDecimal.ZERO, result.getTotalPurchases());
    assertEquals(BigDecimal.ZERO, result.getOutputTax());
    assertEquals(BigDecimal.ZERO, result.getInputTax());
    assertEquals(BigDecimal.ZERO, result.getTaxPayable());
  }

  // ==================== Audit Logging Tests ====================

  @Test
  void generateReturn_logsAuditEvent() {
    // Given
    when(taxLineRepository.findByCompanyAndDateRange(any(), any(), any())).thenReturn(List.of());
    when(taxReturnRepository.save(any(TaxReturn.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    taxReturnService.generateReturn(company, startDate, endDate, Basis.INVOICE, actor);

    // Then
    verify(auditService)
        .logEvent(
            eq(company),
            eq(actor),
            eq("TAX_RETURN_GENERATED"),
            eq("TaxReturn"),
            any(),
            contains("Invoice basis"));
  }

  @Test
  void generateReturn_cashBasis_logsCorrectBasisInAudit() {
    // Given
    when(receivableAllocationRepository.findByCompanyAndAllocatedAtRange(any(), any(), any()))
        .thenReturn(List.of());
    when(payableAllocationRepository.findByCompanyAndAllocatedAtRange(any(), any(), any()))
        .thenReturn(List.of());
    when(taxReturnRepository.save(any(TaxReturn.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    taxReturnService.generateReturn(company, startDate, endDate, Basis.CASH, actor);

    // Then
    verify(auditService)
        .logEvent(
            eq(company),
            eq(actor),
            eq("TAX_RETURN_GENERATED"),
            eq("TaxReturn"),
            any(),
            contains("Cash basis"));
  }

  // ==================== Helper Methods ====================

  private List<TaxLine> createSampleTaxLines() {
    List<TaxLine> taxLines = new ArrayList<>();

    // Sales line (negative taxable amount = credit to income)
    TaxLine salesLine = Mockito.mock(TaxLine.class, Mockito.withSettings().lenient());
    when(salesLine.getTaxableAmount()).thenReturn(new BigDecimal("-100.00"));
    when(salesLine.getTaxAmount()).thenReturn(new BigDecimal("-15.00"));
    when(salesLine.getTaxRate()).thenReturn(new BigDecimal("0.15"));
    taxLines.add(salesLine);

    // Zero-rated sales line
    TaxLine zeroRatedLine = Mockito.mock(TaxLine.class, Mockito.withSettings().lenient());
    when(zeroRatedLine.getTaxableAmount()).thenReturn(new BigDecimal("-50.00"));
    when(zeroRatedLine.getTaxAmount()).thenReturn(BigDecimal.ZERO);
    when(zeroRatedLine.getTaxRate()).thenReturn(BigDecimal.ZERO);
    taxLines.add(zeroRatedLine);

    // Purchase line (positive taxable amount = debit to expense)
    TaxLine purchaseLine = Mockito.mock(TaxLine.class, Mockito.withSettings().lenient());
    when(purchaseLine.getTaxableAmount()).thenReturn(new BigDecimal("80.00"));
    when(purchaseLine.getTaxAmount()).thenReturn(new BigDecimal("12.00"));
    when(purchaseLine.getTaxRate()).thenReturn(new BigDecimal("0.15"));
    taxLines.add(purchaseLine);

    return taxLines;
  }

  private List<ReceivableAllocation> createSampleReceivableAllocations() {
    SalesInvoice invoice = createInvoice(new BigDecimal("100.00"), new BigDecimal("15.00"));
    Transaction receipt = createReceiptTransaction();

    ReceivableAllocation allocation =
        new ReceivableAllocation(company, receipt, invoice, new BigDecimal("57.50")); // 50% payment
    allocation.setAllocatedAt(Instant.now());

    return List.of(allocation);
  }

  private List<PayableAllocation> createSamplePayableAllocations() {
    SupplierBill bill = createBill(new BigDecimal("80.00"), new BigDecimal("12.00"));
    Transaction payment = createPaymentTransaction();

    PayableAllocation allocation =
        new PayableAllocation(company, payment, bill, new BigDecimal("46.00")); // 50% payment
    allocation.setAllocatedAt(Instant.now());

    return List.of(allocation);
  }

  private SalesInvoice createInvoice(BigDecimal subtotal, BigDecimal taxTotal) {
    Contact customer =
        new Contact(company, "CUST01", "Test Customer", Contact.ContactType.CUSTOMER);
    SalesInvoice invoice = new SalesInvoice(company, "INV-001", customer, startDate, endDate);
    invoice.setSubtotal(subtotal);
    invoice.setTaxTotal(taxTotal);
    invoice.setTotal(subtotal.add(taxTotal));
    return invoice;
  }

  private SupplierBill createBill(BigDecimal subtotal, BigDecimal taxTotal) {
    Contact supplier = new Contact(company, "SUP01", "Test Supplier", Contact.ContactType.SUPPLIER);
    SupplierBill bill = new SupplierBill(company, "BILL-001", supplier, startDate, endDate);
    bill.setSubtotal(subtotal);
    bill.setTaxTotal(taxTotal);
    bill.setTotal(subtotal.add(taxTotal));
    return bill;
  }

  private Transaction createReceiptTransaction() {
    Transaction receipt = new Transaction(company, Transaction.TransactionType.RECEIPT, startDate);
    receipt.setId(1L);
    return receipt;
  }

  private Transaction createPaymentTransaction() {
    Transaction payment = new Transaction(company, Transaction.TransactionType.PAYMENT, startDate);
    payment.setId(2L);
    return payment;
  }
}
