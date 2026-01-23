package com.example.application.ui.views;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.example.application.domain.*;
import com.example.application.security.Permissions;
import com.example.application.service.*;
import com.example.application.ui.MainLayout;
import com.example.application.ui.components.AttachmentSection;
import com.example.application.ui.components.GridCustomizer;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
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
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;

import jakarta.annotation.security.PermitAll;

/**
 * View for managing Supplier Bills (Accounts Payable). Features a master-detail layout with: -
 * Left: Bill list with status filters - Right: Bill detail with line items and actions
 *
 * <p>Supports query parameter ?new=true for keyboard shortcut navigation to directly open new bill
 * dialog.
 */
@Route(value = "bills", layout = MainLayout.class)
@PageTitle("Supplier Bills | MoniWorks")
@PermitAll
public class SupplierBillsView extends VerticalLayout implements BeforeEnterObserver {

  private final SupplierBillService billService;
  private final ContactService contactService;
  private final AccountService accountService;
  private final ProductService productService;
  private final TaxCodeService taxCodeService;
  private final CompanyContextService companyContextService;
  private final PayableAllocationService payableAllocationService;
  private final SavedViewService savedViewService;
  private final BillPdfService billPdfService;
  private final CompanyService companyService;
  private final AttachmentService attachmentService;

  private final Grid<SupplierBill> grid = new Grid<>();
  private final TextField searchField = new TextField();
  private final ComboBox<String> statusFilter = new ComboBox<>();
  private GridCustomizer<SupplierBill> gridCustomizer;

  private final VerticalLayout detailLayout = new VerticalLayout();
  private SupplierBill selectedBill;

  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("dd MMM yyyy");

  public SupplierBillsView(
      SupplierBillService billService,
      ContactService contactService,
      AccountService accountService,
      ProductService productService,
      TaxCodeService taxCodeService,
      CompanyContextService companyContextService,
      PayableAllocationService payableAllocationService,
      SavedViewService savedViewService,
      BillPdfService billPdfService,
      CompanyService companyService,
      AttachmentService attachmentService) {
    this.billService = billService;
    this.contactService = contactService;
    this.accountService = accountService;
    this.productService = productService;
    this.taxCodeService = taxCodeService;
    this.companyContextService = companyContextService;
    this.payableAllocationService = payableAllocationService;
    this.savedViewService = savedViewService;
    this.billPdfService = billPdfService;
    this.companyService = companyService;
    this.attachmentService = attachmentService;

    addClassName("bills-view");
    setSizeFull();
    setPadding(false);

    add(createToolbar());

    SplitLayout splitLayout = new SplitLayout();
    splitLayout.setSizeFull();
    splitLayout.setSplitterPosition(50);

    VerticalLayout listLayout = new VerticalLayout();
    listLayout.setSizeFull();
    listLayout.setPadding(false);
    configureGrid();
    listLayout.add(grid);

    detailLayout.setSizeFull();
    detailLayout.setPadding(true);
    showNoSelection();

    splitLayout.addToPrimary(listLayout);
    splitLayout.addToSecondary(detailLayout);

    add(splitLayout);
    expand(splitLayout);

    loadBills();
  }

  @Override
  public void beforeEnter(BeforeEnterEvent event) {
    QueryParameters queryParams = event.getLocation().getQueryParameters();

    // Check if we should open a new bill dialog from keyboard shortcut (Alt+B)
    List<String> newParam = queryParams.getParameters().getOrDefault("new", List.of());

    if (!newParam.isEmpty() && "true".equals(newParam.get(0))) {
      // Schedule the dialog to open after the view is fully attached
      getUI().ifPresent(ui -> ui.accessLater(() -> openNewBillDialog(), () -> {}));
    }
  }

  private void configureGrid() {
    grid.addClassName("bills-grid");
    grid.setSizeFull();
    grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

    grid.addColumn(SupplierBill::getBillNumber)
        .setHeader("Bill #")
        .setKey("billNumber")
        .setSortable(true)
        .setResizable(true)
        .setAutoWidth(true)
        .setFlexGrow(0);

    grid.addColumn(b -> b.getContact().getName())
        .setHeader("Supplier")
        .setKey("supplier")
        .setSortable(true)
        .setResizable(true)
        .setFlexGrow(1);

    grid.addColumn(b -> b.getBillDate().format(DATE_FORMATTER))
        .setHeader("Date")
        .setKey("billDate")
        .setSortable(true)
        .setResizable(true)
        .setAutoWidth(true);

    grid.addColumn(b -> b.getDueDate().format(DATE_FORMATTER))
        .setHeader("Due")
        .setKey("dueDate")
        .setSortable(true)
        .setResizable(true)
        .setAutoWidth(true);

    grid.addColumn(b -> "$" + b.getTotal().toPlainString())
        .setHeader("Total")
        .setKey("total")
        .setSortable(true)
        .setResizable(true)
        .setAutoWidth(true);

    grid.addColumn(b -> "$" + b.getBalance().toPlainString())
        .setHeader("Balance")
        .setKey("balance")
        .setSortable(true)
        .setResizable(true)
        .setAutoWidth(true);

    grid.addComponentColumn(this::createStatusBadge)
        .setHeader("Status")
        .setKey("status")
        .setResizable(true)
        .setAutoWidth(true);

    grid.asSingleSelect()
        .addValueChangeListener(
            e -> {
              selectedBill = e.getValue();
              if (selectedBill != null) {
                showBillDetail(selectedBill);
              } else {
                showNoSelection();
              }
            });
  }

