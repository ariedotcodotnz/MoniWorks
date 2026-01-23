package com.example.application.ui.views;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.example.application.domain.*;
import com.example.application.domain.AttachmentLink.EntityType;
import com.example.application.domain.SavedView;
import com.example.application.domain.Transaction.TransactionType;
import com.example.application.domain.TransactionLine.Direction;
import com.example.application.service.*;
import com.example.application.ui.MainLayout;
import com.example.application.ui.components.GridCustomizer;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
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
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;

import jakarta.annotation.security.PermitAll;

/**
 * View for managing transactions (Payments, Receipts, Journals, Transfers). Supports creating,
 * viewing, posting, and reversing transactions.
 *
 * <p>Supports query parameters for keyboard shortcut navigation:
 *
 * <ul>
 *   <li>?type=PAYMENT&new=true - Opens new payment dialog
 *   <li>?type=RECEIPT&new=true - Opens new receipt dialog
 *   <li>?type=JOURNAL&new=true - Opens new journal dialog
 *   <li>?type=TRANSFER&new=true - Opens new transfer dialog
 * </ul>
 */
@Route(value = "transactions", layout = MainLayout.class)
@PageTitle("Transactions | MoniWorks")
@PermitAll
public class TransactionsView extends VerticalLayout implements BeforeEnterObserver {

  private final TransactionService transactionService;
  private final PostingService postingService;
  private final AccountService accountService;
  private final TaxCodeService taxCodeService;
  private final CompanyContextService companyContextService;
  private final AttachmentService attachmentService;
  private final SavedViewService savedViewService;
  private final ReceivableAllocationService receivableAllocationService;
  private final SalesInvoiceService salesInvoiceService;
  private final PayableAllocationService payableAllocationService;
  private final SupplierBillService supplierBillService;
  private final BankImportService bankImportService;
  private final TransactionImportService transactionImportService;
  private final ContactService contactService;

  private final Grid<Transaction> grid = new Grid<>();
  private final ComboBox<TransactionType> typeFilter = new ComboBox<>();
  private final ComboBox<Transaction.Status> statusFilter = new ComboBox<>();
  private GridCustomizer<Transaction> gridCustomizer;

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

  public TransactionsView(
      TransactionService transactionService,
      PostingService postingService,
      AccountService accountService,
      TaxCodeService taxCodeService,
      CompanyContextService companyContextService,
      AttachmentService attachmentService,
      SavedViewService savedViewService,
      ReceivableAllocationService receivableAllocationService,
      SalesInvoiceService salesInvoiceService,
      PayableAllocationService payableAllocationService,
      SupplierBillService supplierBillService,
      BankImportService bankImportService,
      TransactionImportService transactionImportService,
      ContactService contactService) {
    this.transactionService = transactionService;
    this.postingService = postingService;
    this.accountService = accountService;
    this.taxCodeService = taxCodeService;
    this.companyContextService = companyContextService;
    this.attachmentService = attachmentService;
    this.savedViewService = savedViewService;
    this.receivableAllocationService = receivableAllocationService;
    this.salesInvoiceService = salesInvoiceService;
    this.payableAllocationService = payableAllocationService;
    this.supplierBillService = supplierBillService;
    this.bankImportService = bankImportService;
    this.transactionImportService = transactionImportService;
    this.contactService = contactService;

    addClassName("transactions-view");
    setSizeFull();

    configureGrid();
    add(createToolbar(), grid);

    loadTransactions();
  }

  @Override
  public void beforeEnter(BeforeEnterEvent event) {
    QueryParameters queryParams = event.getLocation().getQueryParameters();

    // Check if we should open a new transaction dialog from keyboard shortcut
    List<String> newParam = queryParams.getParameters().getOrDefault("new", List.of());
    List<String> typeParam = queryParams.getParameters().getOrDefault("type", List.of());

    if (!newParam.isEmpty() && "true".equals(newParam.get(0)) && !typeParam.isEmpty()) {
      // Schedule the dialog to open after the view is fully attached
      String typeStr = typeParam.get(0);
      getUI()
          .ifPresent(
              ui ->
                  ui.accessLater(
                      () -> {
                        try {
                          TransactionType type = TransactionType.valueOf(typeStr.toUpperCase());
                          openNewTransactionDialog(type);
                        } catch (IllegalArgumentException e) {
                          Notification.show("Invalid transaction type: " + typeStr);
                        }
                      },
                      () -> {}));
    }
  }

  private void configureGrid() {
    grid.addClassNames("transactions-grid");
    grid.setSizeFull();
    grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

    grid.addColumn(t -> t.getTransactionDate().format(DATE_FORMAT))
        .setKey("date")
        .setHeader("Date")
        .setSortable(true)
        .setAutoWidth(true)
        .setResizable(true);

    grid.addColumn(t -> t.getType().name())
        .setKey("type")
        .setHeader("Type")
        .setSortable(true)
        .setAutoWidth(true)
        .setResizable(true);

    grid.addColumn(Transaction::getReference)
        .setKey("reference")
        .setHeader("Reference")
        .setAutoWidth(true)
        .setResizable(true);

    grid.addColumn(Transaction::getDescription)
        .setKey("description")
        .setHeader("Description")
        .setFlexGrow(1)
        .setResizable(true);

    grid.addColumn(this::calculateTotal)
        .setKey("amount")
        .setHeader("Amount")
        .setAutoWidth(true)
        .setResizable(true);

    grid.addColumn(t -> t.getStatus().name())
        .setKey("status")
        .setHeader("Status")
        .setSortable(true)
        .setAutoWidth(true)
        .setResizable(true);

    grid.addComponentColumn(this::createActionButtons)
        .setKey("actions")
        .setHeader("Actions")
        .setAutoWidth(true)
        .setFlexGrow(0);
  }

  private String calculateTotal(Transaction transaction) {
    BigDecimal total = BigDecimal.ZERO;
    for (TransactionLine line : transaction.getLines()) {
      if (line.isDebit()) {
        total = total.add(line.getAmount());
      }
    }
    return "$" + total.setScale(2).toPlainString();
  }

