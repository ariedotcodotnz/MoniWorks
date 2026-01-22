package com.example.application.service;

import com.example.application.domain.*;
import com.example.application.repository.*;
import com.example.application.service.BudgetImportService.ImportResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BudgetImportService CSV import functionality.
 */
@ExtendWith(MockitoExtension.class)
class BudgetImportServiceTest {

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private BudgetLineRepository budgetLineRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PeriodRepository periodRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private AuditService auditService;

    private BudgetImportService importService;

    private Company company;
    private User user;
    private Budget budget;
    private Account incomeAccount;
    private Period period;

    @BeforeEach
    void setUp() {
        importService = new BudgetImportService(
            budgetRepository,
            budgetLineRepository,
            accountRepository,
            periodRepository,
            departmentRepository,
            auditService
        );

        company = new Company();
        company.setId(1L);
        company.setName("Test Company");

        user = new User("admin@test.com", "Admin User");
        user.setId(1L);

        budget = new Budget(company, "FY2024 Budget", Budget.BudgetType.A);
        budget.setId(1L);

        incomeAccount = new Account();
        incomeAccount.setId(1L);
        incomeAccount.setCode("4000");
        incomeAccount.setName("Sales Revenue");
        incomeAccount.setType(Account.AccountType.INCOME);

        FiscalYear fiscalYear = new FiscalYear(company,
            LocalDate.of(2024, 7, 1), LocalDate.of(2025, 6, 30), "FY2024");
        fiscalYear.setId(1L);

        period = new Period();
        period.setId(1L);
        period.setFiscalYear(fiscalYear);
        period.setStartDate(LocalDate.of(2024, 7, 1));
        period.setEndDate(LocalDate.of(2024, 7, 31));
        period.setPeriodIndex(1);
    }

