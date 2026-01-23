package com.example.application.ui.views;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.example.application.domain.*;
import com.example.application.repository.LedgerEntryRepository;
import com.example.application.repository.SalesInvoiceRepository;
import com.example.application.repository.SupplierBillRepository;
import com.example.application.service.*;
import com.example.application.service.AccountService;
import com.example.application.service.ReportingService.*;
import com.example.application.ui.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;

import jakarta.annotation.security.PermitAll;

/**
 * View for displaying financial reports. Provides Trial Balance, Profit & Loss, and Balance Sheet
 * reports with configurable date ranges.
 */
@Route(value = "reports", layout = MainLayout.class)
@PageTitle("Financial Reports | MoniWorks")
@PermitAll
public class ReportsView extends VerticalLayout {

  private final ReportingService reportingService;
  private final ReportExportService reportExportService;
  private final CompanyContextService companyContextService;
  private final FiscalYearService fiscalYearService;
  private final DepartmentService departmentService;
  private final BudgetService budgetService;
  private final AccountService accountService;
  private final TransactionService transactionService;
  private final LedgerEntryRepository ledgerEntryRepository;
  private final SalesInvoiceRepository salesInvoiceRepository;
  private final SupplierBillRepository supplierBillRepository;

  private final DatePicker startDatePicker = new DatePicker("Start Date");
  private final DatePicker endDatePicker = new DatePicker("End Date");
  private final DatePicker asOfDatePicker = new DatePicker("As Of Date");

  private final VerticalLayout trialBalanceContent = new VerticalLayout();
  private final VerticalLayout profitLossContent = new VerticalLayout();
  private final VerticalLayout balanceSheetContent = new VerticalLayout();
  private final VerticalLayout budgetVsActualContent = new VerticalLayout();
  private final VerticalLayout arAgingContent = new VerticalLayout();
  private final VerticalLayout apAgingContent = new VerticalLayout();
  private final VerticalLayout cashflowContent = new VerticalLayout();
  private final VerticalLayout bankRegisterContent = new VerticalLayout();
  private final VerticalLayout reconciliationStatusContent = new VerticalLayout();

  // Department filter for P&L
  private ComboBox<Department> plDepartmentFilter;

  // Budget vs Actual controls
  private ComboBox<Budget> bvaBudgetSelect;
  private ComboBox<Department> bvaDepartmentFilter;

  // Bank Register controls
  private ComboBox<Account> bankRegisterAccountSelect;

  // Export button containers to hold current download links
  private HorizontalLayout tbExportButtons;
  private HorizontalLayout plExportButtons;
  private HorizontalLayout bsExportButtons;
  private HorizontalLayout bvaExportButtons;
  private HorizontalLayout arAgingExportButtons;
  private HorizontalLayout apAgingExportButtons;
  private HorizontalLayout cashflowExportButtons;
  private HorizontalLayout bankRegisterExportButtons;
  private HorizontalLayout reconciliationStatusExportButtons;

  // Date pickers for aging reports
  private DatePicker arAgingAsOfDate;
  private DatePicker apAgingAsOfDate;

  // Current report data for exports
  private TrialBalance currentTrialBalance;
  private ProfitAndLoss currentProfitAndLoss;
  private BalanceSheet currentBalanceSheet;
  private BudgetVsActual currentBudgetVsActual;
  private ArAgingReport currentArAgingReport;
  private ApAgingReport currentApAgingReport;
  private CashflowStatement currentCashflowStatement;
  private BankRegister currentBankRegister;
  private ReconciliationStatus currentReconciliationStatus;

  private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0.00");
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

  public ReportsView(
      ReportingService reportingService,
      ReportExportService reportExportService,
      CompanyContextService companyContextService,
      FiscalYearService fiscalYearService,
      DepartmentService departmentService,
      BudgetService budgetService,
      AccountService accountService,
      TransactionService transactionService,
      LedgerEntryRepository ledgerEntryRepository,
      SalesInvoiceRepository salesInvoiceRepository,
      SupplierBillRepository supplierBillRepository) {
    this.reportingService = reportingService;
    this.reportExportService = reportExportService;
    this.companyContextService = companyContextService;
    this.fiscalYearService = fiscalYearService;
    this.departmentService = departmentService;
    this.budgetService = budgetService;
    this.accountService = accountService;
    this.transactionService = transactionService;
    this.ledgerEntryRepository = ledgerEntryRepository;
    this.salesInvoiceRepository = salesInvoiceRepository;
    this.supplierBillRepository = supplierBillRepository;

    addClassName("reports-view");
    setSizeFull();

    add(createHeader(), createTabSheet());

    initializeDateDefaults();
    initializeDepartmentAndBudgetFilters();
  }

  private HorizontalLayout createHeader() {
    H2 title = new H2("Financial Reports");

    HorizontalLayout header = new HorizontalLayout(title);
    header.setWidthFull();
    header.setAlignItems(FlexComponent.Alignment.CENTER);

    return header;
  }

  private void initializeDateDefaults() {
    Company company = companyContextService.getCurrentCompany();
    LocalDate today = LocalDate.now();

    // Try to use current fiscal year dates
    fiscalYearService
        .findByCompanyAndDate(company, today)
        .ifPresentOrElse(
            fiscalYear -> {
              startDatePicker.setValue(fiscalYear.getStartDate());
              endDatePicker.setValue(today);
              asOfDatePicker.setValue(today);
            },
            () -> {
              // Default to calendar year
              startDatePicker.setValue(today.withDayOfYear(1));
              endDatePicker.setValue(today);
              asOfDatePicker.setValue(today);
            });
  }

  private void initializeDepartmentAndBudgetFilters() {
    Company company = companyContextService.getCurrentCompany();

    // Load departments for filters
    List<Department> departments = departmentService.findActiveByCompany(company);

    if (plDepartmentFilter != null) {
      plDepartmentFilter.setItems(departments);
    }

    if (bvaDepartmentFilter != null) {
      bvaDepartmentFilter.setItems(departments);
    }

    // Load budgets for Budget vs Actual
    List<Budget> budgets = budgetService.findActiveByCompany(company);
    if (bvaBudgetSelect != null) {
      bvaBudgetSelect.setItems(budgets);
      if (!budgets.isEmpty()) {
        bvaBudgetSelect.setValue(budgets.get(0));
      }
    }
  }

  private TabSheet createTabSheet() {
    TabSheet tabSheet = new TabSheet();
    tabSheet.setSizeFull();

    // Trial Balance Tab
    Tab trialBalanceTab = new Tab(VaadinIcon.SCALE.create(), new Span("Trial Balance"));
    VerticalLayout trialBalanceLayout = createTrialBalanceTab();
    tabSheet.add(trialBalanceTab, trialBalanceLayout);

    // Profit & Loss Tab
    Tab profitLossTab = new Tab(VaadinIcon.TRENDING_UP.create(), new Span("Profit & Loss"));
    VerticalLayout profitLossLayout = createProfitLossTab();
    tabSheet.add(profitLossTab, profitLossLayout);

    // Balance Sheet Tab
    Tab balanceSheetTab = new Tab(VaadinIcon.PIE_CHART.create(), new Span("Balance Sheet"));
    VerticalLayout balanceSheetLayout = createBalanceSheetTab();
    tabSheet.add(balanceSheetTab, balanceSheetLayout);

    // Budget vs Actual Tab
    Tab budgetVsActualTab = new Tab(VaadinIcon.CHART.create(), new Span("Budget vs Actual"));
    VerticalLayout budgetVsActualLayout = createBudgetVsActualTab();
    tabSheet.add(budgetVsActualTab, budgetVsActualLayout);

    // AR Aging Tab
    Tab arAgingTab = new Tab(VaadinIcon.INVOICE.create(), new Span("AR Aging"));
    VerticalLayout arAgingLayout = createArAgingTab();
    tabSheet.add(arAgingTab, arAgingLayout);

    // AP Aging Tab
    Tab apAgingTab = new Tab(VaadinIcon.RECORDS.create(), new Span("AP Aging"));
    VerticalLayout apAgingLayout = createApAgingTab();
    tabSheet.add(apAgingTab, apAgingLayout);

    // Cashflow Tab
    Tab cashflowTab = new Tab(VaadinIcon.CASH.create(), new Span("Cashflow"));
    VerticalLayout cashflowLayout = createCashflowTab();
    tabSheet.add(cashflowTab, cashflowLayout);

    // Bank Register Tab
    Tab bankRegisterTab = new Tab(VaadinIcon.BOOK.create(), new Span("Bank Register"));
    VerticalLayout bankRegisterLayout = createBankRegisterTab();
    tabSheet.add(bankRegisterTab, bankRegisterLayout);

    // Reconciliation Status Tab
    Tab reconciliationStatusTab =
        new Tab(VaadinIcon.CHECK_CIRCLE.create(), new Span("Reconciliation Status"));
    VerticalLayout reconciliationStatusLayout = createReconciliationStatusTab();
    tabSheet.add(reconciliationStatusTab, reconciliationStatusLayout);

    return tabSheet;
  }

  private VerticalLayout createTrialBalanceTab() {
    VerticalLayout layout = new VerticalLayout();
    layout.setSizeFull();
    layout.setPadding(false);

    // Date range controls
    startDatePicker.setWidth("180px");
    endDatePicker.setWidth("180px");

    Button generateBtn = new Button("Generate Report", VaadinIcon.REFRESH.create());
    generateBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    generateBtn.addClickListener(e -> loadTrialBalance());

    // Export buttons container
    tbExportButtons = new HorizontalLayout();
    tbExportButtons.setSpacing(true);
    tbExportButtons.setVisible(false);

    HorizontalLayout controls =
        new HorizontalLayout(startDatePicker, endDatePicker, generateBtn, tbExportButtons);
    controls.setAlignItems(FlexComponent.Alignment.BASELINE);
    controls.setSpacing(true);

    trialBalanceContent.setSizeFull();
    trialBalanceContent.setPadding(false);

    layout.add(controls, trialBalanceContent);
    return layout;
  }

  private VerticalLayout createProfitLossTab() {
    VerticalLayout layout = new VerticalLayout();
    layout.setSizeFull();
    layout.setPadding(false);

    // Create separate date pickers for P&L (shares values with Trial Balance)
    DatePicker plStartDate = new DatePicker("Start Date");
    DatePicker plEndDate = new DatePicker("End Date");
    plStartDate.setWidth("180px");
    plEndDate.setWidth("180px");

    // Department filter
    plDepartmentFilter = new ComboBox<>("Department");
    plDepartmentFilter.setWidth("200px");
    plDepartmentFilter.setPlaceholder("All Departments");
    plDepartmentFilter.setClearButtonVisible(true);
    plDepartmentFilter.setItemLabelGenerator(d -> d.getCode() + " - " + d.getName());

    // Sync with main date pickers
    startDatePicker.addValueChangeListener(e -> plStartDate.setValue(e.getValue()));
    endDatePicker.addValueChangeListener(e -> plEndDate.setValue(e.getValue()));
    plStartDate.addValueChangeListener(e -> startDatePicker.setValue(e.getValue()));
    plEndDate.addValueChangeListener(e -> endDatePicker.setValue(e.getValue()));

    Button generateBtn = new Button("Generate Report", VaadinIcon.REFRESH.create());
    generateBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    generateBtn.addClickListener(e -> loadProfitAndLoss());

    // Export buttons container
    plExportButtons = new HorizontalLayout();
    plExportButtons.setSpacing(true);
    plExportButtons.setVisible(false);

    HorizontalLayout controls =
        new HorizontalLayout(
            plStartDate, plEndDate, plDepartmentFilter, generateBtn, plExportButtons);
    controls.setAlignItems(FlexComponent.Alignment.BASELINE);
    controls.setSpacing(true);

    profitLossContent.setSizeFull();
    profitLossContent.setPadding(false);

    layout.add(controls, profitLossContent);
    return layout;
  }

  private VerticalLayout createBalanceSheetTab() {
    VerticalLayout layout = new VerticalLayout();
    layout.setSizeFull();
    layout.setPadding(false);

    asOfDatePicker.setWidth("180px");

    Button generateBtn = new Button("Generate Report", VaadinIcon.REFRESH.create());
    generateBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    generateBtn.addClickListener(e -> loadBalanceSheet());

    // Export buttons container
    bsExportButtons = new HorizontalLayout();
    bsExportButtons.setSpacing(true);
    bsExportButtons.setVisible(false);

    HorizontalLayout controls = new HorizontalLayout(asOfDatePicker, generateBtn, bsExportButtons);
    controls.setAlignItems(FlexComponent.Alignment.BASELINE);
    controls.setSpacing(true);

    balanceSheetContent.setSizeFull();
    balanceSheetContent.setPadding(false);

    layout.add(controls, balanceSheetContent);
    return layout;
  }

