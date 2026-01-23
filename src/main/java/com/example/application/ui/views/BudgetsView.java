package com.example.application.ui.views;

import com.example.application.domain.*;
import com.example.application.service.*;
import com.example.application.service.BudgetImportService.ImportResult;
import com.example.application.ui.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
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
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import jakarta.annotation.security.PermitAll;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * View for managing Budgets.
 * Allows creating, editing budgets and entering budget lines per account/period.
 */
@Route(value = "budgets", layout = MainLayout.class)
@PageTitle("Budgets | MoniWorks")
@PermitAll
public class BudgetsView extends VerticalLayout {

    private final BudgetService budgetService;
    private final CompanyContextService companyContextService;
    private final FiscalYearService fiscalYearService;
    private final AccountService accountService;
    private final DepartmentService departmentService;
    private final BudgetImportService budgetImportService;

    private final Grid<Budget> budgetGrid = new Grid<>();
    private final Grid<BudgetLine> lineGrid = new Grid<>();
    private final VerticalLayout detailPanel = new VerticalLayout();

    private Budget selectedBudget;
    private FiscalYear selectedFiscalYear;

    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(Locale.US);
    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("MMM yyyy");

    public BudgetsView(BudgetService budgetService,
                       CompanyContextService companyContextService,
                       FiscalYearService fiscalYearService,
                       AccountService accountService,
                       DepartmentService departmentService,
                       BudgetImportService budgetImportService) {
        this.budgetService = budgetService;
        this.companyContextService = companyContextService;
        this.fiscalYearService = fiscalYearService;
        this.accountService = accountService;
        this.departmentService = departmentService;
        this.budgetImportService = budgetImportService;

        addClassName("budgets-view");
        setSizeFull();

        SplitLayout splitLayout = new SplitLayout();
        splitLayout.setSizeFull();
        splitLayout.setSplitterPosition(35);

        VerticalLayout masterPanel = createMasterPanel();
        splitLayout.addToPrimary(masterPanel);
        splitLayout.addToSecondary(detailPanel);

        add(splitLayout);
        loadBudgets();
    }

    private VerticalLayout createMasterPanel() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setPadding(false);
        layout.setSpacing(false);

        HorizontalLayout toolbar = createToolbar();
        configureGrid();

        layout.add(toolbar, budgetGrid);
        layout.setFlexGrow(1, budgetGrid);

