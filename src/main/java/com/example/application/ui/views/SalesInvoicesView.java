package com.example.application.ui.views;

import com.example.application.domain.*;
import com.example.application.domain.Contact.ContactType;
import com.example.application.domain.SalesInvoice.InvoiceStatus;
import com.example.application.service.*;
import com.example.application.ui.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
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
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * View for managing Sales Invoices (Accounts Receivable).
 * Features a master-detail layout with:
 * - Left: Invoice list with status filters
 * - Right: Invoice detail with line items and actions
 */
@Route(value = "invoices", layout = MainLayout.class)
@PageTitle("Sales Invoices | MoniWorks")
@PermitAll
public class SalesInvoicesView extends VerticalLayout {

    private final SalesInvoiceService invoiceService;
    private final ContactService contactService;
    private final AccountService accountService;
    private final ProductService productService;
    private final TaxCodeService taxCodeService;
    private final CompanyContextService companyContextService;

    private final Grid<SalesInvoice> grid = new Grid<>();
    private final TextField searchField = new TextField();
    private final ComboBox<String> statusFilter = new ComboBox<>();

    private final VerticalLayout detailLayout = new VerticalLayout();
    private SalesInvoice selectedInvoice;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public SalesInvoicesView(SalesInvoiceService invoiceService,
                             ContactService contactService,
                             AccountService accountService,
                             ProductService productService,
                             TaxCodeService taxCodeService,
                             CompanyContextService companyContextService) {
        this.invoiceService = invoiceService;
        this.contactService = contactService;
        this.accountService = accountService;
        this.productService = productService;
        this.taxCodeService = taxCodeService;
        this.companyContextService = companyContextService;

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

    private void configureGrid() {
        grid.addClassName("invoices-grid");
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        grid.addColumn(SalesInvoice::getInvoiceNumber)
            .setHeader("Invoice #")
            .setSortable(true)
            .setAutoWidth(true)
            .setFlexGrow(0);

        grid.addColumn(i -> i.getContact().getName())
            .setHeader("Customer")
            .setSortable(true)
            .setFlexGrow(1);

        grid.addColumn(i -> i.getIssueDate().format(DATE_FORMATTER))
            .setHeader("Date")
            .setSortable(true)
            .setAutoWidth(true);

        grid.addColumn(i -> i.getDueDate().format(DATE_FORMATTER))
            .setHeader("Due")
            .setSortable(true)
            .setAutoWidth(true);

        grid.addColumn(i -> "$" + i.getTotal().toPlainString())
            .setHeader("Total")
            .setSortable(true)
            .setAutoWidth(true);

        grid.addColumn(i -> "$" + i.getBalance().toPlainString())
            .setHeader("Balance")
            .setSortable(true)
            .setAutoWidth(true);

        grid.addComponentColumn(this::createStatusBadge)
            .setHeader("Status")
            .setAutoWidth(true);

        grid.asSingleSelect().addValueChangeListener(e -> {
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

        Button addButton = new Button("New Invoice", VaadinIcon.PLUS.create());
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addButton.addClickListener(e -> openNewInvoiceDialog());

        Button refreshButton = new Button(VaadinIcon.REFRESH.create());
        refreshButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        refreshButton.addClickListener(e -> loadInvoices());
        refreshButton.getElement().setAttribute("title", "Refresh");

        HorizontalLayout filters = new HorizontalLayout(searchField, statusFilter, addButton, refreshButton);
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
            invoices = invoices.stream()
                .filter(inv -> matchesStatusFilter(inv, statusValue))
                .toList();
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

        H3 invoiceLabel = new H3("Invoice #" + invoice.getInvoiceNumber());
        header.add(invoiceLabel, createStatusBadge(invoice));

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

        if (invoice.isIssued() && !invoice.isPaid()) {
            Button voidButton = new Button("Void", VaadinIcon.BAN.create());
            voidButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            voidButton.addClickListener(e -> confirmVoidInvoice(invoice));
            actions.add(voidButton);
        }

        HorizontalLayout spacer = new HorizontalLayout();
        spacer.setWidthFull();
        header.add(spacer, actions);
        header.expand(spacer);

        detailLayout.add(header);

        // Invoice info section
        FormLayout infoForm = new FormLayout();
        infoForm.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 2),
            new FormLayout.ResponsiveStep("500px", 4)
        );

        addReadOnlyField(infoForm, "Customer", invoice.getContact().getName());
        addReadOnlyField(infoForm, "Issue Date", invoice.getIssueDate().format(DATE_FORMATTER));
        addReadOnlyField(infoForm, "Due Date", invoice.getDueDate().format(DATE_FORMATTER));
        addReadOnlyField(infoForm, "Reference", invoice.getReference());

        detailLayout.add(infoForm);

        // Line items grid
        H3 linesHeader = new H3("Line Items");
        detailLayout.add(linesHeader);

        Grid<SalesInvoiceLine> linesGrid = new Grid<>();
        linesGrid.setHeight("200px");
        linesGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

        linesGrid.addColumn(SalesInvoiceLine::getDescription)
            .setHeader("Description")
            .setFlexGrow(2);

        linesGrid.addColumn(line -> line.getAccount().getCode())
            .setHeader("Account")
            .setAutoWidth(true);

        linesGrid.addColumn(line -> line.getQuantity().toPlainString())
            .setHeader("Qty")
            .setAutoWidth(true);

        linesGrid.addColumn(line -> "$" + line.getUnitPrice().toPlainString())
            .setHeader("Price")
            .setAutoWidth(true);

        linesGrid.addColumn(line -> line.getTaxCode() != null ? line.getTaxCode() : "-")
            .setHeader("Tax")
            .setAutoWidth(true);

        linesGrid.addColumn(line -> "$" + line.getLineTotal().toPlainString())
            .setHeader("Net")
            .setAutoWidth(true);

        linesGrid.addColumn(line -> "$" + line.getGrossTotal().toPlainString())
            .setHeader("Total")
            .setAutoWidth(true);

        // Delete button column for draft invoices
        if (invoice.isDraft()) {
            linesGrid.addComponentColumn(line -> {
                Button deleteBtn = new Button(VaadinIcon.TRASH.create());
                deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
                deleteBtn.addClickListener(e -> {
                    invoiceService.removeLine(invoice, line);
                    showInvoiceDetail(invoiceService.findById(invoice.getId()).orElse(invoice));
                    loadInvoices();
                });
                return deleteBtn;
            }).setHeader("").setAutoWidth(true);
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

        // Notes
        if (invoice.getNotes() != null && !invoice.getNotes().isBlank()) {
            detailLayout.add(new H3("Notes"));
            Span notesSpan = new Span(invoice.getNotes());
            notesSpan.getStyle()
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
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 2)
        );

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
        customerCombo.addValueChangeListener(e -> {
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
        createButton.addClickListener(e -> {
            if (customerCombo.isEmpty() || issueDatePicker.isEmpty() || dueDatePicker.isEmpty()) {
                Notification.show("Please fill in all required fields", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                SalesInvoice invoice = invoiceService.createInvoice(
                    company,
                    customerCombo.getValue(),
                    issueDatePicker.getValue(),
                    dueDatePicker.getValue(),
                    null // TODO: Get current user
                );

                if (referenceField.getValue() != null && !referenceField.getValue().isBlank()) {
                    invoice.setReference(referenceField.getValue().trim());
                }
                if (notesArea.getValue() != null && !notesArea.getValue().isBlank()) {
                    invoice.setNotes(notesArea.getValue().trim());
                }
                invoiceService.save(invoice);

                Notification.show("Invoice " + invoice.getInvoiceNumber() + " created", 3000,
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
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 2)
        );

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
        saveButton.addClickListener(e -> {
            if (customerCombo.isEmpty() || issueDatePicker.isEmpty() || dueDatePicker.isEmpty()) {
                Notification.show("Please fill in all required fields", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            invoice.setContact(customerCombo.getValue());
            invoice.setIssueDate(issueDatePicker.getValue());
            invoice.setDueDate(dueDatePicker.getValue());
            invoice.setReference(referenceField.getValue() != null && !referenceField.getValue().isBlank()
                ? referenceField.getValue().trim() : null);
            invoice.setNotes(notesArea.getValue() != null && !notesArea.getValue().isBlank()
                ? notesArea.getValue().trim() : null);

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
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 2)
        );

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
        List<Account> incomeAccounts = accountService.findByType(company.getId(), Account.AccountType.INCOME);
        accountCombo.setItems(incomeAccounts);
        accountCombo.setItemLabelGenerator(a -> a.getCode() + " - " + a.getName());
        accountCombo.setRequired(true);

        ComboBox<TaxCode> taxCodeCombo = new ComboBox<>("Tax Code");
        List<TaxCode> taxCodes = taxCodeService.findActiveByCompany(company);
        taxCodeCombo.setItems(taxCodes);
        taxCodeCombo.setItemLabelGenerator(tc -> tc.getCode() + " - " + tc.getName());
        taxCodeCombo.setClearButtonVisible(true);

        // Auto-fill from product
        productCombo.addValueChangeListener(e -> {
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
        addButton.addClickListener(e -> {
            if (descriptionField.isEmpty() || quantityField.isEmpty() ||
                priceField.isEmpty() || accountCombo.isEmpty()) {
                Notification.show("Please fill in all required fields", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                String taxCode = taxCodeCombo.getValue() != null ?
                    taxCodeCombo.getValue().getCode() : null;

                invoiceService.addLine(
                    invoice,
                    accountCombo.getValue(),
                    descriptionField.getValue().trim(),
                    quantityField.getValue(),
                    priceField.getValue(),
                    taxCode
                );

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
            Notification.show("Cannot issue invoice with no lines", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        ConfirmDialog confirm = new ConfirmDialog();
        confirm.setHeader("Issue Invoice?");
        confirm.setText("Issue invoice #" + invoice.getInvoiceNumber() + " for $" +
            invoice.getTotal().toPlainString() + "? This will post the invoice to the ledger.");
        confirm.setCancelable(true);
        confirm.setConfirmText("Issue");
        confirm.setConfirmButtonTheme("primary success");

        confirm.addConfirmListener(e -> {
            try {
                SalesInvoice issued = invoiceService.issueInvoice(invoice, null);

                Notification.show("Invoice " + issued.getInvoiceNumber() + " issued successfully",
                        3000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                loadInvoices();
                showInvoiceDetail(issued);

            } catch (Exception ex) {
                Notification.show("Error issuing invoice: " + ex.getMessage(),
                        5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        confirm.open();
    }

    private void confirmVoidInvoice(SalesInvoice invoice) {
        ConfirmDialog confirm = new ConfirmDialog();
        confirm.setHeader("Void Invoice?");
        confirm.setText("Void invoice #" + invoice.getInvoiceNumber() + "? " +
            "This will reverse the ledger entries. This action cannot be undone.");
        confirm.setCancelable(true);
        confirm.setConfirmText("Void Invoice");
        confirm.setConfirmButtonTheme("primary error");

        TextField reasonField = new TextField("Reason (optional)");
        reasonField.setWidthFull();
        confirm.add(reasonField);

        confirm.addConfirmListener(e -> {
            try {
                String reason = reasonField.getValue();
                SalesInvoice voided = invoiceService.voidInvoice(invoice, null,
                    reason.isBlank() ? null : reason);

                Notification.show("Invoice voided", 3000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                loadInvoices();
                showInvoiceDetail(voided);

            } catch (Exception ex) {
                Notification.show("Error voiding invoice: " + ex.getMessage(),
                        5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        confirm.open();
    }
}
