package com.example.application.ui.views;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.example.application.domain.*;
import com.example.application.domain.PaymentRun.PaymentRunStatus;
import com.example.application.service.*;
import com.example.application.service.DirectCreditExportService.ExportFormat;
import com.example.application.service.DirectCreditExportService.ExportResult;
import com.example.application.service.PaymentRunService.PaymentRunBill;
import com.example.application.ui.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
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
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;

import jakarta.annotation.security.PermitAll;

/**
 * View for managing Payment Runs - batch supplier payments. Allows creating payment runs, selecting
 * bills to pay, and completing runs which generates payment transactions and remittance advice
 * PDFs.
 */
@Route(value = "payment-runs", layout = MainLayout.class)
@PageTitle("Payment Runs | MoniWorks")
@PermitAll
public class PaymentRunsView extends VerticalLayout {

  private final PaymentRunService paymentRunService;
  private final SupplierBillService supplierBillService;
  private final ContactService contactService;
  private final AccountService accountService;
  private final AttachmentService attachmentService;
  private final CompanyContextService companyContextService;
  private final EmailService emailService;
  private final DirectCreditExportService directCreditExportService;

  private final Grid<PaymentRun> runGrid = new Grid<>();
  private final VerticalLayout detailPanel = new VerticalLayout();

  private PaymentRun selectedRun;
  private ComboBox<PaymentRunStatus> statusFilter;

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy");
  private static final DateTimeFormatter DATETIME_FORMAT =
      DateTimeFormatter.ofPattern("d MMM yyyy HH:mm");
  private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(Locale.US);

  public PaymentRunsView(
      PaymentRunService paymentRunService,
      SupplierBillService supplierBillService,
      ContactService contactService,
      AccountService accountService,
      AttachmentService attachmentService,
      CompanyContextService companyContextService,
      EmailService emailService,
      DirectCreditExportService directCreditExportService) {
    this.paymentRunService = paymentRunService;
    this.supplierBillService = supplierBillService;
    this.contactService = contactService;
    this.accountService = accountService;
    this.attachmentService = attachmentService;
    this.companyContextService = companyContextService;
    this.emailService = emailService;
    this.directCreditExportService = directCreditExportService;

    addClassName("payment-runs-view");
    setSizeFull();

    SplitLayout splitLayout = new SplitLayout();
    splitLayout.setSizeFull();
    splitLayout.setSplitterPosition(50);

    VerticalLayout masterPanel = createMasterPanel();
    splitLayout.addToPrimary(masterPanel);
    splitLayout.addToSecondary(detailPanel);

    add(splitLayout);
    loadRuns();
  }

  private VerticalLayout createMasterPanel() {
    VerticalLayout layout = new VerticalLayout();
    layout.setSizeFull();
    layout.setPadding(false);
    layout.setSpacing(false);

    HorizontalLayout toolbar = createToolbar();
    configureGrid();

    layout.add(toolbar, runGrid);
    layout.setFlexGrow(1, runGrid);

    return layout;
  }

  private void configureGrid() {
    runGrid.addClassNames("payment-runs-grid");
    runGrid.setSizeFull();
    runGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

    runGrid
        .addColumn(run -> formatDate(run.getRunDate()))
        .setHeader("Run Date")
        .setSortable(true)
        .setAutoWidth(true);

    runGrid
        .addColumn(run -> run.getBankAccount() != null ? run.getBankAccount().getName() : "")
        .setHeader("Bank Account")
        .setSortable(true)
        .setAutoWidth(true);

    runGrid.addComponentColumn(this::createStatusBadge).setHeader("Status").setAutoWidth(true);

    runGrid
        .addColumn(run -> formatMoney(paymentRunService.getRunTotal(run)))
        .setHeader("Total")
        .setSortable(true)
        .setAutoWidth(true);

    runGrid
        .addColumn(run -> run.getCreatedBy() != null ? run.getCreatedBy().getDisplayName() : "")
        .setHeader("Created By")
        .setSortable(true)
        .setAutoWidth(true);

    runGrid
        .addColumn(run -> formatDateTime(run.getCreatedAt()))
        .setHeader("Created At")
        .setSortable(true)
        .setFlexGrow(1);

    runGrid
        .asSingleSelect()
        .addValueChangeListener(
            e -> {
              selectedRun = e.getValue();
              updateDetailPanel();
            });
  }

