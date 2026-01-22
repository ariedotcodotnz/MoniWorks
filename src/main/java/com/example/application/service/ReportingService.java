package com.example.application.service;

import com.example.application.domain.*;
import com.example.application.repository.AccountRepository;
import com.example.application.repository.BudgetLineRepository;
import com.example.application.repository.LedgerEntryRepository;
import com.example.application.repository.PeriodRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Service for generating financial reports.
 * Produces Trial Balance, P&L, and Balance Sheet from ledger entries.
 */
@Service
@Transactional(readOnly = true)
public class ReportingService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final BudgetLineRepository budgetLineRepository;
    private final PeriodRepository periodRepository;

    public ReportingService(AccountRepository accountRepository,
                            LedgerEntryRepository ledgerEntryRepository,
                            BudgetLineRepository budgetLineRepository,
                            PeriodRepository periodRepository) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.budgetLineRepository = budgetLineRepository;
        this.periodRepository = periodRepository;
    }

    /**
     * Generates a Trial Balance for the given company and date range.
     * Returns a map of account to balance (debits positive, credits negative).
     */
    public TrialBalance generateTrialBalance(Company company, LocalDate startDate, LocalDate endDate) {
        List<Account> accounts = accountRepository.findByCompanyOrderByCode(company);
        List<TrialBalanceLine> lines = new ArrayList<>();

        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;

        for (Account account : accounts) {
            BigDecimal debits = ledgerEntryRepository.sumDebitsByAccountAsOf(account, endDate);
            BigDecimal credits = ledgerEntryRepository.sumCreditsByAccountAsOf(account, endDate);

            if (debits == null) debits = BigDecimal.ZERO;
            if (credits == null) credits = BigDecimal.ZERO;

            // Skip accounts with no activity
            if (debits.compareTo(BigDecimal.ZERO) == 0 &&
                credits.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            lines.add(new TrialBalanceLine(account, debits, credits));
            totalDebits = totalDebits.add(debits);
            totalCredits = totalCredits.add(credits);
        }

        return new TrialBalance(startDate, endDate, lines, totalDebits, totalCredits);
    }

    /**
     * Generates a Profit & Loss statement for the given date range.
     */
    public ProfitAndLoss generateProfitAndLoss(Company company, LocalDate startDate, LocalDate endDate) {
        List<Account> incomeAccounts = accountRepository.findByCompanyIdAndType(
            company.getId(), Account.AccountType.INCOME);
        List<Account> expenseAccounts = accountRepository.findByCompanyIdAndType(
            company.getId(), Account.AccountType.EXPENSE);

        List<ProfitAndLossLine> incomeLines = new ArrayList<>();
        List<ProfitAndLossLine> expenseLines = new ArrayList<>();

        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpenses = BigDecimal.ZERO;

        // Calculate income (credits - debits)
        for (Account account : incomeAccounts) {
            List<LedgerEntry> entries = ledgerEntryRepository.findByAccountAndDateRange(
                account, startDate, endDate);

            BigDecimal amount = BigDecimal.ZERO;
            for (LedgerEntry entry : entries) {
                amount = amount.add(entry.getAmountCr()).subtract(entry.getAmountDr());
            }

            if (amount.compareTo(BigDecimal.ZERO) != 0) {
                incomeLines.add(new ProfitAndLossLine(account, amount));
                totalIncome = totalIncome.add(amount);
            }
        }

        // Calculate expenses (debits - credits)
        for (Account account : expenseAccounts) {
            List<LedgerEntry> entries = ledgerEntryRepository.findByAccountAndDateRange(
                account, startDate, endDate);

            BigDecimal amount = BigDecimal.ZERO;
            for (LedgerEntry entry : entries) {
                amount = amount.add(entry.getAmountDr()).subtract(entry.getAmountCr());
            }

            if (amount.compareTo(BigDecimal.ZERO) != 0) {
                expenseLines.add(new ProfitAndLossLine(account, amount));
                totalExpenses = totalExpenses.add(amount);
            }
        }

        BigDecimal netProfit = totalIncome.subtract(totalExpenses);

        return new ProfitAndLoss(startDate, endDate, null, incomeLines, expenseLines,
            totalIncome, totalExpenses, netProfit);
    }

    /**
     * Generates a Profit & Loss statement for the given date range, filtered by department.
     * If department is null, all entries are included (same as non-filtered version).
     */
    public ProfitAndLoss generateProfitAndLoss(Company company, LocalDate startDate,
                                                LocalDate endDate, Department department) {
        if (department == null) {
            // Fall back to non-filtered version
            return generateProfitAndLoss(company, startDate, endDate);
        }

        List<Account> incomeAccounts = accountRepository.findByCompanyIdAndType(
            company.getId(), Account.AccountType.INCOME);
        List<Account> expenseAccounts = accountRepository.findByCompanyIdAndType(
            company.getId(), Account.AccountType.EXPENSE);

        List<ProfitAndLossLine> incomeLines = new ArrayList<>();
        List<ProfitAndLossLine> expenseLines = new ArrayList<>();

        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpenses = BigDecimal.ZERO;

        // Calculate income (credits - debits) for department
        for (Account account : incomeAccounts) {
            List<LedgerEntry> entries = ledgerEntryRepository.findByAccountAndDateRangeAndDepartment(
                account, startDate, endDate, department);

            BigDecimal amount = BigDecimal.ZERO;
            for (LedgerEntry entry : entries) {
                amount = amount.add(entry.getAmountCr()).subtract(entry.getAmountDr());
            }

            if (amount.compareTo(BigDecimal.ZERO) != 0) {
                incomeLines.add(new ProfitAndLossLine(account, amount));
                totalIncome = totalIncome.add(amount);
            }
        }

        // Calculate expenses (debits - credits) for department
        for (Account account : expenseAccounts) {
            List<LedgerEntry> entries = ledgerEntryRepository.findByAccountAndDateRangeAndDepartment(
                account, startDate, endDate, department);

            BigDecimal amount = BigDecimal.ZERO;
            for (LedgerEntry entry : entries) {
                amount = amount.add(entry.getAmountDr()).subtract(entry.getAmountCr());
            }

            if (amount.compareTo(BigDecimal.ZERO) != 0) {
                expenseLines.add(new ProfitAndLossLine(account, amount));
                totalExpenses = totalExpenses.add(amount);
            }
        }

        BigDecimal netProfit = totalIncome.subtract(totalExpenses);

        return new ProfitAndLoss(startDate, endDate, department, incomeLines, expenseLines,
            totalIncome, totalExpenses, netProfit);
    }

    /**
     * Generates a Budget vs Actual report for the given budget and date range.
     * Compares budgeted amounts against actual ledger entries.
     * If department is specified, filters both budget lines and actual entries by department.
     */
    public BudgetVsActual generateBudgetVsActual(Company company, Budget budget,
                                                   LocalDate startDate, LocalDate endDate,
                                                   Department department) {
        // Get all income and expense accounts that could have budget lines
        List<Account> incomeAccounts = accountRepository.findByCompanyIdAndType(
            company.getId(), Account.AccountType.INCOME);
        List<Account> expenseAccounts = accountRepository.findByCompanyIdAndType(
            company.getId(), Account.AccountType.EXPENSE);

        // Get periods that fall within the date range
        List<Period> periods = periodRepository.findByFiscalYearCompanyAndDateRangeOverlap(
            company, startDate, endDate);

        List<BudgetVsActualLine> lines = new ArrayList<>();

        // Process income accounts
        for (Account account : incomeAccounts) {
            BudgetVsActualLine line = calculateBudgetVsActualLine(
                account, budget, periods, startDate, endDate, department, false);
            if (line.budgetAmount().compareTo(BigDecimal.ZERO) != 0 ||
                line.actualAmount().compareTo(BigDecimal.ZERO) != 0) {
                lines.add(line);
            }
        }

        // Process expense accounts
        for (Account account : expenseAccounts) {
            BudgetVsActualLine line = calculateBudgetVsActualLine(
                account, budget, periods, startDate, endDate, department, true);
            if (line.budgetAmount().compareTo(BigDecimal.ZERO) != 0 ||
                line.actualAmount().compareTo(BigDecimal.ZERO) != 0) {
                lines.add(line);
            }
        }

        // Calculate totals
        BigDecimal totalBudget = lines.stream()
            .map(BudgetVsActualLine::budgetAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalActual = lines.stream()
            .map(BudgetVsActualLine::actualAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalVariance = totalActual.subtract(totalBudget);
        BigDecimal totalVariancePercent = totalBudget.compareTo(BigDecimal.ZERO) == 0 ?
            BigDecimal.ZERO :
            totalVariance.multiply(BigDecimal.valueOf(100))
                .divide(totalBudget, 2, RoundingMode.HALF_UP);

        return new BudgetVsActual(startDate, endDate, budget, department, lines,
            totalBudget, totalActual, totalVariance, totalVariancePercent);
    }

    private BudgetVsActualLine calculateBudgetVsActualLine(
            Account account, Budget budget, List<Period> periods,
            LocalDate startDate, LocalDate endDate, Department department,
            boolean isExpense) {

        BigDecimal budgetAmount = BigDecimal.ZERO;
        BigDecimal actualAmount = BigDecimal.ZERO;

        // Sum budget amounts for all periods in range
        for (Period period : periods) {
            List<BudgetLine> budgetLines;
            if (department != null) {
                budgetLineRepository.findByBudgetAndPeriodAndAccountAndDepartment(
                    budget, period, account, department)
                    .ifPresent(bl -> {});
                // Need to get budget lines for specific department
                budgetLines = budgetLineRepository.findByBudgetAndPeriod(budget, period)
                    .stream()
                    .filter(bl -> bl.getAccount().equals(account))
                    .filter(bl -> bl.getDepartment() != null && bl.getDepartment().equals(department))
                    .toList();
            } else {
                budgetLines = budgetLineRepository.findByBudgetAndPeriod(budget, period)
                    .stream()
                    .filter(bl -> bl.getAccount().equals(account))
                    .toList();
            }

            for (BudgetLine bl : budgetLines) {
                budgetAmount = budgetAmount.add(bl.getAmount());
            }
        }

        // Calculate actual amount from ledger entries
        if (department != null) {
            List<LedgerEntry> entries = ledgerEntryRepository.findByAccountAndDateRangeAndDepartment(
                account, startDate, endDate, department);
            for (LedgerEntry entry : entries) {
                if (isExpense) {
                    actualAmount = actualAmount.add(entry.getAmountDr()).subtract(entry.getAmountCr());
                } else {
                    actualAmount = actualAmount.add(entry.getAmountCr()).subtract(entry.getAmountDr());
                }
            }
        } else {
            List<LedgerEntry> entries = ledgerEntryRepository.findByAccountAndDateRange(
                account, startDate, endDate);
            for (LedgerEntry entry : entries) {
                if (isExpense) {
                    actualAmount = actualAmount.add(entry.getAmountDr()).subtract(entry.getAmountCr());
                } else {
                    actualAmount = actualAmount.add(entry.getAmountCr()).subtract(entry.getAmountDr());
                }
            }
        }

        BigDecimal variance = actualAmount.subtract(budgetAmount);
        BigDecimal variancePercent = budgetAmount.compareTo(BigDecimal.ZERO) == 0 ?
            BigDecimal.ZERO :
            variance.multiply(BigDecimal.valueOf(100))
                .divide(budgetAmount, 2, RoundingMode.HALF_UP);

        return new BudgetVsActualLine(account, budgetAmount, actualAmount, variance, variancePercent);
    }

    /**
     * Generates a Balance Sheet as of the given date.
     */
    public BalanceSheet generateBalanceSheet(Company company, LocalDate asOfDate) {
        List<Account> assetAccounts = accountRepository.findByCompanyIdAndType(
            company.getId(), Account.AccountType.ASSET);
        List<Account> liabilityAccounts = accountRepository.findByCompanyIdAndType(
            company.getId(), Account.AccountType.LIABILITY);
        List<Account> equityAccounts = accountRepository.findByCompanyIdAndType(
            company.getId(), Account.AccountType.EQUITY);

        List<BalanceSheetLine> assetLines = calculateBalances(assetAccounts, asOfDate, true);
        List<BalanceSheetLine> liabilityLines = calculateBalances(liabilityAccounts, asOfDate, false);
        List<BalanceSheetLine> equityLines = calculateBalances(equityAccounts, asOfDate, false);

        BigDecimal totalAssets = assetLines.stream()
            .map(BalanceSheetLine::balance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalLiabilities = liabilityLines.stream()
            .map(BalanceSheetLine::balance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalEquity = equityLines.stream()
            .map(BalanceSheetLine::balance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new BalanceSheet(asOfDate, assetLines, liabilityLines, equityLines,
            totalAssets, totalLiabilities, totalEquity);
    }

    private List<BalanceSheetLine> calculateBalances(List<Account> accounts,
                                                      LocalDate asOfDate,
                                                      boolean debitPositive) {
        List<BalanceSheetLine> lines = new ArrayList<>();

        for (Account account : accounts) {
            BigDecimal balance = ledgerEntryRepository.getBalanceByAccountAsOf(account, asOfDate);
            if (balance == null) balance = BigDecimal.ZERO;

            // For liability and equity, credit is positive
            if (!debitPositive) {
                balance = balance.negate();
            }

            if (balance.compareTo(BigDecimal.ZERO) != 0) {
                lines.add(new BalanceSheetLine(account, balance));
            }
        }

        return lines;
    }

    // Record classes for report data
    public record TrialBalance(
        LocalDate startDate,
        LocalDate endDate,
        List<TrialBalanceLine> lines,
        BigDecimal totalDebits,
        BigDecimal totalCredits
    ) {
        public boolean isBalanced() {
            return totalDebits.compareTo(totalCredits) == 0;
        }
    }

    public record TrialBalanceLine(Account account, BigDecimal debits, BigDecimal credits) {}

    public record ProfitAndLoss(
        LocalDate startDate,
        LocalDate endDate,
        Department department,  // null means all departments
        List<ProfitAndLossLine> incomeLines,
        List<ProfitAndLossLine> expenseLines,
        BigDecimal totalIncome,
        BigDecimal totalExpenses,
        BigDecimal netProfit
    ) {}

    public record ProfitAndLossLine(Account account, BigDecimal amount) {}

    public record BalanceSheet(
        LocalDate asOfDate,
        List<BalanceSheetLine> assets,
        List<BalanceSheetLine> liabilities,
        List<BalanceSheetLine> equity,
        BigDecimal totalAssets,
        BigDecimal totalLiabilities,
        BigDecimal totalEquity
    ) {
        public boolean isBalanced() {
            return totalAssets.compareTo(totalLiabilities.add(totalEquity)) == 0;
        }
    }

    public record BalanceSheetLine(Account account, BigDecimal balance) {}

    public record BudgetVsActual(
        LocalDate startDate,
        LocalDate endDate,
        Budget budget,
        Department department,  // null means all departments
        List<BudgetVsActualLine> lines,
        BigDecimal totalBudget,
        BigDecimal totalActual,
        BigDecimal totalVariance,
        BigDecimal totalVariancePercent
    ) {}

    public record BudgetVsActualLine(
        Account account,
        BigDecimal budgetAmount,
        BigDecimal actualAmount,
        BigDecimal variance,
        BigDecimal variancePercent
    ) {}
}
