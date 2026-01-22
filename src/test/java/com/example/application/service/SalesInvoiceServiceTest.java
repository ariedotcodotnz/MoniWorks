package com.example.application.service;

import com.example.application.domain.*;
import com.example.application.domain.SalesInvoice.InvoiceStatus;
import com.example.application.domain.SalesInvoice.InvoiceType;
import com.example.application.repository.SalesInvoiceLineRepository;
import com.example.application.repository.SalesInvoiceRepository;
import com.example.application.repository.TaxCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SalesInvoiceService, focusing on credit note functionality.
 */
@ExtendWith(MockitoExtension.class)
class SalesInvoiceServiceTest {

    @Mock
    private SalesInvoiceRepository invoiceRepository;

    @Mock
    private SalesInvoiceLineRepository lineRepository;

    @Mock
    private TaxCodeRepository taxCodeRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private TransactionService transactionService;

    @Mock
    private PostingService postingService;

    @Mock
    private AuditService auditService;

    private SalesInvoiceService invoiceService;

    private Company company;
    private Contact customer;
    private Account incomeAccount;
    private Account arAccount;
    private User user;
    private SalesInvoice issuedInvoice;

    @BeforeEach
    void setUp() {
        invoiceService = new SalesInvoiceService(
            invoiceRepository,
            lineRepository,
            taxCodeRepository,
            accountService,
            transactionService,
            postingService,
            auditService
        );

        company = new Company();
        company.setId(1L);
        company.setName("Test Company");

        customer = new Contact();
        customer.setId(1L);
        customer.setCode("CUST001");
        customer.setName("Test Customer");

        incomeAccount = new Account();
        incomeAccount.setId(1L);
        incomeAccount.setCode("4000");
        incomeAccount.setName("Sales Revenue");
        incomeAccount.setType(Account.AccountType.INCOME);

        arAccount = new Account();
        arAccount.setId(2L);
        arAccount.setCode("1200");
        arAccount.setName("Accounts Receivable");
        arAccount.setType(Account.AccountType.ASSET);

        user = new User("admin@test.com", "Admin User");
        user.setId(1L);

        // Create an issued invoice for credit note tests
        issuedInvoice = new SalesInvoice();
        issuedInvoice.setId(1L);
        issuedInvoice.setCompany(company);
        issuedInvoice.setInvoiceNumber("INV-0001");
        issuedInvoice.setContact(customer);
        issuedInvoice.setIssueDate(LocalDate.now().minusDays(30));
        issuedInvoice.setDueDate(LocalDate.now());
        issuedInvoice.setStatus(InvoiceStatus.ISSUED);
        issuedInvoice.setType(InvoiceType.INVOICE);
        issuedInvoice.setCurrency("AUD");

        // Add a line to the issued invoice
        SalesInvoiceLine line = new SalesInvoiceLine(incomeAccount, BigDecimal.valueOf(2), BigDecimal.valueOf(100));
        line.setId(1L);
        line.setDescription("Test Product");
        line.setTaxCode("GST");
        line.setTaxRate(BigDecimal.valueOf(10));
        line.calculateTotals();
        issuedInvoice.addLine(line);
        issuedInvoice.recalculateTotals();
    }

    @Test
    void createCreditNote_FullCredit_Success() {
        // Given
        when(invoiceRepository.existsByCompanyAndInvoiceNumber(company, "CN-INV-0001"))
            .thenReturn(false);
        when(invoiceRepository.save(any(SalesInvoice.class)))
            .thenAnswer(invocation -> {
                SalesInvoice cn = invocation.getArgument(0);
                cn.setId(2L);
                return cn;
            });

        // When
        SalesInvoice creditNote = invoiceService.createCreditNote(issuedInvoice, user, true);

        // Then
        assertNotNull(creditNote);
        assertEquals("CN-INV-0001", creditNote.getInvoiceNumber());
        assertEquals(InvoiceType.CREDIT_NOTE, creditNote.getType());
        assertEquals(InvoiceStatus.DRAFT, creditNote.getStatus());
        assertEquals(issuedInvoice, creditNote.getOriginalInvoice());
        assertEquals(customer, creditNote.getContact());
        assertEquals(LocalDate.now(), creditNote.getIssueDate());
        assertEquals(LocalDate.now(), creditNote.getDueDate());

        // Credit note should have copied lines
        assertEquals(1, creditNote.getLines().size());
        SalesInvoiceLine creditLine = creditNote.getLines().get(0);
        assertEquals("Test Product", creditLine.getDescription());
        assertEquals(BigDecimal.valueOf(2), creditLine.getQuantity());
        assertEquals(BigDecimal.valueOf(100), creditLine.getUnitPrice());

        verify(auditService).logEvent(eq(company), eq(user), eq("CREDIT_NOTE_CREATED"),
            eq("SalesInvoice"), anyLong(), anyString());
    }