        return layout;
    }

    private void configureGrid() {
        budgetGrid.addClassNames("budgets-grid");
        budgetGrid.setSizeFull();
        budgetGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        budgetGrid.addColumn(Budget::getName)
            .setHeader("Name")
            .setSortable(true)
            .setFlexGrow(1);

        budgetGrid.addColumn(b -> b.getType().name())
            .setHeader("Type")
            .setSortable(true)
            .setAutoWidth(true);

        budgetGrid.addColumn(b -> b.getCurrency() != null ? b.getCurrency() : "NZD")
            .setHeader("Currency")
            .setAutoWidth(true);

        budgetGrid.addColumn(b -> b.isActive() ? "Active" : "Inactive")
            .setHeader("Status")
            .setAutoWidth(true);

        budgetGrid.asSingleSelect().addValueChangeListener(e -> {
            selectedBudget = e.getValue();
            updateDetailPanel();
        });
    }

    private HorizontalLayout createToolbar() {
        H2 title = new H2("Budgets");

        Button addButton = new Button("Add Budget", VaadinIcon.PLUS.create());
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addButton.addClickListener(e -> openBudgetDialog(null));

        Button refreshButton = new Button(VaadinIcon.REFRESH.create());
        refreshButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        refreshButton.addClickListener(e -> loadBudgets());
        refreshButton.getElement().setAttribute("title", "Refresh");

        HorizontalLayout actions = new HorizontalLayout(addButton, refreshButton);
        actions.setAlignItems(FlexComponent.Alignment.BASELINE);

        HorizontalLayout toolbar = new HorizontalLayout(title, actions);
        toolbar.setWidthFull();
        toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        toolbar.setAlignItems(FlexComponent.Alignment.CENTER);
        toolbar.setPadding(true);

        return toolbar;
    }

    private void loadBudgets() {
        Company company = companyContextService.getCurrentCompany();
        List<Budget> budgets = budgetService.findByCompany(company);
        budgetGrid.setItems(budgets);
    }

    private void updateDetailPanel() {
        detailPanel.removeAll();
        detailPanel.setPadding(true);
        detailPanel.setSpacing(true);

        if (selectedBudget == null) {
            detailPanel.add(new Span("Select a budget to view details"));
            return;
        }

        // Header with budget info and actions
        H3 header = new H3(selectedBudget.getName() + " (Type " + selectedBudget.getType() + ")");

        Button editBtn = new Button("Edit", VaadinIcon.EDIT.create());
        editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        editBtn.addClickListener(e -> openBudgetDialog(selectedBudget));

        Button addLineBtn = new Button("Add Line", VaadinIcon.PLUS.create());
        addLineBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addLineBtn.addClickListener(e -> openBudgetLineDialog(null));

        Button importBtn = new Button("Import CSV", VaadinIcon.UPLOAD.create());
        importBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        importBtn.addClickListener(e -> openImportDialog());

        HorizontalLayout headerLayout = new HorizontalLayout(header, editBtn, addLineBtn, importBtn);
        headerLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        headerLayout.setWidthFull();
        headerLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        // Fiscal year selector
        Company company = companyContextService.getCurrentCompany();
        List<FiscalYear> fiscalYears = fiscalYearService.findByCompany(company);

        ComboBox<FiscalYear> fiscalYearCombo = new ComboBox<>("Fiscal Year");
        fiscalYearCombo.setItems(fiscalYears);
        fiscalYearCombo.setItemLabelGenerator(FiscalYear::getLabel);
        fiscalYearCombo.setWidth("200px");

        if (!fiscalYears.isEmpty()) {
            // Try to find current fiscal year (contains today's date), otherwise use most recent
            LocalDate today = LocalDate.now();
            FiscalYear currentFy = fiscalYears.stream()
                .filter(fy -> !today.isBefore(fy.getStartDate()) && !today.isAfter(fy.getEndDate()))
                .findFirst()
                .orElse(fiscalYears.get(0));
            fiscalYearCombo.setValue(currentFy);
            selectedFiscalYear = currentFy;
        }

        fiscalYearCombo.addValueChangeListener(e -> {
            selectedFiscalYear = e.getValue();
            loadBudgetLines();
        });

        // Budget lines grid
        configureLineGrid();
        loadBudgetLines();

        detailPanel.add(headerLayout, fiscalYearCombo, lineGrid);
        detailPanel.setFlexGrow(1, lineGrid);
    }

    private void configureLineGrid() {
        lineGrid.removeAllColumns();
        lineGrid.addClassNames("budget-lines-grid");
        lineGrid.setSizeFull();
        lineGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

        lineGrid.addColumn(bl -> formatPeriod(bl.getPeriod()))
            .setHeader("Period")
            .setSortable(true)
            .setAutoWidth(true);

        lineGrid.addColumn(bl -> bl.getAccount().getCode())
            .setHeader("Account")
            .setSortable(true)
            .setAutoWidth(true);

        lineGrid.addColumn(bl -> bl.getAccount().getName())
            .setHeader("Account Name")
            .setFlexGrow(1);

        lineGrid.addColumn(bl -> bl.getDepartment() != null ? bl.getDepartment().getCode() : "")
            .setHeader("Dept")
            .setAutoWidth(true);

        lineGrid.addColumn(bl -> formatCurrency(bl.getAmount()))
            .setHeader("Amount")
            .setSortable(true)
            .setAutoWidth(true);

        lineGrid.addComponentColumn(this::createLineActionButtons)
            .setHeader("Actions")
            .setAutoWidth(true)
            .setFlexGrow(0);
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "";
        return CURRENCY_FORMAT.format(amount);
    }

    private String formatPeriod(Period period) {
        if (period == null) return "";
        return PERIOD_FORMAT.format(period.getStartDate());
    }

    private HorizontalLayout createLineActionButtons(BudgetLine line) {
        Button editBtn = new Button(VaadinIcon.EDIT.create());
        editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        editBtn.addClickListener(e -> openBudgetLineDialog(line));
        editBtn.getElement().setAttribute("title", "Edit line");

        Button deleteBtn = new Button(VaadinIcon.TRASH.create());
        deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
        deleteBtn.addClickListener(e -> deleteBudgetLine(line));
        deleteBtn.getElement().setAttribute("title", "Delete line");

        HorizontalLayout actions = new HorizontalLayout(editBtn, deleteBtn);
        actions.setSpacing(false);
        actions.setPadding(false);
        return actions;
    }

    private void loadBudgetLines() {
        if (selectedBudget == null || selectedFiscalYear == null) {
            lineGrid.setItems(List.of());
            return;
        }

        List<BudgetLine> lines = budgetService.findLinesByBudgetAndFiscalYear(
            selectedBudget, selectedFiscalYear.getId());
        lineGrid.setItems(lines);
    }

    private void openBudgetDialog(Budget budget) {
        boolean isNew = budget == null;

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(isNew ? "Add Budget" : "Edit Budget");
        dialog.setWidth("500px");

        FormLayout form = new FormLayout();

        TextField nameField = new TextField("Name");
        nameField.setMaxLength(100);
        nameField.setRequired(true);
        if (!isNew) {
            nameField.setValue(budget.getName());
        }

        ComboBox<Budget.BudgetType> typeCombo = new ComboBox<>("Type");
        typeCombo.setItems(Budget.BudgetType.values());
        typeCombo.setRequired(true);
        if (!isNew) {
            typeCombo.setValue(budget.getType());
            typeCombo.setReadOnly(true);
        } else {
            typeCombo.setValue(Budget.BudgetType.A);
        }

        TextField currencyField = new TextField("Currency");
        currencyField.setMaxLength(3);
        currencyField.setHelperText("3-letter currency code (e.g., NZD, USD)");
        if (!isNew && budget.getCurrency() != null) {
            currencyField.setValue(budget.getCurrency());
        } else {
            currencyField.setValue("NZD");
        }

        Checkbox activeCheckbox = new Checkbox("Active");
        activeCheckbox.setValue(isNew || budget.isActive());

        form.add(nameField, typeCombo, currencyField, activeCheckbox);
        form.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 2)
        );

        Button saveButton = new Button("Save");
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.addClickListener(e -> {
            if (nameField.isEmpty() || typeCombo.isEmpty()) {
                Notification.show("Please fill in all required fields", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                Company company = companyContextService.getCurrentCompany();

                if (isNew) {
                    Budget newBudget = budgetService.createBudget(
                        company,
                        nameField.getValue().trim(),
                        typeCombo.getValue(),
                        null
                    );
                    newBudget.setCurrency(currencyField.getValue().trim().toUpperCase());
                    newBudget.setActive(activeCheckbox.getValue());
                    budgetService.save(newBudget, null);

                    Notification.show("Budget created successfully", 3000, Notification.Position.BOTTOM_START)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                } else {
                    budget.setName(nameField.getValue().trim());
                    budget.setCurrency(currencyField.getValue().trim().toUpperCase());
                    budget.setActive(activeCheckbox.getValue());
                    budgetService.save(budget, null);

                    Notification.show("Budget updated successfully", 3000, Notification.Position.BOTTOM_START)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                }

                dialog.close();
                loadBudgets();
                if (selectedBudget != null) {
                    updateDetailPanel();
                }
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage(), 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        Button cancelButton = new Button("Cancel", e -> dialog.close());

        dialog.add(form);
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private void openBudgetLineDialog(BudgetLine line) {
        if (selectedBudget == null) {
            Notification.show("Please select a budget first", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        boolean isNew = line == null;

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(isNew ? "Add Budget Line" : "Edit Budget Line");
        dialog.setWidth("600px");

        FormLayout form = new FormLayout();

        Company company = companyContextService.getCurrentCompany();

        // Period selector
        List<Period> periods = selectedFiscalYear != null
            ? fiscalYearService.findById(selectedFiscalYear.getId())
                .map(fy -> fy.getPeriods().stream().toList())
                .orElse(List.of())
            : List.of();

        ComboBox<Period> periodCombo = new ComboBox<>("Period");
        periodCombo.setItems(periods);
        periodCombo.setItemLabelGenerator(this::formatPeriod);
        periodCombo.setRequired(true);
        if (!isNew) {
            periodCombo.setValue(line.getPeriod());
        }

        // Account selector - filtered by security level
        int securityLevel = companyContextService.getCurrentSecurityLevel();
        List<Account> accounts = accountService.findByCompanyWithSecurityLevel(company, securityLevel);
        ComboBox<Account> accountCombo = new ComboBox<>("Account");
        accountCombo.setItems(accounts);
        accountCombo.setItemLabelGenerator(a -> a.getCode() + " - " + a.getName());
        accountCombo.setRequired(true);
        if (!isNew) {
            accountCombo.setValue(line.getAccount());
        }

        // Department selector (optional)
        List<Department> departments = departmentService.findActiveByCompany(company);
        ComboBox<Department> departmentCombo = new ComboBox<>("Department (Optional)");
        departmentCombo.setItems(departments);
        departmentCombo.setItemLabelGenerator(d -> d.getCode() + " - " + d.getName());
        departmentCombo.setClearButtonVisible(true);
        if (!isNew && line.getDepartment() != null) {
            departmentCombo.setValue(line.getDepartment());
        }

        // Amount field
        BigDecimalField amountField = new BigDecimalField("Amount");
        amountField.setRequired(true);
        if (!isNew) {
            amountField.setValue(line.getAmount());
        } else {
            amountField.setValue(BigDecimal.ZERO);
        }

        form.add(periodCombo, accountCombo, departmentCombo, amountField);
        form.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 2)
        );

        Button saveButton = new Button("Save");
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.addClickListener(e -> {
            if (periodCombo.isEmpty() || accountCombo.isEmpty() || amountField.isEmpty()) {
                Notification.show("Please fill in all required fields", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                budgetService.saveBudgetLine(
                    selectedBudget,
                    periodCombo.getValue(),
                    accountCombo.getValue(),
                    departmentCombo.getValue(),
                    amountField.getValue()
                );

                Notification.show("Budget line saved successfully", 3000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                dialog.close();
                loadBudgetLines();
            } catch (Exception ex) {
                Notification.show("Error saving budget line: " + ex.getMessage(), 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        Button cancelButton = new Button("Cancel", e -> dialog.close());

        dialog.add(form);
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private void deleteBudgetLine(BudgetLine line) {
        budgetService.deleteBudgetLine(line);
        Notification.show("Budget line deleted", 3000, Notification.Position.BOTTOM_START)
            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        loadBudgetLines();
    }

    private void openImportDialog() {
        if (selectedBudget == null) {
            Notification.show("Please select a budget first", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Import Budget Lines from CSV");
        dialog.setWidth("600px");

        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);

        // Instructions
        Span instructions = new Span("Upload a CSV file with budget data. Required columns: account_code, " +
            "period_date (YYYY-MM-DD), amount. Optional: department_code. " +
            "The period_date should be any date within the period (e.g., 2024-07-01 for July 2024).");
        instructions.getStyle()
            .set("color", "var(--lumo-secondary-text-color)")
            .set("font-size", "var(--lumo-font-size-s)");

        // Download sample CSV link
        String sampleCsv = budgetImportService.getSampleCsvContent();
        StreamResource sampleResource = new StreamResource("budget_sample.csv",
            () -> new ByteArrayInputStream(sampleCsv.getBytes(StandardCharsets.UTF_8)));
        sampleResource.setContentType("text/csv");
        Anchor downloadSample = new Anchor(sampleResource, "Download sample CSV");
        downloadSample.getElement().setAttribute("download", true);
        downloadSample.getStyle().set("font-size", "var(--lumo-font-size-s)");

        // Update existing checkbox
        Checkbox updateExisting = new Checkbox("Update existing budget lines");
        updateExisting.setValue(true);
        updateExisting.setHelperText("If unchecked, existing lines (same account/period/department) will be skipped");

        // File upload
        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes(".csv", "text/csv");
        upload.setMaxFiles(1);
        upload.setDropLabel(new Span("Drop CSV file here or click to upload"));
        upload.setWidthFull();

        // Preview/result area
        VerticalLayout resultArea = new VerticalLayout();
        resultArea.setPadding(false);
        resultArea.setVisible(false);

        // Import button
        Button importButton = new Button("Import");
        importButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        importButton.setEnabled(false);

        // Store the uploaded stream for import
        final byte[][] uploadedBytes = {null};

        upload.addSucceededListener(event -> {
            try {
                uploadedBytes[0] = buffer.getInputStream().readAllBytes();

                ImportResult preview = budgetImportService.previewImport(
                    new ByteArrayInputStream(uploadedBytes[0]),
                    selectedBudget,
                    updateExisting.getValue()
                );

                resultArea.removeAll();
                if (preview.success()) {
                    Span previewText = new Span(String.format(
                        "Preview: %d lines to import, %d to update, %d to skip",
                        preview.imported(), preview.updated(), preview.skipped()));
                    previewText.getStyle().set("color", "var(--lumo-success-text-color)");
                    resultArea.add(previewText);

                    if (!preview.warnings().isEmpty()) {
                        for (String warning : preview.warnings()) {
                            Span warningSpan = new Span(warning);
                            warningSpan.getStyle().set("color", "var(--lumo-warning-text-color)")
                                .set("font-size", "var(--lumo-font-size-s)");
                            resultArea.add(warningSpan);
                        }
                    }

                    importButton.setEnabled(preview.imported() > 0 || preview.updated() > 0);
                } else {
                    for (String error : preview.errors()) {
                        Span errorSpan = new Span(error);
                        errorSpan.getStyle().set("color", "var(--lumo-error-text-color)");
                        resultArea.add(errorSpan);
                    }
                    importButton.setEnabled(false);
                }
                resultArea.setVisible(true);
            } catch (IOException e) {
                Notification.show("Error reading file: " + e.getMessage(), 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        upload.addFileRejectedListener(event -> {
            Notification.show("Invalid file: " + event.getErrorMessage(), 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        });

        importButton.addClickListener(e -> {
            if (uploadedBytes[0] == null) {
                Notification.show("Please upload a file first", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                User user = companyContextService.getCurrentUser();
                ImportResult result = budgetImportService.importBudgetLines(
                    new ByteArrayInputStream(uploadedBytes[0]),
                    selectedBudget,
                    user,
                    updateExisting.getValue()
                );

                if (result.success()) {
                    Notification.show(String.format(
                        "Import complete: %d imported, %d updated, %d skipped",
                        result.imported(), result.updated(), result.skipped()),
                        5000, Notification.Position.BOTTOM_START)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    dialog.close();
                    loadBudgetLines();
                } else {
                    resultArea.removeAll();
                    for (String error : result.errors()) {
                        Span errorSpan = new Span(error);
                        errorSpan.getStyle().set("color", "var(--lumo-error-text-color)");
                        resultArea.add(errorSpan);
                    }
                }
            } catch (IOException ex) {
                Notification.show("Import failed: " + ex.getMessage(), 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        Button cancelButton = new Button("Cancel", e -> dialog.close());

        layout.add(instructions, downloadSample, updateExisting, upload, resultArea);

        dialog.add(layout);
        dialog.getFooter().add(cancelButton, importButton);
        dialog.open();
    }
}
