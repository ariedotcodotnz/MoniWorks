package com.example.application.ui.views;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.example.application.domain.Account;
import com.example.application.domain.Company;
import com.example.application.domain.RecurrenceExecutionLog;
import com.example.application.domain.SalesInvoice;
import com.example.application.domain.SupplierBill;
import com.example.application.repository.AccountRepository;
import com.example.application.repository.LedgerEntryRepository;
import com.example.application.repository.RecurrenceExecutionLogRepository;
import com.example.application.repository.SalesInvoiceRepository;
import com.example.application.repository.SupplierBillRepository;
import com.example.application.repository.TaxLineRepository;
import com.example.application.service.CompanyContextService;
import com.example.application.service.ReportingService;
import com.example.application.ui.MainLayout;
import com.example.application.ui.components.SparklineChart;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.PermitAll;

/**
 * Dashboard view showing key metrics and quick actions. Displays tiles for cash balance, income
 * trend, and GST due estimate.
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
  private final SalesInvoiceRepository salesInvoiceRepository;
  private final SupplierBillRepository supplierBillRepository;
  private final RecurrenceExecutionLogRepository recurrenceLogRepository;

  private final NumberFormat currencyFormat;
  private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy");

  public DashboardView(
      CompanyContextService companyContextService,
      AccountRepository accountRepository,
      LedgerEntryRepository ledgerEntryRepository,
      TaxLineRepository taxLineRepository,
      ReportingService reportingService,
      SalesInvoiceRepository salesInvoiceRepository,
      SupplierBillRepository supplierBillRepository,
      RecurrenceExecutionLogRepository recurrenceLogRepository) {
    this.companyContextService = companyContextService;
    this.accountRepository = accountRepository;
    this.ledgerEntryRepository = ledgerEntryRepository;
    this.taxLineRepository = taxLineRepository;
    this.reportingService = reportingService;
    this.salesInvoiceRepository = salesInvoiceRepository;
    this.supplierBillRepository = supplierBillRepository;
    this.recurrenceLogRepository = recurrenceLogRepository;

    // Set up currency format for NZ
    this.currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "NZ"));

    addClassName("dashboard-view");
    setPadding(true);
    setSpacing(true);

    H2 title = new H2("Dashboard");
    title.getStyle().set("margin-top", "0");

    Company company = companyContextService.getCurrentCompany();
    Paragraph welcome = new Paragraph("Welcome to MoniWorks - " + company.getName());
    welcome.getStyle().set("color", "var(--lumo-secondary-text-color)");

    // Create tiles container with flex wrap
    FlexLayout tilesContainer = new FlexLayout();
    tilesContainer.setFlexWrap(FlexLayout.FlexWrap.WRAP);
    tilesContainer
        .getStyle()
        .set("gap", "var(--lumo-space-l)")
        .set("margin-top", "var(--lumo-space-l)");

    // Add dashboard tiles
    tilesContainer.add(
        createCashBalanceTile(company),
        createThisMonthTile(company),
        createIncomeTrendTile(company),
        createGstDueTile(company),
        createOverdueReceivablesTile(company),
        createOverduePayablesTile(company),
        createFailedRecurrencesTile(company));

    add(title, welcome, tilesContainer);
  }

  /**
   * Creates the Cash Balance tile showing current bank account balances. Respects the user's
   * security level - restricted bank accounts are not shown.
   */
  private Div createCashBalanceTile(Company company) {
    Div tile = createTileBase("Cash Balance", "var(--lumo-primary-color)");
    VerticalLayout content = (VerticalLayout) tile.getComponentAt(1);

    // Filter bank accounts by user's security level
    int securityLevel = companyContextService.getCurrentSecurityLevel();
    List<Account> bankAccounts =
        accountRepository.findBankAccountsByCompanyWithSecurityLevel(company, securityLevel);
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
        accountBalance
            .getStyle()
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
        divider
            .getStyle()
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
        balance.getStyle().set("font-size", "var(--lumo-font-size-xl)").set("font-weight", "600");
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
   * Creates the This Month tile showing current month's income vs expenses. Respects the user's
   * security level - restricted accounts are not included.
   */
  private Div createThisMonthTile(Company company) {
    Div tile = createTileBase("This Month", "var(--lumo-success-color)");
    VerticalLayout content = (VerticalLayout) tile.getComponentAt(1);

    // Calculate current month's P&L, filtered by user's security level
    LocalDate today = LocalDate.now();
    LocalDate monthStart = today.withDayOfMonth(1);

    int securityLevel = companyContextService.getCurrentSecurityLevel();
    ReportingService.ProfitAndLoss pnl =
        reportingService.generateProfitAndLoss(company, monthStart, today, securityLevel);

    // Income row
    HorizontalLayout incomeRow =
        createMetricRow("Income", pnl.totalIncome(), "var(--lumo-success-color)");
    content.add(incomeRow);

    // Expenses row
    HorizontalLayout expenseRow =
        createMetricRow("Expenses", pnl.totalExpenses(), "var(--lumo-error-color)");
    content.add(expenseRow);

    // Divider
    Div divider = new Div();
    divider
        .getStyle()
        .set("border-top", "1px solid var(--lumo-contrast-10pct)")
        .set("margin", "var(--lumo-space-s) 0");
    content.add(divider);

    // Net profit/loss
    HorizontalLayout netRow = new HorizontalLayout();
    netRow.setWidthFull();
    netRow.setJustifyContentMode(JustifyContentMode.BETWEEN);
    netRow.setPadding(false);
    netRow.setSpacing(false);

    Span netLabel =
        new Span(pnl.netProfit().compareTo(BigDecimal.ZERO) >= 0 ? "Net Profit" : "Net Loss");
    netLabel.getStyle().set("font-weight", "600");

    Span netValue = new Span(formatCurrency(pnl.netProfit().abs()));
    netValue
        .getStyle()
        .set("font-weight", "600")
        .set("font-size", "var(--lumo-font-size-l)")
        .set(
            "color",
            pnl.netProfit().compareTo(BigDecimal.ZERO) >= 0
                ? "var(--lumo-success-color)"
                : "var(--lumo-error-color)");

    netRow.add(netLabel, netValue);
    content.add(netRow);

    // Period info
    Paragraph period = new Paragraph(monthStart.format(DateTimeFormatter.ofPattern("MMMM yyyy")));
    period
        .getStyle()
        .set("color", "var(--lumo-secondary-text-color)")
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("margin-top", "auto")
        .set("margin-bottom", "0");
    content.add(period);

    return tile;
  }

  /**
   * Creates the Income Trend tile showing income over the last 6 months as a sparkline chart.
   * Respects the user's security level - restricted accounts are not included.
   */
  private Div createIncomeTrendTile(Company company) {
    Div tile = createTileBase("Income Trend", "var(--lumo-primary-color)");
    VerticalLayout content = (VerticalLayout) tile.getComponentAt(1);

    LocalDate today = LocalDate.now();
    int securityLevel = companyContextService.getCurrentSecurityLevel();

    // Calculate income for the last 6 months
    List<SparklineChart.DataPoint> incomeData = new ArrayList<>();
    DateTimeFormatter monthFormat = DateTimeFormatter.ofPattern("MMM");

    BigDecimal previousIncome = null;
    BigDecimal currentIncome = null;

    for (int i = 5; i >= 0; i--) {
      LocalDate monthStart = today.minusMonths(i).withDayOfMonth(1);
      LocalDate monthEnd;
      if (i == 0) {
        // Current month - use today as end date
        monthEnd = today;
      } else {
        // Previous months - use last day of month
        monthEnd = monthStart.plusMonths(1).minusDays(1);
      }

      ReportingService.ProfitAndLoss pnl =
          reportingService.generateProfitAndLoss(company, monthStart, monthEnd, securityLevel);

      String label = monthStart.format(monthFormat);
      incomeData.add(new SparklineChart.DataPoint(label, pnl.totalIncome()));

      // Track for trend calculation
      if (i == 1) {
        previousIncome = pnl.totalIncome();
      } else if (i == 0) {
        currentIncome = pnl.totalIncome();
      }
    }

    // Create sparkline chart
    SparklineChart chart = new SparklineChart(incomeData);
    chart.setBarColor("var(--lumo-success-color)");
    chart.setShowLabels(true);
    chart.setShowValues(false);
    chart.setBarWidth(32);
    chart.setMaxHeight(60);
    chart.setNumberFormat(currencyFormat);

    content.add(chart);

    // Add trend indicator comparing current month to previous
    if (currentIncome != null && previousIncome != null) {
      HorizontalLayout trendRow = new HorizontalLayout();
      trendRow.setAlignItems(FlexComponent.Alignment.CENTER);
      trendRow.setSpacing(true);
      trendRow.getStyle().set("margin-top", "var(--lumo-space-s)");

      Span trendLabel = new Span("vs last month:");
      trendLabel.getStyle().set("color", "var(--lumo-secondary-text-color)");

      Span trendIndicator = SparklineChart.createTrendIndicator(currentIncome, previousIncome);

      trendRow.add(trendLabel, trendIndicator);
      content.add(trendRow);
    }

    // Period info
    Paragraph period = new Paragraph("Last 6 months");
    period
        .getStyle()
        .set("color", "var(--lumo-secondary-text-color)")
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("margin-top", "auto")
        .set("margin-bottom", "0");
    content.add(period);

    return tile;
  }

  /** Creates the GST Due Estimate tile showing estimated GST payable. */
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
    divider
        .getStyle()
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
    netValue
        .getStyle()
        .set("font-weight", "600")
        .set("font-size", "var(--lumo-font-size-l)")
        .set("color", isRefund ? "var(--lumo-success-color)" : "var(--lumo-warning-color)");

    netRow.add(netLabel, netValue);
    content.add(netRow);

    // Period info
    Paragraph period = new Paragraph("Current month estimate");
    period
        .getStyle()
        .set("color", "var(--lumo-secondary-text-color)")
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("margin-top", "auto")
        .set("margin-bottom", "0");
    content.add(period);

    return tile;
  }

  /** Creates the Overdue Receivables tile showing overdue customer invoices. */
  private Div createOverdueReceivablesTile(Company company) {
    Div tile = createTileBase("Overdue Receivables", "var(--lumo-error-color)");
    VerticalLayout content = (VerticalLayout) tile.getComponentAt(1);

    LocalDate today = LocalDate.now();
    List<SalesInvoice> overdueInvoices =
        salesInvoiceRepository.findOverdueByCompany(company, today);
    BigDecimal totalOverdue = salesInvoiceRepository.sumOverdueByCompany(company, today);

    if (overdueInvoices.isEmpty()) {
      Paragraph noOverdue = new Paragraph("No overdue invoices");
      noOverdue.getStyle().set("color", "var(--lumo-success-color)");
      content.add(noOverdue);
    } else {
      // Show count
      HorizontalLayout countRow =
          createMetricRow("Overdue invoices", BigDecimal.valueOf(overdueInvoices.size()), null);
      // Replace the formatted value with just the count
      Span countValue = (Span) countRow.getComponentAt(1);
      countValue.setText(String.valueOf(overdueInvoices.size()));
      content.add(countRow);

      // Show up to 3 oldest overdue invoices
      int displayCount = Math.min(3, overdueInvoices.size());
      for (int i = 0; i < displayCount; i++) {
        SalesInvoice invoice = overdueInvoices.get(i);
        HorizontalLayout invoiceRow = new HorizontalLayout();
        invoiceRow.setWidthFull();
        invoiceRow.setJustifyContentMode(JustifyContentMode.BETWEEN);
        invoiceRow.setPadding(false);
        invoiceRow.setSpacing(false);

        String contactName =
            invoice.getContact() != null
                ? invoice.getContact().getName()
                : invoice.getInvoiceNumber();
        if (contactName.length() > 20) {
          contactName = contactName.substring(0, 17) + "...";
        }

        Span nameSpan = new Span(contactName);
        nameSpan
            .getStyle()
            .set("font-size", "var(--lumo-font-size-xs)")
            .set("color", "var(--lumo-secondary-text-color)");

        Span amountSpan = new Span(formatCurrency(invoice.getBalance()));
        amountSpan
            .getStyle()
            .set("font-size", "var(--lumo-font-size-xs)")
            .set("color", "var(--lumo-error-color)");

        invoiceRow.add(nameSpan, amountSpan);
        content.add(invoiceRow);
      }

      if (overdueInvoices.size() > 3) {
        Paragraph more = new Paragraph("+" + (overdueInvoices.size() - 3) + " more...");
        more.getStyle()
            .set("font-size", "var(--lumo-font-size-xs)")
            .set("color", "var(--lumo-secondary-text-color)")
            .set("margin", "0");
        content.add(more);
      }

      // Divider
      Div divider = new Div();
      divider
          .getStyle()
          .set("border-top", "1px solid var(--lumo-contrast-10pct)")
          .set("margin", "var(--lumo-space-s) 0");
      content.add(divider);

      // Total overdue
      HorizontalLayout totalRow = new HorizontalLayout();
      totalRow.setWidthFull();
      totalRow.setJustifyContentMode(JustifyContentMode.BETWEEN);
      totalRow.setPadding(false);
      totalRow.setSpacing(false);

      Span totalLabel = new Span("Total Overdue");
      totalLabel.getStyle().set("font-weight", "600");

      Span totalValue = new Span(formatCurrency(totalOverdue));
      totalValue
          .getStyle()
          .set("font-weight", "600")
          .set("font-size", "var(--lumo-font-size-l)")
          .set("color", "var(--lumo-error-color)");

      totalRow.add(totalLabel, totalValue);
      content.add(totalRow);
    }

    // As of info
    Paragraph asOf = new Paragraph("As of " + today.format(dateFormatter));
    asOf.getStyle()
        .set("color", "var(--lumo-secondary-text-color)")
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("margin-top", "auto")
        .set("margin-bottom", "0");
    content.add(asOf);

    return tile;
  }

  /** Creates the Overdue Payables tile showing overdue supplier bills. */
  private Div createOverduePayablesTile(Company company) {
    Div tile = createTileBase("Overdue Payables", "var(--lumo-error-color)");
    VerticalLayout content = (VerticalLayout) tile.getComponentAt(1);

    LocalDate today = LocalDate.now();
    List<SupplierBill> overdueBills = supplierBillRepository.findOverdueByCompany(company, today);
    BigDecimal totalOverdue = supplierBillRepository.sumOverdueByCompany(company, today);

    if (overdueBills.isEmpty()) {
      Paragraph noOverdue = new Paragraph("No overdue bills");
      noOverdue.getStyle().set("color", "var(--lumo-success-color)");
      content.add(noOverdue);
    } else {
      // Show count
      HorizontalLayout countRow =
          createMetricRow("Overdue bills", BigDecimal.valueOf(overdueBills.size()), null);
      // Replace the formatted value with just the count
      Span countValue = (Span) countRow.getComponentAt(1);
      countValue.setText(String.valueOf(overdueBills.size()));
      content.add(countRow);

      // Show up to 3 oldest overdue bills
      int displayCount = Math.min(3, overdueBills.size());
      for (int i = 0; i < displayCount; i++) {
        SupplierBill bill = overdueBills.get(i);
        HorizontalLayout billRow = new HorizontalLayout();
        billRow.setWidthFull();
        billRow.setJustifyContentMode(JustifyContentMode.BETWEEN);
        billRow.setPadding(false);
        billRow.setSpacing(false);

        String contactName =
            bill.getContact() != null ? bill.getContact().getName() : bill.getBillNumber();
        if (contactName.length() > 20) {
          contactName = contactName.substring(0, 17) + "...";
        }

        Span nameSpan = new Span(contactName);
        nameSpan
            .getStyle()
            .set("font-size", "var(--lumo-font-size-xs)")
            .set("color", "var(--lumo-secondary-text-color)");

        Span amountSpan = new Span(formatCurrency(bill.getBalance()));
        amountSpan
            .getStyle()
            .set("font-size", "var(--lumo-font-size-xs)")
            .set("color", "var(--lumo-error-color)");

        billRow.add(nameSpan, amountSpan);
        content.add(billRow);
      }

      if (overdueBills.size() > 3) {
        Paragraph more = new Paragraph("+" + (overdueBills.size() - 3) + " more...");
        more.getStyle()
            .set("font-size", "var(--lumo-font-size-xs)")
            .set("color", "var(--lumo-secondary-text-color)")
            .set("margin", "0");
        content.add(more);
      }

      // Divider
      Div divider = new Div();
      divider
          .getStyle()
          .set("border-top", "1px solid var(--lumo-contrast-10pct)")
          .set("margin", "var(--lumo-space-s) 0");
      content.add(divider);

      // Total overdue
      HorizontalLayout totalRow = new HorizontalLayout();
      totalRow.setWidthFull();
      totalRow.setJustifyContentMode(JustifyContentMode.BETWEEN);
      totalRow.setPadding(false);
      totalRow.setSpacing(false);

      Span totalLabel = new Span("Total Overdue");
      totalLabel.getStyle().set("font-weight", "600");

      Span totalValue = new Span(formatCurrency(totalOverdue));
      totalValue
          .getStyle()
          .set("font-weight", "600")
          .set("font-size", "var(--lumo-font-size-l)")
          .set("color", "var(--lumo-error-color)");

      totalRow.add(totalLabel, totalValue);
      content.add(totalRow);
    }

    // As of info
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
   * Creates the Failed Recurrences tile showing recent recurring template failures. Displays
   * failures from the last 7 days to alert users of automation issues.
   */
  private Div createFailedRecurrencesTile(Company company) {
    Div tile = createTileBase("Failed Recurrences", "var(--lumo-error-color)");
    VerticalLayout content = (VerticalLayout) tile.getComponentAt(1);

    // Look back 7 days for failures
    Instant since = Instant.now().minus(7, ChronoUnit.DAYS);
    long failureCount = recurrenceLogRepository.countRecentFailures(company.getId(), since);

    if (failureCount == 0) {
      Paragraph noFailures = new Paragraph("No recent failures");
      noFailures.getStyle().set("color", "var(--lumo-success-color)");
      content.add(noFailures);

      Paragraph allGood = new Paragraph("All recurring templates are running successfully");
      allGood
          .getStyle()
          .set("color", "var(--lumo-secondary-text-color)")
          .set("font-size", "var(--lumo-font-size-xs)")
          .set("margin", "0");
      content.add(allGood);
    } else {
      // Show failure count with warning styling
      HorizontalLayout countRow = new HorizontalLayout();
      countRow.setWidthFull();
      countRow.setJustifyContentMode(JustifyContentMode.BETWEEN);
      countRow.setPadding(false);
      countRow.setSpacing(false);

      Span countLabel = new Span("Failed executions");
      countLabel.getStyle().set("font-size", "var(--lumo-font-size-s)");

      Span countValue = new Span(String.valueOf(failureCount));
      countValue
          .getStyle()
          .set("font-size", "var(--lumo-font-size-xl)")
          .set("font-weight", "600")
          .set("color", "var(--lumo-error-color)");

      countRow.add(countLabel, countValue);
      content.add(countRow);

      // Show up to 3 recent failures with template names
      List<RecurrenceExecutionLog> recentFailures =
          recurrenceLogRepository.findRecentByCompanyAndResult(
              company.getId(), RecurrenceExecutionLog.Result.FAILED, since);

      int displayCount = Math.min(3, recentFailures.size());
      for (int i = 0; i < displayCount; i++) {
        RecurrenceExecutionLog log = recentFailures.get(i);
        HorizontalLayout failureRow = new HorizontalLayout();
        failureRow.setWidthFull();
        failureRow.setJustifyContentMode(JustifyContentMode.BETWEEN);
        failureRow.setPadding(false);
        failureRow.setSpacing(false);

        String templateName =
            log.getTemplate() != null ? log.getTemplate().getName() : "Unknown Template";
        if (templateName.length() > 25) {
          templateName = templateName.substring(0, 22) + "...";
        }

        Span nameSpan = new Span(templateName);
        nameSpan
            .getStyle()
            .set("font-size", "var(--lumo-font-size-xs)")
            .set("color", "var(--lumo-secondary-text-color)");

        // Format the failure time
        LocalDate failedDate =
            log.getRunAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        Span dateSpan = new Span(failedDate.format(dateFormatter));
        dateSpan
            .getStyle()
            .set("font-size", "var(--lumo-font-size-xs)")
            .set("color", "var(--lumo-error-color)");

        failureRow.add(nameSpan, dateSpan);
        content.add(failureRow);
      }

      if (recentFailures.size() > 3) {
        Paragraph more = new Paragraph("+" + (recentFailures.size() - 3) + " more...");
        more.getStyle()
            .set("font-size", "var(--lumo-font-size-xs)")
            .set("color", "var(--lumo-secondary-text-color)")
            .set("margin", "0");
        content.add(more);
      }

      // Add note about checking recurring templates
      Div divider = new Div();
      divider
          .getStyle()
          .set("border-top", "1px solid var(--lumo-contrast-10pct)")
          .set("margin", "var(--lumo-space-s) 0");
      content.add(divider);

      Paragraph note = new Paragraph("Check Recurring Templates for details");
      note.getStyle()
          .set("color", "var(--lumo-secondary-text-color)")
          .set("font-size", "var(--lumo-font-size-xs)")
          .set("margin", "0");
      content.add(note);
    }

    // Period info
    Paragraph period = new Paragraph("Last 7 days");
    period
        .getStyle()
        .set("color", "var(--lumo-secondary-text-color)")
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("margin-top", "auto")
        .set("margin-bottom", "0");
    content.add(period);

    return tile;
  }

  /** Creates the base tile container with header. */
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
    accentBar
        .getStyle()
        .set("height", "4px")
        .set("background", accentColor)
        .set("border-radius", "2px")
        .set("margin-bottom", "var(--lumo-space-m)");

    H3 tileTitle = new H3(title);
    tileTitle
        .getStyle()
        .set("margin", "0 0 var(--lumo-space-m) 0")
        .set("font-size", "var(--lumo-font-size-m)")
        .set("color", "var(--lumo-secondary-text-color)");

    VerticalLayout content = new VerticalLayout();
    content.setPadding(false);
    content.setSpacing(false);
    content.getStyle().set("gap", "var(--lumo-space-xs)").set("flex", "1");

    tile.add(accentBar, content);

    // Add title as first item in content
    content.addComponentAsFirst(tileTitle);

    return tile;
  }

  /** Creates a metric row with label and value. */
  private HorizontalLayout createMetricRow(String label, BigDecimal value, String valueColor) {
    HorizontalLayout row = new HorizontalLayout();
    row.setWidthFull();
    row.setJustifyContentMode(JustifyContentMode.BETWEEN);
    row.setPadding(false);
    row.setSpacing(false);

    Span labelSpan = new Span(label);
    labelSpan.getStyle().set("font-size", "var(--lumo-font-size-s)");

    Span valueSpan = new Span(formatCurrency(value));
    valueSpan.getStyle().set("font-size", "var(--lumo-font-size-s)").set("font-weight", "500");

    if (valueColor != null) {
      valueSpan.getStyle().set("color", valueColor);
    }

    row.add(labelSpan, valueSpan);
    return row;
  }

  /** Formats a BigDecimal as currency. */
  private String formatCurrency(BigDecimal amount) {
    if (amount == null) amount = BigDecimal.ZERO;
    return currencyFormat.format(amount);
  }
}
