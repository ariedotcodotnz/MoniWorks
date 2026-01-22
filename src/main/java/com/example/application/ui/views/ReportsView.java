package com.example.application.ui.views;

import com.example.application.domain.Budget;
import com.example.application.domain.Company;
import com.example.application.domain.Department;
import com.example.application.service.*;
import com.example.application.service.ReportingService.*;
import com.example.application.ui.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
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
import jakarta.annotation.security.PermitAll;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * View for displaying financial reports.
 * Provides Trial Balance, Profit & Loss, and Balance Sheet reports
 * with configurable date ranges.
 */
@Route(value = "reports", layout = MainLayout.class)
@PageTitle("Financial Reports | MoniWorks")
@PermitAll
public class ReportsView extends VerticalLayout {

    private final ReportingService reportingService;
    private final CompanyContextService companyContextService;
    private final FiscalYearService fiscalYearService;
    private final DepartmentService departmentService;
    private final BudgetService budgetService;

    private final DatePicker startDatePicker = new DatePicker("Start Date");
    private final DatePicker endDatePicker = new DatePicker("End Date");
    private final DatePicker asOfDatePicker = new DatePicker("As Of Date");

    private final VerticalLayout trialBalanceContent = new VerticalLayout();
    private final VerticalLayout profitLossContent = new VerticalLayout();
    private final VerticalLayout balanceSheetContent = new VerticalLayout();
    private final VerticalLayout budgetVsActualContent = new VerticalLayout();

    // Department filter for P&L
    private ComboBox<Department> plDepartmentFilter;