  private Span createStatusBadge(PaymentRun run) {
    Span badge = new Span(run.getStatus().name());
    badge.getElement().getThemeList().add("badge");

    switch (run.getStatus()) {
      case COMPLETED -> badge.getElement().getThemeList().add("success");
      case DRAFT -> badge.getElement().getThemeList().add("contrast");
    }

    return badge;
  }

  private HorizontalLayout createToolbar() {
    H2 title = new H2("Payment Runs");

    statusFilter = new ComboBox<>();
    statusFilter.setPlaceholder("All Statuses");
    statusFilter.setItems(PaymentRunStatus.values());
    statusFilter.setClearButtonVisible(true);
    statusFilter.addValueChangeListener(e -> loadRuns());
    statusFilter.setWidth("150px");

    Button createButton = new Button("Create Payment Run", VaadinIcon.PLUS.create());
    createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    createButton.addClickListener(e -> openCreateWizard());

    Button refreshButton = new Button(VaadinIcon.REFRESH.create());
    refreshButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    refreshButton.addClickListener(e -> loadRuns());
    refreshButton.getElement().setAttribute("title", "Refresh");

    HorizontalLayout filters = new HorizontalLayout(statusFilter);
    filters.setAlignItems(FlexComponent.Alignment.BASELINE);

    HorizontalLayout actions = new HorizontalLayout(createButton, refreshButton);
    actions.setAlignItems(FlexComponent.Alignment.BASELINE);

    HorizontalLayout toolbar = new HorizontalLayout(title, filters, actions);
    toolbar.setWidthFull();
    toolbar.expand(filters);
    toolbar.setAlignItems(FlexComponent.Alignment.CENTER);
    toolbar.setPadding(true);

    return toolbar;
  }

  private void loadRuns() {
    Company company = companyContextService.getCurrentCompany();
    List<PaymentRun> runs;

    if (statusFilter.getValue() != null) {
      runs = paymentRunService.findByCompanyAndStatus(company, statusFilter.getValue());
    } else {
      runs = paymentRunService.findByCompany(company);
    }

    runGrid.setItems(runs);
  }