  private Span createStatusBadge(SupplierBill bill) {
    Span badge = new Span(bill.getStatus().name());
    badge.getElement().getThemeList().add("badge");

    switch (bill.getStatus()) {
      case DRAFT -> badge.getElement().getThemeList().add("contrast");
      case POSTED -> {
        if (bill.isPaid()) {
          badge.setText("PAID");
          badge.getElement().getThemeList().add("success");
        } else if (bill.isOverdue()) {
          badge.setText("OVERDUE");
          badge.getElement().getThemeList().add("error");
        } else {
          badge.getElement().getThemeList().add("primary");
        }
      }
      case VOID -> badge.getElement().getThemeList().add("error");
    }

    return badge;
  }

  private Span createTypeBadge(SupplierBill bill) {
    if (bill.isDebitNote()) {
      Span badge = new Span("DEBIT NOTE");
      badge.getElement().getThemeList().add("badge");
      badge.getElement().getThemeList().add("contrast");
      return badge;
    }
    return null;
  }

  private HorizontalLayout createToolbar() {
    H2 title = new H2("Supplier Bills");

    searchField.setPlaceholder("Search bills...");
    searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
    searchField.setClearButtonVisible(true);
    searchField.addValueChangeListener(e -> filterBills());
    searchField.setWidth("200px");

    statusFilter.setPlaceholder("All Statuses");
    statusFilter.setItems("All", "Draft", "Posted", "Overdue", "Paid", "Void");
    statusFilter.setValue("All");
    statusFilter.addValueChangeListener(e -> filterBills());
    statusFilter.setWidth("130px");

    // Grid customizer for column visibility and saved views
    Company company = companyContextService.getCurrentCompany();
    User user = companyContextService.getCurrentUser();
    if (company != null && user != null) {
      gridCustomizer =
          new GridCustomizer<>(grid, SavedView.EntityType.SUPPLIER_BILL, savedViewService, company, user);
    }

    Button refreshButton = new Button(VaadinIcon.REFRESH.create());
    refreshButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    refreshButton.addClickListener(e -> loadBills());
    refreshButton.getElement().setAttribute("title", "Refresh");

    HorizontalLayout filters = new HorizontalLayout(searchField, statusFilter);
    if (gridCustomizer != null) {
      filters.add(gridCustomizer);
    }

    // Only show New Bill button if user has MANAGE_BILLS permission
    if (companyContextService.hasPermission(Permissions.MANAGE_BILLS)) {
      Button addButton = new Button("New Bill", VaadinIcon.PLUS.create());
      addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
      addButton.addClickListener(e -> openNewBillDialog());
      filters.add(addButton);
    }
    filters.add(refreshButton);
    filters.setAlignItems(FlexComponent.Alignment.BASELINE);

    HorizontalLayout toolbar = new HorizontalLayout(title, filters);
    toolbar.setWidthFull();
    toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
    toolbar.setAlignItems(FlexComponent.Alignment.CENTER);
    toolbar.setPadding(true);

    return toolbar;
  }

  private void loadBills() {
    Company company = companyContextService.getCurrentCompany();
    List<SupplierBill> bills = billService.findByCompany(company);
    grid.setItems(bills);
  }

  private void filterBills() {
    Company company = companyContextService.getCurrentCompany();
    String search = searchField.getValue();
    String statusValue = statusFilter.getValue();

    List<SupplierBill> bills;
    if (search != null && !search.isBlank()) {
      bills = billService.searchByCompany(company, search);
    } else {
      bills = billService.findByCompany(company);
    }

    // Filter by status
    if (statusValue != null && !"All".equals(statusValue)) {
      bills = bills.stream().filter(bill -> matchesStatusFilter(bill, statusValue)).toList();
    }

    grid.setItems(bills);
  }

  private boolean matchesStatusFilter(SupplierBill bill, String filter) {
    return switch (filter) {
      case "Draft" -> bill.isDraft();
      case "Posted" -> bill.isPosted() && !bill.isPaid() && !bill.isOverdue();
      case "Overdue" -> bill.isPosted() && bill.isOverdue();
      case "Paid" -> bill.isPosted() && bill.isPaid();
      case "Void" -> bill.isVoid();
      default -> true;
    };
  }

  private void showNoSelection() {
    detailLayout.removeAll();
    Span message = new Span("Select a bill to view details");
    message.getStyle().set("color", "var(--lumo-secondary-text-color)");
    detailLayout.add(message);
    detailLayout.setAlignItems(FlexComponent.Alignment.CENTER);
    detailLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
  }

  private void showBillDetail(SupplierBill bill) {
    detailLayout.removeAll();
    detailLayout.setAlignItems(FlexComponent.Alignment.STRETCH);
    detailLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.START);

