package com.example.application.ui.views;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.example.application.domain.*;
import com.example.application.domain.SavedView.EntityType;
import com.example.application.service.*;
import com.example.application.ui.MainLayout;
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
 * View for managing Sales Invoices (Accounts Receivable). Features a master-detail layout with: -
 * Left: Invoice list with status filters - Right: Invoice detail with line items and actions
 *
 * <p>Supports query parameter ?new=true for keyboard shortcut navigation to directly open new
 * invoice dialog.
 */
@Route(value = "invoices", layout = MainLayout.class)
@PageTitle("Sales Invoices | MoniWorks")
@PermitAll
public class SalesInvoicesView extends VerticalLayout implements BeforeEnterObserver {

  private final SalesInvoiceService invoiceService;
  private final ContactService contactService;
  private final AccountService accountService;
  private final ProductService productService;
  private final TaxCodeService taxCodeService;
  private final CompanyContextService companyContextService;
  private final CompanyService companyService;
  private final InvoicePdfService invoicePdfService;
  private final EmailService emailService;
  private final ReceivableAllocationService receivableAllocationService;
  private final SavedViewService savedViewService;

  private final Grid<SalesInvoice> grid = new Grid<>();
  private final TextField searchField = new TextField();
  private final ComboBox<String> statusFilter = new ComboBox<>();
  private GridCustomizer<SalesInvoice> gridCustomizer;

  private final VerticalLayout detailLayout = new VerticalLayout();
  private SalesInvoice selectedInvoice;

  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("dd MMM yyyy");

  public SalesInvoicesView(
      SalesInvoiceService invoiceService,
      ContactService contactService,
      AccountService accountService,
      ProductService productService,
      TaxCodeService taxCodeService,
      CompanyContextService companyContextService,
      CompanyService companyService,
      InvoicePdfService invoicePdfService,
      EmailService emailService,
      ReceivableAllocationService receivableAllocationService,
      SavedViewService savedViewService) {
    this.invoiceService = invoiceService;
    this.contactService = contactService;
    this.accountService = accountService;
    this.productService = productService;
    this.taxCodeService = taxCodeService;
    this.companyContextService = companyContextService;
    this.companyService = companyService;
    this.invoicePdfService = invoicePdfService;
    this.emailService = emailService;
    this.receivableAllocationService = receivableAllocationService;
    this.savedViewService = savedViewService;

    addClassName("invoices-view");
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

    loadInvoices();
  }

  @Override
  public void beforeEnter(BeforeEnterEvent event) {
    QueryParameters queryParams = event.getLocation().getQueryParameters();

    // Check if we should open a new invoice dialog from keyboard shortcut (Alt+I)
    List<String> newParam = queryParams.getParameters().getOrDefault("new", List.of());

    if (!newParam.isEmpty() && "true".equals(newParam.get(0))) {
      // Schedule the dialog to open after the view is fully attached
      getUI().ifPresent(ui -> ui.accessLater(() -> openNewInvoiceDialog(), () -> {}));
    }
  }