  private HorizontalLayout createActionButtons(Transaction transaction) {
    HorizontalLayout actions = new HorizontalLayout();
    actions.setSpacing(false);
    actions.setPadding(false);

    Button viewBtn = new Button(VaadinIcon.EYE.create());
    viewBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
    viewBtn.addClickListener(e -> openViewDialog(transaction));
    viewBtn.getElement().setAttribute("title", "View details");
    actions.add(viewBtn);

    if (transaction.isDraft()) {
      Button editBtn = new Button(VaadinIcon.EDIT.create());
      editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
      editBtn.addClickListener(e -> openEditDialog(transaction));
      editBtn.getElement().setAttribute("title", "Edit");
      actions.add(editBtn);

      Button postBtn = new Button(VaadinIcon.CHECK.create());
      postBtn.addThemeVariants(
          ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS);
      postBtn.addClickListener(e -> postTransaction(transaction));
      postBtn.getElement().setAttribute("title", "Post");
      actions.add(postBtn);

      Button deleteBtn = new Button(VaadinIcon.TRASH.create());
      deleteBtn.addThemeVariants(
          ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
      deleteBtn.addClickListener(e -> deleteTransaction(transaction));
      deleteBtn.getElement().setAttribute("title", "Delete");
      actions.add(deleteBtn);
    } else {
      // For posted receipts, add allocate button (allocate to invoices)
      if (transaction.getType() == TransactionType.RECEIPT) {
        Button allocateBtn = new Button(VaadinIcon.CONNECT.create());
        allocateBtn.addThemeVariants(
            ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS);
        allocateBtn.addClickListener(e -> openReceiptAllocationDialog(transaction));
        allocateBtn.getElement().setAttribute("title", "Allocate to Invoices");
        actions.add(allocateBtn);
      }

      // For posted payments, add allocate button (allocate to bills)
      if (transaction.getType() == TransactionType.PAYMENT) {
        Button allocateBtn = new Button(VaadinIcon.CONNECT.create());
        allocateBtn.addThemeVariants(
            ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
        allocateBtn.addClickListener(e -> openPaymentAllocationDialog(transaction));
        allocateBtn.getElement().setAttribute("title", "Allocate to Bills");
        actions.add(allocateBtn);
      }

      Button reverseBtn = new Button(VaadinIcon.ROTATE_LEFT.create());
      reverseBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
      reverseBtn.addClickListener(e -> openReverseDialog(transaction));
      reverseBtn.getElement().setAttribute("title", "Reverse");
      actions.add(reverseBtn);
    }

    return actions;
  }

  private HorizontalLayout createToolbar() {
    H2 title = new H2("Transactions");

    // Filters
    typeFilter.setPlaceholder("All Types");
    typeFilter.setItems(TransactionType.values());
    typeFilter.setClearButtonVisible(true);
    typeFilter.addValueChangeListener(e -> loadTransactions());
    typeFilter.setWidth("150px");

    statusFilter.setPlaceholder("All Status");
    statusFilter.setItems(Transaction.Status.values());
    statusFilter.setClearButtonVisible(true);
    statusFilter.addValueChangeListener(e -> loadTransactions());
    statusFilter.setWidth("150px");

    // Grid customizer for column visibility and saved views
    Company company = companyContextService.getCurrentCompany();
    User user = companyContextService.getCurrentUser();
    if (company != null && user != null) {
      gridCustomizer =
          new GridCustomizer<>(
              grid, SavedView.EntityType.TRANSACTION, savedViewService, company, user);
    }

    Button paymentBtn = new Button("Payment", VaadinIcon.MINUS.create());
    paymentBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    paymentBtn.addClickListener(e -> openNewTransactionDialog(TransactionType.PAYMENT));

    Button receiptBtn = new Button("Receipt", VaadinIcon.PLUS.create());
    receiptBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
    receiptBtn.addClickListener(e -> openNewTransactionDialog(TransactionType.RECEIPT));

    Button journalBtn = new Button("Journal", VaadinIcon.BOOK.create());
    journalBtn.addClickListener(e -> openNewTransactionDialog(TransactionType.JOURNAL));

    Button transferBtn = new Button("Transfer", VaadinIcon.EXCHANGE.create());
    transferBtn.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
    transferBtn.addClickListener(e -> openNewTransactionDialog(TransactionType.TRANSFER));

    Button importBtn = new Button("Import CSV", VaadinIcon.UPLOAD.create());
    importBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    importBtn.addClickListener(e -> openImportDialog());

    Button refreshBtn = new Button(VaadinIcon.REFRESH.create());
    refreshBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    refreshBtn.addClickListener(e -> loadTransactions());
    refreshBtn.getElement().setAttribute("title", "Refresh");

    HorizontalLayout filters = new HorizontalLayout(typeFilter, statusFilter);
    if (gridCustomizer != null) {
      filters.add(gridCustomizer);
    }
    filters.setAlignItems(FlexComponent.Alignment.BASELINE);

    HorizontalLayout buttons =
        new HorizontalLayout(
            paymentBtn, receiptBtn, journalBtn, transferBtn, importBtn, refreshBtn);
    buttons.setAlignItems(FlexComponent.Alignment.BASELINE);

    HorizontalLayout left = new HorizontalLayout(title, filters);
    left.setAlignItems(FlexComponent.Alignment.CENTER);

    HorizontalLayout toolbar = new HorizontalLayout(left, buttons);
    toolbar.setWidthFull();
    toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
    toolbar.setAlignItems(FlexComponent.Alignment.CENTER);

    return toolbar;
  }

  private void loadTransactions() {
    Company company = companyContextService.getCurrentCompany();
    List<Transaction> transactions = transactionService.findByCompany(company);

    // Apply filters
    if (typeFilter.getValue() != null) {
      transactions =
          transactions.stream().filter(t -> t.getType() == typeFilter.getValue()).toList();
    }
    if (statusFilter.getValue() != null) {
      transactions =
          transactions.stream().filter(t -> t.getStatus() == statusFilter.getValue()).toList();
    }

    grid.setItems(transactions);
  }

  private void openNewTransactionDialog(TransactionType type) {
    Company company = companyContextService.getCurrentCompany();
    Transaction transaction = new Transaction(company, type, LocalDate.now());
    openTransactionFormDialog(transaction, true);
  }

  private void openEditDialog(Transaction transaction) {
    openTransactionFormDialog(transaction, false);
  }

  private void openTransactionFormDialog(Transaction transaction, boolean isNew) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle((isNew ? "New " : "Edit ") + transaction.getType().name());
    dialog.setWidth("800px");
    dialog.setHeight("600px");

    Company company = companyContextService.getCurrentCompany();
    int securityLevel = companyContextService.getCurrentSecurityLevel();
    List<Account> accounts =
        accountService.findActiveByCompanyWithSecurityLevel(company, securityLevel);
    List<TaxCode> taxCodes = taxCodeService.findActiveByCompany(company);

    // Header form
    FormLayout headerForm = new FormLayout();

    DatePicker datePicker = new DatePicker("Date");
    datePicker.setValue(
        transaction.getTransactionDate() != null
            ? transaction.getTransactionDate()
            : LocalDate.now());
    datePicker.setRequired(true);

    TextField descriptionField = new TextField("Description");
    descriptionField.setValue(
        transaction.getDescription() != null ? transaction.getDescription() : "");
    descriptionField.setWidthFull();

    TextField referenceField = new TextField("Reference");
    referenceField.setValue(transaction.getReference() != null ? transaction.getReference() : "");

    headerForm.add(datePicker, referenceField, descriptionField);
    headerForm.setColspan(descriptionField, 2);

    // Lines grid
    H3 linesTitle = new H3("Lines");

    List<LineEntry> lineEntries = new ArrayList<>();
    for (TransactionLine line : transaction.getLines()) {
      lineEntries.add(new LineEntry(line));
    }

    // Balance indicator (create early so we can pass to listeners)
    Span balanceSpan = new Span();

    Grid<LineEntry> linesGrid = new Grid<>();
    linesGrid.setHeight("200px");
    linesGrid.addThemeVariants(GridVariant.LUMO_COMPACT);

    linesGrid
        .addColumn(le -> le.account != null ? le.account.getCode() : "")
        .setHeader("Account")
        .setAutoWidth(true);
    linesGrid
        .addColumn(le -> le.account != null ? le.account.getName() : "")
        .setHeader("Name")
        .setFlexGrow(1);
    linesGrid
        .addColumn(le -> le.direction != null ? le.direction.name() : "")
        .setHeader("DR/CR")
        .setAutoWidth(true);
    linesGrid
        .addColumn(le -> le.amount != null ? le.amount.toPlainString() : "")
        .setHeader("Amount")
        .setAutoWidth(true);
    linesGrid.addColumn(le -> le.taxCode).setHeader("Tax").setAutoWidth(true);
    linesGrid.addColumn(le -> le.memo).setHeader("Memo").setAutoWidth(true);
    linesGrid
        .addComponentColumn(
            le -> {
              Button removeBtn = new Button(VaadinIcon.CLOSE_SMALL.create());
              removeBtn.addThemeVariants(
                  ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
              removeBtn.addClickListener(
                  e -> {
                    lineEntries.remove(le);
                    linesGrid.setItems(lineEntries);
                    updateBalance(lineEntries, balanceSpan);
                  });
              return removeBtn;
            })
        .setHeader("")
        .setAutoWidth(true)
        .setFlexGrow(0);

    linesGrid.setItems(lineEntries);

    // Add line form
    HorizontalLayout addLineForm = new HorizontalLayout();
    addLineForm.setWidthFull();
    addLineForm.setAlignItems(FlexComponent.Alignment.END);

    // Contact/Payee selector (Spec 07 - Contact default allocation prefill)
    List<Contact> contacts = contactService.findActiveByCompany(company);
    ComboBox<Contact> contactCombo = new ComboBox<>("Payee/Contact");
    contactCombo.setItems(contacts);
    contactCombo.setItemLabelGenerator(c -> c.getCode() + " - " + c.getName());
    contactCombo.setClearButtonVisible(true);
    contactCombo.setWidth("200px");

    ComboBox<Account> accountCombo = new ComboBox<>("Account");
    accountCombo.setItems(accounts);
    accountCombo.setItemLabelGenerator(a -> a.getCode() + " - " + a.getName());
    accountCombo.setWidth("250px");

    ComboBox<Direction> directionCombo = new ComboBox<>("DR/CR");
    directionCombo.setItems(Direction.values());
    directionCombo.setWidth("100px");

    BigDecimalField amountField = new BigDecimalField("Amount");
    amountField.setWidth("120px");

    ComboBox<TaxCode> taxCodeCombo = new ComboBox<>("Tax");
    taxCodeCombo.setItems(taxCodes);
    taxCodeCombo.setItemLabelGenerator(TaxCode::getCode);
    taxCodeCombo.setClearButtonVisible(true);
    taxCodeCombo.setWidth("100px");

    // Auto-populate account and tax code from contact's defaults (Spec 07)
    contactCombo.addValueChangeListener(
        event -> {
          Contact selectedContact = event.getValue();
          if (selectedContact != null) {
            // Prefill default account if set
            if (selectedContact.getDefaultAccount() != null) {
              Account defaultAcct = selectedContact.getDefaultAccount();
              accounts.stream()
                  .filter(a -> a.getId().equals(defaultAcct.getId()))
                  .findFirst()
                  .ifPresent(accountCombo::setValue);
            }
            // Prefill tax override code if set
            if (selectedContact.getTaxOverrideCode() != null
                && !selectedContact.getTaxOverrideCode().isBlank()) {
              String taxOverride = selectedContact.getTaxOverrideCode();
              taxCodes.stream()
                  .filter(tc -> tc.getCode().equals(taxOverride))
                  .findFirst()
                  .ifPresent(taxCodeCombo::setValue);
            }
          }
        });

    // Auto-populate tax code from account's default tax code (Spec 06)
    accountCombo.addValueChangeListener(
        event -> {
          Account selectedAccount = event.getValue();
          if (selectedAccount != null && selectedAccount.getTaxDefaultCode() != null) {
            // Find matching TaxCode object from the available tax codes
            String defaultTaxCode = selectedAccount.getTaxDefaultCode();
            taxCodes.stream()
                .filter(tc -> tc.getCode().equals(defaultTaxCode))
                .findFirst()
                .ifPresent(taxCodeCombo::setValue);
          }
        });

    TextField memoField = new TextField("Memo");
    memoField.setWidth("200px");

    // Allocation rule suggestion display (Spec 05, Spec 11)
    Span suggestionSpan = new Span();
    suggestionSpan.setVisible(false);
    suggestionSpan
        .getStyle()
        .set("background-color", "var(--lumo-primary-color-10pct)")
        .set("padding", "0.5rem")
        .set("border-radius", "var(--lumo-border-radius-m)")
        .set("display", "inline-flex")
        .set("align-items", "center")
        .set("gap", "0.5rem");

    Button applySuggestionBtn = new Button("Apply", VaadinIcon.CHECK.create());
    applySuggestionBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS);
    applySuggestionBtn.setVisible(false);

    // Check allocation rules when memo changes (blur event)
    memoField.addBlurListener(
        event -> {
          String memoText = memoField.getValue();
          if (memoText != null && !memoText.isBlank() && accountCombo.isEmpty()) {
            Optional<AllocationRule> matchingRule =
                bankImportService.findMatchingRule(company, memoText);
            if (matchingRule.isPresent()) {
              AllocationRule rule = matchingRule.get();
              suggestionSpan.setText(
                  "Suggested: "
                      + rule.getTargetAccount().getCode()
                      + " - "
                      + rule.getTargetAccount().getName());
              suggestionSpan.setVisible(true);
              applySuggestionBtn.setVisible(true);

              // Store rule data for apply button
              applySuggestionBtn
                  .getElement()
                  .setProperty("ruleAccountId", String.valueOf(rule.getTargetAccount().getId()));
              applySuggestionBtn
                  .getElement()
                  .setProperty(
                      "ruleTaxCode",
                      rule.getTargetTaxCode() != null ? rule.getTargetTaxCode() : "");

              applySuggestionBtn.addClickListener(
                  applyEvent -> {
                    // Apply the suggested account
                    accounts.stream()
                        .filter(a -> a.getId().equals(rule.getTargetAccount().getId()))
                        .findFirst()
                        .ifPresent(accountCombo::setValue);

                    // Apply the suggested tax code if present
                    if (rule.getTargetTaxCode() != null && !rule.getTargetTaxCode().isBlank()) {
                      taxCodes.stream()
                          .filter(tc -> tc.getCode().equals(rule.getTargetTaxCode()))
                          .findFirst()
                          .ifPresent(taxCodeCombo::setValue);
                    }

                    suggestionSpan.setVisible(false);
                    applySuggestionBtn.setVisible(false);
                  });
            } else {
              suggestionSpan.setVisible(false);
              applySuggestionBtn.setVisible(false);
            }
          }
        });

    Button addLineBtn = new Button("Add", VaadinIcon.PLUS.create());
    addLineBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    addLineBtn.addClickListener(
        e -> {
          if (accountCombo.isEmpty() || directionCombo.isEmpty() || amountField.isEmpty()) {
            Notification.show(
                    "Please fill in account, direction, and amount",
                    3000,
                    Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }

          LineEntry newLine = new LineEntry();
          newLine.account = accountCombo.getValue();
          newLine.direction = directionCombo.getValue();
          newLine.amount = amountField.getValue();
          newLine.taxCode =
              taxCodeCombo.getValue() != null ? taxCodeCombo.getValue().getCode() : null;
          newLine.memo = memoField.getValue();

          lineEntries.add(newLine);
          linesGrid.setItems(lineEntries);
          updateBalance(lineEntries, balanceSpan);

          // Clear fields for next entry
          contactCombo.clear();
          accountCombo.clear();
          directionCombo.clear();
          amountField.clear();
          taxCodeCombo.clear();
          memoField.clear();
          suggestionSpan.setVisible(false);
          applySuggestionBtn.setVisible(false);
          accountCombo.focus();
        });

    addLineForm.add(
        contactCombo,
        accountCombo,
        directionCombo,
        amountField,
        taxCodeCombo,
        memoField,
        addLineBtn);

    // Suggestion row layout
    HorizontalLayout suggestionRow = new HorizontalLayout(suggestionSpan, applySuggestionBtn);
    suggestionRow.setAlignItems(FlexComponent.Alignment.CENTER);
    suggestionRow.setSpacing(true);
    suggestionRow.setVisible(
        true); // Container always visible, contents controlled by child visibility

    updateBalance(lineEntries, balanceSpan);

    // Attachments section
    H3 attachmentsTitle = new H3("Attachments");

    // List to track pending attachments for new transactions
    List<PendingAttachment> pendingAttachments = new ArrayList<>();

    // Container for existing and pending attachments
    VerticalLayout attachmentsContainer = new VerticalLayout();
    attachmentsContainer.setPadding(false);
    attachmentsContainer.setSpacing(false);

    // Load existing attachments if editing
    if (!isNew && transaction.getId() != null) {
      List<Attachment> existingAttachments =
          attachmentService.findByEntity(EntityType.TRANSACTION, transaction.getId());
      for (Attachment att : existingAttachments) {
        attachmentsContainer.add(
            createExistingAttachmentRow(att, attachmentsContainer, transaction));
      }
    }

    // Upload component
    MemoryBuffer uploadBuffer = new MemoryBuffer();
    Upload upload = new Upload(uploadBuffer);
    upload.setAcceptedFileTypes(".pdf", ".jpg", ".jpeg", ".png", ".gif", ".webp", ".tiff", ".bmp");
    upload.setMaxFiles(5);
    upload.setMaxFileSize(10 * 1024 * 1024); // 10 MB
    upload.setDropLabel(new Span("Drop file here or click to upload"));

    upload.addSucceededListener(
        event -> {
          String fileName = event.getFileName();
          String mimeType = event.getMIMEType();
          try {
            byte[] content = uploadBuffer.getInputStream().readAllBytes();
            PendingAttachment pending = new PendingAttachment(fileName, mimeType, content);
            pendingAttachments.add(pending);

            // Add to UI
            HorizontalLayout pendingRow = new HorizontalLayout();
            pendingRow.setAlignItems(FlexComponent.Alignment.CENTER);
            pendingRow.add(
                VaadinIcon.FILE.create(),
                new Span(fileName + " (pending)"),
                createRemovePendingButton(
                    pending, pendingAttachments, pendingRow, attachmentsContainer));
            attachmentsContainer.add(pendingRow);

            Notification.show(
                    "File ready to upload: " + fileName, 2000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
          } catch (IOException ex) {
            Notification.show(
                    "Error reading file: " + ex.getMessage(), 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
          }
        });

    upload.addFailedListener(
        event -> {
          Notification.show(
                  "Upload failed: " + event.getReason().getMessage(),
                  3000,
                  Notification.Position.MIDDLE)
              .addThemeVariants(NotificationVariant.LUMO_ERROR);
        });

    // Footer buttons
    Button saveBtn = new Button("Save as Draft");
    saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    saveBtn.addClickListener(
        e -> {
          if (datePicker.isEmpty()) {
            Notification.show("Please select a date", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }
          if (lineEntries.isEmpty()) {
            Notification.show("Please add at least one line", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }

          try {
            transaction.setTransactionDate(datePicker.getValue());
            transaction.setDescription(descriptionField.getValue());
            transaction.setReference(
                referenceField.getValue().isBlank() ? null : referenceField.getValue());

            // Update lines
            transaction.getLines().clear();
            for (LineEntry le : lineEntries) {
              TransactionLine line = new TransactionLine(le.account, le.amount, le.direction);
              line.setTaxCode(le.taxCode);
              line.setMemo(le.memo);
              transaction.addLine(line);
            }

            Transaction saved = transactionService.save(transaction);

            // Save pending attachments
            savePendingAttachments(pendingAttachments, saved);

            Notification.show("Transaction saved", 3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            dialog.close();
            loadTransactions();
          } catch (Exception ex) {
            Notification.show("Error: " + ex.getMessage(), 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
          }
        });

    Button saveAndPostBtn = new Button("Save & Post");
    saveAndPostBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
    saveAndPostBtn.addClickListener(
        e -> {
          if (datePicker.isEmpty()) {
            Notification.show("Please select a date", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }
          if (lineEntries.isEmpty()) {
            Notification.show("Please add at least one line", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }

          // Check balance
          BigDecimal balance = calculateLineBalance(lineEntries);
          if (balance.compareTo(BigDecimal.ZERO) != 0) {
            Notification.show(
                    "Transaction is unbalanced by " + balance, 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }

          try {
            transaction.setTransactionDate(datePicker.getValue());
            transaction.setDescription(descriptionField.getValue());
            transaction.setReference(
                referenceField.getValue().isBlank() ? null : referenceField.getValue());

            // Update lines
            transaction.getLines().clear();
            for (LineEntry le : lineEntries) {
              TransactionLine line = new TransactionLine(le.account, le.amount, le.direction);
              line.setTaxCode(le.taxCode);
              line.setMemo(le.memo);
              transaction.addLine(line);
            }

            Transaction saved = transactionService.save(transaction);

            // Save pending attachments
            savePendingAttachments(pendingAttachments, saved);

            postingService.postTransaction(saved, companyContextService.getCurrentUser());

            Notification.show(
                    "Transaction posted successfully", 3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            dialog.close();
            loadTransactions();
          } catch (IllegalStateException ex) {
            Notification.show(ex.getMessage(), 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
          }
        });

    Button cancelBtn = new Button("Cancel", e -> dialog.close());

    VerticalLayout content =
        new VerticalLayout(
            headerForm,
            linesTitle,
            linesGrid,
            addLineForm,
            suggestionRow,
            balanceSpan,
            attachmentsTitle,
            attachmentsContainer,
            upload);
    content.setPadding(false);
    content.setSpacing(true);

    dialog.add(content);
    dialog.getFooter().add(cancelBtn, saveBtn, saveAndPostBtn);
    dialog.open();
  }

  private HorizontalLayout createExistingAttachmentRow(
      Attachment attachment, VerticalLayout container, Transaction transaction) {
    HorizontalLayout row = new HorizontalLayout();
    row.setAlignItems(FlexComponent.Alignment.CENTER);

    // Create download link
    StreamResource resource =
        new StreamResource(
            attachment.getFilename(),
            () -> {
              try {
                byte[] fileContent = attachmentService.getFileContent(attachment);
                return new ByteArrayInputStream(fileContent);
              } catch (Exception ex) {
                return new ByteArrayInputStream(new byte[0]);
              }
            });

    Anchor downloadLink = new Anchor(resource, attachment.getFilename());
    downloadLink.getElement().setAttribute("download", true);

    Span sizeSpan = new Span(" (" + attachment.getFormattedSize() + ")");
    sizeSpan.getStyle().set("color", "var(--lumo-secondary-text-color)");

    Button removeBtn = new Button(VaadinIcon.CLOSE_SMALL.create());
    removeBtn.addThemeVariants(
        ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
    removeBtn.getElement().setAttribute("title", "Remove attachment");
    removeBtn.addClickListener(
        e -> {
          attachmentService.unlinkFromEntity(
              attachment, EntityType.TRANSACTION, transaction.getId());
          container.remove(row);
          Notification.show("Attachment removed", 2000, Notification.Position.BOTTOM_START);
        });

    row.add(VaadinIcon.FILE.create(), downloadLink, sizeSpan, removeBtn);
    return row;
  }

  private Button createRemovePendingButton(
      PendingAttachment pending,
      List<PendingAttachment> pendingList,
      HorizontalLayout row,
      VerticalLayout container) {
    Button removeBtn = new Button(VaadinIcon.CLOSE_SMALL.create());
    removeBtn.addThemeVariants(
        ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
    removeBtn.addClickListener(
        e -> {
          pendingList.remove(pending);
          container.remove(row);
        });
    return removeBtn;
  }

  private void updateBalance(List<LineEntry> lines, Span balanceSpan) {
    BigDecimal balance = calculateLineBalance(lines);
    if (balance.compareTo(BigDecimal.ZERO) == 0) {
      balanceSpan.setText("Balance: $0.00 (Balanced)");
      balanceSpan.getStyle().set("color", "var(--lumo-success-text-color)");
    } else {
      balanceSpan.setText(
          "Balance: $"
              + balance.abs().setScale(2).toPlainString()
              + (balance.compareTo(BigDecimal.ZERO) > 0 ? " DR" : " CR"));
      balanceSpan.getStyle().set("color", "var(--lumo-error-text-color)");
    }
  }

  private BigDecimal calculateLineBalance(List<LineEntry> lines) {
    BigDecimal debits = BigDecimal.ZERO;
    BigDecimal credits = BigDecimal.ZERO;
    for (LineEntry le : lines) {
      if (le.amount != null) {
        if (le.direction == Direction.DEBIT) {
          debits = debits.add(le.amount);
        } else {
          credits = credits.add(le.amount);
        }
      }
    }
    return debits.subtract(credits);
  }

  private void openViewDialog(Transaction transaction) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle(
        transaction.getType().name()
            + " - "
            + transaction.getTransactionDate().format(DATE_FORMAT));
    dialog.setWidth("750px");

    VerticalLayout content = new VerticalLayout();
    content.setPadding(false);

    content.add(new Span("Status: " + transaction.getStatus().name()));
    content.add(
        new Span(
            "Reference: "
                + (transaction.getReference() != null ? transaction.getReference() : "-")));
    content.add(
        new Span(
            "Description: "
                + (transaction.getDescription() != null ? transaction.getDescription() : "-")));

    H3 linesTitle = new H3("Lines");
    content.add(linesTitle);

    Grid<TransactionLine> linesGrid = new Grid<>();
    linesGrid.setHeight("200px");
    linesGrid.addThemeVariants(GridVariant.LUMO_COMPACT);

    linesGrid.addColumn(l -> l.getAccount().getCode()).setHeader("Account");
    linesGrid.addColumn(l -> l.getAccount().getName()).setHeader("Name").setFlexGrow(1);
    linesGrid.addColumn(l -> l.getDirection().name()).setHeader("DR/CR");
    linesGrid.addColumn(l -> l.getAmount().toPlainString()).setHeader("Amount");
    linesGrid.addColumn(TransactionLine::getTaxCode).setHeader("Tax");
    linesGrid.addColumn(TransactionLine::getMemo).setHeader("Memo");

    linesGrid.setItems(transaction.getLines());
    content.add(linesGrid);

    // Attachments section
    H3 attachmentsTitle = new H3("Attachments");
    content.add(attachmentsTitle);

    List<Attachment> attachments =
        attachmentService.findByEntity(EntityType.TRANSACTION, transaction.getId());

    if (attachments.isEmpty()) {
      content.add(new Span("No attachments"));
    } else {
      VerticalLayout attachmentsList = new VerticalLayout();
      attachmentsList.setPadding(false);
      attachmentsList.setSpacing(false);

      for (Attachment att : attachments) {
        HorizontalLayout attachmentRow = new HorizontalLayout();
        attachmentRow.setAlignItems(FlexComponent.Alignment.CENTER);

        // Create download link
        StreamResource resource =
            new StreamResource(
                att.getFilename(),
                () -> {
                  try {
                    byte[] fileContent = attachmentService.getFileContent(att);
                    return new ByteArrayInputStream(fileContent);
                  } catch (Exception ex) {
                    Notification.show(
                            "Error loading file: " + ex.getMessage(),
                            3000,
                            Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                    return new ByteArrayInputStream(new byte[0]);
                  }
                });

        Anchor downloadLink = new Anchor(resource, att.getFilename());
        downloadLink.getElement().setAttribute("download", true);

        Span sizeSpan = new Span(" (" + att.getFormattedSize() + ")");
        sizeSpan.getStyle().set("color", "var(--lumo-secondary-text-color)");

        attachmentRow.add(VaadinIcon.FILE.create(), downloadLink, sizeSpan);

        attachmentsList.add(attachmentRow);
      }
      content.add(attachmentsList);
    }

    Button closeBtn = new Button("Close", e -> dialog.close());

    dialog.add(content);
    dialog.getFooter().add(closeBtn);
    dialog.open();
  }

  private void postTransaction(Transaction transaction) {
    try {
      postingService.postTransaction(transaction, companyContextService.getCurrentUser());
      Notification.show("Transaction posted successfully", 3000, Notification.Position.BOTTOM_START)
          .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
      loadTransactions();
    } catch (IllegalStateException ex) {
      Notification.show(ex.getMessage(), 3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
  }

  private void deleteTransaction(Transaction transaction) {
    try {
      transactionService.delete(transaction);
      Notification.show("Transaction deleted", 3000, Notification.Position.BOTTOM_START)
          .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
      loadTransactions();
    } catch (IllegalStateException ex) {
      Notification.show(ex.getMessage(), 3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
  }

  private void openReverseDialog(Transaction transaction) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Reverse Transaction");
    dialog.setWidth("400px");

    TextField reasonField = new TextField("Reason for reversal");
    reasonField.setWidthFull();

    Button reverseBtn = new Button("Reverse");
    reverseBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
    reverseBtn.addClickListener(
        e -> {
          try {
            postingService.reverseTransaction(transaction, null, reasonField.getValue());
            Notification.show(
                    "Transaction reversed successfully", 3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            dialog.close();
            loadTransactions();
          } catch (IllegalStateException ex) {
            Notification.show(ex.getMessage(), 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
          }
        });

    Button cancelBtn = new Button("Cancel", e -> dialog.close());

    dialog.add(
        new Span("This will create a new reversal transaction with inverted debits/credits."));
    dialog.add(reasonField);
    dialog.getFooter().add(cancelBtn, reverseBtn);
    dialog.open();
  }

  /**
   * Opens the receipt allocation dialog to allocate a receipt to outstanding invoices. Shows all
   * outstanding invoices with suggested allocations based on amount matching.
   */
  private void openReceiptAllocationDialog(Transaction receipt) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Allocate Receipt to Invoices");
    dialog.setWidth("900px");
    dialog.setHeight("600px");

    Company company = companyContextService.getCurrentCompany();
    User currentUser = companyContextService.getCurrentUser();

    // Calculate receipt total amount
    BigDecimal receiptTotal = BigDecimal.ZERO;
    for (TransactionLine line : receipt.getLines()) {
      if (line.isDebit()) {
        receiptTotal = receiptTotal.add(line.getAmount());
      }
    }

    // Get unallocated amount
    BigDecimal unallocated = receivableAllocationService.getUnallocatedAmount(receipt);

    // Get existing allocations
    List<ReceivableAllocation> existingAllocations =
        receivableAllocationService.findByReceipt(receipt);
    BigDecimal alreadyAllocated = receiptTotal.subtract(unallocated);

    // Receipt info header
    VerticalLayout headerSection = new VerticalLayout();
    headerSection.setPadding(false);
    headerSection.setSpacing(false);

    Span receiptInfo =
        new Span(
            "Receipt: "
                + receipt.getDescription()
                + " dated "
                + receipt.getTransactionDate().format(DATE_FORMAT));
    receiptInfo.getStyle().set("font-weight", "bold");

    HorizontalLayout amountInfo = new HorizontalLayout();
    amountInfo.setSpacing(true);
    Span totalSpan = new Span("Total: $" + receiptTotal.setScale(2).toPlainString());
    Span allocatedSpan = new Span("Allocated: $" + alreadyAllocated.setScale(2).toPlainString());
    Span unallocatedSpan = new Span("Unallocated: $" + unallocated.setScale(2).toPlainString());
    unallocatedSpan.getStyle().set("font-weight", "bold");
    if (unallocated.compareTo(BigDecimal.ZERO) > 0) {
      unallocatedSpan.getStyle().set("color", "var(--lumo-primary-color)");
    }
    amountInfo.add(totalSpan, allocatedSpan, unallocatedSpan);

    headerSection.add(receiptInfo, amountInfo);

    // Existing allocations section (if any)
    VerticalLayout existingSection = new VerticalLayout();
    existingSection.setPadding(false);

    if (!existingAllocations.isEmpty()) {
      H3 existingTitle = new H3("Current Allocations");
      Grid<ReceivableAllocation> existingGrid = new Grid<>();
      existingGrid.setHeight("120px");
      existingGrid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);

      existingGrid
          .addColumn(a -> a.getSalesInvoice().getInvoiceNumber())
          .setHeader("Invoice")
          .setAutoWidth(true);
      existingGrid
          .addColumn(a -> a.getSalesInvoice().getContact().getName())
          .setHeader("Customer")
          .setFlexGrow(1);
      existingGrid
          .addColumn(a -> "$" + a.getAmount().setScale(2).toPlainString())
          .setHeader("Allocated")
          .setAutoWidth(true);
      existingGrid
          .addColumn(a -> a.getAllocatedAt().toString().substring(0, 10))
          .setHeader("Date")
          .setAutoWidth(true);
      existingGrid
          .addComponentColumn(
              allocation -> {
                Button removeBtn = new Button(VaadinIcon.CLOSE_SMALL.create());
                removeBtn.addThemeVariants(
                    ButtonVariant.LUMO_TERTIARY,
                    ButtonVariant.LUMO_SMALL,
                    ButtonVariant.LUMO_ERROR);
                removeBtn.getElement().setAttribute("title", "Remove allocation");
                removeBtn.addClickListener(
                    e -> {
                      receivableAllocationService.removeAllocation(allocation, currentUser);
                      Notification.show(
                              "Allocation removed", 2000, Notification.Position.BOTTOM_START)
                          .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                      dialog.close();
                      openReceiptAllocationDialog(receipt); // Refresh dialog
                    });
                return removeBtn;
              })
          .setHeader("")
          .setAutoWidth(true)
          .setFlexGrow(0);

      existingGrid.setItems(existingAllocations);
      existingSection.add(existingTitle, existingGrid);
    }

    // Outstanding invoices section
    H3 outstandingTitle = new H3("Outstanding Invoices");

    // Get all outstanding invoices
    List<SalesInvoice> outstandingInvoices = salesInvoiceService.findOutstandingByCompany(company);

    // Get allocation suggestions
    List<ReceivableAllocationService.AllocationSuggestion> suggestions =
        receivableAllocationService.suggestAllocations(company, null, unallocated);

    // Map to track allocation amounts per invoice
    Map<Long, BigDecimalField> allocationFields = new HashMap<>();

    Grid<SalesInvoice> invoiceGrid = new Grid<>();
    invoiceGrid.setHeight("250px");
    invoiceGrid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);

    invoiceGrid
        .addColumn(SalesInvoice::getInvoiceNumber)
        .setHeader("Invoice")
        .setSortable(true)
        .setAutoWidth(true);

    invoiceGrid
        .addColumn(inv -> inv.getContact().getName())
        .setHeader("Customer")
        .setSortable(true)
        .setFlexGrow(1);

    invoiceGrid
        .addColumn(inv -> inv.getDueDate().format(DATE_FORMAT))
        .setHeader("Due Date")
        .setSortable(true)
        .setAutoWidth(true);

    invoiceGrid
        .addColumn(inv -> "$" + inv.getTotal().setScale(2).toPlainString())
        .setHeader("Total")
        .setAutoWidth(true);

    invoiceGrid
        .addColumn(inv -> "$" + inv.getBalance().setScale(2).toPlainString())
        .setHeader("Balance")
        .setAutoWidth(true);

    invoiceGrid
        .addComponentColumn(
            inv -> {
              Span badge = new Span();
              if (inv.isOverdue()) {
                badge.setText("OVERDUE");
                badge.getElement().getThemeList().add("badge error small");
              } else {
                badge.setText("DUE");
                badge.getElement().getThemeList().add("badge small");
              }
              return badge;
            })
        .setHeader("Status")
        .setAutoWidth(true);

    invoiceGrid
        .addComponentColumn(
            inv -> {
              BigDecimalField allocField = new BigDecimalField();
              allocField.setWidth("100px");
              allocField.setPlaceholder("0.00");

              // Pre-fill with suggestion if available
              suggestions.stream()
                  .filter(s -> s.invoice().getId().equals(inv.getId()))
                  .findFirst()
                  .ifPresent(suggestion -> allocField.setValue(suggestion.suggestedAmount()));

              allocationFields.put(inv.getId(), allocField);
              return allocField;
            })
        .setHeader("Allocate")
        .setAutoWidth(true);

    invoiceGrid.setItems(outstandingInvoices);

    // Auto-allocate button
    Button autoAllocateBtn = new Button("Auto-Allocate", VaadinIcon.MAGIC.create());
    autoAllocateBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    autoAllocateBtn.addClickListener(
        e -> {
          // Apply suggestions to fields
          for (ReceivableAllocationService.AllocationSuggestion suggestion : suggestions) {
            BigDecimalField field = allocationFields.get(suggestion.invoice().getId());
            if (field != null) {
              field.setValue(suggestion.suggestedAmount());
            }
          }
          Notification.show(
              "Amounts suggested based on oldest invoices first",
              2000,
              Notification.Position.BOTTOM_START);
        });

    // Clear button
    Button clearBtn = new Button("Clear All", VaadinIcon.CLOSE.create());
    clearBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    clearBtn.addClickListener(
        e -> {
          allocationFields.values().forEach(field -> field.clear());
        });

    HorizontalLayout gridActions = new HorizontalLayout(autoAllocateBtn, clearBtn);
    gridActions.setSpacing(true);

    // Summary section that updates as user enters amounts
    Span allocationSummary = new Span();
    updateAllocationSummary(allocationSummary, allocationFields, unallocated);

    // Add listeners to update summary when allocation amounts change
    for (BigDecimalField field : allocationFields.values()) {
      field.addValueChangeListener(
          e -> updateAllocationSummary(allocationSummary, allocationFields, unallocated));
    }

    // Footer buttons
    Button allocateBtn = new Button("Save Allocations");
    allocateBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
    allocateBtn.addClickListener(
        e -> {
          try {
            int allocationsCreated = 0;
            for (SalesInvoice inv : outstandingInvoices) {
              BigDecimalField field = allocationFields.get(inv.getId());
              if (field != null
                  && field.getValue() != null
                  && field.getValue().compareTo(BigDecimal.ZERO) > 0) {
                receivableAllocationService.allocate(receipt, inv, field.getValue(), currentUser);
                allocationsCreated++;
              }
            }

            if (allocationsCreated > 0) {
              Notification.show(
                      allocationsCreated + " allocation(s) created",
                      3000,
                      Notification.Position.BOTTOM_START)
                  .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } else {
              Notification.show("No allocations to save", 2000, Notification.Position.BOTTOM_START);
            }

            dialog.close();
            loadTransactions();
          } catch (IllegalArgumentException | IllegalStateException ex) {
            Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
          }
        });

    Button cancelBtn = new Button("Cancel", e -> dialog.close());

    // Build layout
    VerticalLayout content =
        new VerticalLayout(
            headerSection,
            existingSection,
            outstandingTitle,
            gridActions,
            invoiceGrid,
            allocationSummary);
    content.setPadding(false);
    content.setSpacing(true);

    dialog.add(content);
    dialog.getFooter().add(cancelBtn, allocateBtn);
    dialog.open();
  }

  /** Updates the allocation summary display to show total being allocated vs available. */
  private void updateAllocationSummary(
      Span summary, Map<Long, BigDecimalField> fields, BigDecimal available) {
    BigDecimal totalToAllocate = BigDecimal.ZERO;
    for (BigDecimalField field : fields.values()) {
      if (field.getValue() != null) {
        totalToAllocate = totalToAllocate.add(field.getValue());
      }
    }

    BigDecimal remaining = available.subtract(totalToAllocate);
    String text =
        "Allocating: $"
            + totalToAllocate.setScale(2).toPlainString()
            + " | Remaining: $"
            + remaining.setScale(2).toPlainString();

    summary.setText(text);
    if (remaining.compareTo(BigDecimal.ZERO) < 0) {
      summary.getStyle().set("color", "var(--lumo-error-text-color)");
      summary.setText(text + " (over-allocated!)");
    } else if (remaining.compareTo(BigDecimal.ZERO) == 0) {
      summary.getStyle().set("color", "var(--lumo-success-text-color)");
      summary.setText(text + " (fully allocated)");
    } else {
      summary.getStyle().set("color", "var(--lumo-body-text-color)");
    }
  }

  /**
   * Opens the payment allocation dialog to allocate a payment to outstanding supplier bills. Shows
   * all outstanding bills with suggested allocations based on amount matching.
   */
  private void openPaymentAllocationDialog(Transaction payment) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Allocate Payment to Bills");
    dialog.setWidth("900px");
    dialog.setHeight("600px");

    Company company = companyContextService.getCurrentCompany();
    User currentUser = companyContextService.getCurrentUser();

    // Calculate payment total amount (sum of credits as payment goes out)
    BigDecimal paymentTotal = BigDecimal.ZERO;
    for (TransactionLine line : payment.getLines()) {
      if (line.isCredit()) {
        paymentTotal = paymentTotal.add(line.getAmount());
      }
    }

    // Get unallocated amount
    BigDecimal unallocated = payableAllocationService.getUnallocatedAmount(payment);

    // Get existing allocations
    List<PayableAllocation> existingAllocations = payableAllocationService.findByPayment(payment);
    BigDecimal alreadyAllocated = paymentTotal.subtract(unallocated);

    // Payment info header
    VerticalLayout headerSection = new VerticalLayout();
    headerSection.setPadding(false);
    headerSection.setSpacing(false);

    Span paymentInfo =
        new Span(
            "Payment: "
                + payment.getDescription()
                + " dated "
                + payment.getTransactionDate().format(DATE_FORMAT));
    paymentInfo.getStyle().set("font-weight", "bold");

    HorizontalLayout amountInfo = new HorizontalLayout();
    amountInfo.setSpacing(true);
    Span totalSpan = new Span("Total: $" + paymentTotal.setScale(2).toPlainString());
    Span allocatedSpan = new Span("Allocated: $" + alreadyAllocated.setScale(2).toPlainString());
    Span unallocatedSpan = new Span("Unallocated: $" + unallocated.setScale(2).toPlainString());
    unallocatedSpan.getStyle().set("font-weight", "bold");
    if (unallocated.compareTo(BigDecimal.ZERO) > 0) {
      unallocatedSpan.getStyle().set("color", "var(--lumo-primary-color)");
    }
    amountInfo.add(totalSpan, allocatedSpan, unallocatedSpan);

    headerSection.add(paymentInfo, amountInfo);

    // Existing allocations section (if any)
    VerticalLayout existingSection = new VerticalLayout();
    existingSection.setPadding(false);

    if (!existingAllocations.isEmpty()) {
      H3 existingTitle = new H3("Current Allocations");
      Grid<PayableAllocation> existingGrid = new Grid<>();
      existingGrid.setHeight("120px");
      existingGrid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);

      existingGrid
          .addColumn(a -> a.getSupplierBill().getBillNumber())
          .setHeader("Bill")
          .setAutoWidth(true);
      existingGrid
          .addColumn(a -> a.getSupplierBill().getContact().getName())
          .setHeader("Supplier")
          .setFlexGrow(1);
      existingGrid
          .addColumn(a -> "$" + a.getAmount().setScale(2).toPlainString())
          .setHeader("Allocated")
          .setAutoWidth(true);
      existingGrid
          .addColumn(a -> a.getAllocatedAt().toString().substring(0, 10))
          .setHeader("Date")
          .setAutoWidth(true);
      existingGrid
          .addComponentColumn(
              allocation -> {
                Button removeBtn = new Button(VaadinIcon.CLOSE_SMALL.create());
                removeBtn.addThemeVariants(
                    ButtonVariant.LUMO_TERTIARY,
                    ButtonVariant.LUMO_SMALL,
                    ButtonVariant.LUMO_ERROR);
                removeBtn.getElement().setAttribute("title", "Remove allocation");
                removeBtn.addClickListener(
                    e -> {
                      payableAllocationService.removeAllocation(allocation, currentUser);
                      Notification.show(
                              "Allocation removed", 2000, Notification.Position.BOTTOM_START)
                          .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                      dialog.close();
                      openPaymentAllocationDialog(payment); // Refresh dialog
                    });
                return removeBtn;
              })
          .setHeader("")
          .setAutoWidth(true)
          .setFlexGrow(0);

      existingGrid.setItems(existingAllocations);
      existingSection.add(existingTitle, existingGrid);
    }

    // Outstanding bills section
    H3 outstandingTitle = new H3("Outstanding Bills");

    // Get all outstanding bills
    List<SupplierBill> outstandingBills = supplierBillService.findOutstandingByCompany(company);

    // Get allocation suggestions
    List<PayableAllocationService.AllocationSuggestion> suggestions =
        payableAllocationService.suggestAllocations(company, null, unallocated);

    // Map to track allocation amounts per bill
    Map<Long, BigDecimalField> allocationFields = new HashMap<>();

    Grid<SupplierBill> billGrid = new Grid<>();
    billGrid.setHeight("250px");
    billGrid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);

    billGrid
        .addColumn(SupplierBill::getBillNumber)
        .setHeader("Bill")
        .setSortable(true)
        .setAutoWidth(true);

    billGrid
        .addColumn(bill -> bill.getContact().getName())
        .setHeader("Supplier")
        .setSortable(true)
        .setFlexGrow(1);

    billGrid
        .addColumn(bill -> bill.getDueDate().format(DATE_FORMAT))
        .setHeader("Due Date")
        .setSortable(true)
        .setAutoWidth(true);

    billGrid
        .addColumn(bill -> "$" + bill.getTotal().setScale(2).toPlainString())
        .setHeader("Total")
        .setAutoWidth(true);

    billGrid
        .addColumn(bill -> "$" + bill.getBalance().setScale(2).toPlainString())
        .setHeader("Balance")
        .setAutoWidth(true);

    billGrid
        .addComponentColumn(
            bill -> {
              Span badge = new Span();
              if (bill.isOverdue()) {
                badge.setText("OVERDUE");
                badge.getElement().getThemeList().add("badge error small");
              } else {
                badge.setText("DUE");
                badge.getElement().getThemeList().add("badge small");
              }
              return badge;
            })
        .setHeader("Status")
        .setAutoWidth(true);

    billGrid
        .addComponentColumn(
            bill -> {
              BigDecimalField allocField = new BigDecimalField();
              allocField.setWidth("100px");
              allocField.setPlaceholder("0.00");

              // Pre-fill with suggestion if available
              suggestions.stream()
                  .filter(s -> s.bill().getId().equals(bill.getId()))
                  .findFirst()
                  .ifPresent(suggestion -> allocField.setValue(suggestion.suggestedAmount()));

              allocationFields.put(bill.getId(), allocField);
              return allocField;
            })
        .setHeader("Allocate")
        .setAutoWidth(true);

    billGrid.setItems(outstandingBills);

    // Auto-allocate button
    Button autoAllocateBtn = new Button("Auto-Allocate", VaadinIcon.MAGIC.create());
    autoAllocateBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    autoAllocateBtn.addClickListener(
        e -> {
          // Apply suggestions to fields
          for (PayableAllocationService.AllocationSuggestion suggestion : suggestions) {
            BigDecimalField field = allocationFields.get(suggestion.bill().getId());
            if (field != null) {
              field.setValue(suggestion.suggestedAmount());
            }
          }
          Notification.show(
              "Amounts suggested based on oldest bills first",
              2000,
              Notification.Position.BOTTOM_START);
        });

    // Clear button
    Button clearBtn = new Button("Clear All", VaadinIcon.CLOSE.create());
    clearBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    clearBtn.addClickListener(
        e -> {
          allocationFields.values().forEach(field -> field.clear());
        });

    HorizontalLayout gridActions = new HorizontalLayout(autoAllocateBtn, clearBtn);
    gridActions.setSpacing(true);

    // Summary section that updates as user enters amounts
    Span allocationSummary = new Span();
    updateAllocationSummary(allocationSummary, allocationFields, unallocated);

    // Add listeners to update summary when allocation amounts change
    for (BigDecimalField field : allocationFields.values()) {
      field.addValueChangeListener(
          e -> updateAllocationSummary(allocationSummary, allocationFields, unallocated));
    }

    // Footer buttons
    Button allocateBtn = new Button("Save Allocations");
    allocateBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
    allocateBtn.addClickListener(
        e -> {
          try {
            int allocationsCreated = 0;
            for (SupplierBill bill : outstandingBills) {
              BigDecimalField field = allocationFields.get(bill.getId());
              if (field != null
                  && field.getValue() != null
                  && field.getValue().compareTo(BigDecimal.ZERO) > 0) {
                payableAllocationService.allocate(payment, bill, field.getValue(), currentUser);
                allocationsCreated++;
              }
            }

            if (allocationsCreated > 0) {
              Notification.show(
                      allocationsCreated + " allocation(s) created",
                      3000,
                      Notification.Position.BOTTOM_START)
                  .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } else {
              Notification.show("No allocations to save", 2000, Notification.Position.BOTTOM_START);
            }

            dialog.close();
            loadTransactions();
          } catch (IllegalArgumentException | IllegalStateException ex) {
            Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
          }
        });

    Button cancelBtn = new Button("Cancel", e -> dialog.close());

    // Build layout
    VerticalLayout content =
        new VerticalLayout(
            headerSection,
            existingSection,
            outstandingTitle,
            gridActions,
            billGrid,
            allocationSummary);
    content.setPadding(false);
    content.setSpacing(true);

    dialog.add(content);
    dialog.getFooter().add(cancelBtn, allocateBtn);
    dialog.open();
  }

  /** Helper class to hold line entry data in the UI before saving. */
  private static class LineEntry {
    Account account;
    Direction direction;
    BigDecimal amount;
    String taxCode;
    String memo;

    LineEntry() {}

    LineEntry(TransactionLine line) {
      this.account = line.getAccount();
      this.direction = line.getDirection();
      this.amount = line.getAmount();
      this.taxCode = line.getTaxCode();
      this.memo = line.getMemo();
    }
  }

  /** Helper class to hold pending attachment data before transaction is saved. */
  private static class PendingAttachment {
    String filename;
    String mimeType;
    byte[] content;

    PendingAttachment(String filename, String mimeType, byte[] content) {
      this.filename = filename;
      this.mimeType = mimeType;
      this.content = content;
    }
  }

  /** Saves all pending attachments and links them to the transaction. */
  private void savePendingAttachments(
      List<PendingAttachment> pendingAttachments, Transaction transaction) {
    Company company = companyContextService.getCurrentCompany();
    for (PendingAttachment pending : pendingAttachments) {
      try {
        attachmentService.uploadAndLink(
            company,
            pending.filename,
            pending.mimeType,
            pending.content,
            companyContextService.getCurrentUser(),
            EntityType.TRANSACTION,
            transaction.getId());
      } catch (Exception ex) {
        Notification.show(
                "Failed to save attachment: " + pending.filename + " - " + ex.getMessage(),
                3000,
                Notification.Position.MIDDLE)
            .addThemeVariants(NotificationVariant.LUMO_ERROR);
      }
    }
  }

  private void openImportDialog() {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Import Transactions from CSV");
    dialog.setWidth("650px");

    VerticalLayout layout = new VerticalLayout();
    layout.setPadding(false);
    layout.setSpacing(true);

    // Instructions
    Span instructions =
        new Span(
            "Upload a CSV file with transaction data. Required columns: date, type, description, "
                + "account_code, amount, direction. "
                + "Optional columns: reference, tax_code, memo, department_code. "
                + "Transactions are grouped by date + type + description + reference - all lines "
                + "with the same combination become one transaction.");
    instructions
        .getStyle()
        .set("color", "var(--lumo-secondary-text-color)")
        .set("font-size", "var(--lumo-font-size-s)");

    // Download sample CSV link
    String sampleCsv = transactionImportService.getSampleCsvContent();
    StreamResource sampleResource =
        new StreamResource(
            "transactions_sample.csv",
            () -> new ByteArrayInputStream(sampleCsv.getBytes(StandardCharsets.UTF_8)));
    sampleResource.setContentType("text/csv");
    Anchor downloadSample = new Anchor(sampleResource, "Download sample CSV");
    downloadSample.getElement().setAttribute("download", true);
    downloadSample.getStyle().set("font-size", "var(--lumo-font-size-s)");

    // Options
    Checkbox autoPostCheckbox = new Checkbox("Auto-post transactions after import");
    autoPostCheckbox.setValue(false);
    autoPostCheckbox.setHelperText("If unchecked, transactions are created as drafts");

    Checkbox groupByReferenceCheckbox = new Checkbox("Group lines by reference");
    groupByReferenceCheckbox.setValue(true);
    groupByReferenceCheckbox.setHelperText(
        "Lines with the same date, type, description, and reference become one transaction");

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

    // Store the uploaded bytes for import
    final byte[][] uploadedBytes = {null};

    upload.addSucceededListener(
        event -> {
          try {
            // Read the stream into bytes for multiple uses
            uploadedBytes[0] = buffer.getInputStream().readAllBytes();

            // Preview the import
            Company company = companyContextService.getCurrentCompany();
            TransactionImportService.ImportConfig config =
                new TransactionImportService.ImportConfig(
                    autoPostCheckbox.getValue(), groupByReferenceCheckbox.getValue());
            TransactionImportService.ImportResult preview =
                transactionImportService.previewImport(
                    new ByteArrayInputStream(uploadedBytes[0]), company, config);

            resultArea.removeAll();
            if (preview.success()) {
              Span previewText =
                  new Span(
                      String.format(
                          "Preview: %d transactions to import, %d skipped",
                          preview.imported(), preview.skipped()));
              previewText.getStyle().set("color", "var(--lumo-success-text-color)");
              resultArea.add(previewText);

              if (!preview.warnings().isEmpty()) {
                for (String warning : preview.warnings()) {
                  Span warningSpan = new Span(warning);
                  warningSpan
                      .getStyle()
                      .set("color", "var(--lumo-warning-text-color)")
                      .set("font-size", "var(--lumo-font-size-s)");
                  resultArea.add(warningSpan);
                }
              }
              importButton.setEnabled(true);
            } else {
              Span errorText = new Span("Import preview failed:");
              errorText.getStyle().set("color", "var(--lumo-error-text-color)");
              resultArea.add(errorText);
              for (String error : preview.errors()) {
                Span errorSpan = new Span(error);
                errorSpan
                    .getStyle()
                    .set("color", "var(--lumo-error-text-color)")
                    .set("font-size", "var(--lumo-font-size-s)");
                resultArea.add(errorSpan);
              }
              importButton.setEnabled(false);
            }
            resultArea.setVisible(true);
          } catch (IOException e) {
            Notification.show("Error reading file: " + e.getMessage())
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
          }
        });

    upload.addFileRejectedListener(
        event -> {
          Notification.show("Invalid file: " + event.getErrorMessage())
              .addThemeVariants(NotificationVariant.LUMO_ERROR);
        });

    importButton.addClickListener(
        event -> {
          if (uploadedBytes[0] == null) {
            Notification.show("Please upload a CSV file first")
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
          }

          try {
            Company company = companyContextService.getCurrentCompany();
            User user = companyContextService.getCurrentUser();
            TransactionImportService.ImportConfig config =
                new TransactionImportService.ImportConfig(
                    autoPostCheckbox.getValue(), groupByReferenceCheckbox.getValue());
            TransactionImportService.ImportResult result =
                transactionImportService.importTransactions(
                    new ByteArrayInputStream(uploadedBytes[0]), company, user, config);

            if (result.success()) {
              String statusMsg = autoPostCheckbox.getValue() ? " (posted)" : " (saved as drafts)";
              Notification.show(
                      "Successfully imported "
                          + result.imported()
                          + " transactions"
                          + statusMsg
                          + (result.skipped() > 0 ? ", skipped " + result.skipped() : ""),
                      5000,
                      Notification.Position.MIDDLE)
                  .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
              dialog.close();
              loadTransactions();
            } else {
              resultArea.removeAll();
              Span errorText = new Span("Import failed:");
              errorText.getStyle().set("color", "var(--lumo-error-text-color)");
              resultArea.add(errorText);
              for (String error : result.errors()) {
                Span errorSpan = new Span(error);
                errorSpan
                    .getStyle()
                    .set("color", "var(--lumo-error-text-color)")
                    .set("font-size", "var(--lumo-font-size-s)");
                resultArea.add(errorSpan);
              }
            }
          } catch (IOException e) {
            Notification.show("Error importing file: " + e.getMessage())
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
          }
        });

    Button cancelButton = new Button("Cancel", e -> dialog.close());

    layout.add(
        instructions,
        downloadSample,
        autoPostCheckbox,
        groupByReferenceCheckbox,
        upload,
        resultArea);

    dialog.add(layout);
    dialog.getFooter().add(cancelButton, importButton);
    dialog.open();
  }
}