    // Header with bill number, type, and status
    HorizontalLayout header = new HorizontalLayout();
    header.setWidthFull();
    header.setAlignItems(FlexComponent.Alignment.CENTER);

    H3 billLabel = new H3("Bill #" + bill.getBillNumber());
    header.add(billLabel);

    // Show type badge for debit notes
    Span typeBadge = createTypeBadge(bill);
    if (typeBadge != null) {
      header.add(typeBadge);
    }
    header.add(createStatusBadge(bill));

    // Action buttons
    HorizontalLayout actions = new HorizontalLayout();
    actions.setSpacing(true);

    // Check user permissions for bill management
    boolean canManage = companyContextService.hasPermission(Permissions.MANAGE_BILLS);

    if (bill.isDraft() && canManage) {
      Button editButton = new Button("Edit", VaadinIcon.EDIT.create());
      editButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
      editButton.addClickListener(e -> openEditBillDialog(bill));

      Button addLineButton = new Button("Add Line", VaadinIcon.PLUS.create());
      addLineButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
      addLineButton.addClickListener(e -> openAddLineDialog(bill));

      // Different post action for debit notes
      if (bill.isDebitNote()) {
        Button postButton = new Button("Post Debit Note", VaadinIcon.CHECK.create());
        postButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        postButton.addClickListener(e -> postDebitNote(bill));
        actions.add(editButton, addLineButton, postButton);
      } else {
        Button postButton = new Button("Post Bill", VaadinIcon.CHECK.create());
        postButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        postButton.addClickListener(e -> postBill(bill));
        actions.add(editButton, addLineButton, postButton);
      }
    }

    if (bill.isPosted() && !bill.isPaid() && canManage) {
      // Debit note button for posted regular bills (not for debit notes themselves)
      if (bill.isBill()) {
        Button debitNoteButton = new Button("Debit Note", VaadinIcon.CREDIT_CARD.create());
        debitNoteButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        debitNoteButton.addClickListener(e -> openCreateDebitNoteDialog(bill));
        actions.add(debitNoteButton);
      }

      // Void button - use different method for debit notes
      Button voidButton = new Button("Void", VaadinIcon.BAN.create());
      voidButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
      if (bill.isDebitNote()) {
        voidButton.addClickListener(e -> confirmVoidDebitNote(bill));
      } else {
        voidButton.addClickListener(e -> confirmVoidBill(bill));
      }
      actions.add(voidButton);
    }

    // PDF export for posted bills (including paid and debit notes)
    if (bill.isPosted()) {
      Button exportPdfButton = new Button("Export PDF", VaadinIcon.DOWNLOAD.create());
      exportPdfButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
      exportPdfButton.addClickListener(e -> exportBillPdf(bill));
      actions.add(exportPdfButton);
    }

    HorizontalLayout spacer = new HorizontalLayout();
    spacer.setWidthFull();
    header.add(spacer, actions);
    header.expand(spacer);

    detailLayout.add(header);

    // Show original bill link for debit notes
    if (bill.isDebitNote() && bill.getOriginalBill() != null) {
      SupplierBill originalBill = bill.getOriginalBill();
      HorizontalLayout linkRow = new HorizontalLayout();
      linkRow.setAlignItems(FlexComponent.Alignment.CENTER);
      linkRow.getStyle().set("margin-bottom", "var(--lumo-space-s)");

      Span linkLabel = new Span("Debit note for bill: ");
      Button linkButton = new Button(originalBill.getBillNumber());
      linkButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
      linkButton.addClickListener(
          e -> {
            grid.select(originalBill);
            showBillDetail(originalBill);
          });

      linkRow.add(linkLabel, linkButton);
      detailLayout.add(linkRow);
    }

    // Bill info section
    FormLayout infoForm = new FormLayout();
    infoForm.setResponsiveSteps(
        new FormLayout.ResponsiveStep("0", 2), new FormLayout.ResponsiveStep("500px", 4));

    addReadOnlyField(infoForm, "Supplier", bill.getContact().getName());
    addReadOnlyField(infoForm, "Bill Date", bill.getBillDate().format(DATE_FORMATTER));
    addReadOnlyField(infoForm, "Due Date", bill.getDueDate().format(DATE_FORMATTER));
    addReadOnlyField(infoForm, "Supplier Ref", bill.getSupplierReference());

    detailLayout.add(infoForm);

    // Line items grid
    H3 linesHeader = new H3("Line Items");
    detailLayout.add(linesHeader);

    Grid<SupplierBillLine> linesGrid = new Grid<>();
    linesGrid.setHeight("200px");
    linesGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

    linesGrid.addColumn(SupplierBillLine::getDescription).setHeader("Description").setFlexGrow(2);

    linesGrid
        .addColumn(line -> line.getAccount().getCode())
        .setHeader("Account")
        .setAutoWidth(true);

    linesGrid
        .addColumn(line -> line.getQuantity().toPlainString())
        .setHeader("Qty")
        .setAutoWidth(true);

    linesGrid
        .addColumn(line -> "$" + line.getUnitPrice().toPlainString())
        .setHeader("Price")
        .setAutoWidth(true);