  private void updateDetailPanel() {
    detailPanel.removeAll();
    detailPanel.setPadding(true);
    detailPanel.setSpacing(true);

    if (selectedRun == null) {
      detailPanel.add(new Span("Select a payment run to view details"));
      return;
    }

    // Header with run info
    H3 header = new H3("Payment Run #" + selectedRun.getId());

    // Action buttons based on status
    HorizontalLayout actionButtons = new HorizontalLayout();
    actionButtons.setSpacing(true);

    if (selectedRun.isDraft()) {
      Button addBillsBtn = new Button("Add Bills", VaadinIcon.PLUS.create());
      addBillsBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
      addBillsBtn.addClickListener(e -> openAddBillsDialog());
      actionButtons.add(addBillsBtn);

      Button completeBtn = new Button("Complete Run", VaadinIcon.CHECK.create());
      completeBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
      completeBtn.addClickListener(e -> completeRun());
      actionButtons.add(completeBtn);
    }

    if (selectedRun.isCompleted() && selectedRun.getOutputAttachment() != null) {
      Button downloadBtn = new Button("Download Remittance", VaadinIcon.DOWNLOAD.create());
      downloadBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
      downloadBtn.addClickListener(e -> downloadRemittance());
      actionButtons.add(downloadBtn);

      Button emailBtn = new Button("Email Remittance", VaadinIcon.ENVELOPE.create());
      emailBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
      emailBtn.addClickListener(e -> openEmailRemittanceDialog());
      actionButtons.add(emailBtn);

      Button exportBtn = new Button("Export Direct Credit", VaadinIcon.FILE_TABLE.create());
      exportBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
      exportBtn.addClickListener(e -> openDirectCreditExportDialog());
      actionButtons.add(exportBtn);
    }

    HorizontalLayout headerLayout = new HorizontalLayout(header, actionButtons);
    headerLayout.setAlignItems(FlexComponent.Alignment.CENTER);
    headerLayout.setWidthFull();
    headerLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

    // Run details
    VerticalLayout details = new VerticalLayout();
    details.setPadding(false);
    details.setSpacing(false);

    details.add(createDetailRow("Status:", selectedRun.getStatus().name()));
    details.add(createDetailRow("Run Date:", formatDate(selectedRun.getRunDate())));
    details.add(
        createDetailRow(
            "Bank Account:",
            selectedRun.getBankAccount() != null ? selectedRun.getBankAccount().getName() : ""));
    details.add(createDetailRow("Total:", formatMoney(paymentRunService.getRunTotal(selectedRun))));
    details.add(createDetailRow("Created At:", formatDateTime(selectedRun.getCreatedAt())));

    if (selectedRun.getCreatedBy() != null) {
      details.add(createDetailRow("Created By:", selectedRun.getCreatedBy().getDisplayName()));
    }

    if (selectedRun.getCompletedAt() != null) {
      details.add(createDetailRow("Completed At:", formatDateTime(selectedRun.getCompletedAt())));
    }

    if (selectedRun.getNotes() != null && !selectedRun.getNotes().isBlank()) {
      details.add(createDetailRow("Notes:", selectedRun.getNotes()));
    }

    // Bills in run
    H3 billsHeader = new H3("Bills to Pay");
    Grid<PaymentRunBill> billsGrid = new Grid<>();
    billsGrid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
    billsGrid.setHeight("300px");

    billsGrid.addColumn(prb -> prb.bill().getBillNumber()).setHeader("Bill #").setAutoWidth(true);

    billsGrid
        .addColumn(prb -> prb.bill().getContact() != null ? prb.bill().getContact().getName() : "")
        .setHeader("Supplier")
        .setFlexGrow(1);

    billsGrid
        .addColumn(prb -> formatDate(prb.bill().getBillDate()))
        .setHeader("Bill Date")
        .setAutoWidth(true);

    billsGrid
        .addColumn(prb -> formatDate(prb.bill().getDueDate()))
        .setHeader("Due Date")
        .setAutoWidth(true);

    billsGrid
        .addColumn(prb -> formatMoney(prb.bill().getTotal()))
        .setHeader("Total")
        .setAutoWidth(true);

    billsGrid.addColumn(prb -> formatMoney(prb.amount())).setHeader("Payment").setAutoWidth(true);

    // Add remove button for draft runs
    if (selectedRun.isDraft()) {
      billsGrid
          .addComponentColumn(
              prb -> {
                Button removeBtn = new Button(VaadinIcon.TRASH.create());
                removeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
                removeBtn.setTooltipText("Remove from run");
                removeBtn.addClickListener(e -> removeBillFromRun(prb.bill()));
                return removeBtn;
              })
          .setHeader("")
          .setAutoWidth(true);
    }

    List<PaymentRunBill> runBills = paymentRunService.getRunBills(selectedRun);
    billsGrid.setItems(runBills);

    // Group by supplier summary
    VerticalLayout supplierSummary = new VerticalLayout();
    supplierSummary.setPadding(false);
    supplierSummary.setSpacing(false);

    Map<String, BigDecimal> bySupplier =
        runBills.stream()
            .collect(
                Collectors.groupingBy(
                    prb ->
                        prb.bill().getContact() != null
                            ? prb.bill().getContact().getName()
                            : "Unknown",
                    Collectors.reducing(BigDecimal.ZERO, PaymentRunBill::amount, BigDecimal::add)));

    if (!bySupplier.isEmpty()) {
      H3 summaryHeader = new H3("Summary by Supplier");
      supplierSummary.add(summaryHeader);

      for (Map.Entry<String, BigDecimal> entry : bySupplier.entrySet()) {
        supplierSummary.add(createDetailRow(entry.getKey() + ":", formatMoney(entry.getValue())));
      }
    }

    detailPanel.add(headerLayout, details, billsHeader, billsGrid, supplierSummary);
  }

  private HorizontalLayout createDetailRow(String label, String value) {
    Span labelSpan = new Span(label);
    labelSpan.getStyle().set("font-weight", "bold");
    labelSpan.setWidth("150px");

    Span valueSpan = new Span(value != null ? value : "");

    HorizontalLayout row = new HorizontalLayout(labelSpan, valueSpan);
    row.setSpacing(true);
    row.setPadding(false);
    return row;
  }

  private String formatDate(LocalDate date) {
    return date != null ? DATE_FORMAT.format(date) : "";
  }

  private String formatDateTime(Instant instant) {
    if (instant == null) return "";
    return DATETIME_FORMAT.format(instant.atZone(ZoneId.systemDefault()));
  }

  private String formatMoney(BigDecimal amount) {
    if (amount == null) return "$0.00";
    return CURRENCY_FORMAT.format(amount);
  }

  // === Dialog and Actions ===