  private void configureGrid() {
    grid.addClassName("invoices-grid");
    grid.setSizeFull();
    grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

    grid.addColumn(SalesInvoice::getInvoiceNumber)
        .setHeader("Invoice #")
        .setKey("invoiceNumber")
        .setSortable(true)
        .setResizable(true)
        .setAutoWidth(true)
        .setFlexGrow(0);

    grid.addColumn(i -> i.getContact().getName())
        .setHeader("Customer")
        .setKey("customer")
        .setSortable(true)
        .setResizable(true)
        .setFlexGrow(1);

    grid.addColumn(i -> i.getIssueDate().format(DATE_FORMATTER))
        .setHeader("Date")
        .setKey("issueDate")
        .setSortable(true)
        .setResizable(true)
        .setAutoWidth(true);

    grid.addColumn(i -> i.getDueDate().format(DATE_FORMATTER))
        .setHeader("Due")
        .setKey("dueDate")
        .setSortable(true)
        .setResizable(true)
        .setAutoWidth(true);

    grid.addColumn(i -> "$" + i.getTotal().toPlainString())
        .setHeader("Total")
        .setKey("total")
        .setSortable(true)
        .setResizable(true)
        .setAutoWidth(true);

    grid.addColumn(i -> "$" + i.getBalance().toPlainString())
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
              selectedInvoice = e.getValue();
              if (selectedInvoice != null) {
                showInvoiceDetail(selectedInvoice);
              } else {
                showNoSelection();
              }
            });
  }

  private Span createStatusBadge(SalesInvoice invoice) {
    Span badge = new Span(invoice.getStatus().name());
    badge.getElement().getThemeList().add("badge");

    switch (invoice.getStatus()) {
      case DRAFT -> badge.getElement().getThemeList().add("contrast");
      case ISSUED -> {
        if (invoice.isPaid()) {
          badge.setText("PAID");
          badge.getElement().getThemeList().add("success");
        } else if (invoice.isOverdue()) {
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

  private HorizontalLayout createToolbar() {
    H2 title = new H2("Sales Invoices");

    searchField.setPlaceholder("Search invoices...");
    searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
    searchField.setClearButtonVisible(true);
    searchField.addValueChangeListener(e -> filterInvoices());
    searchField.setWidth("200px");

    statusFilter.setPlaceholder("All Statuses");
    statusFilter.setItems("All", "Draft", "Issued", "Overdue", "Paid", "Void");
    statusFilter.setValue("All");
    statusFilter.addValueChangeListener(e -> filterInvoices());
    statusFilter.setWidth("130px");

    // Grid customizer for column visibility and saved views
    Company company = companyContextService.getCurrentCompany();
    User user = companyContextService.getCurrentUser();
    if (company != null && user != null) {
      gridCustomizer =
          new GridCustomizer<>(grid, EntityType.SALES_INVOICE, savedViewService, company, user);
    }

    Button addButton = new Button("New Invoice", VaadinIcon.PLUS.create());
    addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    addButton.addClickListener(e -> openNewInvoiceDialog());

    Button refreshButton = new Button(VaadinIcon.REFRESH.create());
    refreshButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    refreshButton.addClickListener(e -> loadInvoices());
    refreshButton.getElement().setAttribute("title", "Refresh");

    HorizontalLayout filters = new HorizontalLayout(searchField, statusFilter);
    if (gridCustomizer != null) {
      filters.add(gridCustomizer);
    }
    filters.add(addButton, refreshButton);
    filters.setAlignItems(FlexComponent.Alignment.BASELINE);

    HorizontalLayout toolbar = new HorizontalLayout(title, filters);
    toolbar.setWidthFull();
    toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
    toolbar.setAlignItems(FlexComponent.Alignment.CENTER);
    toolbar.setPadding(true);

    return toolbar;
  }

  private void loadInvoices() {
    Company company = companyContextService.getCurrentCompany();
    List<SalesInvoice> invoices = invoiceService.findByCompany(company);
    grid.setItems(invoices);
  }

  private void filterInvoices() {
    Company company = companyContextService.getCurrentCompany();
    String search = searchField.getValue();
    String statusValue = statusFilter.getValue();

    List<SalesInvoice> invoices;
    if (search != null && !search.isBlank()) {
      invoices = invoiceService.searchByCompany(company, search);
    } else {
      invoices = invoiceService.findByCompany(company);
    }

    // Filter by status
    if (statusValue != null && !"All".equals(statusValue)) {
      invoices = invoices.stream().filter(inv -> matchesStatusFilter(inv, statusValue)).toList();
    }

    grid.setItems(invoices);
  }

  private boolean matchesStatusFilter(SalesInvoice invoice, String filter) {
    return switch (filter) {
      case "Draft" -> invoice.isDraft();
      case "Issued" -> invoice.isIssued() && !invoice.isPaid() && !invoice.isOverdue();
      case "Overdue" -> invoice.isIssued() && invoice.isOverdue();
      case "Paid" -> invoice.isIssued() && invoice.isPaid();
      case "Void" -> invoice.isVoid();
      default -> true;
    };
  }

  private void showNoSelection() {
    detailLayout.removeAll();
    Span message = new Span("Select an invoice to view details");
    message.getStyle().set("color", "var(--lumo-secondary-text-color)");
    detailLayout.add(message);
    detailLayout.setAlignItems(FlexComponent.Alignment.CENTER);
    detailLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
  }

  private void showInvoiceDetail(SalesInvoice invoice) {
    detailLayout.removeAll();
    detailLayout.setAlignItems(FlexComponent.Alignment.STRETCH);
    detailLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.START);

    // Header with invoice number and status
    HorizontalLayout header = new HorizontalLayout();
    header.setWidthFull();
    header.setAlignItems(FlexComponent.Alignment.CENTER);

    String headerText =
        invoice.isCreditNote()
            ? "Credit Note #" + invoice.getInvoiceNumber()
            : "Invoice #" + invoice.getInvoiceNumber();
    H3 invoiceLabel = new H3(headerText);

    // Show type badge for credit notes
    if (invoice.isCreditNote()) {
      Span typeBadge = new Span("CREDIT NOTE");
      typeBadge.getElement().getThemeList().add("badge contrast");
      header.add(invoiceLabel, typeBadge, createStatusBadge(invoice));
    } else {
      header.add(invoiceLabel, createStatusBadge(invoice));
    }

    // Action buttons
    HorizontalLayout actions = new HorizontalLayout();
    actions.setSpacing(true);

    if (invoice.isDraft()) {
      Button editButton = new Button("Edit", VaadinIcon.EDIT.create());
      editButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
      editButton.addClickListener(e -> openEditInvoiceDialog(invoice));

      Button addLineButton = new Button("Add Line", VaadinIcon.PLUS.create());
      addLineButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
      addLineButton.addClickListener(e -> openAddLineDialog(invoice));

      Button issueButton = new Button("Issue Invoice", VaadinIcon.CHECK.create());
      issueButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
      issueButton.addClickListener(e -> issueInvoice(invoice));

      actions.add(editButton, addLineButton, issueButton);
    }

    if (invoice.isIssued() && !invoice.isPaid() && invoice.isInvoice()) {
      // Credit Note button (only for invoices, not credit notes)
      Button creditNoteButton = new Button("Credit Note", VaadinIcon.FILE_REMOVE.create());
      creditNoteButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
      creditNoteButton.addClickListener(e -> openCreateCreditNoteDialog(invoice));
      actions.add(creditNoteButton);

      // Void button for regular invoices
      Button voidButton = new Button("Void", VaadinIcon.BAN.create());
      voidButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
      voidButton.addClickListener(e -> confirmVoidInvoice(invoice));
      actions.add(voidButton);
    }

    // Void button for issued credit notes
    if (invoice.isIssued() && invoice.isCreditNote()) {
      Button voidCreditNoteButton = new Button("Void", VaadinIcon.BAN.create());
      voidCreditNoteButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
      voidCreditNoteButton.addClickListener(e -> confirmVoidCreditNote(invoice));
      actions.add(voidCreditNoteButton);
    }

    // PDF export and email buttons for issued invoices
    if (invoice.isIssued()) {
      // PDF download button
      Button pdfButton = new Button("Export PDF", VaadinIcon.DOWNLOAD.create());
      pdfButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
      pdfButton.addClickListener(
          e -> {
            try {
              Company company = companyContextService.getCurrentCompany();
              PdfSettings pdfSettings = companyService.getPdfSettings(company);
              byte[] pdfContent = invoicePdfService.generateInvoicePdf(invoice, pdfSettings);
              String filename = "Invoice_" + invoice.getInvoiceNumber() + ".pdf";

              StreamResource resource =
                  new StreamResource(filename, () -> new ByteArrayInputStream(pdfContent));
              resource.setContentType("application/pdf");
              resource.setCacheTime(0);

              Anchor downloadLink = new Anchor(resource, "");
              downloadLink.getElement().setAttribute("download", true);
              downloadLink.getElement().getStyle().set("display", "none");
              add(downloadLink);
              downloadLink.getElement().executeJs("this.click()");
              downloadLink.getElement().executeJs("setTimeout(() => this.remove(), 100)");

              Notification.show(
                      "PDF generated successfully", 3000, Notification.Position.BOTTOM_START)
                  .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
              Notification.show(
                      "Failed to generate PDF: " + ex.getMessage(),
                      3000,
                      Notification.Position.MIDDLE)
                  .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
          });
      actions.add(pdfButton);

      // Email button
      if (invoice.getContact().getEmail() != null && !invoice.getContact().getEmail().isBlank()) {
        Button emailButton = new Button("Email", VaadinIcon.ENVELOPE.create());
        emailButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        emailButton.addClickListener(e -> emailInvoice(invoice));
        actions.add(emailButton);
      }
    }

    HorizontalLayout spacer = new HorizontalLayout();
    spacer.setWidthFull();
    header.add(spacer, actions);
    header.expand(spacer);

    detailLayout.add(header);

    // Invoice info section
    FormLayout infoForm = new FormLayout();
    infoForm.setResponsiveSteps(
        new FormLayout.ResponsiveStep("0", 2), new FormLayout.ResponsiveStep("500px", 4));

    addReadOnlyField(infoForm, "Customer", invoice.getContact().getName());
    addReadOnlyField(infoForm, "Issue Date", invoice.getIssueDate().format(DATE_FORMATTER));
    addReadOnlyField(infoForm, "Due Date", invoice.getDueDate().format(DATE_FORMATTER));
    addReadOnlyField(infoForm, "Reference", invoice.getReference());

    // Show link to original invoice for credit notes
    if (invoice.isCreditNote() && invoice.getOriginalInvoice() != null) {
      addReadOnlyField(
          infoForm, "Credits Invoice", "#" + invoice.getOriginalInvoice().getInvoiceNumber());
    }

    // Show credit notes issued against this invoice
    if (invoice.isInvoice() && invoice.isIssued()) {
      List<SalesInvoice> creditNotes = invoiceService.findCreditNotesForInvoice(invoice);
      if (!creditNotes.isEmpty()) {
        String creditNotesText =
            creditNotes.stream()
                .map(SalesInvoice::getInvoiceNumber)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        addReadOnlyField(infoForm, "Credit Notes", creditNotesText);
      }
    }

    detailLayout.add(infoForm);

    // Line items grid
    H3 linesHeader = new H3("Line Items");
    detailLayout.add(linesHeader);

    Grid<SalesInvoiceLine> linesGrid = new Grid<>();
    linesGrid.setHeight("200px");
    linesGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

    linesGrid.addColumn(SalesInvoiceLine::getDescription).setHeader("Description").setFlexGrow(2);

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

    // Delete button column for draft invoices
    if (invoice.isDraft()) {
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
                      invoiceService.removeLine(invoice, line);
                      showInvoiceDetail(invoiceService.findById(invoice.getId()).orElse(invoice));
                      loadInvoices();
                    });
                return deleteBtn;
              })
          .setHeader("")
          .setAutoWidth(true);
    }

    linesGrid.setItems(invoice.getLines());
    detailLayout.add(linesGrid);

    // Totals section
    FormLayout totalsForm = new FormLayout();
    totalsForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));

    addReadOnlyField(totalsForm, "Subtotal", "$" + invoice.getSubtotal().toPlainString());
    addReadOnlyField(totalsForm, "Tax", "$" + invoice.getTaxTotal().toPlainString());
    addReadOnlyField(totalsForm, "Total", "$" + invoice.getTotal().toPlainString());
    addReadOnlyField(totalsForm, "Paid", "$" + invoice.getAmountPaid().toPlainString());
    addReadOnlyField(totalsForm, "Balance", "$" + invoice.getBalance().toPlainString());

    detailLayout.add(new H3("Totals"), totalsForm);

    // Allocations section (for issued invoices with payments)
    if (invoice.isIssued() && invoice.getAmountPaid().compareTo(BigDecimal.ZERO) > 0) {
      List<ReceivableAllocation> allocations = receivableAllocationService.findByInvoice(invoice);
      if (!allocations.isEmpty()) {
        H3 allocationsHeader = new H3("Payment Allocations");
        detailLayout.add(allocationsHeader);

        Grid<ReceivableAllocation> allocGrid = new Grid<>();
        allocGrid.setHeight("120px");
        allocGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

        allocGrid
            .addColumn(a -> a.getReceiptTransaction().getTransactionDate().format(DATE_FORMATTER))
            .setHeader("Date")
            .setAutoWidth(true);

        allocGrid
            .addColumn(
                a ->
                    a.getReceiptTransaction().getReference() != null
                        ? a.getReceiptTransaction().getReference()
                        : a.getReceiptTransaction().getDescription())
            .setHeader("Receipt")
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

    // Notes
    if (invoice.getNotes() != null && !invoice.getNotes().isBlank()) {
      detailLayout.add(new H3("Notes"));
      Span notesSpan = new Span(invoice.getNotes());
      notesSpan
          .getStyle()
          .set("white-space", "pre-wrap")
          .set("color", "var(--lumo-secondary-text-color)");
      detailLayout.add(notesSpan);
    }
  }

  private void addReadOnlyField(FormLayout form, String label, String value) {
    TextField field = new TextField(label);
    field.setValue(value != null ? value : "");
    field.setReadOnly(true);
    form.add(field);
  }

  private void openNewInvoiceDialog() {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("New Invoice");
    dialog.setWidth("600px");

    FormLayout form = new FormLayout();
    form.setResponsiveSteps(
        new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("400px", 2));

    Company company = companyContextService.getCurrentCompany();

    ComboBox<Contact> customerCombo = new ComboBox<>("Customer");
    List<Contact> customers = contactService.findActiveCustomers(company);
    customerCombo.setItems(customers);
    customerCombo.setItemLabelGenerator(c -> c.getCode() + " - " + c.getName());
    customerCombo.setRequired(true);
    customerCombo.setWidthFull();

    DatePicker issueDatePicker = new DatePicker("Issue Date");
    issueDatePicker.setValue(LocalDate.now());
    issueDatePicker.setRequired(true);

    DatePicker dueDatePicker = new DatePicker("Due Date");
    dueDatePicker.setValue(LocalDate.now().plusDays(30));
    dueDatePicker.setRequired(true);

    // Auto-set due date based on customer payment terms
    customerCombo.addValueChangeListener(
        e -> {
          Contact customer = e.getValue();
          if (customer != null && customer.getPaymentTerms() != null) {
            // Parse simple payment terms like "Net 30"
            String terms = customer.getPaymentTerms().toLowerCase();
            if (terms.contains("30")) {
              dueDatePicker.setValue(issueDatePicker.getValue().plusDays(30));
            } else if (terms.contains("14")) {
              dueDatePicker.setValue(issueDatePicker.getValue().plusDays(14));
            } else if (terms.contains("7")) {
              dueDatePicker.setValue(issueDatePicker.getValue().plusDays(7));
            } else if (terms.contains("receipt") || terms.contains("due")) {
              dueDatePicker.setValue(issueDatePicker.getValue());
            }
          }
        });

    TextField referenceField = new TextField("Reference");
    referenceField.setMaxLength(255);

    TextArea notesArea = new TextArea("Notes");
    notesArea.setMaxLength(500);
    notesArea.setWidthFull();

    form.add(customerCombo, 2);
    form.add(issueDatePicker, dueDatePicker);
    form.add(referenceField, 2);
    form.add(notesArea, 2);

    Button createButton = new Button("Create Invoice");
    createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    createButton.addClickListener(
        e -> {
          if (customerCombo.isEmpty() || issueDatePicker.isEmpty() || dueDatePicker.isEmpty()) {
            Notification.show(
                    "Please fill in all required fields", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }

          try {
            SalesInvoice invoice =
                invoiceService.createInvoice(
                    company,
                    customerCombo.getValue(),
                    issueDatePicker.getValue(),
                    dueDatePicker.getValue(),
                    companyContextService.getCurrentUser());

            if (referenceField.getValue() != null && !referenceField.getValue().isBlank()) {
              invoice.setReference(referenceField.getValue().trim());
            }
            if (notesArea.getValue() != null && !notesArea.getValue().isBlank()) {
              invoice.setNotes(notesArea.getValue().trim());
            }
            invoiceService.save(invoice);

            Notification.show(
                    "Invoice " + invoice.getInvoiceNumber() + " created",
                    3000,
                    Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            dialog.close();
            loadInvoices();
            grid.select(invoice);

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

  private void openEditInvoiceDialog(SalesInvoice invoice) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Edit Invoice #" + invoice.getInvoiceNumber());
    dialog.setWidth("600px");

    FormLayout form = new FormLayout();
    form.setResponsiveSteps(
        new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("400px", 2));

    Company company = companyContextService.getCurrentCompany();

    ComboBox<Contact> customerCombo = new ComboBox<>("Customer");
    List<Contact> customers = contactService.findActiveCustomers(company);
    customerCombo.setItems(customers);
    customerCombo.setItemLabelGenerator(c -> c.getCode() + " - " + c.getName());
    customerCombo.setValue(invoice.getContact());
    customerCombo.setRequired(true);
    customerCombo.setWidthFull();

    DatePicker issueDatePicker = new DatePicker("Issue Date");
    issueDatePicker.setValue(invoice.getIssueDate());
    issueDatePicker.setRequired(true);

    DatePicker dueDatePicker = new DatePicker("Due Date");
    dueDatePicker.setValue(invoice.getDueDate());
    dueDatePicker.setRequired(true);

    TextField referenceField = new TextField("Reference");
    referenceField.setMaxLength(255);
    if (invoice.getReference() != null) {
      referenceField.setValue(invoice.getReference());
    }

    TextArea notesArea = new TextArea("Notes");
    notesArea.setMaxLength(500);
    notesArea.setWidthFull();
    if (invoice.getNotes() != null) {
      notesArea.setValue(invoice.getNotes());
    }

    form.add(customerCombo, 2);
    form.add(issueDatePicker, dueDatePicker);
    form.add(referenceField, 2);
    form.add(notesArea, 2);

    Button saveButton = new Button("Save");
    saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    saveButton.addClickListener(
        e -> {
          if (customerCombo.isEmpty() || issueDatePicker.isEmpty() || dueDatePicker.isEmpty()) {
            Notification.show(
                    "Please fill in all required fields", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }

          invoice.setContact(customerCombo.getValue());
          invoice.setIssueDate(issueDatePicker.getValue());
          invoice.setDueDate(dueDatePicker.getValue());
          invoice.setReference(
              referenceField.getValue() != null && !referenceField.getValue().isBlank()
                  ? referenceField.getValue().trim()
                  : null);
          invoice.setNotes(
              notesArea.getValue() != null && !notesArea.getValue().isBlank()
                  ? notesArea.getValue().trim()
                  : null);

          invoiceService.save(invoice);

          Notification.show("Invoice updated", 2000, Notification.Position.BOTTOM_START)
              .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

          dialog.close();
          loadInvoices();
          showInvoiceDetail(invoice);
        });

    Button cancelButton = new Button("Cancel", e -> dialog.close());

    dialog.add(form);
    dialog.getFooter().add(cancelButton, saveButton);
    dialog.open();
  }

  private void openAddLineDialog(SalesInvoice invoice) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Add Invoice Line");
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

    ComboBox<Account> accountCombo = new ComboBox<>("Income Account");
    int securityLevel = companyContextService.getCurrentSecurityLevel();
    List<Account> incomeAccounts =
        accountService.findByTypeWithSecurityLevel(
            company.getId(), Account.AccountType.INCOME, securityLevel);
    accountCombo.setItems(incomeAccounts);
    accountCombo.setItemLabelGenerator(a -> a.getCode() + " - " + a.getName());
    accountCombo.setRequired(true);

    ComboBox<TaxCode> taxCodeCombo = new ComboBox<>("Tax Code");
    List<TaxCode> taxCodes = taxCodeService.findActiveByCompany(company);
    taxCodeCombo.setItems(taxCodes);
    taxCodeCombo.setItemLabelGenerator(tc -> tc.getCode() + " - " + tc.getName());
    taxCodeCombo.setClearButtonVisible(true);

    // Pre-populate tax code from contact's tax override (Spec 06: Tax defaults by contact)
    Contact customer = invoice.getContact();
    if (customer != null
        && customer.getTaxOverrideCode() != null
        && !customer.getTaxOverrideCode().isBlank()) {
      taxCodes.stream()
          .filter(tc -> tc.getCode().equals(customer.getTaxOverrideCode()))
          .findFirst()
          .ifPresent(
              tc -> {
                taxCodeCombo.setValue(tc);
                taxCodeCombo.setHelperText(
                    "Default from " + customer.getName() + "'s tax override");
              });
    }

    // Auto-fill from product (overrides contact default if product has tax code)
    productCombo.addValueChangeListener(
        e -> {
          Product product = e.getValue();
          if (product != null) {
            descriptionField.setValue(product.getName());
            if (product.getSellPrice() != null) {
              priceField.setValue(product.getSellPrice());
            }
            if (product.getSalesAccount() != null) {
              accountCombo.setValue(product.getSalesAccount());
            }
            if (product.getTaxCode() != null) {
              taxCodes.stream()
                  .filter(tc -> tc.getCode().equals(product.getTaxCode()))
                  .findFirst()
                  .ifPresent(taxCodeCombo::setValue);
            }
          }
        });

    form.add(productCombo, 2);
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

            invoiceService.addLine(
                invoice,
                accountCombo.getValue(),
                descriptionField.getValue().trim(),
                quantityField.getValue(),
                priceField.getValue(),
                taxCode);

            Notification.show("Line added", 2000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            dialog.close();
            loadInvoices();
            showInvoiceDetail(invoiceService.findById(invoice.getId()).orElse(invoice));

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

  private void issueInvoice(SalesInvoice invoice) {
    if (invoice.getLines().isEmpty()) {
      String type = invoice.isCreditNote() ? "credit note" : "invoice";
      Notification.show(
              "Cannot issue " + type + " with no lines", 3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
      return;
    }

    ConfirmDialog confirm = new ConfirmDialog();
    boolean isCreditNote = invoice.isCreditNote();
    String typeLabel = isCreditNote ? "Credit Note" : "Invoice";

    confirm.setHeader("Issue " + typeLabel + "?");
    confirm.setText(
        "Issue "
            + typeLabel.toLowerCase()
            + " #"
            + invoice.getInvoiceNumber()
            + " for $"
            + invoice.getTotal().toPlainString()
            + "? This will post it to the ledger.");
    confirm.setCancelable(true);
    confirm.setConfirmText("Issue");
    confirm.setConfirmButtonTheme("primary success");

    confirm.addConfirmListener(
        e -> {
          try {
            SalesInvoice issued;
            if (isCreditNote) {
              issued =
                  invoiceService.issueCreditNote(invoice, companyContextService.getCurrentUser());
            } else {
              issued = invoiceService.issueInvoice(invoice, companyContextService.getCurrentUser());
            }

            Notification.show(
                    typeLabel + " " + issued.getInvoiceNumber() + " issued successfully",
                    3000,
                    Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            loadInvoices();
            showInvoiceDetail(issued);

          } catch (Exception ex) {
            Notification.show(
                    "Error issuing " + typeLabel.toLowerCase() + ": " + ex.getMessage(),
                    5000,
                    Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
          }
        });

    confirm.open();
  }

  private void confirmVoidInvoice(SalesInvoice invoice) {
    ConfirmDialog confirm = new ConfirmDialog();
    confirm.setHeader("Void Invoice?");
    confirm.setText(
        "Void invoice #"
            + invoice.getInvoiceNumber()
            + "? "
            + "This will reverse the ledger entries. This action cannot be undone.");
    confirm.setCancelable(true);
    confirm.setConfirmText("Void Invoice");
    confirm.setConfirmButtonTheme("primary error");

    TextField reasonField = new TextField("Reason (optional)");
    reasonField.setWidthFull();
    confirm.add(reasonField);

    confirm.addConfirmListener(
        e -> {
          try {
            String reason = reasonField.getValue();
            User currentUser = companyContextService.getCurrentUser();
            SalesInvoice voided =
                invoiceService.voidInvoice(invoice, currentUser, reason.isBlank() ? null : reason);

            Notification.show("Invoice voided", 3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            loadInvoices();
            showInvoiceDetail(voided);

          } catch (Exception ex) {
            Notification.show(
                    "Error voiding invoice: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
          }
        });

    confirm.open();
  }

  private void confirmVoidCreditNote(SalesInvoice creditNote) {
    ConfirmDialog confirm = new ConfirmDialog();
    confirm.setHeader("Void Credit Note?");
    confirm.setText(
        "Void credit note #"
            + creditNote.getInvoiceNumber()
            + "? "
            + "This will reverse the ledger entries and restore the original invoice balance. "
            + "This action cannot be undone.");
    confirm.setCancelable(true);
    confirm.setConfirmText("Void Credit Note");
    confirm.setConfirmButtonTheme("primary error");

    TextField reasonField = new TextField("Reason (optional)");
    reasonField.setWidthFull();
    confirm.add(reasonField);

    confirm.addConfirmListener(
        e -> {
          try {
            String reason = reasonField.getValue();
            User currentUser = companyContextService.getCurrentUser();
            SalesInvoice voided =
                invoiceService.voidCreditNote(
                    creditNote, currentUser, reason.isBlank() ? null : reason);

            Notification.show("Credit note voided", 3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            loadInvoices();
            showInvoiceDetail(voided);

          } catch (Exception ex) {
            Notification.show(
                    "Error voiding credit note: " + ex.getMessage(),
                    5000,
                    Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
          }
        });

    confirm.open();
  }

  private void emailInvoice(SalesInvoice invoice) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Email Invoice");
    dialog.setWidth("500px");

    FormLayout form = new FormLayout();
    form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

    TextField toField = new TextField("To");
    toField.setValue(invoice.getContact().getEmail());
    toField.setWidthFull();

    TextField subjectField = new TextField("Subject");
    subjectField.setValue(
        "Invoice " + invoice.getInvoiceNumber() + " from " + invoice.getCompany().getName());
    subjectField.setWidthFull();

    TextArea messageArea = new TextArea("Message");
    messageArea.setValue(
        "Please find attached invoice "
            + invoice.getInvoiceNumber()
            + " for "
            + formatCurrency(invoice.getTotal())
            + ".\n\n"
            + "Payment is due by "
            + invoice.getDueDate().format(DATE_FORMATTER)
            + ".\n\n"
            + "Thank you for your business.");
    messageArea.setWidthFull();
    messageArea.setHeight("150px");

    form.add(toField, subjectField, messageArea);

    Button sendButton = new Button("Send", VaadinIcon.ENVELOPE.create());
    sendButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    sendButton.addClickListener(
        e -> {
          try {
            Company company = companyContextService.getCurrentCompany();
            PdfSettings pdfSettings = companyService.getPdfSettings(company);
            byte[] pdfContent = invoicePdfService.generateInvoicePdf(invoice, pdfSettings);
            User currentUser = companyContextService.getCurrentUser();

            EmailService.EmailResult result =
                emailService.sendInvoice(invoice, pdfContent, currentUser);

            if (result.success()) {
              Notification.show(
                      "Invoice emailed successfully", 3000, Notification.Position.BOTTOM_START)
                  .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
              dialog.close();
            } else {
              Notification.show(
                      "Email queued: " + result.message(), 3000, Notification.Position.BOTTOM_START)
                  .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
              dialog.close();
            }
          } catch (Exception ex) {
            Notification.show(
                    "Failed to send email: " + ex.getMessage(), 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
          }
        });

    Button cancelButton = new Button("Cancel", e -> dialog.close());

    dialog.add(form);
    dialog.getFooter().add(cancelButton, sendButton);
    dialog.open();
  }

  private String formatCurrency(java.math.BigDecimal amount) {
    if (amount == null) return "$0.00";
    return String.format("$%,.2f", amount);
  }

  private void openCreateCreditNoteDialog(SalesInvoice invoice) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Create Credit Note");
    dialog.setWidth("600px");

    FormLayout form = new FormLayout();
    form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

    // Display original invoice info
    TextField invoiceField = new TextField("Original Invoice");
    invoiceField.setValue(
        "#" + invoice.getInvoiceNumber() + " - " + formatCurrency(invoice.getTotal()));
    invoiceField.setReadOnly(true);
    invoiceField.setWidthFull();

    TextField balanceField = new TextField("Outstanding Balance");
    balanceField.setValue(formatCurrency(invoice.getBalance()));
    balanceField.setReadOnly(true);
    balanceField.setWidthFull();

    // Credit note options
    ComboBox<String> creditTypeCombo = new ComboBox<>("Credit Type");
    creditTypeCombo.setItems("Full Credit", "Partial Credit");
    creditTypeCombo.setValue("Full Credit");
    creditTypeCombo.setRequired(true);
    creditTypeCombo.setWidthFull();
    creditTypeCombo.setHelperText(
        "Full credit copies all lines. Partial credit creates an empty draft for manual line entry.");

    form.add(invoiceField, balanceField, creditTypeCombo);

    Button createButton = new Button("Create Credit Note");
    createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    createButton.addClickListener(
        e -> {
          try {
            boolean fullCredit = "Full Credit".equals(creditTypeCombo.getValue());
            User currentUser = companyContextService.getCurrentUser();

            SalesInvoice creditNote =
                invoiceService.createCreditNote(invoice, currentUser, fullCredit);

            Notification.show(
                    "Credit Note " + creditNote.getInvoiceNumber() + " created",
                    3000,
                    Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            dialog.close();
            loadInvoices();
            grid.select(creditNote);

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
}