  private void loadTrialBalance() {
    if (startDatePicker.isEmpty() || endDatePicker.isEmpty()) {
      Notification.show("Please select a date range", 3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
      return;
    }

    try {
      Company company = companyContextService.getCurrentCompany();
      TrialBalance report =
          reportingService.generateTrialBalance(
              company, startDatePicker.getValue(), endDatePicker.getValue());

      displayTrialBalance(report);
    } catch (Exception e) {
      Notification.show(
              "Error generating report: " + e.getMessage(), 3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
  }

  private void displayTrialBalance(TrialBalance report) {
    trialBalanceContent.removeAll();
    currentTrialBalance = report;
    updateTrialBalanceExportButtons();

    // Report header
    H3 header = new H3("Trial Balance");
    Span dateRange =
        new Span(
            "Period: "
                + report.startDate().format(DATE_FORMAT)
                + " to "
                + report.endDate().format(DATE_FORMAT));
    dateRange.getStyle().set("color", "var(--lumo-secondary-text-color)");

    // Balance status indicator
    Span balanceStatus = new Span(report.isBalanced() ? "BALANCED" : "OUT OF BALANCE");
    balanceStatus.getStyle().set("font-weight", "bold");
    balanceStatus.getStyle().set("padding", "4px 8px");
    balanceStatus.getStyle().set("border-radius", "4px");
    if (report.isBalanced()) {
      balanceStatus.getStyle().set("background-color", "var(--lumo-success-color-10pct)");
      balanceStatus.getStyle().set("color", "var(--lumo-success-text-color)");
    } else {
      balanceStatus.getStyle().set("background-color", "var(--lumo-error-color-10pct)");
      balanceStatus.getStyle().set("color", "var(--lumo-error-text-color)");
    }

    HorizontalLayout headerRow = new HorizontalLayout(header, balanceStatus);
    headerRow.setAlignItems(FlexComponent.Alignment.CENTER);

    // Grid for trial balance lines
    Grid<TrialBalanceLine> grid = new Grid<>();
    grid.setSizeFull();
    grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

    grid.addColumn(line -> line.account().getCode())
        .setHeader("Code")
        .setAutoWidth(true)
        .setFlexGrow(0);

    grid.addColumn(line -> line.account().getName()).setHeader("Account").setFlexGrow(1);

    grid.addColumn(line -> formatMoney(line.debits()))
        .setHeader("Debits")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    grid.addColumn(line -> formatMoney(line.credits()))
        .setHeader("Credits")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    grid.setItems(report.lines());

    // Enable drilldown on row click
    grid.addItemClickListener(
        event -> {
          TrialBalanceLine line = event.getItem();
          openLedgerDrilldownDialog(line.account(), report.startDate(), report.endDate(), null);
        });
    grid.getStyle().set("cursor", "pointer");

    // Totals row
    HorizontalLayout totalsRow = new HorizontalLayout();
    totalsRow.setWidthFull();
    totalsRow.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
    totalsRow.getStyle().set("font-weight", "bold");
    totalsRow.getStyle().set("padding", "8px");
    totalsRow.getStyle().set("border-top", "2px solid var(--lumo-contrast-20pct)");

    Span totalLabel = new Span("Totals:");
    Span totalDebits = new Span(formatMoney(report.totalDebits()));
    Span totalCredits = new Span(formatMoney(report.totalCredits()));

    totalDebits.getStyle().set("width", "120px").set("text-align", "right");
    totalCredits.getStyle().set("width", "120px").set("text-align", "right");

    totalsRow.add(totalLabel, totalDebits, totalCredits);

    trialBalanceContent.add(headerRow, dateRange, grid, totalsRow);
  }

  private void loadProfitAndLoss() {
    if (startDatePicker.isEmpty() || endDatePicker.isEmpty()) {
      Notification.show("Please select a date range", 3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
      return;
    }

    try {
      Company company = companyContextService.getCurrentCompany();
      Department department = plDepartmentFilter.getValue(); // null means all departments

      ProfitAndLoss report =
          reportingService.generateProfitAndLoss(
              company, startDatePicker.getValue(), endDatePicker.getValue(), department);

      displayProfitAndLoss(report);
    } catch (Exception e) {
      Notification.show(
              "Error generating report: " + e.getMessage(), 3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
  }

  private void displayProfitAndLoss(ProfitAndLoss report) {
    profitLossContent.removeAll();
    currentProfitAndLoss = report;
    updateProfitAndLossExportButtons();

    // Report header
    H3 header = new H3("Profit & Loss Statement");
    String dateRangeText =
        "Period: "
            + report.startDate().format(DATE_FORMAT)
            + " to "
            + report.endDate().format(DATE_FORMAT);
    if (report.department() != null) {
      dateRangeText +=
          " | Department: " + report.department().getCode() + " - " + report.department().getName();
    }
    Span dateRange = new Span(dateRangeText);
    dateRange.getStyle().set("color", "var(--lumo-secondary-text-color)");

    // Income section
    H3 incomeHeader = new H3("Income");
    incomeHeader.getStyle().set("margin-top", "16px");

    Grid<ProfitAndLossLine> incomeGrid = createPLGrid();
    incomeGrid.setItems(report.incomeLines());
    incomeGrid.setAllRowsVisible(true);
    // Enable drilldown on row click
    incomeGrid.addItemClickListener(
        event -> {
          ProfitAndLossLine line = event.getItem();
          openLedgerDrilldownDialog(
              line.account(), report.startDate(), report.endDate(), report.department());
        });
    incomeGrid.getStyle().set("cursor", "pointer");

    Span totalIncome = createTotalLine("Total Income", report.totalIncome());

    // Expenses section
    H3 expenseHeader = new H3("Expenses");
    expenseHeader.getStyle().set("margin-top", "16px");

    Grid<ProfitAndLossLine> expenseGrid = createPLGrid();
    expenseGrid.setItems(report.expenseLines());
    expenseGrid.setAllRowsVisible(true);
    // Enable drilldown on row click
    expenseGrid.addItemClickListener(
        event -> {
          ProfitAndLossLine line = event.getItem();
          openLedgerDrilldownDialog(
              line.account(), report.startDate(), report.endDate(), report.department());
        });
    expenseGrid.getStyle().set("cursor", "pointer");

    Span totalExpenses = createTotalLine("Total Expenses", report.totalExpenses());

    // Net profit
    HorizontalLayout netProfitRow = new HorizontalLayout();
    netProfitRow.setWidthFull();
    netProfitRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
    netProfitRow
        .getStyle()
        .set("font-weight", "bold")
        .set("font-size", "var(--lumo-font-size-l)")
        .set("padding", "12px")
        .set("margin-top", "16px")
        .set("border-top", "3px solid var(--lumo-contrast-30pct)")
        .set("background-color", "var(--lumo-contrast-5pct)");

    Span netProfitLabel =
        new Span(report.netProfit().compareTo(BigDecimal.ZERO) >= 0 ? "Net Profit" : "Net Loss");
    Span netProfitAmount = new Span(formatMoney(report.netProfit().abs()));

    if (report.netProfit().compareTo(BigDecimal.ZERO) >= 0) {
      netProfitAmount.getStyle().set("color", "var(--lumo-success-text-color)");
    } else {
      netProfitAmount.getStyle().set("color", "var(--lumo-error-text-color)");
    }

    netProfitRow.add(netProfitLabel, netProfitAmount);

    profitLossContent.add(
        header,
        dateRange,
        incomeHeader,
        incomeGrid,
        totalIncome,
        expenseHeader,
        expenseGrid,
        totalExpenses,
        netProfitRow);
  }

  private Grid<ProfitAndLossLine> createPLGrid() {
    Grid<ProfitAndLossLine> grid = new Grid<>();
    grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

    grid.addColumn(line -> line.account().getCode())
        .setHeader("Code")
        .setAutoWidth(true)
        .setFlexGrow(0);

    grid.addColumn(line -> line.account().getName()).setHeader("Account").setFlexGrow(1);

    grid.addColumn(line -> formatMoney(line.amount()))
        .setHeader("Amount")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    return grid;
  }

  private Span createTotalLine(String label, BigDecimal amount) {
    Span span = new Span(label + ": " + formatMoney(amount));
    span.getStyle()
        .set("font-weight", "bold")
        .set("display", "block")
        .set("text-align", "right")
        .set("padding", "8px")
        .set("border-top", "1px solid var(--lumo-contrast-20pct)");
    return span;
  }

  private void loadBalanceSheet() {
    if (asOfDatePicker.isEmpty()) {
      Notification.show("Please select a date", 3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
      return;
    }

    try {
      Company company = companyContextService.getCurrentCompany();
      BalanceSheet report =
          reportingService.generateBalanceSheet(company, asOfDatePicker.getValue());

      displayBalanceSheet(report);
    } catch (Exception e) {
      Notification.show(
              "Error generating report: " + e.getMessage(), 3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
  }

  private void displayBalanceSheet(BalanceSheet report) {
    balanceSheetContent.removeAll();
    currentBalanceSheet = report;
    updateBalanceSheetExportButtons();

    // Report header
    H3 header = new H3("Balance Sheet");
    Span asOfDate = new Span("As of " + report.asOfDate().format(DATE_FORMAT));
    asOfDate.getStyle().set("color", "var(--lumo-secondary-text-color)");

    // Balance status indicator
    Span balanceStatus = new Span(report.isBalanced() ? "BALANCED" : "OUT OF BALANCE");
    balanceStatus.getStyle().set("font-weight", "bold");
    balanceStatus.getStyle().set("padding", "4px 8px");
    balanceStatus.getStyle().set("border-radius", "4px");
    if (report.isBalanced()) {
      balanceStatus.getStyle().set("background-color", "var(--lumo-success-color-10pct)");
      balanceStatus.getStyle().set("color", "var(--lumo-success-text-color)");
    } else {
      balanceStatus.getStyle().set("background-color", "var(--lumo-error-color-10pct)");
      balanceStatus.getStyle().set("color", "var(--lumo-error-text-color)");
    }

    HorizontalLayout headerRow = new HorizontalLayout(header, balanceStatus);
    headerRow.setAlignItems(FlexComponent.Alignment.CENTER);

    // Assets section
    H3 assetsHeader = new H3("Assets");
    assetsHeader.getStyle().set("margin-top", "16px");

    Grid<BalanceSheetLine> assetsGrid = createBSGrid();
    assetsGrid.setItems(report.assets());
    assetsGrid.setAllRowsVisible(true);
    // Enable drilldown on row click - Balance sheet shows all entries up to asOfDate
    assetsGrid.addItemClickListener(
        event -> {
          BalanceSheetLine line = event.getItem();
          openLedgerDrilldownDialog(
              line.account(),
              LocalDate.of(1900, 1, 1), // Show all entries from the beginning
              report.asOfDate(),
              null);
        });
    assetsGrid.getStyle().set("cursor", "pointer");

    Span totalAssets = createTotalLine("Total Assets", report.totalAssets());

    // Liabilities section
    H3 liabilitiesHeader = new H3("Liabilities");
    liabilitiesHeader.getStyle().set("margin-top", "16px");

    Grid<BalanceSheetLine> liabilitiesGrid = createBSGrid();
    liabilitiesGrid.setItems(report.liabilities());
    liabilitiesGrid.setAllRowsVisible(true);
    // Enable drilldown on row click
    liabilitiesGrid.addItemClickListener(
        event -> {
          BalanceSheetLine line = event.getItem();
          openLedgerDrilldownDialog(
              line.account(), LocalDate.of(1900, 1, 1), report.asOfDate(), null);
        });
    liabilitiesGrid.getStyle().set("cursor", "pointer");

    Span totalLiabilities = createTotalLine("Total Liabilities", report.totalLiabilities());

    // Equity section
    H3 equityHeader = new H3("Equity");
    equityHeader.getStyle().set("margin-top", "16px");

    Grid<BalanceSheetLine> equityGrid = createBSGrid();
    equityGrid.setItems(report.equity());
    equityGrid.setAllRowsVisible(true);
    // Enable drilldown on row click
    equityGrid.addItemClickListener(
        event -> {
          BalanceSheetLine line = event.getItem();
          openLedgerDrilldownDialog(
              line.account(), LocalDate.of(1900, 1, 1), report.asOfDate(), null);
        });
    equityGrid.getStyle().set("cursor", "pointer");

    Span totalEquity = createTotalLine("Total Equity", report.totalEquity());

    // Total Liabilities + Equity
    BigDecimal totalLiabilitiesAndEquity = report.totalLiabilities().add(report.totalEquity());
    HorizontalLayout totalRow = new HorizontalLayout();
    totalRow.setWidthFull();
    totalRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
    totalRow
        .getStyle()
        .set("font-weight", "bold")
        .set("font-size", "var(--lumo-font-size-l)")
        .set("padding", "12px")
        .set("margin-top", "16px")
        .set("border-top", "3px solid var(--lumo-contrast-30pct)")
        .set("background-color", "var(--lumo-contrast-5pct)");

    Span totalLabel = new Span("Total Liabilities + Equity");
    Span totalAmount = new Span(formatMoney(totalLiabilitiesAndEquity));
    totalRow.add(totalLabel, totalAmount);

    balanceSheetContent.add(
        headerRow,
        asOfDate,
        assetsHeader,
        assetsGrid,
        totalAssets,
        liabilitiesHeader,
        liabilitiesGrid,
        totalLiabilities,
        equityHeader,
        equityGrid,
        totalEquity,
        totalRow);
  }

  private Grid<BalanceSheetLine> createBSGrid() {
    Grid<BalanceSheetLine> grid = new Grid<>();
    grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

    grid.addColumn(line -> line.account().getCode())
        .setHeader("Code")
        .setAutoWidth(true)
        .setFlexGrow(0);

    grid.addColumn(line -> line.account().getName()).setHeader("Account").setFlexGrow(1);

    grid.addColumn(line -> formatMoney(line.balance()))
        .setHeader("Balance")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    return grid;
  }

  private VerticalLayout createBudgetVsActualTab() {
    VerticalLayout layout = new VerticalLayout();
    layout.setSizeFull();
    layout.setPadding(false);

    // Date pickers for Budget vs Actual
    DatePicker bvaStartDate = new DatePicker("Start Date");
    DatePicker bvaEndDate = new DatePicker("End Date");
    bvaStartDate.setWidth("180px");
    bvaEndDate.setWidth("180px");

    // Budget selector
    bvaBudgetSelect = new ComboBox<>("Budget");
    bvaBudgetSelect.setWidth("200px");
    bvaBudgetSelect.setPlaceholder("Select Budget");
    bvaBudgetSelect.setItemLabelGenerator(Budget::getName);

    // Department filter
    bvaDepartmentFilter = new ComboBox<>("Department");
    bvaDepartmentFilter.setWidth("200px");
    bvaDepartmentFilter.setPlaceholder("All Departments");
    bvaDepartmentFilter.setClearButtonVisible(true);
    bvaDepartmentFilter.setItemLabelGenerator(d -> d.getCode() + " - " + d.getName());

    // Sync with main date pickers
    startDatePicker.addValueChangeListener(e -> bvaStartDate.setValue(e.getValue()));
    endDatePicker.addValueChangeListener(e -> bvaEndDate.setValue(e.getValue()));
    bvaStartDate.addValueChangeListener(e -> startDatePicker.setValue(e.getValue()));
    bvaEndDate.addValueChangeListener(e -> endDatePicker.setValue(e.getValue()));

    Button generateBtn = new Button("Generate Report", VaadinIcon.REFRESH.create());
    generateBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    generateBtn.addClickListener(e -> loadBudgetVsActual());

    // Export buttons container
    bvaExportButtons = new HorizontalLayout();
    bvaExportButtons.setSpacing(true);
    bvaExportButtons.setVisible(false);

    HorizontalLayout controls =
        new HorizontalLayout(
            bvaStartDate,
            bvaEndDate,
            bvaBudgetSelect,
            bvaDepartmentFilter,
            generateBtn,
            bvaExportButtons);
    controls.setAlignItems(FlexComponent.Alignment.BASELINE);
    controls.setSpacing(true);

    budgetVsActualContent.setSizeFull();
    budgetVsActualContent.setPadding(false);

    layout.add(controls, budgetVsActualContent);
    return layout;
  }

  private VerticalLayout createArAgingTab() {
    VerticalLayout layout = new VerticalLayout();
    layout.setSizeFull();
    layout.setPadding(false);

    arAgingAsOfDate = new DatePicker("As Of Date");
    arAgingAsOfDate.setWidth("180px");
    arAgingAsOfDate.setValue(LocalDate.now());

    Button generateBtn = new Button("Generate Report", VaadinIcon.REFRESH.create());
    generateBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    generateBtn.addClickListener(e -> loadArAging());

    // Export buttons container
    arAgingExportButtons = new HorizontalLayout();
    arAgingExportButtons.setSpacing(true);
    arAgingExportButtons.setVisible(false);

    HorizontalLayout controls =
        new HorizontalLayout(arAgingAsOfDate, generateBtn, arAgingExportButtons);
    controls.setAlignItems(FlexComponent.Alignment.BASELINE);
    controls.setSpacing(true);

    arAgingContent.setSizeFull();
    arAgingContent.setPadding(false);

    layout.add(controls, arAgingContent);
    return layout;
  }

  private VerticalLayout createApAgingTab() {
    VerticalLayout layout = new VerticalLayout();
    layout.setSizeFull();
    layout.setPadding(false);

    apAgingAsOfDate = new DatePicker("As Of Date");
    apAgingAsOfDate.setWidth("180px");
    apAgingAsOfDate.setValue(LocalDate.now());

    Button generateBtn = new Button("Generate Report", VaadinIcon.REFRESH.create());
    generateBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    generateBtn.addClickListener(e -> loadApAging());

    // Export buttons container
    apAgingExportButtons = new HorizontalLayout();
    apAgingExportButtons.setSpacing(true);
    apAgingExportButtons.setVisible(false);

    HorizontalLayout controls =
        new HorizontalLayout(apAgingAsOfDate, generateBtn, apAgingExportButtons);
    controls.setAlignItems(FlexComponent.Alignment.BASELINE);
    controls.setSpacing(true);

    apAgingContent.setSizeFull();
    apAgingContent.setPadding(false);

    layout.add(controls, apAgingContent);
    return layout;
  }

  private void loadArAging() {
    if (arAgingAsOfDate.isEmpty()) {
      Notification.show("Please select a date", 3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
      return;
    }

    try {
      Company company = companyContextService.getCurrentCompany();
      ArAgingReport report = reportingService.generateArAging(company, arAgingAsOfDate.getValue());
      displayArAging(report);
    } catch (Exception e) {
      Notification.show(
              "Error generating report: " + e.getMessage(), 3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
  }

  private void displayArAging(ArAgingReport report) {
    arAgingContent.removeAll();
    currentArAgingReport = report;
    updateArAgingExportButtons();

    // Report header
    H3 header = new H3("Accounts Receivable Aging Report");
    Span asOfDate = new Span("As of " + report.asOfDate().format(DATE_FORMAT));
    asOfDate.getStyle().set("color", "var(--lumo-secondary-text-color)");

    // Summary by customer grid
    H3 summaryHeader = new H3("Aging Summary by Customer");
    summaryHeader.getStyle().set("margin-top", "16px");

    Grid<ArAgingCustomerSummary> summaryGrid = new Grid<>();
    summaryGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

    summaryGrid
        .addColumn(summary -> summary.customer().getName())
        .setHeader("Customer")
        .setFlexGrow(1);

    summaryGrid
        .addColumn(summary -> formatMoney(summary.current()))
        .setHeader("Current")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    summaryGrid
        .addColumn(summary -> formatMoney(summary.days1to30()))
        .setHeader("1-30 Days")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    summaryGrid
        .addColumn(summary -> formatMoney(summary.days31to60()))
        .setHeader("31-60 Days")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    summaryGrid
        .addColumn(summary -> formatMoney(summary.days61to90()))
        .setHeader("61-90 Days")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    summaryGrid
        .addColumn(summary -> formatMoney(summary.days90Plus()))
        .setHeader("90+ Days")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    summaryGrid
        .addColumn(summary -> formatMoney(summary.total()))
        .setHeader("Total")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    summaryGrid.setItems(report.customerSummaries());
    summaryGrid.setAllRowsVisible(true);

    // Enable drilldown on row click to show outstanding invoices
    summaryGrid.addItemClickListener(
        event -> {
          ArAgingCustomerSummary summary = event.getItem();
          openArAgingDrilldownDialog(summary.customer(), report.asOfDate());
        });
    summaryGrid.getStyle().set("cursor", "pointer");

    // Totals row
    HorizontalLayout totalsRow = new HorizontalLayout();
    totalsRow.setWidthFull();
    totalsRow.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
    totalsRow.getStyle().set("font-weight", "bold");
    totalsRow.getStyle().set("padding", "8px");
    totalsRow.getStyle().set("border-top", "2px solid var(--lumo-contrast-20pct)");

    Span totalLabel = new Span("Totals:");
    Span totalCurrent = new Span(formatMoney(report.totalCurrent()));
    Span total1to30 = new Span(formatMoney(report.total1to30()));
    Span total31to60 = new Span(formatMoney(report.total31to60()));
    Span total61to90 = new Span(formatMoney(report.total61to90()));
    Span total90Plus = new Span(formatMoney(report.total90Plus()));
    Span grandTotal = new Span(formatMoney(report.grandTotal()));

    totalCurrent.getStyle().set("width", "100px").set("text-align", "right");
    total1to30.getStyle().set("width", "100px").set("text-align", "right");
    total31to60.getStyle().set("width", "100px").set("text-align", "right");
    total61to90.getStyle().set("width", "100px").set("text-align", "right");
    total90Plus
        .getStyle()
        .set("width", "100px")
        .set("text-align", "right")
        .set("color", "var(--lumo-error-text-color)");
    grandTotal.getStyle().set("width", "100px").set("text-align", "right");

    totalsRow.add(
        totalLabel, totalCurrent, total1to30, total31to60, total61to90, total90Plus, grandTotal);

    // Grand total highlight
    HorizontalLayout grandTotalRow = new HorizontalLayout();
    grandTotalRow.setWidthFull();
    grandTotalRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
    grandTotalRow
        .getStyle()
        .set("font-weight", "bold")
        .set("font-size", "var(--lumo-font-size-l)")
        .set("padding", "12px")
        .set("margin-top", "16px")
        .set("border-top", "3px solid var(--lumo-contrast-30pct)")
        .set("background-color", "var(--lumo-contrast-5pct)");

    Span grandTotalLabel = new Span("Total Outstanding Receivables");
    Span grandTotalAmount = new Span(formatMoney(report.grandTotal()));
    grandTotalRow.add(grandTotalLabel, grandTotalAmount);

    arAgingContent.add(header, asOfDate, summaryHeader, summaryGrid, totalsRow, grandTotalRow);
  }

  private void loadApAging() {
    if (apAgingAsOfDate.isEmpty()) {
      Notification.show("Please select a date", 3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
      return;
    }

    try {
      Company company = companyContextService.getCurrentCompany();
      ApAgingReport report = reportingService.generateApAging(company, apAgingAsOfDate.getValue());
      displayApAging(report);
    } catch (Exception e) {
      Notification.show(
              "Error generating report: " + e.getMessage(), 3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
  }

  private void displayApAging(ApAgingReport report) {
    apAgingContent.removeAll();
    currentApAgingReport = report;
    updateApAgingExportButtons();

    // Report header
    H3 header = new H3("Accounts Payable Aging Report");
    Span asOfDate = new Span("As of " + report.asOfDate().format(DATE_FORMAT));
    asOfDate.getStyle().set("color", "var(--lumo-secondary-text-color)");

    // Summary by supplier grid
    H3 summaryHeader = new H3("Aging Summary by Supplier");
    summaryHeader.getStyle().set("margin-top", "16px");

    Grid<ApAgingSupplierSummary> summaryGrid = new Grid<>();
    summaryGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

    summaryGrid
        .addColumn(summary -> summary.supplier().getName())
        .setHeader("Supplier")
        .setFlexGrow(1);

    summaryGrid
        .addColumn(summary -> formatMoney(summary.current()))
        .setHeader("Current")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    summaryGrid
        .addColumn(summary -> formatMoney(summary.days1to30()))
        .setHeader("1-30 Days")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    summaryGrid
        .addColumn(summary -> formatMoney(summary.days31to60()))
        .setHeader("31-60 Days")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    summaryGrid
        .addColumn(summary -> formatMoney(summary.days61to90()))
        .setHeader("61-90 Days")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    summaryGrid
        .addColumn(summary -> formatMoney(summary.days90Plus()))
        .setHeader("90+ Days")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    summaryGrid
        .addColumn(summary -> formatMoney(summary.total()))
        .setHeader("Total")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    summaryGrid.setItems(report.supplierSummaries());
    summaryGrid.setAllRowsVisible(true);

    // Enable drilldown on row click to show outstanding bills
    summaryGrid.addItemClickListener(
        event -> {
          ApAgingSupplierSummary summary = event.getItem();
          openApAgingDrilldownDialog(summary.supplier(), report.asOfDate());
        });
    summaryGrid.getStyle().set("cursor", "pointer");

    // Totals row
    HorizontalLayout totalsRow = new HorizontalLayout();
    totalsRow.setWidthFull();
    totalsRow.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
    totalsRow.getStyle().set("font-weight", "bold");
    totalsRow.getStyle().set("padding", "8px");
    totalsRow.getStyle().set("border-top", "2px solid var(--lumo-contrast-20pct)");

    Span totalLabel = new Span("Totals:");
    Span totalCurrent = new Span(formatMoney(report.totalCurrent()));
    Span total1to30 = new Span(formatMoney(report.total1to30()));
    Span total31to60 = new Span(formatMoney(report.total31to60()));
    Span total61to90 = new Span(formatMoney(report.total61to90()));
    Span total90Plus = new Span(formatMoney(report.total90Plus()));
    Span grandTotal = new Span(formatMoney(report.grandTotal()));

    totalCurrent.getStyle().set("width", "100px").set("text-align", "right");
    total1to30.getStyle().set("width", "100px").set("text-align", "right");
    total31to60.getStyle().set("width", "100px").set("text-align", "right");
    total61to90.getStyle().set("width", "100px").set("text-align", "right");
    total90Plus
        .getStyle()
        .set("width", "100px")
        .set("text-align", "right")
        .set("color", "var(--lumo-error-text-color)");
    grandTotal.getStyle().set("width", "100px").set("text-align", "right");

    totalsRow.add(
        totalLabel, totalCurrent, total1to30, total31to60, total61to90, total90Plus, grandTotal);

    // Grand total highlight
    HorizontalLayout grandTotalRow = new HorizontalLayout();
    grandTotalRow.setWidthFull();
    grandTotalRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
    grandTotalRow
        .getStyle()
        .set("font-weight", "bold")
        .set("font-size", "var(--lumo-font-size-l)")
        .set("padding", "12px")
        .set("margin-top", "16px")
        .set("border-top", "3px solid var(--lumo-contrast-30pct)")
        .set("background-color", "var(--lumo-contrast-5pct)");

    Span grandTotalLabel = new Span("Total Outstanding Payables");
    Span grandTotalAmount = new Span(formatMoney(report.grandTotal()));
    grandTotalRow.add(grandTotalLabel, grandTotalAmount);

    apAgingContent.add(header, asOfDate, summaryHeader, summaryGrid, totalsRow, grandTotalRow);
  }

  private void loadBudgetVsActual() {
    if (startDatePicker.isEmpty() || endDatePicker.isEmpty()) {
      Notification.show("Please select a date range", 3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
      return;
    }

    if (bvaBudgetSelect.isEmpty()) {
      Notification.show("Please select a budget", 3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
      return;
    }

    try {
      Company company = companyContextService.getCurrentCompany();
      Budget budget = bvaBudgetSelect.getValue();
      Department department = bvaDepartmentFilter.getValue(); // null means all departments

      BudgetVsActual report =
          reportingService.generateBudgetVsActual(
              company, budget, startDatePicker.getValue(), endDatePicker.getValue(), department);

      displayBudgetVsActual(report);
    } catch (Exception e) {
      Notification.show(
              "Error generating report: " + e.getMessage(), 3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
  }

  private void displayBudgetVsActual(BudgetVsActual report) {
    budgetVsActualContent.removeAll();
    currentBudgetVsActual = report;
    updateBudgetVsActualExportButtons();

    // Report header
    H3 header = new H3("Budget vs Actual Report");
    String headerText =
        "Budget: "
            + report.budget().getName()
            + " | Period: "
            + report.startDate().format(DATE_FORMAT)
            + " to "
            + report.endDate().format(DATE_FORMAT);
    if (report.department() != null) {
      headerText +=
          " | Department: " + report.department().getCode() + " - " + report.department().getName();
    }
    Span subtitle = new Span(headerText);
    subtitle.getStyle().set("color", "var(--lumo-secondary-text-color)");

    // Grid for budget vs actual lines
    Grid<BudgetVsActualLine> grid = new Grid<>();
    grid.setSizeFull();
    grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

    grid.addColumn(line -> line.account().getCode())
        .setHeader("Code")
        .setAutoWidth(true)
        .setFlexGrow(0);

    grid.addColumn(line -> line.account().getName()).setHeader("Account").setFlexGrow(1);

    grid.addColumn(line -> formatMoney(line.budgetAmount()))
        .setHeader("Budget")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    grid.addColumn(line -> formatMoney(line.actualAmount()))
        .setHeader("Actual")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    grid.addColumn(line -> formatVariance(line.variance()))
        .setHeader("Variance")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    grid.addColumn(line -> formatPercent(line.variancePercent()))
        .setHeader("Variance %")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    grid.setItems(report.lines());

    // Enable drilldown on row click for actual amounts
    grid.addItemClickListener(
        event -> {
          BudgetVsActualLine line = event.getItem();
          openLedgerDrilldownDialog(
              line.account(), report.startDate(), report.endDate(), report.department());
        });
    grid.getStyle().set("cursor", "pointer");

    // Totals row
    HorizontalLayout totalsRow = new HorizontalLayout();
    totalsRow.setWidthFull();
    totalsRow.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
    totalsRow.getStyle().set("font-weight", "bold");
    totalsRow.getStyle().set("padding", "8px");
    totalsRow.getStyle().set("border-top", "2px solid var(--lumo-contrast-20pct)");

    Span totalLabel = new Span("Totals:");
    Span totalBudget = new Span(formatMoney(report.totalBudget()));
    Span totalActual = new Span(formatMoney(report.totalActual()));
    Span totalVariance = new Span(formatVariance(report.totalVariance()));
    Span totalVariancePercent = new Span(formatPercent(report.totalVariancePercent()));

    totalBudget.getStyle().set("width", "100px").set("text-align", "right");
    totalActual.getStyle().set("width", "100px").set("text-align", "right");
    totalVariance.getStyle().set("width", "100px").set("text-align", "right");
    totalVariancePercent.getStyle().set("width", "100px").set("text-align", "right");

    totalsRow.add(totalLabel, totalBudget, totalActual, totalVariance, totalVariancePercent);

    budgetVsActualContent.add(header, subtitle, grid, totalsRow);
  }

  private String formatMoney(BigDecimal amount) {
    if (amount == null) return "0.00";
    return MONEY_FORMAT.format(amount);
  }

  private String formatVariance(BigDecimal variance) {
    if (variance == null) return "0.00";
    String formatted = MONEY_FORMAT.format(variance.abs());
    if (variance.compareTo(BigDecimal.ZERO) < 0) {
      return "(" + formatted + ")";
    }
    return formatted;
  }

  private String formatPercent(BigDecimal percent) {
    if (percent == null) return "0.0%";
    return MONEY_FORMAT.format(percent) + "%";
  }

  // ==================== EXPORT BUTTON METHODS ====================

  private void updateTrialBalanceExportButtons() {
    tbExportButtons.removeAll();
    if (currentTrialBalance == null) {
      tbExportButtons.setVisible(false);
      return;
    }

    Company company = companyContextService.getCurrentCompany();
    String dateStr = currentTrialBalance.endDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

    // PDF Export
    StreamResource pdfResource =
        new StreamResource(
            "TrialBalance_" + dateStr + ".pdf",
            () ->
                new ByteArrayInputStream(
                    reportExportService.exportTrialBalanceToPdf(currentTrialBalance, company)));
    Anchor pdfLink = new Anchor(pdfResource, "");
    pdfLink.getElement().setAttribute("download", true);
    Button pdfBtn = new Button("PDF", VaadinIcon.FILE_TEXT.create());
    pdfBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
    pdfLink.add(pdfBtn);

    // Excel Export
    StreamResource excelResource =
        new StreamResource(
            "TrialBalance_" + dateStr + ".xlsx",
            () ->
                new ByteArrayInputStream(
                    reportExportService.exportTrialBalanceToExcel(currentTrialBalance, company)));
    Anchor excelLink = new Anchor(excelResource, "");
    excelLink.getElement().setAttribute("download", true);
    Button excelBtn = new Button("Excel", VaadinIcon.FILE_TABLE.create());
    excelBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
    excelLink.add(excelBtn);

    // CSV Export
    StreamResource csvResource =
        new StreamResource(
            "TrialBalance_" + dateStr + ".csv",
            () ->
                new ByteArrayInputStream(
                    reportExportService.exportTrialBalanceToCsv(currentTrialBalance, company)));
    Anchor csvLink = new Anchor(csvResource, "");
    csvLink.getElement().setAttribute("download", true);
    Button csvBtn = new Button("CSV", VaadinIcon.FILE.create());
    csvBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
    csvLink.add(csvBtn);

    tbExportButtons.add(pdfLink, excelLink, csvLink);
    tbExportButtons.setVisible(true);
  }

  private void updateProfitAndLossExportButtons() {
    plExportButtons.removeAll();
    if (currentProfitAndLoss == null) {
      plExportButtons.setVisible(false);
      return;
    }

    Company company = companyContextService.getCurrentCompany();
    String dateStr = currentProfitAndLoss.endDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

    // PDF Export
    StreamResource pdfResource =
        new StreamResource(
            "ProfitAndLoss_" + dateStr + ".pdf",
            () ->
                new ByteArrayInputStream(
                    reportExportService.exportProfitAndLossToPdf(currentProfitAndLoss, company)));
    Anchor pdfLink = new Anchor(pdfResource, "");
    pdfLink.getElement().setAttribute("download", true);
    Button pdfBtn = new Button("PDF", VaadinIcon.FILE_TEXT.create());
    pdfBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
    pdfLink.add(pdfBtn);

    // Excel Export
    StreamResource excelResource =
        new StreamResource(
            "ProfitAndLoss_" + dateStr + ".xlsx",
            () ->
                new ByteArrayInputStream(
                    reportExportService.exportProfitAndLossToExcel(currentProfitAndLoss, company)));
    Anchor excelLink = new Anchor(excelResource, "");
    excelLink.getElement().setAttribute("download", true);
    Button excelBtn = new Button("Excel", VaadinIcon.FILE_TABLE.create());
    excelBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
    excelLink.add(excelBtn);

    // CSV Export
    StreamResource csvResource =
        new StreamResource(
            "ProfitAndLoss_" + dateStr + ".csv",
            () ->
                new ByteArrayInputStream(
                    reportExportService.exportProfitAndLossToCsv(currentProfitAndLoss, company)));
    Anchor csvLink = new Anchor(csvResource, "");
    csvLink.getElement().setAttribute("download", true);
    Button csvBtn = new Button("CSV", VaadinIcon.FILE.create());
    csvBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
    csvLink.add(csvBtn);

    plExportButtons.add(pdfLink, excelLink, csvLink);
    plExportButtons.setVisible(true);
  }

  private void updateBalanceSheetExportButtons() {
    bsExportButtons.removeAll();
    if (currentBalanceSheet == null) {
      bsExportButtons.setVisible(false);
      return;
    }

    Company company = companyContextService.getCurrentCompany();
    String dateStr = currentBalanceSheet.asOfDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

    // PDF Export
    StreamResource pdfResource =
        new StreamResource(
            "BalanceSheet_" + dateStr + ".pdf",
            () ->
                new ByteArrayInputStream(
                    reportExportService.exportBalanceSheetToPdf(currentBalanceSheet, company)));
    Anchor pdfLink = new Anchor(pdfResource, "");
    pdfLink.getElement().setAttribute("download", true);
    Button pdfBtn = new Button("PDF", VaadinIcon.FILE_TEXT.create());
    pdfBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
    pdfLink.add(pdfBtn);

    // Excel Export
    StreamResource excelResource =
        new StreamResource(
            "BalanceSheet_" + dateStr + ".xlsx",
            () ->
                new ByteArrayInputStream(
                    reportExportService.exportBalanceSheetToExcel(currentBalanceSheet, company)));
    Anchor excelLink = new Anchor(excelResource, "");
    excelLink.getElement().setAttribute("download", true);
    Button excelBtn = new Button("Excel", VaadinIcon.FILE_TABLE.create());
    excelBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
    excelLink.add(excelBtn);

    // CSV Export
    StreamResource csvResource =
        new StreamResource(
            "BalanceSheet_" + dateStr + ".csv",
            () ->
                new ByteArrayInputStream(
                    reportExportService.exportBalanceSheetToCsv(currentBalanceSheet, company)));
    Anchor csvLink = new Anchor(csvResource, "");
    csvLink.getElement().setAttribute("download", true);
    Button csvBtn = new Button("CSV", VaadinIcon.FILE.create());
    csvBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
    csvLink.add(csvBtn);

    bsExportButtons.add(pdfLink, excelLink, csvLink);
    bsExportButtons.setVisible(true);
  }

  private void updateBudgetVsActualExportButtons() {
    bvaExportButtons.removeAll();
    if (currentBudgetVsActual == null) {
      bvaExportButtons.setVisible(false);
      return;
    }

    Company company = companyContextService.getCurrentCompany();
    String dateStr =
        currentBudgetVsActual.endDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

    // PDF Export
    StreamResource pdfResource =
        new StreamResource(
            "BudgetVsActual_" + dateStr + ".pdf",
            () ->
                new ByteArrayInputStream(
                    reportExportService.exportBudgetVsActualToPdf(currentBudgetVsActual, company)));
    Anchor pdfLink = new Anchor(pdfResource, "");
    pdfLink.getElement().setAttribute("download", true);
    Button pdfBtn = new Button("PDF", VaadinIcon.FILE_TEXT.create());
    pdfBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
    pdfLink.add(pdfBtn);

    // Excel Export
    StreamResource excelResource =
        new StreamResource(
            "BudgetVsActual_" + dateStr + ".xlsx",
            () ->
                new ByteArrayInputStream(
                    reportExportService.exportBudgetVsActualToExcel(
                        currentBudgetVsActual, company)));
    Anchor excelLink = new Anchor(excelResource, "");
    excelLink.getElement().setAttribute("download", true);
    Button excelBtn = new Button("Excel", VaadinIcon.FILE_TABLE.create());
    excelBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
    excelLink.add(excelBtn);

    // CSV Export
    StreamResource csvResource =
        new StreamResource(
            "BudgetVsActual_" + dateStr + ".csv",
            () ->
                new ByteArrayInputStream(
                    reportExportService.exportBudgetVsActualToCsv(currentBudgetVsActual, company)));
    Anchor csvLink = new Anchor(csvResource, "");
    csvLink.getElement().setAttribute("download", true);
    Button csvBtn = new Button("CSV", VaadinIcon.FILE.create());
    csvBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
    csvLink.add(csvBtn);

    bvaExportButtons.add(pdfLink, excelLink, csvLink);
    bvaExportButtons.setVisible(true);
  }

  private void updateArAgingExportButtons() {
    arAgingExportButtons.removeAll();
    if (currentArAgingReport == null) {
      arAgingExportButtons.setVisible(false);
      return;
    }

    Company company = companyContextService.getCurrentCompany();
    String dateStr =
        currentArAgingReport.asOfDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

    // PDF Export
    StreamResource pdfResource =
        new StreamResource(
            "AR_Aging_" + dateStr + ".pdf",
            () ->
                new ByteArrayInputStream(
                    reportExportService.exportArAgingToPdf(currentArAgingReport, company)));
    Anchor pdfLink = new Anchor(pdfResource, "");
    pdfLink.getElement().setAttribute("download", true);
    Button pdfBtn = new Button("PDF", VaadinIcon.FILE_TEXT.create());
    pdfBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
    pdfLink.add(pdfBtn);

    // Excel Export
    StreamResource excelResource =
        new StreamResource(
            "AR_Aging_" + dateStr + ".xlsx",
            () ->
                new ByteArrayInputStream(
                    reportExportService.exportArAgingToExcel(currentArAgingReport, company)));
    Anchor excelLink = new Anchor(excelResource, "");
    excelLink.getElement().setAttribute("download", true);
    Button excelBtn = new Button("Excel", VaadinIcon.FILE_TABLE.create());
    excelBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
    excelLink.add(excelBtn);

    // CSV Export
    StreamResource csvResource =
        new StreamResource(
            "AR_Aging_" + dateStr + ".csv",
            () ->
                new ByteArrayInputStream(
                    reportExportService.exportArAgingToCsv(currentArAgingReport, company)));
    Anchor csvLink = new Anchor(csvResource, "");
    csvLink.getElement().setAttribute("download", true);
    Button csvBtn = new Button("CSV", VaadinIcon.FILE.create());
    csvBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
    csvLink.add(csvBtn);

    arAgingExportButtons.add(pdfLink, excelLink, csvLink);
    arAgingExportButtons.setVisible(true);
  }

  private void updateApAgingExportButtons() {
    apAgingExportButtons.removeAll();
    if (currentApAgingReport == null) {
      apAgingExportButtons.setVisible(false);
      return;
    }

    Company company = companyContextService.getCurrentCompany();
    String dateStr =
        currentApAgingReport.asOfDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

    // PDF Export
    StreamResource pdfResource =
        new StreamResource(
            "AP_Aging_" + dateStr + ".pdf",
            () ->
                new ByteArrayInputStream(
                    reportExportService.exportApAgingToPdf(currentApAgingReport, company)));
    Anchor pdfLink = new Anchor(pdfResource, "");
    pdfLink.getElement().setAttribute("download", true);
    Button pdfBtn = new Button("PDF", VaadinIcon.FILE_TEXT.create());
    pdfBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
    pdfLink.add(pdfBtn);

    // Excel Export
    StreamResource excelResource =
        new StreamResource(
            "AP_Aging_" + dateStr + ".xlsx",
            () ->
                new ByteArrayInputStream(
                    reportExportService.exportApAgingToExcel(currentApAgingReport, company)));
    Anchor excelLink = new Anchor(excelResource, "");
    excelLink.getElement().setAttribute("download", true);
    Button excelBtn = new Button("Excel", VaadinIcon.FILE_TABLE.create());
    excelBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
    excelLink.add(excelBtn);

    // CSV Export
    StreamResource csvResource =
        new StreamResource(
            "AP_Aging_" + dateStr + ".csv",
            () ->
                new ByteArrayInputStream(
                    reportExportService.exportApAgingToCsv(currentApAgingReport, company)));
    Anchor csvLink = new Anchor(csvResource, "");
    csvLink.getElement().setAttribute("download", true);
    Button csvBtn = new Button("CSV", VaadinIcon.FILE.create());
    csvBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
    csvLink.add(csvBtn);

    apAgingExportButtons.add(pdfLink, excelLink, csvLink);
    apAgingExportButtons.setVisible(true);
  }

  // ==================== CASHFLOW TAB ====================

  private VerticalLayout createCashflowTab() {
    VerticalLayout layout = new VerticalLayout();
    layout.setSizeFull();
    layout.setPadding(false);

    // Date pickers for cashflow report
    DatePicker cashflowStartDate = new DatePicker("Start Date");
    DatePicker cashflowEndDate = new DatePicker("End Date");
    cashflowStartDate.setWidth("180px");
    cashflowEndDate.setWidth("180px");

    // Set defaults to current fiscal year or current month
    LocalDate today = LocalDate.now();
    cashflowStartDate.setValue(today.withDayOfMonth(1));
    cashflowEndDate.setValue(today);

    Button generateBtn = new Button("Generate Report", VaadinIcon.REFRESH.create());
    generateBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    generateBtn.addClickListener(
        e -> loadCashflow(cashflowStartDate.getValue(), cashflowEndDate.getValue()));

    // Export buttons container
    cashflowExportButtons = new HorizontalLayout();
    cashflowExportButtons.setSpacing(true);
    cashflowExportButtons.setVisible(false);

    HorizontalLayout controls =
        new HorizontalLayout(
            cashflowStartDate, cashflowEndDate, generateBtn, cashflowExportButtons);
    controls.setAlignItems(FlexComponent.Alignment.END);
    controls.setSpacing(true);

    // Content area
    cashflowContent.setSizeFull();
    cashflowContent.setPadding(false);

    // Initial message
    Span initialMessage =
        new Span("Select a date range and click Generate Report to view the Cashflow Statement");
    initialMessage.getStyle().set("color", "var(--lumo-secondary-text-color)");
    cashflowContent.add(initialMessage);

    layout.add(controls, cashflowContent);
    return layout;
  }

  private void loadCashflow(LocalDate startDate, LocalDate endDate) {
    if (startDate == null || endDate == null) {
      Notification.show("Please select a date range", 3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
      return;
    }

    if (startDate.isAfter(endDate)) {
      Notification.show("Start date must be before end date", 3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
      return;
    }

    try {
      Company company = companyContextService.getCurrentCompany();
      CashflowStatement report = reportingService.generateCashflow(company, startDate, endDate);
      displayCashflow(report);
    } catch (Exception e) {
      Notification.show(
              "Error generating report: " + e.getMessage(), 3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
  }

  private void displayCashflow(CashflowStatement report) {
    cashflowContent.removeAll();
    currentCashflowStatement = report;
    updateCashflowExportButtons();

    // Report header
    H3 header = new H3("Cashflow Statement");
    Span subtitle =
        new Span(
            "Period: "
                + report.startDate().format(DATE_FORMAT)
                + " to "
                + report.endDate().format(DATE_FORMAT));
    subtitle.getStyle().set("color", "var(--lumo-secondary-text-color)");

    // Reconciliation status
    Span status = new Span(report.isReconciled() ? "RECONCILED" : "UNRECONCILED");
    status
        .getStyle()
        .set(
            "color",
            report.isReconciled() ? "var(--lumo-success-color)" : "var(--lumo-error-color)")
        .set("font-weight", "bold")
        .set("margin-left", "16px");

    HorizontalLayout headerRow = new HorizontalLayout(header, status);
    headerRow.setAlignItems(FlexComponent.Alignment.BASELINE);

    // Cash Summary section
    H3 summaryHeader = new H3("Cash Summary");
    summaryHeader.getStyle().set("margin-top", "16px");

    VerticalLayout summarySection = new VerticalLayout();
    summarySection.setPadding(false);
    summarySection.setSpacing(false);

    addCashSummaryRow(summarySection, "Opening Cash Balance", report.openingBalance(), false);
    addCashSummaryRow(summarySection, "Total Cash Inflows", report.totalInflows(), true);
    addCashSummaryRow(
        summarySection, "Total Cash Outflows", report.totalOutflows().negate(), false);
    addNetCashFlowRow(summarySection, "Net Cash Flow", report.netCashFlow());
    addCashSummaryRow(summarySection, "Closing Cash Balance", report.closingBalance(), false);

    // Bank Account Summaries
    if (!report.accountSummaries().isEmpty()) {
      H3 accountHeader = new H3("Summary by Bank Account");
      accountHeader.getStyle().set("margin-top", "24px");

      Grid<CashflowAccountSummary> accountGrid = new Grid<>();
      accountGrid.setHeight("300px");
      accountGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

      accountGrid
          .addColumn(summary -> summary.account().getCode() + " - " + summary.account().getName())
          .setHeader("Account")
          .setFlexGrow(2);

      accountGrid
          .addColumn(summary -> formatMoney(summary.openingBalance()))
          .setHeader("Opening")
          .setAutoWidth(true)
          .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

      accountGrid
          .addColumn(summary -> formatMoney(summary.inflows()))
          .setHeader("Inflows")
          .setAutoWidth(true)
          .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

      accountGrid
          .addColumn(summary -> formatMoney(summary.outflows()))
          .setHeader("Outflows")
          .setAutoWidth(true)
          .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

      accountGrid
          .addColumn(summary -> formatMoney(summary.closingBalance()))
          .setHeader("Closing")
          .setAutoWidth(true)
          .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

      accountGrid.setItems(report.accountSummaries());

      // Totals row
      HorizontalLayout accountTotals = new HorizontalLayout();
      accountTotals.setWidthFull();
      accountTotals.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
      accountTotals
          .getStyle()
          .set("font-weight", "bold")
          .set("padding", "8px")
          .set("border-top", "2px solid var(--lumo-contrast-20pct)");

      Span totalLabel = new Span("Totals:");
      Span totalOpening = new Span(formatMoney(report.openingBalance()));
      Span totalInflows = new Span(formatMoney(report.totalInflows()));
      Span totalOutflows = new Span(formatMoney(report.totalOutflows()));
      Span totalClosing = new Span(formatMoney(report.closingBalance()));

      totalOpening.getStyle().set("width", "100px").set("text-align", "right");
      totalInflows
          .getStyle()
          .set("width", "100px")
          .set("text-align", "right")
          .set("color", "var(--lumo-success-color)");
      totalOutflows
          .getStyle()
          .set("width", "100px")
          .set("text-align", "right")
          .set("color", "var(--lumo-error-color)");
      totalClosing.getStyle().set("width", "100px").set("text-align", "right");

      accountTotals.add(totalLabel, totalOpening, totalInflows, totalOutflows, totalClosing);

      cashflowContent.add(
          headerRow,
          subtitle,
          summaryHeader,
          summarySection,
          accountHeader,
          accountGrid,
          accountTotals);
    } else {
      Span noData = new Span("No bank account activity found for this period.");
      noData.getStyle().set("color", "var(--lumo-secondary-text-color)");
      cashflowContent.add(headerRow, subtitle, summaryHeader, summarySection, noData);
    }
  }

  private void addCashSummaryRow(
      VerticalLayout container, String label, BigDecimal amount, boolean isInflow) {
    HorizontalLayout row = new HorizontalLayout();
    row.setWidthFull();
    row.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
    row.getStyle().set("padding", "4px 8px");

    Span labelSpan = new Span(label);
    Span amountSpan = new Span(formatMoney(amount));

    if (isInflow && amount.compareTo(BigDecimal.ZERO) > 0) {
      amountSpan.getStyle().set("color", "var(--lumo-success-color)");
    } else if (!isInflow && amount.compareTo(BigDecimal.ZERO) < 0) {
      amountSpan.getStyle().set("color", "var(--lumo-error-color)");
    }

    row.add(labelSpan, amountSpan);
    container.add(row);
  }

  private void addNetCashFlowRow(VerticalLayout container, String label, BigDecimal amount) {
    HorizontalLayout row = new HorizontalLayout();
    row.setWidthFull();
    row.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
    row.getStyle()
        .set("padding", "8px")
        .set("font-weight", "bold")
        .set("background-color", "var(--lumo-contrast-5pct)")
        .set("border-top", "1px solid var(--lumo-contrast-20pct)")
        .set("border-bottom", "1px solid var(--lumo-contrast-20pct)");

    Span labelSpan = new Span(label);
    Span amountSpan = new Span(formatMoney(amount));

    if (amount.compareTo(BigDecimal.ZERO) >= 0) {
      amountSpan.getStyle().set("color", "var(--lumo-success-color)");
    } else {
      amountSpan.getStyle().set("color", "var(--lumo-error-color)");
    }

    row.add(labelSpan, amountSpan);
    container.add(row);
  }

  private void updateCashflowExportButtons() {
    cashflowExportButtons.removeAll();
    if (currentCashflowStatement == null) {
      cashflowExportButtons.setVisible(false);
      return;
    }

    Company company = companyContextService.getCurrentCompany();
    String dateStr =
        currentCashflowStatement.endDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

    // PDF Export
    StreamResource pdfResource =
        new StreamResource(
            "Cashflow_" + dateStr + ".pdf",
            () ->
                new ByteArrayInputStream(
                    reportExportService.exportCashflowToPdf(currentCashflowStatement, company)));
    Anchor pdfLink = new Anchor(pdfResource, "");
    pdfLink.getElement().setAttribute("download", true);
    Button pdfBtn = new Button("PDF", VaadinIcon.FILE_TEXT.create());
    pdfBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
    pdfLink.add(pdfBtn);

    // Excel Export
    StreamResource excelResource =
        new StreamResource(
            "Cashflow_" + dateStr + ".xlsx",
            () ->
                new ByteArrayInputStream(
                    reportExportService.exportCashflowToExcel(currentCashflowStatement, company)));
    Anchor excelLink = new Anchor(excelResource, "");
    excelLink.getElement().setAttribute("download", true);
    Button excelBtn = new Button("Excel", VaadinIcon.FILE_TABLE.create());
    excelBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
    excelLink.add(excelBtn);

    // CSV Export
    StreamResource csvResource =
        new StreamResource(
            "Cashflow_" + dateStr + ".csv",
            () ->
                new ByteArrayInputStream(
                    reportExportService.exportCashflowToCsv(currentCashflowStatement, company)));
    Anchor csvLink = new Anchor(csvResource, "");
    csvLink.getElement().setAttribute("download", true);
    Button csvBtn = new Button("CSV", VaadinIcon.FILE.create());
    csvBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
    csvLink.add(csvBtn);

    cashflowExportButtons.add(pdfLink, excelLink, csvLink);
    cashflowExportButtons.setVisible(true);
  }

  // ==================== DRILLDOWN DIALOGS ====================

  /**
   * Opens a dialog showing ledger entries for a specific account within a date range. Used for
   * drilldown from Trial Balance, P&L, Balance Sheet, and Budget vs Actual reports.
   */
  private void openLedgerDrilldownDialog(
      Account account, LocalDate startDate, LocalDate endDate, Department department) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Ledger Entries: " + account.getCode() + " - " + account.getName());
    dialog.setWidth("900px");
    dialog.setHeight("600px");

    VerticalLayout content = new VerticalLayout();
    content.setSizeFull();
    content.setPadding(false);

    // Date range info
    String periodText =
        "Period: " + startDate.format(DATE_FORMAT) + " to " + endDate.format(DATE_FORMAT);
    if (department != null) {
      periodText += " | Department: " + department.getCode() + " - " + department.getName();
    }
    Span periodInfo = new Span(periodText);
    periodInfo.getStyle().set("color", "var(--lumo-secondary-text-color)");

    // Fetch ledger entries
    List<LedgerEntry> entries;
    if (department != null) {
      entries =
          ledgerEntryRepository.findByAccountAndDateRangeAndDepartment(
              account, startDate, endDate, department);
    } else {
      entries = ledgerEntryRepository.findByAccountAndDateRange(account, startDate, endDate);
    }

    // Create grid for ledger entries
    Grid<LedgerEntry> grid = new Grid<>();
    grid.setSizeFull();
    grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

    grid.addColumn(entry -> entry.getEntryDate().format(DATE_FORMAT))
        .setHeader("Date")
        .setAutoWidth(true)
        .setFlexGrow(0);

    grid.addColumn(entry -> entry.getTransaction().getReference())
        .setHeader("Reference")
        .setAutoWidth(true)
        .setFlexGrow(0);

    grid.addColumn(entry -> entry.getTransaction().getDescription())
        .setHeader("Description")
        .setFlexGrow(1);

    grid.addColumn(entry -> entry.getTransaction().getType().name())
        .setHeader("Type")
        .setAutoWidth(true)
        .setFlexGrow(0);

    grid.addColumn(
            entry ->
                entry.getAmountDr().compareTo(BigDecimal.ZERO) > 0
                    ? formatMoney(entry.getAmountDr())
                    : "")
        .setHeader("Debit")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    grid.addColumn(
            entry ->
                entry.getAmountCr().compareTo(BigDecimal.ZERO) > 0
                    ? formatMoney(entry.getAmountCr())
                    : "")
        .setHeader("Credit")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    grid.setItems(entries);

    // Calculate totals
    BigDecimal totalDebits =
        entries.stream().map(LedgerEntry::getAmountDr).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal totalCredits =
        entries.stream().map(LedgerEntry::getAmountCr).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal netBalance = totalDebits.subtract(totalCredits);

    // Totals row
    HorizontalLayout totalsRow = new HorizontalLayout();
    totalsRow.setWidthFull();
    totalsRow.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
    totalsRow
        .getStyle()
        .set("font-weight", "bold")
        .set("padding", "8px")
        .set("border-top", "2px solid var(--lumo-contrast-20pct)");

    Span countLabel = new Span(entries.size() + " entries");
    countLabel.getStyle().set("flex-grow", "1");

    Span debitTotal = new Span("Debits: " + formatMoney(totalDebits));
    Span creditTotal = new Span("Credits: " + formatMoney(totalCredits));
    Span netTotal = new Span("Net: " + formatMoney(netBalance));

    debitTotal.getStyle().set("margin-right", "16px");
    creditTotal.getStyle().set("margin-right", "16px");
    if (netBalance.compareTo(BigDecimal.ZERO) >= 0) {
      netTotal.getStyle().set("color", "var(--lumo-success-text-color)");
    } else {
      netTotal.getStyle().set("color", "var(--lumo-error-text-color)");
    }

    totalsRow.add(countLabel, debitTotal, creditTotal, netTotal);

    content.add(periodInfo, grid, totalsRow);

    Button closeBtn = new Button("Close", e -> dialog.close());
    dialog.add(content);
    dialog.getFooter().add(closeBtn);
    dialog.open();
  }

  /**
   * Opens a dialog showing outstanding invoices for a specific customer. Used for drilldown from AR
   * Aging report.
   */
  private void openArAgingDrilldownDialog(Contact customer, LocalDate asOfDate) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Outstanding Invoices: " + customer.getName());
    dialog.setWidth("800px");
    dialog.setHeight("500px");

    VerticalLayout content = new VerticalLayout();
    content.setSizeFull();
    content.setPadding(false);

    // As of date info
    Span dateInfo = new Span("As of " + asOfDate.format(DATE_FORMAT));
    dateInfo.getStyle().set("color", "var(--lumo-secondary-text-color)");

    // Fetch outstanding invoices for this customer
    Company company = companyContextService.getCurrentCompany();
    List<SalesInvoice> invoices =
        salesInvoiceRepository.findOutstandingByCompanyAndContact(company, customer);

    // Create grid for invoices
    Grid<SalesInvoice> grid = new Grid<>();
    grid.setSizeFull();
    grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

    grid.addColumn(SalesInvoice::getInvoiceNumber)
        .setHeader("Invoice #")
        .setAutoWidth(true)
        .setFlexGrow(0);

    grid.addColumn(inv -> inv.getIssueDate().format(DATE_FORMAT))
        .setHeader("Issue Date")
        .setAutoWidth(true)
        .setFlexGrow(0);

    grid.addColumn(inv -> inv.getDueDate().format(DATE_FORMAT))
        .setHeader("Due Date")
        .setAutoWidth(true)
        .setFlexGrow(0);

    grid.addColumn(
            inv -> {
              long daysOverdue =
                  java.time.temporal.ChronoUnit.DAYS.between(inv.getDueDate(), asOfDate);
              if (daysOverdue <= 0) return "Current";
              else if (daysOverdue <= 30) return "1-30 days";
              else if (daysOverdue <= 60) return "31-60 days";
              else if (daysOverdue <= 90) return "61-90 days";
              else return "90+ days";
            })
        .setHeader("Aging")
        .setAutoWidth(true)
        .setFlexGrow(0);

    grid.addColumn(inv -> formatMoney(inv.getTotal()))
        .setHeader("Total")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    grid.addColumn(inv -> formatMoney(inv.getBalance()))
        .setHeader("Balance")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    grid.setItems(invoices);

    // Calculate totals
    BigDecimal totalOutstanding =
        invoices.stream().map(SalesInvoice::getBalance).reduce(BigDecimal.ZERO, BigDecimal::add);

    // Totals row
    HorizontalLayout totalsRow = new HorizontalLayout();
    totalsRow.setWidthFull();
    totalsRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
    totalsRow
        .getStyle()
        .set("font-weight", "bold")
        .set("padding", "8px")
        .set("border-top", "2px solid var(--lumo-contrast-20pct)");

    Span countLabel = new Span(invoices.size() + " outstanding invoice(s)");
    Span totalLabel = new Span("Total Outstanding: " + formatMoney(totalOutstanding));
    totalLabel.getStyle().set("color", "var(--lumo-error-text-color)");

    totalsRow.add(countLabel, totalLabel);

    content.add(dateInfo, grid, totalsRow);

    Button closeBtn = new Button("Close", e -> dialog.close());
    dialog.add(content);
    dialog.getFooter().add(closeBtn);
    dialog.open();
  }

  /**
   * Opens a dialog showing outstanding bills for a specific supplier. Used for drilldown from AP
   * Aging report.
   */
  private void openApAgingDrilldownDialog(Contact supplier, LocalDate asOfDate) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Outstanding Bills: " + supplier.getName());
    dialog.setWidth("800px");
    dialog.setHeight("500px");

    VerticalLayout content = new VerticalLayout();
    content.setSizeFull();
    content.setPadding(false);

    // As of date info
    Span dateInfo = new Span("As of " + asOfDate.format(DATE_FORMAT));
    dateInfo.getStyle().set("color", "var(--lumo-secondary-text-color)");

    // Fetch outstanding bills for this supplier
    Company company = companyContextService.getCurrentCompany();
    List<SupplierBill> bills =
        supplierBillRepository.findOutstandingByCompanyAndContact(company, supplier);

    // Create grid for bills
    Grid<SupplierBill> grid = new Grid<>();
    grid.setSizeFull();
    grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

    grid.addColumn(SupplierBill::getBillNumber)
        .setHeader("Bill #")
        .setAutoWidth(true)
        .setFlexGrow(0);

    grid.addColumn(bill -> bill.getSupplierReference() != null ? bill.getSupplierReference() : "")
        .setHeader("Supplier Ref")
        .setAutoWidth(true)
        .setFlexGrow(0);

    grid.addColumn(bill -> bill.getBillDate().format(DATE_FORMAT))
        .setHeader("Bill Date")
        .setAutoWidth(true)
        .setFlexGrow(0);

    grid.addColumn(bill -> bill.getDueDate().format(DATE_FORMAT))
        .setHeader("Due Date")
        .setAutoWidth(true)
        .setFlexGrow(0);

    grid.addColumn(
            bill -> {
              long daysOverdue =
                  java.time.temporal.ChronoUnit.DAYS.between(bill.getDueDate(), asOfDate);
              if (daysOverdue <= 0) return "Current";
              else if (daysOverdue <= 30) return "1-30 days";
              else if (daysOverdue <= 60) return "31-60 days";
              else if (daysOverdue <= 90) return "61-90 days";
              else return "90+ days";
            })
        .setHeader("Aging")
        .setAutoWidth(true)
        .setFlexGrow(0);

    grid.addColumn(bill -> formatMoney(bill.getTotal()))
        .setHeader("Total")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    grid.addColumn(bill -> formatMoney(bill.getBalance()))
        .setHeader("Balance")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    grid.setItems(bills);

    // Calculate totals
    BigDecimal totalOutstanding =
        bills.stream().map(SupplierBill::getBalance).reduce(BigDecimal.ZERO, BigDecimal::add);

    // Totals row
    HorizontalLayout totalsRow = new HorizontalLayout();
    totalsRow.setWidthFull();
    totalsRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
    totalsRow
        .getStyle()
        .set("font-weight", "bold")
        .set("padding", "8px")
        .set("border-top", "2px solid var(--lumo-contrast-20pct)");

    Span countLabel = new Span(bills.size() + " outstanding bill(s)");
    Span totalLabel = new Span("Total Outstanding: " + formatMoney(totalOutstanding));
    totalLabel.getStyle().set("color", "var(--lumo-error-text-color)");

    totalsRow.add(countLabel, totalLabel);

    content.add(dateInfo, grid, totalsRow);

    Button closeBtn = new Button("Close", e -> dialog.close());
    dialog.add(content);
    dialog.getFooter().add(closeBtn);
    dialog.open();
  }

  /**
   * Opens a dialog showing details of a specific transaction. Used for drilldown from Bank Register
   * report.
   */
  private void openTransactionDrilldownDialog(Long transactionId) {
    transactionService
        .findById(transactionId)
        .ifPresentOrElse(
            transaction -> {
              Dialog dialog = new Dialog();
              dialog.setHeaderTitle("Transaction: " + transaction.getDescription());
              dialog.setWidth("800px");
              dialog.setHeight("500px");

              VerticalLayout content = new VerticalLayout();
              content.setSizeFull();
              content.setPadding(false);

              // Transaction header info
              HorizontalLayout headerRow = new HorizontalLayout();
              headerRow.setWidthFull();
              headerRow.setSpacing(true);
              headerRow
                  .getStyle()
                  .set("padding", "8px")
                  .set("background-color", "var(--lumo-contrast-5pct)")
                  .set("border-radius", "4px");

              Span typeLabel = new Span(transaction.getType().name());
              typeLabel.getElement().getThemeList().add("badge");
              if (transaction.getType() == Transaction.TransactionType.RECEIPT) {
                typeLabel.getElement().getThemeList().add("success");
              } else if (transaction.getType() == Transaction.TransactionType.PAYMENT) {
                typeLabel.getElement().getThemeList().add("error");
              }

              Span dateLabel = new Span(transaction.getTransactionDate().format(DATE_FORMAT));
              Span statusLabel = new Span(transaction.getStatus().name());
              statusLabel.getElement().getThemeList().add("badge");
              if (transaction.getStatus() == Transaction.Status.POSTED) {
                statusLabel.getElement().getThemeList().add("success");
              }

              Span referenceLabel =
                  new Span(
                      "Ref: "
                          + (transaction.getReference() != null
                              ? transaction.getReference()
                              : "-"));
              referenceLabel.getStyle().set("color", "var(--lumo-secondary-text-color)");

              headerRow.add(typeLabel, dateLabel, statusLabel, referenceLabel);

              // Transaction lines grid
              Grid<TransactionLine> grid = new Grid<>();
              grid.setSizeFull();
              grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

              grid.addColumn(line -> line.getAccount().getCode())
                  .setHeader("Account")
                  .setAutoWidth(true)
                  .setFlexGrow(0);

              grid.addColumn(line -> line.getAccount().getName())
                  .setHeader("Account Name")
                  .setFlexGrow(1);

              grid.addColumn(line -> line.getMemo() != null ? line.getMemo() : "")
                  .setHeader("Memo")
                  .setFlexGrow(1);

              grid.addColumn(line -> line.getTaxCode() != null ? line.getTaxCode() : "")
                  .setHeader("Tax")
                  .setAutoWidth(true)
                  .setFlexGrow(0);

              grid.addColumn(
                      line ->
                          line.getDirection() == TransactionLine.Direction.DEBIT
                              ? formatMoney(line.getAmount())
                              : "")
                  .setHeader("Debit")
                  .setAutoWidth(true)
                  .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

              grid.addColumn(
                      line ->
                          line.getDirection() == TransactionLine.Direction.CREDIT
                              ? formatMoney(line.getAmount())
                              : "")
                  .setHeader("Credit")
                  .setAutoWidth(true)
                  .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

              grid.setItems(transaction.getLines());

              // Calculate totals
              BigDecimal totalDebits =
                  transaction.getLines().stream()
                      .filter(line -> line.getDirection() == TransactionLine.Direction.DEBIT)
                      .map(TransactionLine::getAmount)
                      .reduce(BigDecimal.ZERO, BigDecimal::add);
              BigDecimal totalCredits =
                  transaction.getLines().stream()
                      .filter(line -> line.getDirection() == TransactionLine.Direction.CREDIT)
                      .map(TransactionLine::getAmount)
                      .reduce(BigDecimal.ZERO, BigDecimal::add);

              // Totals row
              HorizontalLayout totalsRow = new HorizontalLayout();
              totalsRow.setWidthFull();
              totalsRow.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
              totalsRow
                  .getStyle()
                  .set("font-weight", "bold")
                  .set("padding", "8px")
                  .set("border-top", "2px solid var(--lumo-contrast-20pct)");

              Span lineCount = new Span(transaction.getLines().size() + " line(s)");
              lineCount.getStyle().set("flex-grow", "1");

              Span debitTotal = new Span("Debits: " + formatMoney(totalDebits));
              Span creditTotal = new Span("Credits: " + formatMoney(totalCredits));

              debitTotal.getStyle().set("margin-right", "16px");

              totalsRow.add(lineCount, debitTotal, creditTotal);

              content.add(headerRow, grid, totalsRow);

              Button closeBtn = new Button("Close", e -> dialog.close());
              dialog.add(content);
              dialog.getFooter().add(closeBtn);
              dialog.open();
            },
            () -> {
              Notification.show("Transaction not found", 3000, Notification.Position.BOTTOM_START)
                  .addThemeVariants(NotificationVariant.LUMO_ERROR);
            });
  }

  // ==================== BANK REGISTER TAB ====================

  /**
   * Creates the Bank Register tab layout. The Bank Register shows a chronological list of all
   * transactions for a specific bank account with running balance.
   */
  private VerticalLayout createBankRegisterTab() {
    VerticalLayout layout = new VerticalLayout();
    layout.setSizeFull();
    layout.setPadding(false);

    // Bank account selector
    bankRegisterAccountSelect = new ComboBox<>("Bank Account");
    bankRegisterAccountSelect.setWidth("300px");
    bankRegisterAccountSelect.setPlaceholder("Select a bank account");
    bankRegisterAccountSelect.setItemLabelGenerator(a -> a.getCode() + " - " + a.getName());

    // Load bank accounts
    Company company = companyContextService.getCurrentCompany();
    int securityLevel = companyContextService.getCurrentSecurityLevel();
    List<Account> bankAccounts =
        accountService.findBankAccountsByCompanyWithSecurityLevel(company, securityLevel);
    bankRegisterAccountSelect.setItems(bankAccounts);
    if (!bankAccounts.isEmpty()) {
      bankRegisterAccountSelect.setValue(bankAccounts.get(0));
    }

    // Date pickers
    DatePicker brStartDate = new DatePicker("Start Date");
    DatePicker brEndDate = new DatePicker("End Date");
    brStartDate.setWidth("180px");
    brEndDate.setWidth("180px");

    // Set defaults to current month
    LocalDate today = LocalDate.now();
    brStartDate.setValue(today.withDayOfMonth(1));
    brEndDate.setValue(today);

    Button generateBtn = new Button("Generate Report", VaadinIcon.REFRESH.create());
    generateBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    generateBtn.addClickListener(
        e ->
            loadBankRegister(
                bankRegisterAccountSelect.getValue(),
                brStartDate.getValue(),
                brEndDate.getValue()));

    // Export buttons container
    bankRegisterExportButtons = new HorizontalLayout();
    bankRegisterExportButtons.setSpacing(true);
    bankRegisterExportButtons.setVisible(false);

    HorizontalLayout controls =
        new HorizontalLayout(
            bankRegisterAccountSelect,
            brStartDate,
            brEndDate,
            generateBtn,
            bankRegisterExportButtons);
    controls.setAlignItems(FlexComponent.Alignment.END);
    controls.setSpacing(true);

    // Content area
    bankRegisterContent.setSizeFull();
    bankRegisterContent.setPadding(false);

    // Initial message
    Span initialMessage =
        new Span(
            "Select a bank account and date range, then click Generate Report to view the Bank Register");
    initialMessage.getStyle().set("color", "var(--lumo-secondary-text-color)");
    bankRegisterContent.add(initialMessage);

    layout.add(controls, bankRegisterContent);
    return layout;
  }

  private void loadBankRegister(Account bankAccount, LocalDate startDate, LocalDate endDate) {
    if (bankAccount == null) {
      Notification.show("Please select a bank account", 3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
      return;
    }

    if (startDate == null || endDate == null) {
      Notification.show("Please select a date range", 3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
      return;
    }

    if (startDate.isAfter(endDate)) {
      Notification.show("Start date must be before end date", 3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
      return;
    }

    try {
      Company company = companyContextService.getCurrentCompany();
      int securityLevel = companyContextService.getCurrentSecurityLevel();
      BankRegister report =
          reportingService.generateBankRegister(
              company, bankAccount, startDate, endDate, securityLevel);
      displayBankRegister(report);
    } catch (Exception e) {
      Notification.show(
              "Error generating report: " + e.getMessage(), 3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
  }

  private void displayBankRegister(BankRegister report) {
    bankRegisterContent.removeAll();
    currentBankRegister = report;
    updateBankRegisterExportButtons();

    // Report header
    H3 header = new H3("Bank Register");
    Span accountInfo =
        new Span(
            "Account: " + report.bankAccount().getCode() + " - " + report.bankAccount().getName());
    accountInfo.getStyle().set("font-weight", "bold");

    Span subtitle =
        new Span(
            "Period: "
                + report.startDate().format(DATE_FORMAT)
                + " to "
                + report.endDate().format(DATE_FORMAT));
    subtitle.getStyle().set("color", "var(--lumo-secondary-text-color)");

    // Reconciliation status
    Span status = new Span(report.isReconciled() ? "RECONCILED" : "UNRECONCILED");
    status
        .getStyle()
        .set(
            "color",
            report.isReconciled() ? "var(--lumo-success-color)" : "var(--lumo-error-color)")
        .set("font-weight", "bold")
        .set("margin-left", "16px");

    HorizontalLayout headerRow = new HorizontalLayout(header, status);
    headerRow.setAlignItems(FlexComponent.Alignment.BASELINE);

    // Opening balance display
    HorizontalLayout openingRow = new HorizontalLayout();
    openingRow.setWidthFull();
    openingRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
    openingRow
        .getStyle()
        .set("padding", "8px")
        .set("background-color", "var(--lumo-contrast-5pct)");

    Span openingLabel = new Span("Opening Balance:");
    openingLabel.getStyle().set("font-weight", "bold");
    Span openingAmount = new Span(formatMoney(report.openingBalance()));
    openingRow.add(openingLabel, openingAmount);

    // Transactions grid
    Grid<BankRegisterLine> grid = new Grid<>();
    grid.setSizeFull();
    grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

    grid.addColumn(line -> line.date().format(DATE_FORMAT))
        .setHeader("Date")
        .setAutoWidth(true)
        .setFlexGrow(0);

    grid.addColumn(BankRegisterLine::reference)
        .setHeader("Reference")
        .setAutoWidth(true)
        .setFlexGrow(0);

    grid.addColumn(BankRegisterLine::description).setHeader("Description").setFlexGrow(1);

    grid.addColumn(BankRegisterLine::transactionType)
        .setHeader("Type")
        .setAutoWidth(true)
        .setFlexGrow(0);

    grid.addColumn(line -> line.debit() != null ? formatMoney(line.debit()) : "")
        .setHeader("Debit")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    grid.addColumn(line -> line.credit() != null ? formatMoney(line.credit()) : "")
        .setHeader("Credit")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    grid.addColumn(line -> formatMoney(line.runningBalance()))
        .setHeader("Balance")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    grid.setItems(report.lines());

    // Enable drilldown to transaction on row click
    grid.addItemClickListener(
        event -> {
          BankRegisterLine line = event.getItem();
          if (line.transactionId() != null) {
            openTransactionDrilldownDialog(line.transactionId());
          }
        });
    grid.getStyle().set("cursor", "pointer");

    // Totals row
    HorizontalLayout totalsRow = new HorizontalLayout();
    totalsRow.setWidthFull();
    totalsRow.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
    totalsRow
        .getStyle()
        .set("font-weight", "bold")
        .set("padding", "8px")
        .set("border-top", "2px solid var(--lumo-contrast-20pct)");

    Span countLabel = new Span(report.lines().size() + " transactions");
    countLabel.getStyle().set("flex-grow", "1");

    Span totalDebits = new Span("Total Debits: " + formatMoney(report.totalDebits()));
    Span totalCredits = new Span("Total Credits: " + formatMoney(report.totalCredits()));

    totalDebits.getStyle().set("margin-right", "16px").set("color", "var(--lumo-success-color)");
    totalCredits.getStyle().set("margin-right", "16px").set("color", "var(--lumo-error-color)");

    totalsRow.add(countLabel, totalDebits, totalCredits);

    // Closing balance display
    HorizontalLayout closingRow = new HorizontalLayout();
    closingRow.setWidthFull();
    closingRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
    closingRow
        .getStyle()
        .set("padding", "12px")
        .set("background-color", "var(--lumo-contrast-5pct)")
        .set("border-top", "2px solid var(--lumo-contrast-20pct)")
        .set("font-weight", "bold")
        .set("font-size", "var(--lumo-font-size-l)");

    Span closingLabel = new Span("Closing Balance:");
    Span closingAmount = new Span(formatMoney(report.closingBalance()));
    closingRow.add(closingLabel, closingAmount);

    // Net change summary
    HorizontalLayout summaryRow = new HorizontalLayout();
    summaryRow.setWidthFull();
    summaryRow.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
    summaryRow.getStyle().set("padding", "8px");

    Span netChangeLabel = new Span("Net Change: " + formatMoney(report.netChange()));
    if (report.netChange().compareTo(BigDecimal.ZERO) >= 0) {
      netChangeLabel.getStyle().set("color", "var(--lumo-success-color)");
    } else {
      netChangeLabel.getStyle().set("color", "var(--lumo-error-color)");
    }
    summaryRow.add(netChangeLabel);

    bankRegisterContent.add(
        headerRow, accountInfo, subtitle, openingRow, grid, totalsRow, closingRow, summaryRow);
  }

  private void updateBankRegisterExportButtons() {
    bankRegisterExportButtons.removeAll();
    if (currentBankRegister == null) {
      bankRegisterExportButtons.setVisible(false);
      return;
    }

    Company company = companyContextService.getCurrentCompany();
    String dateStr = currentBankRegister.endDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    String accountCode = currentBankRegister.bankAccount().getCode().replaceAll("[^a-zA-Z0-9]", "");

    // PDF Export
    StreamResource pdfResource =
        new StreamResource(
            "BankRegister_" + accountCode + "_" + dateStr + ".pdf",
            () ->
                new ByteArrayInputStream(
                    reportExportService.exportBankRegisterToPdf(currentBankRegister, company)));
    Anchor pdfLink = new Anchor(pdfResource, "");
    pdfLink.getElement().setAttribute("download", true);
    Button pdfBtn = new Button("PDF", VaadinIcon.FILE_TEXT.create());
    pdfBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
    pdfLink.add(pdfBtn);

    // Excel Export
    StreamResource excelResource =
        new StreamResource(
            "BankRegister_" + accountCode + "_" + dateStr + ".xlsx",
            () ->
                new ByteArrayInputStream(
                    reportExportService.exportBankRegisterToExcel(currentBankRegister, company)));
    Anchor excelLink = new Anchor(excelResource, "");
    excelLink.getElement().setAttribute("download", true);
    Button excelBtn = new Button("Excel", VaadinIcon.FILE_TABLE.create());
    excelBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
    excelLink.add(excelBtn);

    // CSV Export
    StreamResource csvResource =
        new StreamResource(
            "BankRegister_" + accountCode + "_" + dateStr + ".csv",
            () ->
                new ByteArrayInputStream(
                    reportExportService.exportBankRegisterToCsv(currentBankRegister, company)));
    Anchor csvLink = new Anchor(csvResource, "");
    csvLink.getElement().setAttribute("download", true);
    Button csvBtn = new Button("CSV", VaadinIcon.FILE.create());
    csvBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
    csvLink.add(csvBtn);

    bankRegisterExportButtons.add(pdfLink, excelLink, csvLink);
    bankRegisterExportButtons.setVisible(true);
  }

  private VerticalLayout createReconciliationStatusTab() {
    VerticalLayout layout = new VerticalLayout();
    layout.setSizeFull();
    layout.setPadding(false);

    Button generateBtn = new Button("Generate Report", VaadinIcon.REFRESH.create());
    generateBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    generateBtn.addClickListener(e -> loadReconciliationStatus());

    // Export buttons container
    reconciliationStatusExportButtons = new HorizontalLayout();
    reconciliationStatusExportButtons.setSpacing(true);
    reconciliationStatusExportButtons.setVisible(false);

    HorizontalLayout controls =
        new HorizontalLayout(generateBtn, reconciliationStatusExportButtons);
    controls.setAlignItems(FlexComponent.Alignment.BASELINE);
    controls.setSpacing(true);

    // Content area
    reconciliationStatusContent.setSizeFull();
    reconciliationStatusContent.setPadding(false);

    // Initial message
    Span initialMessage =
        new Span("Click Generate Report to view the reconciliation status of all bank accounts");
    initialMessage.getStyle().set("color", "var(--lumo-secondary-text-color)");
    reconciliationStatusContent.add(initialMessage);

    layout.add(controls, reconciliationStatusContent);
    return layout;
  }

  private void loadReconciliationStatus() {
    try {
      Company company = companyContextService.getCurrentCompany();
      int securityLevel = companyContextService.getCurrentSecurityLevel();
      ReconciliationStatus report =
          reportingService.generateReconciliationStatus(company, securityLevel);
      displayReconciliationStatus(report);
    } catch (Exception e) {
      Notification.show(
              "Error generating report: " + e.getMessage(), 3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
  }

  private void displayReconciliationStatus(ReconciliationStatus report) {
    reconciliationStatusContent.removeAll();
    currentReconciliationStatus = report;
    updateReconciliationStatusExportButtons();

    // Report header
    H3 header = new H3("Bank Reconciliation Status");
    Span subtitle = new Span("As of: " + report.asOfDate().format(DATE_FORMAT));
    subtitle.getStyle().set("color", "var(--lumo-secondary-text-color)");

    // Overall status indicator
    boolean isFullyReconciled =
        report.grandTotal() > 0
            && report.overallReconciledPercent().compareTo(new BigDecimal("100")) >= 0;
    Span overallStatus = new Span(isFullyReconciled ? "FULLY RECONCILED" : "ITEMS PENDING");
    overallStatus.getStyle().set("font-weight", "bold");
    overallStatus.getStyle().set("padding", "4px 8px");
    overallStatus.getStyle().set("border-radius", "4px");
    if (isFullyReconciled) {
      overallStatus.getStyle().set("background-color", "var(--lumo-success-color-10pct)");
      overallStatus.getStyle().set("color", "var(--lumo-success-text-color)");
    } else {
      overallStatus.getStyle().set("background-color", "var(--lumo-warning-color-10pct)");
      overallStatus.getStyle().set("color", "var(--lumo-warning-text-color)");
    }

    HorizontalLayout headerRow = new HorizontalLayout(header, overallStatus);
    headerRow.setAlignItems(FlexComponent.Alignment.BASELINE);

    // Summary statistics
    HorizontalLayout summaryRow = new HorizontalLayout();
    summaryRow.setWidthFull();
    summaryRow.setSpacing(true);
    summaryRow
        .getStyle()
        .set("padding", "12px")
        .set("background-color", "var(--lumo-contrast-5pct)");

    summaryRow.add(createStatBox("Total Items", String.valueOf(report.grandTotal())));
    summaryRow.add(
        createStatBox("New", String.valueOf(report.totalNew()), "var(--lumo-primary-color)"));
    summaryRow.add(
        createStatBox(
            "Matched", String.valueOf(report.totalMatched()), "var(--lumo-success-color)"));
    summaryRow.add(
        createStatBox(
            "Created", String.valueOf(report.totalCreated()), "var(--lumo-success-color)"));
    summaryRow.add(
        createStatBox(
            "Ignored", String.valueOf(report.totalIgnored()), "var(--lumo-secondary-text-color)"));
    summaryRow.add(
        createStatBox(
            "Unreconciled Amount",
            formatMoney(report.totalUnreconciledAmount()),
            report.totalUnreconciledAmount().compareTo(BigDecimal.ZERO) > 0
                ? "var(--lumo-warning-color)"
                : "var(--lumo-success-color)"));
    summaryRow.add(
        createStatBox(
            "Reconciled %",
            report.overallReconciledPercent().setScale(1, java.math.RoundingMode.HALF_UP) + "%",
            report.overallReconciledPercent().compareTo(new BigDecimal("100")) >= 0
                ? "var(--lumo-success-color)"
                : "var(--lumo-warning-color)"));

    // Account details grid
    Grid<ReconciliationAccountSummary> grid = new Grid<>();
    grid.setSizeFull();
    grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

    grid.addColumn(summary -> summary.account().getCode())
        .setHeader("Account")
        .setAutoWidth(true)
        .setFlexGrow(0);

    grid.addColumn(summary -> summary.account().getName()).setHeader("Name").setFlexGrow(1);

    grid.addColumn(ReconciliationAccountSummary::newCount)
        .setHeader("New")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    grid.addColumn(ReconciliationAccountSummary::matchedCount)
        .setHeader("Matched")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    grid.addColumn(ReconciliationAccountSummary::createdCount)
        .setHeader("Created")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    grid.addColumn(ReconciliationAccountSummary::ignoredCount)
        .setHeader("Ignored")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    grid.addColumn(ReconciliationAccountSummary::totalItems)
        .setHeader("Total")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    grid.addColumn(summary -> formatMoney(summary.unreconciledAmount()))
        .setHeader("Unreconciled $")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    grid.addColumn(
            summary ->
                summary.reconciledPercent().setScale(1, java.math.RoundingMode.HALF_UP) + "%")
        .setHeader("Reconciled %")
        .setAutoWidth(true)
        .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

    grid.addColumn(
            summary ->
                summary.oldestUnmatchedDate() != null
                    ? summary.oldestUnmatchedDate().format(DATE_FORMAT)
                    : "-")
        .setHeader("Oldest Pending")
        .setAutoWidth(true);

    grid.setItems(report.accountSummaries());

    reconciliationStatusContent.add(headerRow, subtitle, summaryRow, grid);
  }

  private VerticalLayout createStatBox(String label, String value) {
    return createStatBox(label, value, null);
  }

  private VerticalLayout createStatBox(String label, String value, String color) {
    VerticalLayout box = new VerticalLayout();
    box.setSpacing(false);
    box.setPadding(false);
    box.setAlignItems(FlexComponent.Alignment.CENTER);

    Span labelSpan = new Span(label);
    labelSpan.getStyle().set("font-size", "var(--lumo-font-size-s)");
    labelSpan.getStyle().set("color", "var(--lumo-secondary-text-color)");

    Span valueSpan = new Span(value);
    valueSpan.getStyle().set("font-size", "var(--lumo-font-size-l)");
    valueSpan.getStyle().set("font-weight", "bold");
    if (color != null) {
      valueSpan.getStyle().set("color", color);
    }

    box.add(labelSpan, valueSpan);
    return box;
  }

  private void updateReconciliationStatusExportButtons() {
    reconciliationStatusExportButtons.removeAll();

    if (currentReconciliationStatus == null) {
      return;
    }

    Company company = companyContextService.getCurrentCompany();
    String dateStr =
        currentReconciliationStatus.asOfDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

    // PDF Export
    StreamResource pdfResource =
        new StreamResource(
            "ReconciliationStatus_" + dateStr + ".pdf",
            () ->
                new ByteArrayInputStream(
                    reportExportService.exportReconciliationStatusToPdf(
                        currentReconciliationStatus, company)));
    Anchor pdfLink = new Anchor(pdfResource, "");
    pdfLink.getElement().setAttribute("download", true);
    Button pdfBtn = new Button("PDF", VaadinIcon.FILE_TEXT.create());
    pdfBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
    pdfLink.add(pdfBtn);

    // Excel Export
    StreamResource excelResource =
        new StreamResource(
            "ReconciliationStatus_" + dateStr + ".xlsx",
            () ->
                new ByteArrayInputStream(
                    reportExportService.exportReconciliationStatusToExcel(
                        currentReconciliationStatus, company)));
    Anchor excelLink = new Anchor(excelResource, "");
    excelLink.getElement().setAttribute("download", true);
    Button excelBtn = new Button("Excel", VaadinIcon.FILE_TABLE.create());
    excelBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
    excelLink.add(excelBtn);

    // CSV Export
    StreamResource csvResource =
        new StreamResource(
            "ReconciliationStatus_" + dateStr + ".csv",
            () ->
                new ByteArrayInputStream(
                    reportExportService.exportReconciliationStatusToCsv(
                        currentReconciliationStatus, company)));
    Anchor csvLink = new Anchor(csvResource, "");
    csvLink.getElement().setAttribute("download", true);
    Button csvBtn = new Button("CSV", VaadinIcon.FILE.create());
    csvBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
    csvLink.add(csvBtn);

    reconciliationStatusExportButtons.add(pdfLink, excelLink, csvLink);
    reconciliationStatusExportButtons.setVisible(true);
  }
}