  /**
   * Opens the Create Payment Run wizard - a step-by-step dialog for creating a payment run. Step 1:
   * Select bank account and run date, filter criteria Step 2: Review selected bills Step 3: Confirm
   * and optionally complete
   */
  private void openCreateWizard() {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Create Payment Run");
    dialog.setWidth("900px");
    dialog.setHeight("700px");

    Company company = companyContextService.getCurrentCompany();

    // Wizard state
    List<SupplierBill> selectedBills = new ArrayList<>();

    // Create wizard steps
    VerticalLayout step1 = createWizardStep1(company, dialog, selectedBills);

    dialog.add(step1);
    dialog.open();
  }

  private VerticalLayout createWizardStep1(
      Company company, Dialog dialog, List<SupplierBill> selectedBills) {
    VerticalLayout layout = new VerticalLayout();
    layout.setSizeFull();
    layout.setPadding(false);

    H3 stepTitle = new H3("Step 1: Select Bank Account & Filter Bills");

    FormLayout form = new FormLayout();

    // Bank account selection
    List<Account> bankAccounts = accountService.findBankAccountsByCompany(company);
    ComboBox<Account> bankAccountField = new ComboBox<>("Bank Account");
    bankAccountField.setItems(bankAccounts);
    bankAccountField.setItemLabelGenerator(Account::getName);
    bankAccountField.setRequired(true);
    bankAccountField.setHelperText("Select the bank account to pay from");
    if (!bankAccounts.isEmpty()) {
      bankAccountField.setValue(bankAccounts.get(0));
    }

    // Run date
    DatePicker runDateField = new DatePicker("Payment Date");
    runDateField.setRequired(true);
    runDateField.setValue(LocalDate.now());
    runDateField.setHelperText("Date the payments will be made");

    // Due date filter
    DatePicker dueDateFilter = new DatePicker("Pay Bills Due By");
    dueDateFilter.setValue(LocalDate.now().plusDays(7));
    dueDateFilter.setHelperText("Only include bills due on or before this date");

    // Supplier filter
    List<Contact> suppliers = contactService.findActiveSuppliers(company);
    MultiSelectComboBox<Contact> supplierFilter =
        new MultiSelectComboBox<>("Specific Suppliers (Optional)");
    supplierFilter.setItems(suppliers);
    supplierFilter.setItemLabelGenerator(c -> c.getCode() + " - " + c.getName());
    supplierFilter.setClearButtonVisible(true);
    supplierFilter.setWidthFull();
    supplierFilter.setHelperText("Leave empty to include all suppliers with bills due");

    // Notes
    TextArea notesField = new TextArea("Notes");
    notesField.setWidthFull();
    notesField.setMaxLength(500);

    form.add(bankAccountField, runDateField, dueDateFilter, supplierFilter, notesField);
    form.setColspan(supplierFilter, 2);
    form.setColspan(notesField, 2);
    form.setResponsiveSteps(
        new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("400px", 2));

    // Preview section
    H3 previewTitle = new H3("Bills to Include");
    Grid<SupplierBill> previewGrid = new Grid<>();
    previewGrid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
    previewGrid.setHeight("250px");
    previewGrid.setSelectionMode(Grid.SelectionMode.MULTI);

    previewGrid.addColumn(SupplierBill::getBillNumber).setHeader("Bill #").setAutoWidth(true);

    previewGrid
        .addColumn(b -> b.getContact() != null ? b.getContact().getName() : "")
        .setHeader("Supplier")
        .setFlexGrow(1);

    previewGrid
        .addColumn(b -> formatDate(b.getBillDate()))
        .setHeader("Bill Date")
        .setAutoWidth(true);

    previewGrid.addColumn(b -> formatDate(b.getDueDate())).setHeader("Due Date").setAutoWidth(true);

    previewGrid.addColumn(b -> formatMoney(b.getBalance())).setHeader("Balance").setAutoWidth(true);

    Span totalLabel = new Span("Total: $0.00");
    totalLabel.getStyle().set("font-weight", "bold");
    totalLabel.getStyle().set("font-size", "large");

    // Load bills button
    Button loadBillsBtn = new Button("Load Bills", VaadinIcon.SEARCH.create());
    loadBillsBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    loadBillsBtn.addClickListener(
        e -> {
          LocalDate dueBy = dueDateFilter.getValue();
          Set<Contact> selectedSuppliers = supplierFilter.getValue();

          List<SupplierBill> bills;
          if (dueBy != null) {
            bills = supplierBillService.findPayableBillsDueBy(company, dueBy);
          } else {
            bills = supplierBillService.findOutstandingByCompany(company);
          }

          // Filter by suppliers if specified
          if (selectedSuppliers != null && !selectedSuppliers.isEmpty()) {
            Set<Long> supplierIds =
                selectedSuppliers.stream().map(Contact::getId).collect(Collectors.toSet());
            bills =
                bills.stream()
                    .filter(
                        b -> b.getContact() != null && supplierIds.contains(b.getContact().getId()))
                    .collect(Collectors.toList());
          }

          previewGrid.setItems(bills);
          previewGrid.asMultiSelect().select(bills);

          BigDecimal total =
              bills.stream().map(SupplierBill::getBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
          totalLabel.setText("Total: " + formatMoney(total));
        });

    // Update total when selection changes
    previewGrid
        .asMultiSelect()
        .addValueChangeListener(
            e -> {
              Set<SupplierBill> selected = e.getValue();
              BigDecimal total =
                  selected.stream()
                      .map(SupplierBill::getBalance)
                      .reduce(BigDecimal.ZERO, BigDecimal::add);
              totalLabel.setText("Total: " + formatMoney(total));
              selectedBills.clear();
              selectedBills.addAll(selected);
            });

    // Buttons
    Button cancelBtn = new Button("Cancel", e -> dialog.close());

    Button createDraftBtn = new Button("Create Draft");
    createDraftBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    createDraftBtn.addClickListener(
        e -> {
          if (bankAccountField.isEmpty()) {
            Notification.show("Please select a bank account", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }
          if (selectedBills.isEmpty()) {
            Notification.show(
                    "Please select at least one bill to pay", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }

          createPaymentRunDraft(
              bankAccountField.getValue(),
              runDateField.getValue(),
              notesField.getValue(),
              selectedBills,
              dialog);
        });

    Button createAndCompleteBtn = new Button("Create & Complete");
    createAndCompleteBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
    createAndCompleteBtn.addClickListener(
        e -> {
          if (bankAccountField.isEmpty()) {
            Notification.show("Please select a bank account", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }
          if (selectedBills.isEmpty()) {
            Notification.show(
                    "Please select at least one bill to pay", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }

          createAndCompletePaymentRun(
              bankAccountField.getValue(),
              runDateField.getValue(),
              notesField.getValue(),
              selectedBills,
              dialog);
        });

    HorizontalLayout previewHeader = new HorizontalLayout(previewTitle, loadBillsBtn);
    previewHeader.setAlignItems(FlexComponent.Alignment.CENTER);

    layout.add(stepTitle, form, previewHeader, previewGrid, totalLabel);
    layout.setFlexGrow(1, previewGrid);

    dialog.getFooter().add(cancelBtn, createDraftBtn, createAndCompleteBtn);

    // Auto-load bills on open
    loadBillsBtn.click();

    return layout;
  }

  private void createPaymentRunDraft(
      Account bankAccount,
      LocalDate runDate,
      String notes,
      List<SupplierBill> bills,
      Dialog dialog) {
    try {
      Company company = companyContextService.getCurrentCompany();
      User currentUser = companyContextService.getCurrentUser();

      PaymentRun run =
          paymentRunService.createPaymentRun(company, runDate, bankAccount, currentUser);
      if (notes != null && !notes.isBlank()) {
        run.setNotes(notes);
      }
      paymentRunService.addBillsToRun(run, bills);

      Notification.show(
              "Payment run created. Review and click 'Complete Run' to process.",
              5000,
              Notification.Position.BOTTOM_START)
          .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

      dialog.close();
      loadRuns();
      runGrid.select(run);
    } catch (Exception ex) {
      Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
  }

  private void createAndCompletePaymentRun(
      Account bankAccount,
      LocalDate runDate,
      String notes,
      List<SupplierBill> bills,
      Dialog dialog) {
    try {
      Company company = companyContextService.getCurrentCompany();
      User currentUser = companyContextService.getCurrentUser();

      PaymentRun run =
          paymentRunService.createPaymentRun(company, runDate, bankAccount, currentUser);
      if (notes != null && !notes.isBlank()) {
        run.setNotes(notes);
      }
      paymentRunService.addBillsToRun(run, bills);

      run = paymentRunService.completePaymentRun(run, currentUser);

      Notification.show(
              "Payment run completed. "
                  + bills.size()
                  + " bills paid. Remittance advice generated.",
              5000,
              Notification.Position.BOTTOM_START)
          .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

      dialog.close();
      loadRuns();
      runGrid.select(run);
    } catch (Exception ex) {
      Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
  }

  private void openAddBillsDialog() {
    if (selectedRun == null || !selectedRun.isDraft()) {
      return;
    }

    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Add Bills to Payment Run");
    dialog.setWidth("800px");

    Company company = companyContextService.getCurrentCompany();

    // Get already-added bill IDs
    Set<Long> existingBillIds =
        paymentRunService.getRunBills(selectedRun).stream()
            .map(prb -> prb.bill().getId())
            .collect(Collectors.toSet());

    // Get available bills
    List<SupplierBill> availableBills =
        supplierBillService.findOutstandingByCompany(company).stream()
            .filter(b -> !existingBillIds.contains(b.getId()))
            .collect(Collectors.toList());

    Grid<SupplierBill> billsGrid = new Grid<>();
    billsGrid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
    billsGrid.setHeight("400px");
    billsGrid.setSelectionMode(Grid.SelectionMode.MULTI);

    billsGrid.addColumn(SupplierBill::getBillNumber).setHeader("Bill #").setAutoWidth(true);

    billsGrid
        .addColumn(b -> b.getContact() != null ? b.getContact().getName() : "")
        .setHeader("Supplier")
        .setFlexGrow(1);

    billsGrid.addColumn(b -> formatDate(b.getDueDate())).setHeader("Due Date").setAutoWidth(true);

    billsGrid.addColumn(b -> formatMoney(b.getBalance())).setHeader("Balance").setAutoWidth(true);

    billsGrid.setItems(availableBills);

    Span totalLabel = new Span("Selected: $0.00");
    totalLabel.getStyle().set("font-weight", "bold");

    billsGrid
        .asMultiSelect()
        .addValueChangeListener(
            e -> {
              BigDecimal total =
                  e.getValue().stream()
                      .map(SupplierBill::getBalance)
                      .reduce(BigDecimal.ZERO, BigDecimal::add);
              totalLabel.setText("Selected: " + formatMoney(total));
            });

    Button cancelBtn = new Button("Cancel", e -> dialog.close());
    Button addBtn = new Button("Add Selected Bills");
    addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    addBtn.addClickListener(
        e -> {
          Set<SupplierBill> selected = billsGrid.asMultiSelect().getValue();
          if (selected.isEmpty()) {
            Notification.show("Please select at least one bill", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }

          try {
            paymentRunService.addBillsToRun(selectedRun, new ArrayList<>(selected));
            Notification.show(
                    "Added " + selected.size() + " bills to payment run",
                    3000,
                    Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            dialog.close();
            updateDetailPanel();
          } catch (Exception ex) {
            Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
          }
        });

    VerticalLayout content = new VerticalLayout(billsGrid, totalLabel);
    content.setSizeFull();
    content.setPadding(false);

    dialog.add(content);
    dialog.getFooter().add(cancelBtn, addBtn);
    dialog.open();
  }

  private void removeBillFromRun(SupplierBill bill) {
    if (selectedRun == null || !selectedRun.isDraft()) {
      return;
    }

    try {
      paymentRunService.removeBillFromRun(selectedRun, bill);
      Notification.show("Bill removed from payment run", 3000, Notification.Position.BOTTOM_START)
          .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
      updateDetailPanel();
    } catch (Exception ex) {
      Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
  }

  private void completeRun() {
    if (selectedRun == null || !selectedRun.isDraft()) {
      return;
    }

    User currentUser = companyContextService.getCurrentUser();

    try {
      selectedRun = paymentRunService.completePaymentRun(selectedRun, currentUser);

      Notification.show(
              "Payment run completed. Remittance advice generated.",
              5000,
              Notification.Position.BOTTOM_START)
          .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

      loadRuns();
      updateDetailPanel();
    } catch (Exception ex) {
      Notification.show(
              "Error completing run: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
  }

  private void downloadRemittance() {
    if (selectedRun == null || selectedRun.getOutputAttachment() == null) {
      return;
    }

    Attachment attachment = selectedRun.getOutputAttachment();
    try {
      byte[] content = attachmentService.getFileContent(attachment);
      String filename = attachment.getFilename();

      StreamResource resource =
          new StreamResource(filename, () -> new ByteArrayInputStream(content));
      resource.setContentType("application/pdf");

      Anchor downloadLink = new Anchor(resource, "");
      downloadLink.getElement().setAttribute("download", true);
      downloadLink.getElement().getStyle().set("display", "none");
      add(downloadLink);
      downloadLink.getElement().executeJs("this.click()");
      downloadLink.getElement().executeJs("setTimeout(() => this.remove(), 100)");
    } catch (Exception e) {
      Notification.show(
              "Error downloading PDF: " + e.getMessage(), 5000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
  }

  private void openEmailRemittanceDialog() {
    if (selectedRun == null || selectedRun.getOutputAttachment() == null) {
      return;
    }

    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Email Remittance Advice");
    dialog.setWidth("500px");

    // Get suppliers from the run
    List<PaymentRunBill> runBills = paymentRunService.getRunBills(selectedRun);
    Set<Contact> suppliers =
        runBills.stream()
            .map(prb -> prb.bill().getContact())
            .filter(c -> c != null && c.getEmail() != null && !c.getEmail().isBlank())
            .collect(Collectors.toSet());

    if (suppliers.isEmpty()) {
      dialog.add(new Span("No suppliers in this payment run have email addresses configured."));
      Button closeBtn = new Button("Close", e -> dialog.close());
      dialog.getFooter().add(closeBtn);
      dialog.open();
      return;
    }

    VerticalLayout content = new VerticalLayout();
    content.setPadding(false);

    content.add(new Span("Send remittance advice to the following suppliers:"));

    Checkbox selectAll = new Checkbox("Select All");
    selectAll.setValue(true);

    VerticalLayout supplierList = new VerticalLayout();
    supplierList.setPadding(false);
    supplierList.setSpacing(false);

    // Use a map to associate checkboxes with suppliers since Checkbox doesn't have setUserData
    Map<Checkbox, Contact> checkboxToSupplier = new java.util.LinkedHashMap<>();
    List<Checkbox> supplierCheckboxes = new ArrayList<>();
    for (Contact supplier : suppliers) {
      Checkbox cb = new Checkbox(supplier.getName() + " (" + supplier.getEmail() + ")");
      cb.setValue(true);
      checkboxToSupplier.put(cb, supplier);
      supplierCheckboxes.add(cb);
      supplierList.add(cb);
    }

    selectAll.addValueChangeListener(
        e -> supplierCheckboxes.forEach(cb -> cb.setValue(e.getValue())));

    content.add(selectAll, supplierList);

    Button cancelBtn = new Button("Cancel", e -> dialog.close());
    Button sendBtn = new Button("Send Emails");
    sendBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    sendBtn.addClickListener(
        e -> {
          List<Contact> selectedSuppliers =
              supplierCheckboxes.stream()
                  .filter(Checkbox::getValue)
                  .map(checkboxToSupplier::get)
                  .collect(Collectors.toList());

          if (selectedSuppliers.isEmpty()) {
            Notification.show(
                    "Please select at least one supplier", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }

          sendRemittanceEmails(selectedSuppliers);
          dialog.close();
        });

    dialog.add(content);
    dialog.getFooter().add(cancelBtn, sendBtn);
    dialog.open();
  }

  private void sendRemittanceEmails(List<Contact> suppliers) {
    Company company = companyContextService.getCurrentCompany();
    User currentUser = companyContextService.getCurrentUser();
    Attachment attachment = selectedRun.getOutputAttachment();

    int sent = 0;
    int failed = 0;

    for (Contact supplier : suppliers) {
      try {
        byte[] pdfContent = attachmentService.getFileContent(attachment);
        EmailService.EmailResult result =
            emailService.sendRemittanceAdvice(supplier, company, pdfContent, currentUser);

        if (result.success() || "QUEUED".equals(result.status())) {
          sent++;
        } else {
          failed++;
        }
      } catch (Exception ex) {
        failed++;
      }
    }

    if (failed == 0) {
      Notification.show(
              "Remittance advice sent to " + sent + " supplier(s)",
              5000,
              Notification.Position.BOTTOM_START)
          .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    } else {
      Notification.show("Sent: " + sent + ", Failed: " + failed, 5000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_WARNING);
    }
  }

  /**
   * Opens a dialog for exporting the payment run to a direct credit file format for bank
   * submission.
   */
  private void openDirectCreditExportDialog() {
    if (selectedRun == null || !selectedRun.isCompleted()) {
      return;
    }

    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Export Direct Credit File");
    dialog.setWidth("500px");

    VerticalLayout content = new VerticalLayout();
    content.setPadding(false);
    content.setSpacing(true);

    // Format selection
    ComboBox<ExportFormat> formatCombo = new ComboBox<>("Export Format");
    formatCombo.setItems(ExportFormat.values());
    formatCombo.setItemLabelGenerator(ExportFormat::getDisplayName);
    formatCombo.setValue(ExportFormat.CSV);
    formatCombo.setWidthFull();

    // Explanation
    Span helpText = new Span();
    helpText.getStyle().set("color", "var(--lumo-secondary-text-color)");
    helpText.getStyle().set("font-size", "var(--lumo-font-size-s)");
    updateExportHelpText(helpText, ExportFormat.CSV);

    formatCombo.addValueChangeListener(
        e -> {
          if (e.getValue() != null) {
            updateExportHelpText(helpText, e.getValue());
          }
        });

    // Run info
    VerticalLayout runInfo = new VerticalLayout();
    runInfo.setPadding(false);
    runInfo.setSpacing(false);
    runInfo.add(new Span("Payment Run #" + selectedRun.getId()));
    runInfo.add(new Span("Run Date: " + formatDate(selectedRun.getRunDate())));
    runInfo.add(new Span("Total: " + formatMoney(paymentRunService.getRunTotal(selectedRun))));

    content.add(runInfo, formatCombo, helpText);

    // Results area (hidden initially)
    VerticalLayout resultsArea = new VerticalLayout();
    resultsArea.setPadding(false);
    resultsArea.setSpacing(false);
    resultsArea.setVisible(false);
    content.add(resultsArea);

    dialog.add(content);

    // Footer buttons
    Button cancelBtn = new Button("Cancel", e -> dialog.close());
    Button exportBtn = new Button("Export", VaadinIcon.DOWNLOAD.create());
    exportBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    exportBtn.addClickListener(
        e -> {
          ExportFormat format = formatCombo.getValue();
          if (format == null) {
            Notification.show("Please select an export format")
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }

          try {
            ExportResult result = directCreditExportService.exportPaymentRun(selectedRun, format);

            // Show results
            resultsArea.removeAll();
            resultsArea.setVisible(true);
            resultsArea.add(new Span("Exported " + result.paymentCount() + " payment(s)"));
            resultsArea.add(new Span("Total amount: " + formatMoney(result.totalAmount())));

            // Show warnings if any
            if (result.warnings() != null && !result.warnings().isEmpty()) {
              Span warningHeader = new Span("Warnings:");
              warningHeader.getStyle().set("color", "var(--lumo-warning-text-color)");
              resultsArea.add(warningHeader);
              for (String warning : result.warnings()) {
                Span warningSpan = new Span("• " + warning);
                warningSpan.getStyle().set("color", "var(--lumo-warning-text-color)");
                warningSpan.getStyle().set("font-size", "var(--lumo-font-size-s)");
                resultsArea.add(warningSpan);
              }
            }

            // Create download link
            StreamResource resource =
                new StreamResource(
                    result.filename(), () -> new ByteArrayInputStream(result.content()));
            resource.setContentType(result.contentType());
            resource.setCacheTime(0);

            Anchor downloadLink = new Anchor(resource, "");
            downloadLink.getElement().setAttribute("download", true);

            Button downloadBtn =
                new Button("Download " + result.filename(), VaadinIcon.DOWNLOAD.create());
            downloadBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
            downloadLink.add(downloadBtn);

            resultsArea.add(downloadLink);

            // Disable export button after successful export
            exportBtn.setEnabled(false);

            Notification.show(
                    "Direct credit file generated successfully",
                    3000,
                    Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

          } catch (Exception ex) {
            Notification.show("Export failed: " + ex.getMessage())
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
          }
        });

    dialog.getFooter().add(cancelBtn, exportBtn);
    dialog.open();
  }

  /** Updates the help text based on the selected export format. */
  private void updateExportHelpText(Span helpText, ExportFormat format) {
    switch (format) {
      case CSV:
        helpText.setText(
            "Generic CSV format compatible with most NZ bank portals. "
                + "Upload this file to your online banking to process batch payments.");
        break;
      case ABA:
        helpText.setText(
            "ABA format used by ANZ, Westpac, and some other banks. "
                + "Fixed-width format suitable for host-to-host banking systems.");
        break;
    }
  }
}
