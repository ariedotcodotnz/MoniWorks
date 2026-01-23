package com.example.application.ui.views;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.example.application.domain.*;
import com.example.application.domain.StatementRun.RunStatus;
import com.example.application.service.*;
import com.example.application.service.EmailService.EmailResult;
import com.example.application.service.StatementRunService.StatementCriteria;
import com.example.application.service.StatementService.StatementType;
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
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;

import jakarta.annotation.security.PermitAll;

/**
 * View for managing Statement Runs - batch customer statement generation. Allows creating runs with
 * filter criteria, previewing customers, and downloading combined PDFs.
 */
@Route(value = "statement-runs", layout = MainLayout.class)
@PageTitle("Statement Runs | MoniWorks")
@PermitAll
public class StatementRunsView extends VerticalLayout {

  private final StatementRunService runService;
  private final ContactService contactService;
  private final AttachmentService attachmentService;
  private final CompanyContextService companyContextService;
  private final EmailService emailService;
  private final StatementService statementService;

  private final Grid<StatementRun> runGrid = new Grid<>();
  private final VerticalLayout detailPanel = new VerticalLayout();

  private StatementRun selectedRun;
  private ComboBox<RunStatus> statusFilter;

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy");
  private static final DateTimeFormatter DATETIME_FORMAT =
      DateTimeFormatter.ofPattern("d MMM yyyy HH:mm");