    linesGrid
        .addColumn(line -> line.getTaxCode() != null ? line.getTaxCode() : "-")
        .setHeader("Tax")
        .setAutoWidth(true);

    linesGrid
        .addColumn(line -> "$" + line.getLineTotal().toPlainString())
        .setHeader("Net")
        .setAutoWidth(true);

    linesGrid
        .addColumn(line -> "$" + line.getGrossTotal().toPlainString())
        .setHeader("Total")
        .setAutoWidth(true);

    // Delete button column for draft bills
    if (bill.isDraft()) {
      linesGrid
          .addComponentColumn(
              line -> {
                Button deleteBtn = new Button(VaadinIcon.TRASH.create());
                deleteBtn.addThemeVariants(
                    ButtonVariant.LUMO_TERTIARY,
                    ButtonVariant.LUMO_SMALL,
                    ButtonVariant.LUMO_ERROR);
                deleteBtn.addClickListener(
                    e -> {
                      billService.removeLine(bill, line);
                      showBillDetail(billService.findById(bill.getId()).orElse(bill));
                      loadBills();
                    });
                return deleteBtn;
              })
          .setHeader("")
          .setAutoWidth(true);
    }

    linesGrid.setItems(bill.getLines());
    detailLayout.add(linesGrid);

    // Totals section
    FormLayout totalsForm = new FormLayout();
    totalsForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));

    addReadOnlyField(totalsForm, "Subtotal", "$" + bill.getSubtotal().toPlainString());
    addReadOnlyField(totalsForm, "Tax", "$" + bill.getTaxTotal().toPlainString());
    addReadOnlyField(totalsForm, "Total", "$" + bill.getTotal().toPlainString());
    addReadOnlyField(totalsForm, "Paid", "$" + bill.getAmountPaid().toPlainString());
    addReadOnlyField(totalsForm, "Balance", "$" + bill.getBalance().toPlainString());

    detailLayout.add(new H3("Totals"), totalsForm);

    // Allocations section (for posted bills with payments)
    if (bill.isPosted() && bill.getAmountPaid().compareTo(BigDecimal.ZERO) > 0) {
      List<PayableAllocation> allocations = payableAllocationService.findByBill(bill);
      if (!allocations.isEmpty()) {
        H3 allocationsHeader = new H3("Payment Allocations");
        detailLayout.add(allocationsHeader);

        Grid<PayableAllocation> allocGrid = new Grid<>();
        allocGrid.setHeight("120px");
        allocGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

        allocGrid
            .addColumn(a -> a.getPaymentTransaction().getTransactionDate().format(DATE_FORMATTER))
            .setHeader("Date")
            .setAutoWidth(true);

        allocGrid
            .addColumn(
                a ->
                    a.getPaymentTransaction().getReference() != null
                        ? a.getPaymentTransaction().getReference()
                        : a.getPaymentTransaction().getDescription())
            .setHeader("Payment")
            .setFlexGrow(1);

        allocGrid
            .addColumn(a -> "$" + a.getAmount().setScale(2).toPlainString())
            .setHeader("Amount")
            .setAutoWidth(true);

        allocGrid
            .addColumn(a -> a.getAllocatedAt().toString().substring(0, 10))
            .setHeader("Allocated")
            .setAutoWidth(true);

        allocGrid.setItems(allocations);
        detailLayout.add(allocGrid);
      }
    }

    // Debit notes section (for posted regular bills)
    if (bill.isPosted() && bill.isBill()) {
      List<SupplierBill> debitNotes = billService.findDebitNotesForBill(bill);
      if (!debitNotes.isEmpty()) {
        H3 debitNotesHeader = new H3("Debit Notes");
        detailLayout.add(debitNotesHeader);

        Grid<SupplierBill> debitNotesGrid = new Grid<>();
        debitNotesGrid.setHeight("120px");
        debitNotesGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

        debitNotesGrid
            .addColumn(dn -> dn.getBillDate().format(DATE_FORMATTER))
            .setHeader("Date")
            .setAutoWidth(true);

        debitNotesGrid.addColumn(SupplierBill::getBillNumber).setHeader("Number").setFlexGrow(1);

        debitNotesGrid
            .addColumn(dn -> "$" + dn.getTotal().setScale(2).toPlainString())
            .setHeader("Amount")
            .setAutoWidth(true);

        debitNotesGrid
            .addComponentColumn(this::createStatusBadge)
            .setHeader("Status")
            .setAutoWidth(true);

        debitNotesGrid.addItemClickListener(
            event -> {
              grid.select(event.getItem());
              showBillDetail(event.getItem());
            });

        debitNotesGrid.setItems(debitNotes);
        detailLayout.add(debitNotesGrid);
      }
    }

    // Notes
    if (bill.getNotes() != null && !bill.getNotes().isBlank()) {
      detailLayout.add(new H3("Notes"));
      Span notesSpan = new Span(bill.getNotes());
      notesSpan
          .getStyle()
          .set("white-space", "pre-wrap")
          .set("color", "var(--lumo-secondary-text-color)");
      detailLayout.add(notesSpan);
    }

    // Attachments section
    Company company = companyContextService.getCurrentCompany();
    User user = companyContextService.getCurrentUser();
    AttachmentSection attachmentSection =
        new AttachmentSection(
            attachmentService,
            AttachmentLink.EntityType.BILL,
            bill.getId(),
            company,
            user);
    detailLayout.add(attachmentSection);
  }

  private void addReadOnlyField(FormLayout form, String label, String value) {
    TextField field = new TextField(label);
    field.setValue(value != null ? value : "");
    field.setReadOnly(true);
    form.add(field);
  }

  private void openNewBillDialog() {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("New Bill");
    dialog.setWidth("600px");

    FormLayout form = new FormLayout();
    form.setResponsiveSteps(
        new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("400px", 2));

    Company company = companyContextService.getCurrentCompany();

    ComboBox<Contact> supplierCombo = new ComboBox<>("Supplier");
    List<Contact> suppliers = contactService.findActiveSuppliers(company);
    supplierCombo.setItems(suppliers);
    supplierCombo.setItemLabelGenerator(c -> c.getCode() + " - " + c.getName());
    supplierCombo.setRequired(true);
    supplierCombo.setWidthFull();

    DatePicker billDatePicker = new DatePicker("Bill Date");
    billDatePicker.setValue(LocalDate.now());
    billDatePicker.setRequired(true);

    DatePicker dueDatePicker = new DatePicker("Due Date");
    dueDatePicker.setValue(LocalDate.now().plusDays(30));
    dueDatePicker.setRequired(true);

    // Auto-set due date based on supplier payment terms
    supplierCombo.addValueChangeListener(
        e -> {
          Contact supplier = e.getValue();
          if (supplier != null && supplier.getPaymentTerms() != null) {
            // Parse simple payment terms like "Net 30"
            String terms = supplier.getPaymentTerms().toLowerCase();
            if (terms.contains("30")) {
              dueDatePicker.setValue(billDatePicker.getValue().plusDays(30));
            } else if (terms.contains("14")) {
              dueDatePicker.setValue(billDatePicker.getValue().plusDays(14));
            } else if (terms.contains("7")) {
              dueDatePicker.setValue(billDatePicker.getValue().plusDays(7));
            } else if (terms.contains("receipt") || terms.contains("due")) {
              dueDatePicker.setValue(billDatePicker.getValue());
            }
          }
        });

    TextField supplierRefField = new TextField("Supplier Invoice #");
    supplierRefField.setMaxLength(100);

    TextArea notesArea = new TextArea("Notes");
    notesArea.setMaxLength(500);
    notesArea.setWidthFull();

    form.add(supplierCombo, 2);
    form.add(billDatePicker, dueDatePicker);
    form.add(supplierRefField, 2);
    form.add(notesArea, 2);

    Button createButton = new Button("Create Bill");
    createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    createButton.addClickListener(
        e -> {
          if (supplierCombo.isEmpty() || billDatePicker.isEmpty() || dueDatePicker.isEmpty()) {
            Notification.show(
                    "Please fill in all required fields", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }

          try {
            SupplierBill bill =
                billService.createBill(
                    company,
                    supplierCombo.getValue(),
                    billDatePicker.getValue(),
                    dueDatePicker.getValue(),
                    companyContextService.getCurrentUser());

            if (supplierRefField.getValue() != null && !supplierRefField.getValue().isBlank()) {
              bill.setSupplierReference(supplierRefField.getValue().trim());
            }
            if (notesArea.getValue() != null && !notesArea.getValue().isBlank()) {
              bill.setNotes(notesArea.getValue().trim());
            }
            billService.save(bill);

            Notification.show(
                    "Bill " + bill.getBillNumber() + " created",
                    3000,
                    Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            dialog.close();
            loadBills();
            grid.select(bill);

          } catch (Exception ex) {
            Notification.show("Error: " + ex.getMessage(), 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
          }
        });

    Button cancelButton = new Button("Cancel", e -> dialog.close());

    dialog.add(form);
    dialog.getFooter().add(cancelButton, createButton);
    dialog.open();
  }

  private void openEditBillDialog(SupplierBill bill) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Edit Bill #" + bill.getBillNumber());
    dialog.setWidth("600px");

    FormLayout form = new FormLayout();
    form.setResponsiveSteps(
        new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("400px", 2));

    Company company = companyContextService.getCurrentCompany();

    ComboBox<Contact> supplierCombo = new ComboBox<>("Supplier");
    List<Contact> suppliers = contactService.findActiveSuppliers(company);
    supplierCombo.setItems(suppliers);
    supplierCombo.setItemLabelGenerator(c -> c.getCode() + " - " + c.getName());
    supplierCombo.setValue(bill.getContact());
    supplierCombo.setRequired(true);
    supplierCombo.setWidthFull();

    DatePicker billDatePicker = new DatePicker("Bill Date");
    billDatePicker.setValue(bill.getBillDate());
    billDatePicker.setRequired(true);

    DatePicker dueDatePicker = new DatePicker("Due Date");
    dueDatePicker.setValue(bill.getDueDate());
    dueDatePicker.setRequired(true);

    TextField supplierRefField = new TextField("Supplier Invoice #");
    supplierRefField.setMaxLength(100);
    if (bill.getSupplierReference() != null) {
      supplierRefField.setValue(bill.getSupplierReference());
    }

    TextArea notesArea = new TextArea("Notes");
    notesArea.setMaxLength(500);
    notesArea.setWidthFull();
    if (bill.getNotes() != null) {
      notesArea.setValue(bill.getNotes());
    }

    form.add(supplierCombo, 2);
    form.add(billDatePicker, dueDatePicker);
    form.add(supplierRefField, 2);
    form.add(notesArea, 2);

    Button saveButton = new Button("Save");
    saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    saveButton.addClickListener(
        e -> {
          if (supplierCombo.isEmpty() || billDatePicker.isEmpty() || dueDatePicker.isEmpty()) {
            Notification.show(
                    "Please fill in all required fields", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }

          bill.setContact(supplierCombo.getValue());
          bill.setBillDate(billDatePicker.getValue());
          bill.setDueDate(dueDatePicker.getValue());
          bill.setSupplierReference(
              supplierRefField.getValue() != null && !supplierRefField.getValue().isBlank()
                  ? supplierRefField.getValue().trim()
                  : null);
          bill.setNotes(
              notesArea.getValue() != null && !notesArea.getValue().isBlank()
                  ? notesArea.getValue().trim()
                  : null);

          billService.save(bill);

          Notification.show("Bill updated", 2000, Notification.Position.BOTTOM_START)
              .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

          dialog.close();
          loadBills();
          showBillDetail(bill);
        });

    Button cancelButton = new Button("Cancel", e -> dialog.close());

    dialog.add(form);
    dialog.getFooter().add(cancelButton, saveButton);
    dialog.open();
  }

  private void openAddLineDialog(SupplierBill bill) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Add Bill Line");
    dialog.setWidth("700px");

    FormLayout form = new FormLayout();
    form.setResponsiveSteps(
        new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("400px", 2));

    Company company = companyContextService.getCurrentCompany();

    // Product selector (optional)
    ComboBox<Product> productCombo = new ComboBox<>("Product (optional)");
    List<Product> products = productService.findActiveByCompany(company);
    productCombo.setItems(products);
    productCombo.setItemLabelGenerator(p -> p.getCode() + " - " + p.getName());
    productCombo.setClearButtonVisible(true);
    productCombo.setWidthFull();

    TextField descriptionField = new TextField("Description");
    descriptionField.setMaxLength(500);
    descriptionField.setRequired(true);
    descriptionField.setWidthFull();

    BigDecimalField quantityField = new BigDecimalField("Quantity");
    quantityField.setValue(BigDecimal.ONE);
    quantityField.setRequired(true);

    BigDecimalField priceField = new BigDecimalField("Unit Price (incl. tax)");
    priceField.setValue(BigDecimal.ZERO);
    priceField.setRequired(true);

    ComboBox<Account> accountCombo = new ComboBox<>("Expense Account");
    int securityLevel = companyContextService.getCurrentSecurityLevel();
    List<Account> expenseAccounts =
        accountService.findByTypeWithSecurityLevel(
            company.getId(), Account.AccountType.EXPENSE, securityLevel);
    accountCombo.setItems(expenseAccounts);
    accountCombo.setItemLabelGenerator(a -> a.getCode() + " - " + a.getName());
    accountCombo.setRequired(true);

    ComboBox<TaxCode> taxCodeCombo = new ComboBox<>("Tax Code");
    List<TaxCode> taxCodes = taxCodeService.findActiveByCompany(company);
    taxCodeCombo.setItems(taxCodes);
    taxCodeCombo.setItemLabelGenerator(tc -> tc.getCode() + " - " + tc.getName());
    taxCodeCombo.setClearButtonVisible(true);

    // Pre-populate tax code from contact's tax override (Spec 06: Tax defaults by contact)
    Contact supplier = bill.getContact();
    if (supplier != null
        && supplier.getTaxOverrideCode() != null
        && !supplier.getTaxOverrideCode().isBlank()) {
      taxCodes.stream()
          .filter(tc -> tc.getCode().equals(supplier.getTaxOverrideCode()))
          .findFirst()
          .ifPresent(
              tc -> {
                taxCodeCombo.setValue(tc);
                taxCodeCombo.setHelperText(
                    "Default from " + supplier.getName() + "'s tax override");
              });
    }

    // Sticky note display area (Spec 08: sticky note appears when product selected)
    Div stickyNoteDisplay = new Div();
    stickyNoteDisplay.getStyle().set("background-color", "var(--lumo-primary-color-10pct)");
    stickyNoteDisplay.getStyle().set("border-left", "4px solid var(--lumo-primary-color)");
    stickyNoteDisplay.getStyle().set("padding", "var(--lumo-space-s) var(--lumo-space-m)");
    stickyNoteDisplay.getStyle().set("margin", "var(--lumo-space-s) 0");
    stickyNoteDisplay.getStyle().set("border-radius", "var(--lumo-border-radius-m)");
    stickyNoteDisplay.setVisible(false);

    // Auto-fill from product (overrides contact default if product has tax code)
    productCombo.addValueChangeListener(
        e -> {
          Product product = e.getValue();
          if (product != null) {
            descriptionField.setValue(product.getName());
            if (product.getBuyPrice() != null) {
              priceField.setValue(product.getBuyPrice());
            }
            if (product.getPurchaseAccount() != null) {
              accountCombo.setValue(product.getPurchaseAccount());
            }
            if (product.getTaxCode() != null) {
              taxCodes.stream()
                  .filter(tc -> tc.getCode().equals(product.getTaxCode()))
                  .findFirst()
                  .ifPresent(taxCodeCombo::setValue);
            }
            // Display sticky note if present (Spec 08 acceptance criteria)
            if (product.getStickyNote() != null && !product.getStickyNote().isBlank()) {
              stickyNoteDisplay.removeAll();
              Span icon = new Span("📝 ");
              Span noteText = new Span(product.getStickyNote());
              stickyNoteDisplay.add(icon, noteText);
              stickyNoteDisplay.setVisible(true);
            } else {
              stickyNoteDisplay.setVisible(false);
            }
          } else {
            stickyNoteDisplay.setVisible(false);
          }
        });

    form.add(productCombo, 2);
    form.add(stickyNoteDisplay, 2);
    form.add(descriptionField, 2);
    form.add(quantityField, priceField);
    form.add(accountCombo, taxCodeCombo);

    Button addButton = new Button("Add Line");
    addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    addButton.addClickListener(
        e -> {
          if (descriptionField.isEmpty()
              || quantityField.isEmpty()
              || priceField.isEmpty()
              || accountCombo.isEmpty()) {
            Notification.show(
                    "Please fill in all required fields", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }

          try {
            String taxCode =
                taxCodeCombo.getValue() != null ? taxCodeCombo.getValue().getCode() : null;

            billService.addLine(
                bill,
                accountCombo.getValue(),
                descriptionField.getValue().trim(),
                quantityField.getValue(),
                priceField.getValue(),
                taxCode);

            Notification.show("Line added", 2000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            dialog.close();
            loadBills();
            showBillDetail(billService.findById(bill.getId()).orElse(bill));

          } catch (Exception ex) {
            Notification.show("Error: " + ex.getMessage(), 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
          }
        });

    Button cancelButton = new Button("Cancel", e -> dialog.close());

    dialog.add(form);
    dialog.getFooter().add(cancelButton, addButton);
    dialog.open();
  }

  private void postBill(SupplierBill bill) {
    if (bill.getLines().isEmpty()) {
      Notification.show("Cannot post bill with no lines", 3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
      return;
    }

    ConfirmDialog confirm = new ConfirmDialog();
    confirm.setHeader("Post Bill?");
    confirm.setText(
        "Post bill #"
            + bill.getBillNumber()
            + " for $"
            + bill.getTotal().toPlainString()
            + "? This will post the bill to the ledger.");
    confirm.setCancelable(true);
    confirm.setConfirmText("Post");
    confirm.setConfirmButtonTheme("primary success");

    confirm.addConfirmListener(
        e -> {
          try {
            SupplierBill posted = billService.postBill(bill, null);

            Notification.show(
                    "Bill " + posted.getBillNumber() + " posted successfully",
                    3000,
                    Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            loadBills();
            showBillDetail(posted);

          } catch (Exception ex) {
            Notification.show(
                    "Error posting bill: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
          }
        });

    confirm.open();
  }

  private void confirmVoidBill(SupplierBill bill) {
    ConfirmDialog confirm = new ConfirmDialog();
    confirm.setHeader("Void Bill?");
    confirm.setText(
        "Void bill #"
            + bill.getBillNumber()
            + "? "
            + "This will reverse the ledger entries. This action cannot be undone.");
    confirm.setCancelable(true);
    confirm.setConfirmText("Void Bill");
    confirm.setConfirmButtonTheme("primary error");

    TextField reasonField = new TextField("Reason (optional)");
    reasonField.setWidthFull();
    confirm.add(reasonField);

    confirm.addConfirmListener(
        e -> {
          try {
            String reason = reasonField.getValue();
            SupplierBill voided =
                billService.voidBill(bill, null, reason.isBlank() ? null : reason);

            Notification.show("Bill voided", 3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            loadBills();
            showBillDetail(voided);

          } catch (Exception ex) {
            Notification.show(
                    "Error voiding bill: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
          }
        });

    confirm.open();
  }

  private void openCreateDebitNoteDialog(SupplierBill bill) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Create Debit Note");
    dialog.setWidth("500px");

    VerticalLayout content = new VerticalLayout();
    content.setPadding(false);

    Span infoText =
        new Span(
            "Create a debit note against bill #"
                + bill.getBillNumber()
                + " for $"
                + bill.getTotal().toPlainString()
                + " (Balance: $"
                + bill.getBalance().toPlainString()
                + ")");
    infoText.getStyle().set("margin-bottom", "var(--lumo-space-m)");
    content.add(infoText);

    ComboBox<String> typeCombo = new ComboBox<>("Debit Type");
    typeCombo.setItems("Full Debit (all lines)", "Partial Debit (empty draft)");
    typeCombo.setValue("Full Debit (all lines)");
    typeCombo.setRequired(true);
    typeCombo.setWidthFull();
    content.add(typeCombo);

    Span helpText = new Span();
    helpText
        .getStyle()
        .set("font-style", "italic")
        .set("color", "var(--lumo-secondary-text-color)");
    typeCombo.addValueChangeListener(
        e -> {
          if ("Full Debit (all lines)".equals(e.getValue())) {
            helpText.setText("Copies all lines from the original bill.");
          } else {
            helpText.setText("Creates an empty debit note for you to add lines manually.");
          }
        });
    helpText.setText("Copies all lines from the original bill.");
    content.add(helpText);

    dialog.add(content);

    Button createButton = new Button("Create Debit Note");
    createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    createButton.addClickListener(
        e -> {
          try {
            boolean fullDebit = "Full Debit (all lines)".equals(typeCombo.getValue());
            SupplierBill debitNote =
                billService.createDebitNote(
                    bill, companyContextService.getCurrentUser(), fullDebit);

            Notification.show(
                    "Debit note " + debitNote.getBillNumber() + " created",
                    3000,
                    Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            dialog.close();
            loadBills();
            grid.select(debitNote);

          } catch (Exception ex) {
            Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
          }
        });

    Button cancelButton = new Button("Cancel", e -> dialog.close());
    dialog.getFooter().add(cancelButton, createButton);
    dialog.open();
  }

  private void postDebitNote(SupplierBill debitNote) {
    if (debitNote.getLines().isEmpty()) {
      Notification.show("Cannot post debit note with no lines", 3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
      return;
    }

    ConfirmDialog confirm = new ConfirmDialog();
    confirm.setHeader("Post Debit Note?");
    confirm.setText(
        "Post debit note #"
            + debitNote.getBillNumber()
            + " for $"
            + debitNote.getTotal().toPlainString()
            + "? This will reduce the balance on bill #"
            + debitNote.getOriginalBill().getBillNumber()
            + ".");
    confirm.setCancelable(true);
    confirm.setConfirmText("Post");
    confirm.setConfirmButtonTheme("primary success");

    confirm.addConfirmListener(
        e -> {
          try {
            SupplierBill posted =
                billService.postDebitNote(debitNote, companyContextService.getCurrentUser());

            Notification.show(
                    "Debit note " + posted.getBillNumber() + " posted successfully",
                    3000,
                    Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            loadBills();
            showBillDetail(posted);

          } catch (Exception ex) {
            Notification.show(
                    "Error posting debit note: " + ex.getMessage(),
                    5000,
                    Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
          }
        });

    confirm.open();
  }

  private void confirmVoidDebitNote(SupplierBill debitNote) {
    ConfirmDialog confirm = new ConfirmDialog();
    confirm.setHeader("Void Debit Note?");
    confirm.setText(
        "Void debit note #"
            + debitNote.getBillNumber()
            + "? This will reverse the ledger entries and restore the balance on bill #"
            + debitNote.getOriginalBill().getBillNumber()
            + ". This action cannot be undone.");
    confirm.setCancelable(true);
    confirm.setConfirmText("Void Debit Note");
    confirm.setConfirmButtonTheme("primary error");

    TextField reasonField = new TextField("Reason (optional)");
    reasonField.setWidthFull();
    confirm.add(reasonField);

    confirm.addConfirmListener(
        e -> {
          try {
            String reason = reasonField.getValue();
            SupplierBill voided =
                billService.voidDebitNote(
                    debitNote,
                    companyContextService.getCurrentUser(),
                    reason.isBlank() ? null : reason);

            Notification.show("Debit note voided", 3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            loadBills();
            showBillDetail(voided);

          } catch (Exception ex) {
            Notification.show(
                    "Error voiding debit note: " + ex.getMessage(),
                    5000,
                    Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
          }
        });

    confirm.open();
  }

  private void exportBillPdf(SupplierBill bill) {
    try {
      Company company = companyContextService.getCurrentCompany();
      PdfSettings pdfSettings = companyService.getPdfSettings(company);

      byte[] pdfContent = billPdfService.generateBillPdf(bill, pdfSettings);

      String filename =
          bill.isDebitNote()
              ? "DebitNote_" + bill.getBillNumber() + ".pdf"
              : "Bill_" + bill.getBillNumber() + ".pdf";

      StreamResource resource =
          new StreamResource(filename, () -> new ByteArrayInputStream(pdfContent));
      resource.setContentType("application/pdf");

      Anchor downloadLink = new Anchor(resource, "");
      downloadLink.getElement().setAttribute("download", true);
      downloadLink.getElement().getStyle().set("display", "none");
      add(downloadLink);
      downloadLink.getElement().executeJs("this.click()");
      downloadLink.getElement().executeJs("setTimeout(() => this.remove(), 100)");

    } catch (Exception e) {
      Notification.show(
              "Error generating PDF: " + e.getMessage(), 5000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
  }
}
