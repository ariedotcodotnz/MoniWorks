package com.example.application.ui.views;

import com.example.application.domain.Account;
import com.example.application.domain.Company;
import com.example.application.repository.AccountRepository;
import com.example.application.repository.LedgerEntryRepository;
import com.example.application.repository.TaxLineRepository;
import com.example.application.service.CompanyContextService;
import com.example.application.service.ReportingService;
import com.example.application.ui.MainLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Dashboard view showing key metrics and quick actions.
 * Displays tiles for cash balance, income trend, and GST due estimate.
 */
@Route(value = "", layout = MainLayout.class)
@PageTitle("Dashboard | MoniWorks")
@PermitAll
public class DashboardView extends VerticalLayout {

    private final CompanyContextService companyContextService;
    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final TaxLineRepository taxLineRepository;
    private final ReportingService reportingService;

    private final NumberFormat currencyFormat;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy");

    public DashboardView(CompanyContextService companyContextService,
                         AccountRepository accountRepository,
                         LedgerEntryRepository ledgerEntryRepository,
                         TaxLineRepository taxLineRepository,
                         ReportingService reportingService) {
        this.companyContextService = companyContextService;
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.taxLineRepository = taxLineRepository;
        this.reportingService = reportingService;

        // Set up currency format for NZ
        this.currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "NZ"));

        addClassName("dashboard-view");
        setPadding(true);
        setSpacing(true);

        H2 title = new H2("Dashboard");
        title.getStyle().set("margin-top", "0");

        Company company = companyContextService.getCurrentCompany();
        Paragraph welcome = new Paragraph(
            "Welcome to MoniWorks - " + company.getName());
        welcome.getStyle().set("color", "var(--lumo-secondary-text-color)");

        // Create tiles container with flex wrap
        FlexLayout tilesContainer = new FlexLayout();
        tilesContainer.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        tilesContainer.getStyle()
            .set("gap", "var(--lumo-space-l)")
            .set("margin-top", "var(--lumo-space-l)");

        // Add dashboard tiles
        tilesContainer.add(
            createCashBalanceTile(company),
            createIncomeTrendTile(company),
            createGstDueTile(company)
        );

        add(title, welcome, tilesContainer);
    }

    /**
     * Creates the Cash Balance tile showing current bank account balances.
     */
    private Div createCashBalanceTile(Company company) {
        Div tile = createTileBase("Cash Balance", "var(--lumo-primary-color)");
        VerticalLayout content = (VerticalLayout) tile.getComponentAt(1);

        List<Account> bankAccounts = accountRepository.findBankAccountsByCompany(company);
        LocalDate today = LocalDate.now();

        if (bankAccounts.isEmpty()) {
            Paragraph noAccounts = new Paragraph("No bank accounts configured");
            noAccounts.getStyle().set("color", "var(--lumo-secondary-text-color)");
            content.add(noAccounts);
        } else {
            BigDecimal totalBalance = BigDecimal.ZERO;

            for (Account bankAccount : bankAccounts) {
                BigDecimal balance = ledgerEntryRepository.getBalanceByAccountAsOf(bankAccount, today);
                if (balance == null) balance = BigDecimal.ZERO;

                // Bank accounts are assets, so debit-positive is correct
                totalBalance = totalBalance.add(balance);

                HorizontalLayout accountRow = new HorizontalLayout();
                accountRow.setWidthFull();
                accountRow.setJustifyContentMode(JustifyContentMode.BETWEEN);
                accountRow.setPadding(false);
                accountRow.setSpacing(false);

                Span accountName = new Span(bankAccount.getName());
                accountName.getStyle().set("font-size", "var(--lumo-font-size-s)");

                Span accountBalance = new Span(formatCurrency(balance));
                accountBalance.getStyle()
                    .set("font-size", "var(--lumo-font-size-s)")
                    .set("font-weight", "500");

                if (balance.compareTo(BigDecimal.ZERO) < 0) {
                    accountBalance.getStyle().set("color", "var(--lumo-error-color)");
                }

                accountRow.add(accountName, accountBalance);
                content.add(accountRow);
            }

            // Add total if multiple accounts
            if (bankAccounts.size() > 1) {
                Div divider = new Div();
                divider.getStyle()
                    .set("border-top", "1px solid var(--lumo-contrast-10pct)")
                    .set("margin", "var(--lumo-space-s) 0");
                content.add(divider);

                HorizontalLayout totalRow = new HorizontalLayout();
                totalRow.setWidthFull();
                totalRow.setJustifyContentMode(JustifyContentMode.BETWEEN);
                totalRow.setPadding(false);
                totalRow.setSpacing(false);

                Span totalLabel = new Span("Total");
                totalLabel.getStyle().set("font-weight", "600");

                Span totalValue = new Span(formatCurrency(totalBalance));
                totalValue.getStyle().set("font-weight", "600");

                if (totalBalance.compareTo(BigDecimal.ZERO) < 0) {
                    totalValue.getStyle().set("color", "var(--lumo-error-color)");
                } else {
                    totalValue.getStyle().set("color", "var(--lumo-success-color)");
                }

                totalRow.add(totalLabel, totalValue);
                content.add(totalRow);
            } else if (bankAccounts.size() == 1) {
                // Single account - make balance prominent
                Span balance = (Span) ((HorizontalLayout) content.getComponentAt(0)).getComponentAt(1);
                balance.getStyle()
                    .set("font-size", "var(--lumo-font-size-xl)")
                    .set("font-weight", "600");
            }
        }

        Paragraph asOf = new Paragraph("As of " + today.format(dateFormatter));
        asOf.getStyle()
            .set("color", "var(--lumo-secondary-text-color)")
            .set("font-size", "var(--lumo-font-size-xs)")
            .set("margin-top", "auto")
            .set("margin-bottom", "0");
        content.add(asOf);

        return tile;
    }

    /**
     * Creates the Income Trend tile showing recent month's income vs expenses.
     */
    private Div createIncomeTrendTile(Company company) {
        Div tile = createTileBase("This Month", "var(--lumo-success-color)");
        VerticalLayout content = (VerticalLayout) tile.getComponentAt(1);

        // Calculate current month's P&L
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);

        ReportingService.ProfitAndLoss pnl = reportingService.generateProfitAndLoss(
            company, monthStart, today);

        // Income row
        HorizontalLayout incomeRow = createMetricRow("Income", pnl.totalIncome(), "var(--lumo-success-color)");
        content.add(incomeRow);

        // Expenses row
        HorizontalLayout expenseRow = createMetricRow("Expenses", pnl.totalExpenses(), "var(--lumo-error-color)");
        content.add(expenseRow);

        // Divider
        Div divider = new Div();
        divider.getStyle()
            .set("border-top", "1px solid var(--lumo-contrast-10pct)")
            .set("margin", "var(--lumo-space-s) 0");
        content.add(divider);

        // Net profit/loss
        HorizontalLayout netRow = new HorizontalLayout();
        netRow.setWidthFull();
        netRow.setJustifyContentMode(JustifyContentMode.BETWEEN);
        netRow.setPadding(false);
        netRow.setSpacing(false);

        Span netLabel = new Span(pnl.netProfit().compareTo(BigDecimal.ZERO) >= 0 ? "Net Profit" : "Net Loss");
        netLabel.getStyle().set("font-weight", "600");

        Span netValue = new Span(formatCurrency(pnl.netProfit().abs()));
        netValue.getStyle()
            .set("font-weight", "600")
            .set("font-size", "var(--lumo-font-size-l)")
            .set("color", pnl.netProfit().compareTo(BigDecimal.ZERO) >= 0
                ? "var(--lumo-success-color)"
                : "var(--lumo-error-color)");

        netRow.add(netLabel, netValue);
        content.add(netRow);

        // Period info
        Paragraph period = new Paragraph(monthStart.format(DateTimeFormatter.ofPattern("MMMM yyyy")));
        period.getStyle()
            .set("color", "var(--lumo-secondary-text-color)")
            .set("font-size", "var(--lumo-font-size-xs)")
            .set("margin-top", "auto")
            .set("margin-bottom", "0");
        content.add(period);

        return tile;
    }

    /**
     * Creates the GST Due Estimate tile showing estimated GST payable.
     */
    private Div createGstDueTile(Company company) {
        Div tile = createTileBase("GST Estimate", "var(--lumo-warning-color)");
        VerticalLayout content = (VerticalLayout) tile.getComponentAt(1);

        // Calculate current period GST (using current month as estimate period)
        LocalDate today = LocalDate.now();
        LocalDate periodStart = today.withDayOfMonth(1);

        // Get tax amounts from TaxLineRepository
        BigDecimal outputTax = BigDecimal.ZERO;
        BigDecimal inputTax = BigDecimal.ZERO;

        List<Object[]> taxByBox = taxLineRepository.sumByReportBox(company, periodStart, today);

        for (Object[] row : taxByBox) {
            String box = (String) row[0];
            BigDecimal taxAmount = row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO;

            if (box != null) {
                // NZ GST boxes: 9 = output tax (sales), 11 = input tax (purchases)
                if (box.equals("9") || box.contains("OUTPUT") || box.contains("SALES")) {
                    outputTax = outputTax.add(taxAmount.abs());
                } else if (box.equals("11") || box.contains("INPUT") || box.contains("PURCHASES")) {
                    inputTax = inputTax.add(taxAmount.abs());
                }
            }
        }

        BigDecimal gstPayable = outputTax.subtract(inputTax);

        // Output tax row
        HorizontalLayout outputRow = createMetricRow("GST on Sales", outputTax, null);
        content.add(outputRow);

        // Input tax row
        HorizontalLayout inputRow = createMetricRow("GST on Purchases", inputTax, null);
        content.add(inputRow);

        // Divider
        Div divider = new Div();
        divider.getStyle()
            .set("border-top", "1px solid var(--lumo-contrast-10pct)")
            .set("margin", "var(--lumo-space-s) 0");
        content.add(divider);

        // Net GST
        HorizontalLayout netRow = new HorizontalLayout();
        netRow.setWidthFull();
        netRow.setJustifyContentMode(JustifyContentMode.BETWEEN);
        netRow.setPadding(false);
        netRow.setSpacing(false);

        boolean isRefund = gstPayable.compareTo(BigDecimal.ZERO) < 0;
        Span netLabel = new Span(isRefund ? "GST Refund" : "GST Payable");
        netLabel.getStyle().set("font-weight", "600");

        Span netValue = new Span(formatCurrency(gstPayable.abs()));
        netValue.getStyle()
            .set("font-weight", "600")
            .set("font-size", "var(--lumo-font-size-l)")
            .set("color", isRefund ? "var(--lumo-success-color)" : "var(--lumo-warning-color)");

        netRow.add(netLabel, netValue);
        content.add(netRow);

        // Period info
        Paragraph period = new Paragraph("Current month estimate");
        period.getStyle()
            .set("color", "var(--lumo-secondary-text-color)")
            .set("font-size", "var(--lumo-font-size-xs)")
            .set("margin-top", "auto")
            .set("margin-bottom", "0");
        content.add(period);

        return tile;
    }

    /**
     * Creates the base tile container with header.
     */
    private Div createTileBase(String title, String accentColor) {
        Div tile = new Div();
        tile.addClassName("dashboard-tile");
        tile.getStyle()
            .set("background", "var(--lumo-base-color)")
            .set("border-radius", "var(--lumo-border-radius-l)")
            .set("box-shadow", "var(--lumo-box-shadow-s)")
            .set("padding", "var(--lumo-space-m)")
            .set("min-width", "280px")
            .set("max-width", "350px")
            .set("flex", "1 1 280px")
            .set("display", "flex")
            .set("flex-direction", "column");

        // Accent bar at top
        Div accentBar = new Div();
        accentBar.getStyle()
            .set("height", "4px")
            .set("background", accentColor)
            .set("border-radius", "2px")
            .set("margin-bottom", "var(--lumo-space-m)");

        H3 tileTitle = new H3(title);
        tileTitle.getStyle()
            .set("margin", "0 0 var(--lumo-space-m) 0")
            .set("font-size", "var(--lumo-font-size-m)")
            .set("color", "var(--lumo-secondary-text-color)");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(false);
        content.getStyle()
            .set("gap", "var(--lumo-space-xs)")
            .set("flex", "1");

        tile.add(accentBar, content);

        // Add title as first item in content
        content.addComponentAsFirst(tileTitle);

        return tile;
    }

    /**
     * Creates a metric row with label and value.
     */
    private HorizontalLayout createMetricRow(String label, BigDecimal value, String valueColor) {
        HorizontalLayout row = new HorizontalLayout();
        row.setWidthFull();
        row.setJustifyContentMode(JustifyContentMode.BETWEEN);
        row.setPadding(false);
        row.setSpacing(false);

        Span labelSpan = new Span(label);
        labelSpan.getStyle().set("font-size", "var(--lumo-font-size-s)");

        Span valueSpan = new Span(formatCurrency(value));
        valueSpan.getStyle()
            .set("font-size", "var(--lumo-font-size-s)")
            .set("font-weight", "500");

        if (valueColor != null) {
            valueSpan.getStyle().set("color", valueColor);
        }

        row.add(labelSpan, valueSpan);
        return row;
    }

    /**
     * Formats a BigDecimal as currency.
     */
    private String formatCurrency(BigDecimal amount) {
        if (amount == null) amount = BigDecimal.ZERO;
        return currencyFormat.format(amount);
    }
}