  public StatementRunsView(
      StatementRunService runService,
      ContactService contactService,
      AttachmentService attachmentService,
      CompanyContextService companyContextService,
      EmailService emailService,
      StatementService statementService) {
    this.runService = runService;
    this.contactService = contactService;
    this.attachmentService = attachmentService;
    this.companyContextService = companyContextService;
    this.emailService = emailService;
    this.statementService = statementService;

    addClassName("statement-runs-view");
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
    runGrid.addClassNames("statement-runs-grid");
    runGrid.setSizeFull();
    runGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

    runGrid
        .addColumn(run -> formatDate(run.getRunDate()))
        .setHeader("Run Date")
        .setSortable(true)
        .setAutoWidth(true);

    runGrid
        .addColumn(run -> formatDate(run.getAsOfDate()))
        .setHeader("As Of Date")
        .setSortable(true)
        .setAutoWidth(true);

    runGrid.addComponentColumn(this::createStatusBadge).setHeader("Status").setAutoWidth(true);

    runGrid
        .addColumn(
            run -> run.getStatementCount() != null ? run.getStatementCount().toString() : "-")
        .setHeader("Statements")
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

  private Span createStatusBadge(StatementRun run) {
    Span badge = new Span(run.getStatus().name());
    badge.getElement().getThemeList().add("badge");

    switch (run.getStatus()) {
      case COMPLETED -> badge.getElement().getThemeList().add("success");
      case PROCESSING -> badge.getElement().getThemeList().add("primary");
      case PENDING -> badge.getElement().getThemeList().add("contrast");
      case FAILED -> badge.getElement().getThemeList().add("error");
    }

    return badge;
  }

  private HorizontalLayout createToolbar() {
    H2 title = new H2("Statement Runs");

    statusFilter = new ComboBox<>();
    statusFilter.setPlaceholder("All Statuses");
    statusFilter.setItems(RunStatus.values());
    statusFilter.setClearButtonVisible(true);
    statusFilter.addValueChangeListener(e -> loadRuns());
    statusFilter.setWidth("150px");

    Button createButton = new Button("Create Statement Run", VaadinIcon.PLUS.create());
    createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    createButton.addClickListener(e -> openCreateDialog());

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
    List<StatementRun> runs;

    if (statusFilter.getValue() != null) {
      runs = runService.findByCompanyAndStatus(company, statusFilter.getValue());
    } else {
      runs = runService.findByCompany(company);
    }

    runGrid.setItems(runs);
  }

  private void updateDetailPanel() {
    detailPanel.removeAll();
    detailPanel.setPadding(true);
    detailPanel.setSpacing(true);

    if (selectedRun == null) {
      detailPanel.add(new Span("Select a statement run to view details"));
      return;
    }

    // Header with run info
    H3 header = new H3("Statement Run #" + selectedRun.getId());

    // Action buttons based on status
    HorizontalLayout actionButtons = new HorizontalLayout();
    actionButtons.setSpacing(true);

    if (selectedRun.isPending()) {
      Button processBtn = new Button("Process Now", VaadinIcon.PLAY.create());
      processBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
      processBtn.addClickListener(e -> processRun());
      actionButtons.add(processBtn);
    }

    if (selectedRun.isCompleted() && selectedRun.getOutputAttachmentId() != null) {
      Button downloadBtn = new Button("Download PDF", VaadinIcon.DOWNLOAD.create());
      downloadBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
      downloadBtn.addClickListener(e -> downloadPdf());
      actionButtons.add(downloadBtn);

      Button emailBtn = new Button("Email Statements", VaadinIcon.ENVELOPE.create());
      emailBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
      emailBtn.addClickListener(e -> openEmailStatementsDialog());
      actionButtons.add(emailBtn);
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
    details.add(createDetailRow("As Of Date:", formatDate(selectedRun.getAsOfDate())));
    details.add(createDetailRow("Created At:", formatDateTime(selectedRun.getCreatedAt())));

    if (selectedRun.getCreatedBy() != null) {
      details.add(createDetailRow("Created By:", selectedRun.getCreatedBy().getDisplayName()));
    }

    if (selectedRun.getStatementCount() != null) {
      details.add(
          createDetailRow("Statements Generated:", selectedRun.getStatementCount().toString()));
    }

    if (selectedRun.getCompletedAt() != null) {
      details.add(createDetailRow("Completed At:", formatDateTime(selectedRun.getCompletedAt())));
    }

    if (selectedRun.isFailed() && selectedRun.getErrorMessage() != null) {
      Span errorLabel = new Span("Error:");
      errorLabel.getStyle().set("font-weight", "bold");
      errorLabel.getStyle().set("color", "var(--lumo-error-color)");

      Span errorValue = new Span(selectedRun.getErrorMessage());
      errorValue.getStyle().set("color", "var(--lumo-error-color)");

      HorizontalLayout errorRow = new HorizontalLayout(errorLabel, errorValue);
      errorRow.setSpacing(true);
      details.add(errorRow);
    }

    // Criteria section
    H3 criteriaHeader = new H3("Filter Criteria");
    VerticalLayout criteriaDetails = new VerticalLayout();
    criteriaDetails.setPadding(false);
    criteriaDetails.setSpacing(false);

    String criteriaJson = selectedRun.getCriteriaJson();
    if (criteriaJson != null && !criteriaJson.isBlank()) {
      criteriaDetails.add(new Span(criteriaJson));
    } else {
      criteriaDetails.add(new Span("Default criteria (all customers with balances)"));
    }

    detailPanel.add(headerLayout, details, criteriaHeader, criteriaDetails);
  }

  private HorizontalLayout createDetailRow(String label, String value) {
    Span labelSpan = new Span(label);
    labelSpan.getStyle().set("font-weight", "bold");
    labelSpan.setWidth("150px");

    Span valueSpan = new Span(value);

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

  // Actions

  private void processRun() {
    if (selectedRun == null || !selectedRun.isPending()) {
      return;
    }

    User currentUser = companyContextService.getCurrentUser();

    try {
      selectedRun = runService.processRun(selectedRun, currentUser);

      if (selectedRun.isCompleted()) {
        Notification.show(
                "Statement run completed. Generated "
                    + selectedRun.getStatementCount()
                    + " statements.",
                5000,
                Notification.Position.BOTTOM_START)
            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
      } else if (selectedRun.isFailed()) {
        Notification.show(
                "Statement run failed: " + selectedRun.getErrorMessage(),
                5000,
                Notification.Position.MIDDLE)
            .addThemeVariants(NotificationVariant.LUMO_ERROR);
      }

      loadRuns();
      updateDetailPanel();
    } catch (Exception e) {
      Notification.show(
              "Error processing run: " + e.getMessage(), 5000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
  }

  private void downloadPdf() {
    if (selectedRun == null || selectedRun.getOutputAttachmentId() == null) {
      return;
    }

    attachmentService
        .findById(selectedRun.getOutputAttachmentId())
        .ifPresent(
            attachment -> {
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
                        "Error downloading PDF: " + e.getMessage(),
                        5000,
                        Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
              }
            });
  }

  private void openEmailStatementsDialog() {
    if (selectedRun == null || !selectedRun.isCompleted()) {
      return;
    }

    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Email Statements to Customers");
    dialog.setWidth("600px");

    Company company = companyContextService.getCurrentCompany();
    LocalDate asOfDate = selectedRun.getAsOfDate();

    // Re-calculate the customers included in this run using the same criteria
    StatementCriteria criteria = parseCriteriaFromRun(selectedRun);
    List<Contact> customers =
        runService.previewCustomers(company, asOfDate, criteria).stream()
            .filter(c -> c.getEmail() != null && !c.getEmail().isBlank())
            .collect(Collectors.toList());

    if (customers.isEmpty()) {
      VerticalLayout content = new VerticalLayout();
      content.add(new Span("No customers in this statement run have email addresses configured."));
      dialog.add(content);
      Button closeBtn = new Button("Close", e -> dialog.close());
      dialog.getFooter().add(closeBtn);
      dialog.open();
      return;
    }

    VerticalLayout content = new VerticalLayout();
    content.setPadding(false);
    content.setSpacing(true);

    content.add(new Span("Send individual statements to the following customers:"));
    content.add(new Span("Statements will be generated as of " + formatDate(asOfDate)));

    Checkbox selectAll = new Checkbox("Select All");
    selectAll.setValue(true);

    VerticalLayout customerList = new VerticalLayout();
    customerList.setPadding(false);
    customerList.setSpacing(false);
    customerList.getStyle().set("max-height", "300px");
    customerList.getStyle().set("overflow-y", "auto");

    Map<Checkbox, Contact> checkboxToCustomer = new LinkedHashMap<>();
    List<Checkbox> customerCheckboxes = new ArrayList<>();

    for (Contact customer : customers) {
      Checkbox cb = new Checkbox(customer.getName() + " (" + customer.getEmail() + ")");
      cb.setValue(true);
      checkboxToCustomer.put(cb, customer);
      customerCheckboxes.add(cb);
      customerList.add(cb);
    }

    selectAll.addValueChangeListener(
        e -> customerCheckboxes.forEach(cb -> cb.setValue(e.getValue())));

    content.add(selectAll, customerList);

    Span summaryText = new Span(customers.size() + " customer(s) with email addresses");
    summaryText.getStyle().set("font-style", "italic");
    summaryText.getStyle().set("color", "var(--lumo-secondary-text-color)");
    content.add(summaryText);

    Button cancelBtn = new Button("Cancel", e -> dialog.close());
    Button sendBtn = new Button("Send Emails", VaadinIcon.ENVELOPE.create());
    sendBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    sendBtn.addClickListener(
        e -> {
          List<Contact> selectedCustomers =
              customerCheckboxes.stream()
                  .filter(Checkbox::getValue)
                  .map(checkboxToCustomer::get)
                  .collect(Collectors.toList());

          if (selectedCustomers.isEmpty()) {
            Notification.show(
                    "Please select at least one customer", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }

          sendStatementEmails(selectedCustomers, asOfDate);
          dialog.close();
        });

    dialog.add(content);
    dialog.getFooter().add(cancelBtn, sendBtn);
    dialog.open();
  }

  private void sendStatementEmails(List<Contact> customers, LocalDate asOfDate) {
    Company company = companyContextService.getCurrentCompany();
    User currentUser = companyContextService.getCurrentUser();

    int sent = 0;
    int failed = 0;

    for (Contact customer : customers) {
      try {
        // Generate individual statement PDF for this customer
        byte[] pdfContent = statementService.generateStatementPdf(company, customer, asOfDate);
        EmailResult result = emailService.sendStatement(customer, company, pdfContent, currentUser);

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
              "Statements sent to " + sent + " customer(s)",
              5000,
              Notification.Position.BOTTOM_START)
          .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    } else {
      Notification.show("Sent: " + sent + ", Failed: " + failed, 5000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_WARNING);
    }
  }

  /**
   * Parses criteria JSON from a statement run back into a StatementCriteria object. Used for
   * retrieving the customers in a completed run for email sending.
   */
  private StatementCriteria parseCriteriaFromRun(StatementRun run) {
    String json = run.getCriteriaJson();
    if (json == null || json.isBlank()) {
      return StatementCriteria.defaultCriteria();
    }

    try {
      com.fasterxml.jackson.databind.ObjectMapper objectMapper =
          new com.fasterxml.jackson.databind.ObjectMapper();
      com.fasterxml.jackson.databind.node.ObjectNode node =
          (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(json);

      BigDecimal minimumBalance = null;
      if (node.has("minimumBalance")) {
        minimumBalance = new BigDecimal(node.get("minimumBalance").asText());
      }

      Integer minimumDaysOverdue = null;
      if (node.has("minimumDaysOverdue")) {
        minimumDaysOverdue = node.get("minimumDaysOverdue").asInt();
      }

      List<Long> contactIds = new ArrayList<>();
      if (node.has("contactIds")) {
        node.get("contactIds").forEach(n -> contactIds.add(n.asLong()));
      }

      boolean includeZeroBalance =
          node.has("includeZeroBalance") && node.get("includeZeroBalance").asBoolean();

      StatementType statementType = StatementType.OPEN_ITEM;
      if (node.has("statementType")) {
        try {
          statementType = StatementType.valueOf(node.get("statementType").asText());
        } catch (IllegalArgumentException ignored) {
          // Use default OPEN_ITEM
        }
      }

      LocalDate periodStart = null;
      if (node.has("periodStart")) {
        periodStart = LocalDate.parse(node.get("periodStart").asText());
      }

      return new StatementCriteria(
          minimumBalance,
          minimumDaysOverdue,
          contactIds,
          includeZeroBalance,
          statementType,
          periodStart);

    } catch (Exception e) {
      return StatementCriteria.defaultCriteria();
    }
  }

  // Create Dialog

  private void openCreateDialog() {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Create Statement Run");
    dialog.setWidth("700px");

    FormLayout form = new FormLayout();

    // Statement Type selector
    ComboBox<StatementType> statementTypeField = new ComboBox<>("Statement Type");
    statementTypeField.setItems(StatementType.values());
    statementTypeField.setValue(StatementType.OPEN_ITEM);
    statementTypeField.setItemLabelGenerator(
        type -> type == StatementType.OPEN_ITEM ? "Open Item" : "Balance Forward");
    statementTypeField.setRequired(true);
    statementTypeField.setHelperText(
        "Open Item lists individual invoices; Balance Forward shows period activity");

    DatePicker asOfDateField = new DatePicker("As Of Date");
    asOfDateField.setRequired(true);
    asOfDateField.setValue(LocalDate.now());
    asOfDateField.setHelperText("Generate statements as of this date");

    // Period Start for Balance Forward statements
    DatePicker periodStartField = new DatePicker("Period Start Date");
    periodStartField.setValue(LocalDate.now().withDayOfMonth(1));
    periodStartField.setHelperText("Start date for balance-forward statement period");
    periodStartField.setVisible(false);

    // Show/hide period start based on statement type
    statementTypeField.addValueChangeListener(
        e -> {
          boolean isBalanceForward = e.getValue() == StatementType.BALANCE_FORWARD;
          periodStartField.setVisible(isBalanceForward);
          periodStartField.setRequired(isBalanceForward);
        });

    BigDecimalField minimumBalanceField = new BigDecimalField("Minimum Balance");
    minimumBalanceField.setValue(BigDecimal.ZERO);
    minimumBalanceField.setHelperText("Only include customers with balance >= this amount");

    IntegerField minimumDaysOverdueField = new IntegerField("Minimum Days Overdue");
    minimumDaysOverdueField.setClearButtonVisible(true);
    minimumDaysOverdueField.setMin(0);
    minimumDaysOverdueField.setStepButtonsVisible(true);
    minimumDaysOverdueField.setHelperText(
        "Only include customers with invoices overdue >= this many days");

    Checkbox includeZeroBalanceField = new Checkbox("Include Zero Balance Customers");
    includeZeroBalanceField.setValue(false);
    includeZeroBalanceField.setHelperText(
        "Include customers with no outstanding balance (rarely needed)");

    // Customer multi-select
    Company company = companyContextService.getCurrentCompany();
    List<Contact> customers = contactService.findActiveCustomers(company);

    MultiSelectComboBox<Contact> customerSelect =
        new MultiSelectComboBox<>("Specific Customers (Optional)");
    customerSelect.setItems(customers);
    customerSelect.setItemLabelGenerator(c -> c.getCode() + " - " + c.getName());
    customerSelect.setClearButtonVisible(true);
    customerSelect.setWidthFull();
    customerSelect.setHelperText("Leave empty to include all customers matching other criteria");

    // Preview section
    VerticalLayout previewSection = new VerticalLayout();
    previewSection.setPadding(false);
    previewSection.setSpacing(true);

    Grid<Contact> previewGrid = new Grid<>();
    previewGrid.addColumn(Contact::getCode).setHeader("Code").setAutoWidth(true);
    previewGrid.addColumn(Contact::getName).setHeader("Name").setFlexGrow(1);
    previewGrid.setHeight("200px");
    previewGrid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);

    Span previewLabel = new Span("Customers to be included:");
    previewLabel.getStyle().set("font-weight", "bold");

    Span previewCount = new Span("");

    Button previewButton = new Button("Preview Customers", VaadinIcon.EYE.create());
    previewButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    previewButton.addClickListener(
        e -> {
          StatementCriteria criteria =
              buildCriteria(
                  minimumBalanceField.getValue(),
                  minimumDaysOverdueField.getValue(),
                  customerSelect.getValue(),
                  includeZeroBalanceField.getValue(),
                  statementTypeField.getValue(),
                  periodStartField.getValue());

          List<Contact> preview =
              runService.previewCustomers(company, asOfDateField.getValue(), criteria);

          previewGrid.setItems(preview);
          previewCount.setText(preview.size() + " customer(s) will receive statements");
        });

    previewSection.add(previewButton, previewLabel, previewCount, previewGrid);

    form.add(
        statementTypeField,
        asOfDateField,
        periodStartField,
        minimumBalanceField,
        minimumDaysOverdueField,
        includeZeroBalanceField,
        customerSelect,
        previewSection);
    form.setColspan(customerSelect, 2);
    form.setColspan(previewSection, 2);
    form.setResponsiveSteps(
        new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("400px", 2));

    Button createButton = new Button("Create Run");
    createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    createButton.addClickListener(
        e -> {
          if (asOfDateField.isEmpty()) {
            Notification.show("Please select an As Of Date", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }

          if (statementTypeField.getValue() == StatementType.BALANCE_FORWARD
              && periodStartField.isEmpty()) {
            Notification.show(
                    "Please select a Period Start Date for Balance Forward statements",
                    3000,
                    Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }

          try {
            StatementCriteria criteria =
                buildCriteria(
                    minimumBalanceField.getValue(),
                    minimumDaysOverdueField.getValue(),
                    customerSelect.getValue(),
                    includeZeroBalanceField.getValue(),
                    statementTypeField.getValue(),
                    periodStartField.getValue());

            User currentUser = companyContextService.getCurrentUser();
            StatementRun run =
                runService.createRun(company, asOfDateField.getValue(), criteria, currentUser);

            Notification.show(
                    "Statement run created. Click 'Process Now' to generate statements.",
                    5000,
                    Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            dialog.close();
            loadRuns();

            // Select the new run
            runGrid.select(run);
          } catch (Exception ex) {
            Notification.show("Error: " + ex.getMessage(), 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
          }
        });

    Button createAndProcessButton = new Button("Create & Process");
    createAndProcessButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
    createAndProcessButton.addClickListener(
        e -> {
          if (asOfDateField.isEmpty()) {
            Notification.show("Please select an As Of Date", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }

          if (statementTypeField.getValue() == StatementType.BALANCE_FORWARD
              && periodStartField.isEmpty()) {
            Notification.show(
                    "Please select a Period Start Date for Balance Forward statements",
                    3000,
                    Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }

          try {
            StatementCriteria criteria =
                buildCriteria(
                    minimumBalanceField.getValue(),
                    minimumDaysOverdueField.getValue(),
                    customerSelect.getValue(),
                    includeZeroBalanceField.getValue(),
                    statementTypeField.getValue(),
                    periodStartField.getValue());

            User currentUser = companyContextService.getCurrentUser();
            StatementRun run =
                runService.createAndProcessRun(
                    company, asOfDateField.getValue(), criteria, currentUser);

            if (run.isCompleted()) {
              Notification.show(
                      "Statement run completed. Generated "
                          + run.getStatementCount()
                          + " statements.",
                      5000,
                      Notification.Position.BOTTOM_START)
                  .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } else if (run.isFailed()) {
              Notification.show(
                      "Statement run failed: " + run.getErrorMessage(),
                      5000,
                      Notification.Position.MIDDLE)
                  .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }

            dialog.close();
            loadRuns();

            // Select the new run
            runGrid.select(run);
          } catch (Exception ex) {
            Notification.show("Error: " + ex.getMessage(), 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
          }
        });

    Button cancelButton = new Button("Cancel", e -> dialog.close());

    dialog.add(form);
    dialog.getFooter().add(cancelButton, createButton, createAndProcessButton);
    dialog.open();
  }

  private StatementCriteria buildCriteria(
      BigDecimal minimumBalance,
      Integer minimumDaysOverdue,
      Set<Contact> selectedCustomers,
      boolean includeZeroBalance,
      StatementService.StatementType statementType,
      LocalDate periodStart) {
    List<Long> contactIds =
        selectedCustomers != null && !selectedCustomers.isEmpty()
            ? selectedCustomers.stream().map(Contact::getId).collect(Collectors.toList())
            : List.of();

    if (statementType == StatementService.StatementType.BALANCE_FORWARD) {
      return StatementCriteria.balanceForwardCriteria(periodStart, contactIds, includeZeroBalance);
    } else {
      return StatementCriteria.openItemCriteria(
          minimumBalance != null ? minimumBalance : BigDecimal.ZERO,
          minimumDaysOverdue,
          contactIds,
          includeZeroBalance);
    }
  }
}