    // Budget vs Actual controls
    private ComboBox<Budget> bvaBudgetSelect;
    private ComboBox<Department> bvaDepartmentFilter;

    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0.00");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public ReportsView(ReportingService reportingService,
                       CompanyContextService companyContextService,
                       FiscalYearService fiscalYearService,
                       DepartmentService departmentService,
                       BudgetService budgetService) {
        this.reportingService = reportingService;
        this.companyContextService = companyContextService;
        this.fiscalYearService = fiscalYearService;
        this.departmentService = departmentService;
        this.budgetService = budgetService;

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
        fiscalYearService.findByCompanyAndDate(company, today)
            .ifPresentOrElse(fiscalYear -> {
                startDatePicker.setValue(fiscalYear.getStartDate());
                endDatePicker.setValue(today);
                asOfDatePicker.setValue(today);
            }, () -> {
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

        HorizontalLayout controls = new HorizontalLayout(startDatePicker, endDatePicker, generateBtn);
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

        HorizontalLayout controls = new HorizontalLayout(plStartDate, plEndDate, plDepartmentFilter, generateBtn);
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

        HorizontalLayout controls = new HorizontalLayout(asOfDatePicker, generateBtn);
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
            TrialBalance report = reportingService.generateTrialBalance(
                company, startDatePicker.getValue(), endDatePicker.getValue());

            displayTrialBalance(report);
        } catch (Exception e) {
            Notification.show("Error generating report: " + e.getMessage(),
                3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void displayTrialBalance(TrialBalance report) {
        trialBalanceContent.removeAll();

        // Report header
        H3 header = new H3("Trial Balance");
        Span dateRange = new Span("Period: " + report.startDate().format(DATE_FORMAT) +
            " to " + report.endDate().format(DATE_FORMAT));
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

        grid.addColumn(line -> line.account().getName())
            .setHeader("Account")
            .setFlexGrow(1);

        grid.addColumn(line -> formatMoney(line.debits()))
            .setHeader("Debits")
            .setAutoWidth(true)
            .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

        grid.addColumn(line -> formatMoney(line.credits()))
            .setHeader("Credits")
            .setAutoWidth(true)
            .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

        grid.setItems(report.lines());

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

            ProfitAndLoss report = reportingService.generateProfitAndLoss(
                company, startDatePicker.getValue(), endDatePicker.getValue(), department);

            displayProfitAndLoss(report);
        } catch (Exception e) {
            Notification.show("Error generating report: " + e.getMessage(),
                3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void displayProfitAndLoss(ProfitAndLoss report) {
        profitLossContent.removeAll();

        // Report header
        H3 header = new H3("Profit & Loss Statement");
        String dateRangeText = "Period: " + report.startDate().format(DATE_FORMAT) +
            " to " + report.endDate().format(DATE_FORMAT);
        if (report.department() != null) {
            dateRangeText += " | Department: " + report.department().getCode() +
                " - " + report.department().getName();
        }
        Span dateRange = new Span(dateRangeText);
        dateRange.getStyle().set("color", "var(--lumo-secondary-text-color)");

        // Income section
        H3 incomeHeader = new H3("Income");
        incomeHeader.getStyle().set("margin-top", "16px");

        Grid<ProfitAndLossLine> incomeGrid = createPLGrid();
        incomeGrid.setItems(report.incomeLines());
        incomeGrid.setAllRowsVisible(true);

        Span totalIncome = createTotalLine("Total Income", report.totalIncome());

        // Expenses section
        H3 expenseHeader = new H3("Expenses");
        expenseHeader.getStyle().set("margin-top", "16px");

        Grid<ProfitAndLossLine> expenseGrid = createPLGrid();
        expenseGrid.setItems(report.expenseLines());
        expenseGrid.setAllRowsVisible(true);

        Span totalExpenses = createTotalLine("Total Expenses", report.totalExpenses());

        // Net profit
        HorizontalLayout netProfitRow = new HorizontalLayout();
        netProfitRow.setWidthFull();
        netProfitRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        netProfitRow.getStyle()
            .set("font-weight", "bold")
            .set("font-size", "var(--lumo-font-size-l)")
            .set("padding", "12px")
            .set("margin-top", "16px")
            .set("border-top", "3px solid var(--lumo-contrast-30pct)")
            .set("background-color", "var(--lumo-contrast-5pct)");

        Span netProfitLabel = new Span(report.netProfit().compareTo(BigDecimal.ZERO) >= 0 ?
            "Net Profit" : "Net Loss");
        Span netProfitAmount = new Span(formatMoney(report.netProfit().abs()));

        if (report.netProfit().compareTo(BigDecimal.ZERO) >= 0) {
            netProfitAmount.getStyle().set("color", "var(--lumo-success-text-color)");
        } else {
            netProfitAmount.getStyle().set("color", "var(--lumo-error-text-color)");
        }

        netProfitRow.add(netProfitLabel, netProfitAmount);

        profitLossContent.add(header, dateRange,
            incomeHeader, incomeGrid, totalIncome,
            expenseHeader, expenseGrid, totalExpenses,
            netProfitRow);
    }

    private Grid<ProfitAndLossLine> createPLGrid() {
        Grid<ProfitAndLossLine> grid = new Grid<>();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

        grid.addColumn(line -> line.account().getCode())
            .setHeader("Code")
            .setAutoWidth(true)
            .setFlexGrow(0);

        grid.addColumn(line -> line.account().getName())
            .setHeader("Account")
            .setFlexGrow(1);

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
            BalanceSheet report = reportingService.generateBalanceSheet(
                company, asOfDatePicker.getValue());

            displayBalanceSheet(report);
        } catch (Exception e) {
            Notification.show("Error generating report: " + e.getMessage(),
                3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void displayBalanceSheet(BalanceSheet report) {
        balanceSheetContent.removeAll();

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

        Span totalAssets = createTotalLine("Total Assets", report.totalAssets());

        // Liabilities section
        H3 liabilitiesHeader = new H3("Liabilities");
        liabilitiesHeader.getStyle().set("margin-top", "16px");

        Grid<BalanceSheetLine> liabilitiesGrid = createBSGrid();
        liabilitiesGrid.setItems(report.liabilities());
        liabilitiesGrid.setAllRowsVisible(true);

        Span totalLiabilities = createTotalLine("Total Liabilities", report.totalLiabilities());

        // Equity section
        H3 equityHeader = new H3("Equity");
        equityHeader.getStyle().set("margin-top", "16px");

        Grid<BalanceSheetLine> equityGrid = createBSGrid();
        equityGrid.setItems(report.equity());
        equityGrid.setAllRowsVisible(true);

        Span totalEquity = createTotalLine("Total Equity", report.totalEquity());

        // Total Liabilities + Equity
        BigDecimal totalLiabilitiesAndEquity = report.totalLiabilities().add(report.totalEquity());
        HorizontalLayout totalRow = new HorizontalLayout();
        totalRow.setWidthFull();
        totalRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        totalRow.getStyle()
            .set("font-weight", "bold")
            .set("font-size", "var(--lumo-font-size-l)")
            .set("padding", "12px")
            .set("margin-top", "16px")
            .set("border-top", "3px solid var(--lumo-contrast-30pct)")
            .set("background-color", "var(--lumo-contrast-5pct)");

        Span totalLabel = new Span("Total Liabilities + Equity");
        Span totalAmount = new Span(formatMoney(totalLiabilitiesAndEquity));
        totalRow.add(totalLabel, totalAmount);

        balanceSheetContent.add(headerRow, asOfDate,
            assetsHeader, assetsGrid, totalAssets,
            liabilitiesHeader, liabilitiesGrid, totalLiabilities,
            equityHeader, equityGrid, totalEquity,
            totalRow);
    }

    private Grid<BalanceSheetLine> createBSGrid() {
        Grid<BalanceSheetLine> grid = new Grid<>();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

        grid.addColumn(line -> line.account().getCode())
            .setHeader("Code")
            .setAutoWidth(true)
            .setFlexGrow(0);

        grid.addColumn(line -> line.account().getName())
            .setHeader("Account")
            .setFlexGrow(1);

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

        HorizontalLayout controls = new HorizontalLayout(bvaStartDate, bvaEndDate,
            bvaBudgetSelect, bvaDepartmentFilter, generateBtn);
        controls.setAlignItems(FlexComponent.Alignment.BASELINE);
        controls.setSpacing(true);

        budgetVsActualContent.setSizeFull();
        budgetVsActualContent.setPadding(false);

        layout.add(controls, budgetVsActualContent);
        return layout;
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

            BudgetVsActual report = reportingService.generateBudgetVsActual(
                company, budget, startDatePicker.getValue(), endDatePicker.getValue(), department);

            displayBudgetVsActual(report);
        } catch (Exception e) {
            Notification.show("Error generating report: " + e.getMessage(),
                3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void displayBudgetVsActual(BudgetVsActual report) {
        budgetVsActualContent.removeAll();

        // Report header
        H3 header = new H3("Budget vs Actual Report");
        String headerText = "Budget: " + report.budget().getName() + " | Period: " +
            report.startDate().format(DATE_FORMAT) + " to " + report.endDate().format(DATE_FORMAT);
        if (report.department() != null) {
            headerText += " | Department: " + report.department().getCode() +
                " - " + report.department().getName();
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

        grid.addColumn(line -> line.account().getName())
            .setHeader("Account")
            .setFlexGrow(1);

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
}