    @Test
    void createCreditNote_PartialCredit_CreatesEmptyDraft() {
        // Given
        when(invoiceRepository.existsByCompanyAndInvoiceNumber(company, "CN-INV-0001"))
            .thenReturn(false);
        when(invoiceRepository.save(any(SalesInvoice.class)))
            .thenAnswer(invocation -> {
                SalesInvoice cn = invocation.getArgument(0);
                cn.setId(2L);
                return cn;
            });

        // When
        SalesInvoice creditNote = invoiceService.createCreditNote(issuedInvoice, user, false);

        // Then
        assertNotNull(creditNote);
        assertEquals(InvoiceType.CREDIT_NOTE, creditNote.getType());
        assertEquals(InvoiceStatus.DRAFT, creditNote.getStatus());
        assertTrue(creditNote.getLines().isEmpty()); // No lines copied for partial credit
    }

    @Test
    void createCreditNote_DraftInvoice_ThrowsException() {
        // Given
        issuedInvoice.setStatus(InvoiceStatus.DRAFT);

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> invoiceService.createCreditNote(issuedInvoice, user, true));
        assertEquals("Can only create credit notes against issued invoices", exception.getMessage());
    }

    @Test
    void createCreditNote_AgainstCreditNote_ThrowsException() {
        // Given
        issuedInvoice.setType(InvoiceType.CREDIT_NOTE);

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> invoiceService.createCreditNote(issuedInvoice, user, true));
        assertEquals("Cannot create a credit note against another credit note", exception.getMessage());
    }

    @Test
    void createCreditNote_SecondCreditNote_IncrementsSuffix() {
        // Given - One credit note already exists
        when(invoiceRepository.existsByCompanyAndInvoiceNumber(company, "CN-INV-0001"))
            .thenReturn(true);  // First number exists
        when(invoiceRepository.existsByCompanyAndInvoiceNumber(company, "CN-INV-0001-1"))
            .thenReturn(false); // Suffix 1 available
        when(invoiceRepository.save(any(SalesInvoice.class)))
            .thenAnswer(invocation -> {
                SalesInvoice cn = invocation.getArgument(0);
                cn.setId(3L);
                return cn;
            });

        // When
        SalesInvoice creditNote = invoiceService.createCreditNote(issuedInvoice, user, true);

        // Then
        assertEquals("CN-INV-0001-1", creditNote.getInvoiceNumber());
    }

    @Test
    void issueCreditNote_ValidCreditNote_PostsReversedEntries() {
        // Given
        SalesInvoice creditNote = new SalesInvoice();
        creditNote.setId(2L);
        creditNote.setCompany(company);
        creditNote.setInvoiceNumber("CN-INV-0001");
        creditNote.setContact(customer);
        creditNote.setIssueDate(LocalDate.now());
        creditNote.setDueDate(LocalDate.now());
        creditNote.setStatus(InvoiceStatus.DRAFT);
        creditNote.setType(InvoiceType.CREDIT_NOTE);
        creditNote.setOriginalInvoice(issuedInvoice);
        creditNote.setCurrency("AUD");

        // Add a line
        SalesInvoiceLine creditLine = new SalesInvoiceLine(incomeAccount, BigDecimal.ONE, BigDecimal.valueOf(100));
        creditLine.setDescription("Credit for product");
        creditLine.setTaxCode("GST");
        creditLine.setTaxRate(BigDecimal.valueOf(10));
        creditLine.calculateTotals();
        creditNote.addLine(creditLine);
        creditNote.recalculateTotals();

        when(accountService.findByCompanyAndCode(company, "1200")).thenReturn(Optional.of(arAccount));

        Account gstAccount = new Account();
        gstAccount.setId(3L);
        gstAccount.setCode("2200");
        gstAccount.setName("GST Collected");
        when(accountService.findByCompanyAndCode(company, "2200")).thenReturn(Optional.of(gstAccount));

        Transaction mockTransaction = new Transaction();
        mockTransaction.setId(1L);
        when(transactionService.createTransaction(any(), any(), any(), anyString(), any()))
            .thenReturn(mockTransaction);
        when(postingService.postTransaction(any(), any())).thenReturn(mockTransaction);
        when(invoiceRepository.save(any(SalesInvoice.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        SalesInvoice issued = invoiceService.issueCreditNote(creditNote, user);

        // Then
        assertEquals(InvoiceStatus.ISSUED, issued.getStatus());
        assertNotNull(issued.getIssuedAt());
        assertEquals(mockTransaction, issued.getPostedTransaction());

        // Verify reversed posting was created
        verify(transactionService).createTransaction(
            eq(company),
            eq(Transaction.TransactionType.JOURNAL),
            eq(creditNote.getIssueDate()),
            contains("Credit Note"),
            eq(user)
        );
    }

    @Test
    void issueCreditNote_ExceedsBalance_ThrowsException() {
        // Given
        issuedInvoice.setAmountPaid(BigDecimal.valueOf(180)); // Most of it paid already
        // Balance is now 220 - 180 = 40

        SalesInvoice creditNote = new SalesInvoice();
        creditNote.setId(2L);
        creditNote.setCompany(company);
        creditNote.setInvoiceNumber("CN-INV-0001");
        creditNote.setContact(customer);
        creditNote.setIssueDate(LocalDate.now());
        creditNote.setDueDate(LocalDate.now());
        creditNote.setStatus(InvoiceStatus.DRAFT);
        creditNote.setType(InvoiceType.CREDIT_NOTE);
        creditNote.setOriginalInvoice(issuedInvoice);

        // Add a line that would exceed the remaining balance
        SalesInvoiceLine creditLine = new SalesInvoiceLine(incomeAccount, BigDecimal.ONE, BigDecimal.valueOf(100));
        creditLine.setTaxCode("GST");
        creditLine.setTaxRate(BigDecimal.valueOf(10));
        creditLine.calculateTotals();
        creditNote.addLine(creditLine);
        creditNote.recalculateTotals(); // Total = 110 (exceeds 40 balance)

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> invoiceService.issueCreditNote(creditNote, user));
        assertTrue(exception.getMessage().contains("exceeds invoice remaining balance"));
    }

    @Test
    void issueCreditNote_NotCreditNote_ThrowsException() {
        // Given - Regular invoice, not a credit note
        SalesInvoice regularInvoice = new SalesInvoice();
        regularInvoice.setId(2L);
        regularInvoice.setType(InvoiceType.INVOICE);
        regularInvoice.setStatus(InvoiceStatus.DRAFT);

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> invoiceService.issueCreditNote(regularInvoice, user));
        assertEquals("This is not a credit note", exception.getMessage());
    }

    @Test
    void issueCreditNote_AlreadyIssued_ThrowsException() {
        // Given
        SalesInvoice creditNote = new SalesInvoice();
        creditNote.setId(2L);
        creditNote.setType(InvoiceType.CREDIT_NOTE);
        creditNote.setStatus(InvoiceStatus.ISSUED);

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> invoiceService.issueCreditNote(creditNote, user));
        assertEquals("Credit note is not in draft status", exception.getMessage());
    }

    @Test
    void issueCreditNote_NoLines_ThrowsException() {
        // Given
        SalesInvoice creditNote = new SalesInvoice();
        creditNote.setId(2L);
        creditNote.setType(InvoiceType.CREDIT_NOTE);
        creditNote.setStatus(InvoiceStatus.DRAFT);
        creditNote.setOriginalInvoice(issuedInvoice);
        // No lines added

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> invoiceService.issueCreditNote(creditNote, user));
        assertEquals("Credit note has no lines", exception.getMessage());
    }

    @Test
    void findCreditNotesForInvoice_ReturnsCreditNotes() {
        // Given
        SalesInvoice creditNote1 = new SalesInvoice();
        creditNote1.setId(2L);
        creditNote1.setInvoiceNumber("CN-INV-0001");
        creditNote1.setType(InvoiceType.CREDIT_NOTE);

        SalesInvoice creditNote2 = new SalesInvoice();
        creditNote2.setId(3L);
        creditNote2.setInvoiceNumber("CN-INV-0001-2");
        creditNote2.setType(InvoiceType.CREDIT_NOTE);

        when(invoiceRepository.findByOriginalInvoice(issuedInvoice))
            .thenReturn(List.of(creditNote1, creditNote2));

        // When
        List<SalesInvoice> creditNotes = invoiceService.findCreditNotesForInvoice(issuedInvoice);

        // Then
        assertEquals(2, creditNotes.size());
        assertEquals("CN-INV-0001", creditNotes.get(0).getInvoiceNumber());
        assertEquals("CN-INV-0001-2", creditNotes.get(1).getInvoiceNumber());
    }
}