    @Test
    void importBudgetLines_ValidCsv_ImportsAllLines() throws IOException {
        // Given
        String csv = """
            account_code,period_date,amount
            4000,2024-07-15,50000
            4000,2024-08-15,55000
            """;

        Period period2 = new Period();
        period2.setId(2L);
        period2.setStartDate(LocalDate.of(2024, 8, 1));

        when(accountRepository.findByCompanyAndCode(company, "4000"))
            .thenReturn(Optional.of(incomeAccount));
        when(periodRepository.findByCompanyAndDate(company, LocalDate.of(2024, 7, 15)))
            .thenReturn(Optional.of(period));
        when(periodRepository.findByCompanyAndDate(company, LocalDate.of(2024, 8, 15)))
            .thenReturn(Optional.of(period2));
        when(budgetLineRepository.findByBudgetAndPeriodAndAccountAndDepartment(any(), any(), any(), any()))
            .thenReturn(Optional.empty());
        when(budgetLineRepository.save(any(BudgetLine.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ImportResult result = importService.importBudgetLines(
            toInputStream(csv), budget, user, false);

        // Then
        assertTrue(result.success());
        assertEquals(2, result.imported());
        assertEquals(0, result.updated());
        assertEquals(0, result.skipped());

        verify(budgetLineRepository, times(2)).save(any(BudgetLine.class));
        verify(auditService).logEvent(eq(company), eq(user), eq("BUDGET_IMPORTED"),
            eq("Budget"), eq(1L), contains("2 new"));
    }

    @Test
    void importBudgetLines_MissingAccountColumn_ReturnsError() throws IOException {
        // Given - CSV without required 'account_code' column
        String csv = """
            period_date,amount
            2024-07-01,50000
            """;

        // When
        ImportResult result = importService.importBudgetLines(
            toInputStream(csv), budget, user, false);

        // Then
        assertFalse(result.success());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("account")));
        verify(budgetLineRepository, never()).save(any());
    }

    @Test
    void importBudgetLines_MissingPeriodColumn_ReturnsError() throws IOException {
        // Given - CSV without required period column
        String csv = """
            account_code,amount
            4000,50000
            """;

        // When
        ImportResult result = importService.importBudgetLines(
            toInputStream(csv), budget, user, false);

        // Then
        assertFalse(result.success());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("period")));
    }

    @Test
    void importBudgetLines_MissingAmountColumn_ReturnsError() throws IOException {
        // Given - CSV without required amount column
        String csv = """
            account_code,period_date
            4000,2024-07-01
            """;

        // When
        ImportResult result = importService.importBudgetLines(
            toInputStream(csv), budget, user, false);

        // Then
        assertFalse(result.success());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("amount")));
    }

    @Test
    void importBudgetLines_AccountNotFound_ReportsError() throws IOException {
        // Given
        String csv = """
            account_code,period_date,amount
            9999,2024-07-01,50000
            """;

        when(accountRepository.findByCompanyAndCode(company, "9999"))
            .thenReturn(Optional.empty());

        // When
        ImportResult result = importService.importBudgetLines(
            toInputStream(csv), budget, user, false);

        // Then
        assertFalse(result.success());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Account '9999' not found")));
    }

    @Test
    void importBudgetLines_PeriodNotFound_ReportsError() throws IOException {
        // Given
        String csv = """
            account_code,period_date,amount
            4000,2030-01-01,50000
            """;

        when(accountRepository.findByCompanyAndCode(company, "4000"))
            .thenReturn(Optional.of(incomeAccount));
        when(periodRepository.findByCompanyAndDate(company, LocalDate.of(2030, 1, 1)))
            .thenReturn(Optional.empty());

        // When
        ImportResult result = importService.importBudgetLines(
            toInputStream(csv), budget, user, false);

        // Then
        assertFalse(result.success());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("No period found")));
    }

    @Test
    void importBudgetLines_InvalidDateFormat_ReportsError() throws IOException {
        // Given
        String csv = """
            account_code,period_date,amount
            4000,July 2024,50000
            """;

        when(accountRepository.findByCompanyAndCode(company, "4000"))
            .thenReturn(Optional.of(incomeAccount));

        // When
        ImportResult result = importService.importBudgetLines(
            toInputStream(csv), budget, user, false);

        // Then
        assertFalse(result.success());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Invalid date format")));
    }

    @Test
    void importBudgetLines_InvalidAmount_ReportsError() throws IOException {
        // Given
        String csv = """
            account_code,period_date,amount
            4000,2024-07-01,abc
            """;

        when(accountRepository.findByCompanyAndCode(company, "4000"))
            .thenReturn(Optional.of(incomeAccount));
        when(periodRepository.findByCompanyAndDate(company, LocalDate.of(2024, 7, 1)))
            .thenReturn(Optional.of(period));

        // When
        ImportResult result = importService.importBudgetLines(
            toInputStream(csv), budget, user, false);

        // Then
        assertFalse(result.success());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Invalid amount")));
    }

    @Test
    void importBudgetLines_ExistingLine_SkipsWithoutUpdate() throws IOException {
        // Given
        String csv = """
            account_code,period_date,amount
            4000,2024-07-01,50000
            """;

        BudgetLine existing = new BudgetLine(budget, period, incomeAccount, new BigDecimal("40000"));
        existing.setId(1L);

        when(accountRepository.findByCompanyAndCode(company, "4000"))
            .thenReturn(Optional.of(incomeAccount));
        when(periodRepository.findByCompanyAndDate(company, LocalDate.of(2024, 7, 1)))
            .thenReturn(Optional.of(period));
        when(budgetLineRepository.findByBudgetAndPeriodAndAccountAndDepartment(budget, period, incomeAccount, null))
            .thenReturn(Optional.of(existing));

        // When
        ImportResult result = importService.importBudgetLines(
            toInputStream(csv), budget, user, false); // updateExisting = false

        // Then
        assertTrue(result.success());
        assertEquals(0, result.imported());
        assertEquals(0, result.updated());
        assertEquals(1, result.skipped());
        verify(budgetLineRepository, never()).save(any());
    }

    @Test
    void importBudgetLines_ExistingLine_UpdatesWhenEnabled() throws IOException {
        // Given
        String csv = """
            account_code,period_date,amount
            4000,2024-07-01,50000
            """;

        BudgetLine existing = new BudgetLine(budget, period, incomeAccount, new BigDecimal("40000"));
        existing.setId(1L);

        when(accountRepository.findByCompanyAndCode(company, "4000"))
            .thenReturn(Optional.of(incomeAccount));
        when(periodRepository.findByCompanyAndDate(company, LocalDate.of(2024, 7, 1)))
            .thenReturn(Optional.of(period));
        when(budgetLineRepository.findByBudgetAndPeriodAndAccountAndDepartment(budget, period, incomeAccount, null))
            .thenReturn(Optional.of(existing));
        when(budgetLineRepository.save(any(BudgetLine.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ImportResult result = importService.importBudgetLines(
            toInputStream(csv), budget, user, true); // updateExisting = true

        // Then
        assertTrue(result.success());
        assertEquals(0, result.imported());
        assertEquals(1, result.updated());
        assertEquals(0, result.skipped());

        ArgumentCaptor<BudgetLine> lineCaptor = ArgumentCaptor.forClass(BudgetLine.class);
        verify(budgetLineRepository).save(lineCaptor.capture());
        assertEquals(new BigDecimal("50000"), lineCaptor.getValue().getAmount());
    }

    @Test
    void importBudgetLines_WithDepartment_ParsesCorrectly() throws IOException {
        // Given
        String csv = """
            account_code,period_date,amount,department_code
            4000,2024-07-01,50000,SALES
            """;

        Department salesDept = new Department();
        salesDept.setId(1L);
        salesDept.setCode("SALES");
        salesDept.setName("Sales Department");

        when(accountRepository.findByCompanyAndCode(company, "4000"))
            .thenReturn(Optional.of(incomeAccount));
        when(periodRepository.findByCompanyAndDate(company, LocalDate.of(2024, 7, 1)))
            .thenReturn(Optional.of(period));
        when(departmentRepository.findByCompanyAndCode(company, "SALES"))
            .thenReturn(Optional.of(salesDept));
        when(budgetLineRepository.findByBudgetAndPeriodAndAccountAndDepartment(budget, period, incomeAccount, salesDept))
            .thenReturn(Optional.empty());
        when(budgetLineRepository.save(any(BudgetLine.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ImportResult result = importService.importBudgetLines(
            toInputStream(csv), budget, user, false);

        // Then
        assertTrue(result.success());
        assertEquals(1, result.imported());

        ArgumentCaptor<BudgetLine> lineCaptor = ArgumentCaptor.forClass(BudgetLine.class);
        verify(budgetLineRepository).save(lineCaptor.capture());
        assertEquals(salesDept, lineCaptor.getValue().getDepartment());
    }

    @Test
    void importBudgetLines_DepartmentNotFound_ReportsError() throws IOException {
        // Given
        String csv = """
            account_code,period_date,amount,department_code
            4000,2024-07-01,50000,INVALID
            """;

        when(accountRepository.findByCompanyAndCode(company, "4000"))
            .thenReturn(Optional.of(incomeAccount));
        when(periodRepository.findByCompanyAndDate(company, LocalDate.of(2024, 7, 1)))
            .thenReturn(Optional.of(period));
        when(departmentRepository.findByCompanyAndCode(company, "INVALID"))
            .thenReturn(Optional.empty());

        // When
        ImportResult result = importService.importBudgetLines(
            toInputStream(csv), budget, user, false);

        // Then
        assertFalse(result.success());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Department 'INVALID' not found")));
    }

    @Test
    void importBudgetLines_MultipleDateFormats_ParsesCorrectly() throws IOException {
        // Given - Different date formats
        String csv = """
            account_code,period_date,amount
            4000,01/07/2024,50000
            """;

        when(accountRepository.findByCompanyAndCode(company, "4000"))
            .thenReturn(Optional.of(incomeAccount));
        when(periodRepository.findByCompanyAndDate(company, LocalDate.of(2024, 7, 1)))
            .thenReturn(Optional.of(period));
        when(budgetLineRepository.findByBudgetAndPeriodAndAccountAndDepartment(any(), any(), any(), any()))
            .thenReturn(Optional.empty());
        when(budgetLineRepository.save(any(BudgetLine.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ImportResult result = importService.importBudgetLines(
            toInputStream(csv), budget, user, false);

        // Then
        assertTrue(result.success());
        assertEquals(1, result.imported());
    }

    @Test
    void importBudgetLines_AmountWithCommasAndDollarSign_ParsesCorrectly() throws IOException {
        // Given
        String csv = """
            account_code,period_date,amount
            4000,2024-07-01,"$50,000"
            """;

        when(accountRepository.findByCompanyAndCode(company, "4000"))
            .thenReturn(Optional.of(incomeAccount));
        when(periodRepository.findByCompanyAndDate(company, LocalDate.of(2024, 7, 1)))
            .thenReturn(Optional.of(period));
        when(budgetLineRepository.findByBudgetAndPeriodAndAccountAndDepartment(any(), any(), any(), any()))
            .thenReturn(Optional.empty());
        when(budgetLineRepository.save(any(BudgetLine.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ImportResult result = importService.importBudgetLines(
            toInputStream(csv), budget, user, false);

        // Then
        assertTrue(result.success());
        assertEquals(1, result.imported());

        ArgumentCaptor<BudgetLine> lineCaptor = ArgumentCaptor.forClass(BudgetLine.class);
        verify(budgetLineRepository).save(lineCaptor.capture());
        assertEquals(new BigDecimal("50000"), lineCaptor.getValue().getAmount());
    }

    @Test
    void previewImport_DoesNotSaveAnything() throws IOException {
        // Given
        String csv = """
            account_code,period_date,amount
            4000,2024-07-01,50000
            """;

        when(accountRepository.findByCompanyAndCode(company, "4000"))
            .thenReturn(Optional.of(incomeAccount));
        when(periodRepository.findByCompanyAndDate(company, LocalDate.of(2024, 7, 1)))
            .thenReturn(Optional.of(period));
        when(budgetLineRepository.findByBudgetAndPeriodAndAccountAndDepartment(any(), any(), any(), any()))
            .thenReturn(Optional.empty());

        // When
        ImportResult result = importService.previewImport(
            toInputStream(csv), budget, false);

        // Then
        assertTrue(result.success());
        assertEquals(1, result.imported());

        // Preview should NOT save anything
        verify(budgetLineRepository, never()).save(any());
        verify(auditService, never()).logEvent(any(), any(), any(), any(), any(), any());
    }

    @Test
    void getSampleCsvContent_ReturnsValidFormat() {
        // When
        String sample = importService.getSampleCsvContent();

        // Then
        assertNotNull(sample);
        assertTrue(sample.contains("account_code"));
        assertTrue(sample.contains("period_date"));
        assertTrue(sample.contains("amount"));
        assertTrue(sample.contains("department_code"));
    }

    private InputStream toInputStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
