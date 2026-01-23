package com.example.application.ui.views;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.example.application.domain.*;
import com.example.application.domain.BankFeedItem.FeedItemStatus;
import com.example.application.domain.Transaction.TransactionType;
import com.example.application.domain.TransactionLine.Direction;
import com.example.application.service.*;
import com.example.application.ui.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.*;
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
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.PermitAll;

/**
 * View for bank reconciliation. Two-pane layout: bank feed items on left, matching/create actions
 * on right.
 *
 * <p>Features: - Import bank statements (QIF, OFX, CSV) - View unmatched bank feed items - Match
 * items to existing transactions - Create new transactions from feed items - Ignore items that
 * don't need transactions - Auto-suggest coding based on allocation rules
 */
@Route(value = "reconciliation", layout = MainLayout.class)
@PageTitle("Bank Reconciliation | MoniWorks")
@PermitAll
public class BankReconciliationView extends VerticalLayout {

  private final BankImportService bankImportService;
  private final TransactionService transactionService;
  private final PostingService postingService;
  private final AccountService accountService;
  private final TaxCodeService taxCodeService;
  private final CompanyContextService companyContextService;

  private final ComboBox<Account> bankAccountSelector = new ComboBox<>("Bank Account");
  private final Grid<BankFeedItem> feedItemGrid = new Grid<>();
  private final VerticalLayout detailPanel = new VerticalLayout();

  private Account selectedBankAccount;
  private BankFeedItem selectedFeedItem;

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

  public BankReconciliationView(
      BankImportService bankImportService,
      TransactionService transactionService,
      PostingService postingService,
      AccountService accountService,
      TaxCodeService taxCodeService,
      CompanyContextService companyContextService) {
    this.bankImportService = bankImportService;
    this.transactionService = transactionService;
    this.postingService = postingService;
    this.accountService = accountService;
    this.taxCodeService = taxCodeService;
    this.companyContextService = companyContextService;

    addClassName("bank-reconciliation-view");
    setSizeFull();
    setPadding(false);
    setSpacing(false);

    add(createToolbar());
    add(createMainContent());

    loadBankAccounts();
  }

  private HorizontalLayout createToolbar() {
    H2 title = new H2("Bank Reconciliation");

    bankAccountSelector.setPlaceholder("Select bank account");
    bankAccountSelector.setItemLabelGenerator(a -> a.getCode() + " - " + a.getName());
    bankAccountSelector.setWidth("300px");
    bankAccountSelector.addValueChangeListener(
        e -> {
          selectedBankAccount = e.getValue();
          loadFeedItems();
        });

    Button importBtn = new Button("Import Statement", VaadinIcon.UPLOAD.create());
    importBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    importBtn.addClickListener(e -> openImportDialog());

    Button refreshBtn = new Button(VaadinIcon.REFRESH.create());
    refreshBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    refreshBtn.addClickListener(e -> loadFeedItems());
    refreshBtn.getElement().setAttribute("title", "Refresh");

    HorizontalLayout left = new HorizontalLayout(title, bankAccountSelector);
    left.setAlignItems(FlexComponent.Alignment.CENTER);
    left.setSpacing(true);

    HorizontalLayout right = new HorizontalLayout(importBtn, refreshBtn);
    right.setAlignItems(FlexComponent.Alignment.CENTER);

    HorizontalLayout toolbar = new HorizontalLayout(left, right);
    toolbar.setWidthFull();
    toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
    toolbar.setAlignItems(FlexComponent.Alignment.CENTER);
    toolbar.setPadding(true);
    toolbar.getStyle().set("background-color", "var(--lumo-contrast-5pct)");

    return toolbar;
  }

  private SplitLayout createMainContent() {
    // Left panel - bank feed items
    VerticalLayout leftPanel = new VerticalLayout();
    leftPanel.setSizeFull();
    leftPanel.setPadding(true);
    leftPanel.setSpacing(true);

    H3 feedTitle = new H3("Bank Feed Items");
    Span feedSubtitle = new Span("Unmatched items from imported statements");
    feedSubtitle.getStyle().set("color", "var(--lumo-secondary-text-color)");

    configureFeedItemGrid();

    leftPanel.add(feedTitle, feedSubtitle, feedItemGrid);
    leftPanel.setFlexGrow(1, feedItemGrid);

    // Right panel - detail/action panel
    detailPanel.setSizeFull();
    detailPanel.setPadding(true);
    detailPanel.setSpacing(true);

    showEmptyDetailPanel();

    SplitLayout splitLayout = new SplitLayout(leftPanel, detailPanel);
    splitLayout.setSizeFull();
    splitLayout.setSplitterPosition(55);
    splitLayout.setOrientation(SplitLayout.Orientation.HORIZONTAL);

    return splitLayout;
  }

  private void configureFeedItemGrid() {
    feedItemGrid.addClassNames("feed-item-grid");
    feedItemGrid.setSizeFull();
    feedItemGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

    feedItemGrid
        .addColumn(item -> item.getPostedDate().format(DATE_FORMAT))
        .setHeader("Date")
        .setSortable(true)
        .setAutoWidth(true);

    feedItemGrid
        .addColumn(this::formatAmount)
        .setHeader("Amount")
        .setSortable(true)
        .setAutoWidth(true);

    feedItemGrid.addColumn(BankFeedItem::getDescription).setHeader("Description").setFlexGrow(1);

    feedItemGrid.addColumn(item -> item.getStatus().name()).setHeader("Status").setAutoWidth(true);

    feedItemGrid.addSelectionListener(
        e -> {
          Optional<BankFeedItem> selected = e.getFirstSelectedItem();
          if (selected.isPresent()) {
            selectedFeedItem = selected.get();
            showFeedItemDetail(selectedFeedItem);
          } else {
            selectedFeedItem = null;
            showEmptyDetailPanel();
          }
        });
  }

  private String formatAmount(BankFeedItem item) {
    BigDecimal amount = item.getAmount();
    String prefix = amount.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
    return prefix + "$" + amount.setScale(2).toPlainString();
  }

  private void showEmptyDetailPanel() {
    detailPanel.removeAll();

    Div placeholder = new Div();
    placeholder.add(new H3("Select a bank feed item"));
    placeholder.add(
        new Paragraph("Click on an item from the list to view details and take action."));
    placeholder
        .getStyle()
        .set("text-align", "center")
        .set("padding", "2rem")
        .set("color", "var(--lumo-secondary-text-color)");

    detailPanel.add(placeholder);
    detailPanel.setAlignItems(FlexComponent.Alignment.CENTER);
    detailPanel.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
  }

  private void showFeedItemDetail(BankFeedItem item) {
    detailPanel.removeAll();
    detailPanel.setAlignItems(FlexComponent.Alignment.STRETCH);
    detailPanel.setJustifyContentMode(FlexComponent.JustifyContentMode.START);

    // Header
    H3 detailTitle = new H3("Transaction Details");

    // Info section
    VerticalLayout infoSection = new VerticalLayout();
    infoSection.setPadding(false);
    infoSection.setSpacing(false);

    infoSection.add(createInfoRow("Date", item.getPostedDate().format(DATE_FORMAT)));
    infoSection.add(createInfoRow("Amount", formatAmount(item)));
    infoSection.add(createInfoRow("Description", item.getDescription()));
    infoSection.add(createInfoRow("Status", item.getStatus().name()));

    // Check for suggested allocation
    Company company = companyContextService.getCurrentCompany();
    Optional<AllocationRule> suggestedRule =
        bankImportService.findMatchingRule(company, item.getDescription());

    if (suggestedRule.isPresent()) {
      AllocationRule rule = suggestedRule.get();
      Span suggestion =
          new Span(
              "Suggested: "
                  + rule.getTargetAccount().getCode()
                  + " - "
                  + rule.getTargetAccount().getName());
      suggestion
          .getStyle()
          .set("background-color", "var(--lumo-primary-color-10pct)")
          .set("padding", "0.5rem")
          .set("border-radius", "var(--lumo-border-radius-m)")
          .set("display", "block");
      infoSection.add(suggestion);
    }

    // Action buttons
    H4 actionsTitle = new H4("Actions");

    Button createTransactionBtn = new Button("Create Transaction", VaadinIcon.PLUS.create());
    createTransactionBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    createTransactionBtn.setWidthFull();
    createTransactionBtn.addClickListener(
        e -> openCreateTransactionDialog(item, suggestedRule.orElse(null)));

    Button matchBtn = new Button("Match to Existing", VaadinIcon.LINK.create());
    matchBtn.setWidthFull();
    matchBtn.addClickListener(e -> openMatchDialog(item));

    Button ignoreBtn = new Button("Ignore", VaadinIcon.BAN.create());
    ignoreBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
    ignoreBtn.setWidthFull();
    ignoreBtn.addClickListener(e -> ignoreItem(item));

    VerticalLayout actionsLayout = new VerticalLayout(createTransactionBtn, matchBtn, ignoreBtn);
    actionsLayout.setPadding(false);
    actionsLayout.setSpacing(true);

    detailPanel.add(detailTitle, infoSection, actionsTitle, actionsLayout);
  }

  private HorizontalLayout createInfoRow(String label, String value) {
    Span labelSpan = new Span(label + ": ");
    labelSpan.getStyle().set("font-weight", "bold").set("min-width", "100px");

    Span valueSpan = new Span(value != null ? value : "-");

    HorizontalLayout row = new HorizontalLayout(labelSpan, valueSpan);
    row.setWidthFull();
    row.setSpacing(true);
    return row;
  }

  private void loadBankAccounts() {
    Company company = companyContextService.getCurrentCompany();
    List<Account> bankAccounts = bankImportService.findBankAccounts(company);
    bankAccountSelector.setItems(bankAccounts);

    if (!bankAccounts.isEmpty()) {
      bankAccountSelector.setValue(bankAccounts.get(0));
    }
  }

  private void loadFeedItems() {
    if (selectedBankAccount == null) {
      feedItemGrid.setItems();
      return;
    }

    List<BankFeedItem> items = bankImportService.findUnmatchedItems(selectedBankAccount);
    feedItemGrid.setItems(items);
  }

  private void openImportDialog() {
    if (selectedBankAccount == null) {
      Notification.show("Please select a bank account first", 3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
      return;
    }

    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Import Bank Statement");
    dialog.setWidth("500px");

    MemoryBuffer buffer = new MemoryBuffer();
    Upload upload = new Upload(buffer);
    upload.setAcceptedFileTypes(".qif", ".ofx", ".qfx", ".qbo", ".csv");
    upload.setMaxFiles(1);
    upload.setDropLabel(new Span("Drop file here or click to upload"));

    Span helpText = new Span("Supported formats: QIF, OFX, QFX, QBO, CSV");
    helpText.getStyle().set("color", "var(--lumo-secondary-text-color)");

    TextArea resultArea = new TextArea("Import Result");
    resultArea.setWidthFull();
    resultArea.setReadOnly(true);
    resultArea.setVisible(false);

    upload.addSucceededListener(
        event -> {
          try {
            String fileName = event.getFileName();
            InputStream inputStream = buffer.getInputStream();
            String content =
                new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));

            Company company = companyContextService.getCurrentCompany();
            BankStatementImport importRecord =
                bankImportService.importStatement(company, selectedBankAccount, fileName, content);

            resultArea.setValue(
                "Import successful!\n"
                    + "File: "
                    + fileName
                    + "\n"
                    + "Items imported: "
                    + importRecord.getTotalItems());
            resultArea.setVisible(true);

            loadFeedItems();

            Notification.show(
                    "Imported " + importRecord.getTotalItems() + " items",
                    3000,
                    Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

          } catch (Exception e) {
            resultArea.setValue("Import failed: " + e.getMessage());
            resultArea.setVisible(true);
            Notification.show(
                    "Import failed: " + e.getMessage(), 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
          }
        });

    upload.addFailedListener(
        event -> {
          resultArea.setValue("Upload failed: " + event.getReason().getMessage());
          resultArea.setVisible(true);
        });

    Button closeBtn = new Button("Close", e -> dialog.close());

    VerticalLayout content = new VerticalLayout(upload, helpText, resultArea);
    content.setPadding(false);

    dialog.add(content);
    dialog.getFooter().add(closeBtn);
    dialog.open();
  }

  private void openCreateTransactionDialog(BankFeedItem item, AllocationRule suggestedRule) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Create Transaction from Bank Item");
    dialog.setWidth("600px");

    Company company = companyContextService.getCurrentCompany();
    int securityLevel = companyContextService.getCurrentSecurityLevel();
    List<Account> accounts =
        accountService.findActiveByCompanyWithSecurityLevel(company, securityLevel);
    List<TaxCode> taxCodes = taxCodeService.findActiveByCompany(company);

    FormLayout form = new FormLayout();

    // Determine transaction type based on amount
    boolean isReceipt = item.isInflow();
    TransactionType type = isReceipt ? TransactionType.RECEIPT : TransactionType.PAYMENT;

    Span typeSpan = new Span("Type: " + type.name());
    typeSpan.getStyle().set("font-weight", "bold");

    DatePicker datePicker = new DatePicker("Date");
    datePicker.setValue(item.getPostedDate());
    datePicker.setRequired(true);

    TextField descriptionField = new TextField("Description");
    descriptionField.setValue(item.getDescription() != null ? item.getDescription() : "");
    descriptionField.setWidthFull();

    ComboBox<Account> accountCombo = new ComboBox<>("Allocate to Account");
    accountCombo.setItems(accounts);
    accountCombo.setItemLabelGenerator(a -> a.getCode() + " - " + a.getName());
    accountCombo.setRequired(true);
    accountCombo.setWidthFull();

    // Pre-select suggested account if available
    if (suggestedRule != null) {
      accountCombo.setValue(suggestedRule.getTargetAccount());
    }

    ComboBox<TaxCode> taxCodeCombo = new ComboBox<>("Tax Code");
    taxCodeCombo.setItems(taxCodes);
    taxCodeCombo.setItemLabelGenerator(TaxCode::getCode);
    taxCodeCombo.setClearButtonVisible(true);

    // Pre-select suggested tax code if available
    if (suggestedRule != null && suggestedRule.getTargetTaxCode() != null) {
      taxCodes.stream()
          .filter(tc -> tc.getCode().equals(suggestedRule.getTargetTaxCode()))
          .findFirst()
          .ifPresent(taxCodeCombo::setValue);
    }

    BigDecimalField amountField = new BigDecimalField("Amount");
    amountField.setValue(item.getAmount().abs());
    amountField.setReadOnly(true);

    form.add(typeSpan, datePicker, descriptionField, accountCombo, taxCodeCombo, amountField);
    form.setColspan(descriptionField, 2);

    Button createBtn = new Button("Create & Post");
    createBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    createBtn.addClickListener(
        e -> {
          if (accountCombo.isEmpty() || datePicker.isEmpty()) {
            Notification.show("Please fill in required fields", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }

          try {
            // Create the transaction
            Transaction transaction = new Transaction(company, type, datePicker.getValue());
            transaction.setDescription(descriptionField.getValue());

            BigDecimal amount = item.getAmount().abs();
            String taxCode =
                taxCodeCombo.getValue() != null ? taxCodeCombo.getValue().getCode() : null;

            // For a receipt: DR Bank, CR Income/Other
            // For a payment: CR Bank, DR Expense/Other
            TransactionLine bankLine =
                new TransactionLine(
                    selectedBankAccount, amount, isReceipt ? Direction.DEBIT : Direction.CREDIT);
            bankLine.setTaxCode(taxCode);
            bankLine.setMemo("Bank: " + item.getDescription());
            transaction.addLine(bankLine);

            TransactionLine allocLine =
                new TransactionLine(
                    accountCombo.getValue(),
                    amount,
                    isReceipt ? Direction.CREDIT : Direction.DEBIT);
            allocLine.setTaxCode(taxCode);
            allocLine.setMemo(item.getDescription());
            transaction.addLine(allocLine);

            // Save and post
            User currentUser = companyContextService.getCurrentUser();
            Transaction saved = transactionService.save(transaction);
            postingService.postTransaction(saved, currentUser);

            // Mark feed item as created with audit trail
            item.setMatchedTransaction(saved);
            item.setStatus(FeedItemStatus.CREATED);
            bankImportService.matchItem(
                item,
                saved,
                com.example.application.domain.ReconciliationMatch.MatchType.MANUAL,
                currentUser);

            Notification.show(
                    "Transaction created and posted", 3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            dialog.close();
            loadFeedItems();
            showEmptyDetailPanel();

          } catch (Exception ex) {
            Notification.show("Error: " + ex.getMessage(), 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
          }
        });

    Button cancelBtn = new Button("Cancel", e -> dialog.close());

    dialog.add(form);
    dialog.getFooter().add(cancelBtn, createBtn);
    dialog.open();
  }

  private void openMatchDialog(BankFeedItem item) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Match to Existing Transaction");
    dialog.setWidth("800px");
    dialog.setHeight("500px");

    Company company = companyContextService.getCurrentCompany();

    // Find candidate transactions for matching
    // Looking for transactions that affect the bank account with similar amounts
    List<Transaction> candidates =
        transactionService.findByCompany(company).stream()
            .filter(t -> t.isPosted())
            .filter(
                t ->
                    t.getLines().stream()
                        .anyMatch(l -> l.getAccount().getId().equals(selectedBankAccount.getId())))
            .filter(
                t -> {
                  // Find transactions with similar amounts
                  BigDecimal targetAmount = item.getAmount().abs();
                  for (TransactionLine line : t.getLines()) {
                    if (line.getAccount().getId().equals(selectedBankAccount.getId())) {
                      if (line.getAmount().abs().compareTo(targetAmount) == 0) {
                        return true;
                      }
                    }
                  }
                  return false;
                })
            .toList();

    Grid<Transaction> candidateGrid = new Grid<>();
    candidateGrid.setSizeFull();
    candidateGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

    candidateGrid
        .addColumn(t -> t.getTransactionDate().format(DATE_FORMAT))
        .setHeader("Date")
        .setSortable(true)
        .setAutoWidth(true);

    candidateGrid.addColumn(t -> t.getType().name()).setHeader("Type").setAutoWidth(true);

    candidateGrid.addColumn(Transaction::getDescription).setHeader("Description").setFlexGrow(1);

    candidateGrid
        .addColumn(
            t -> {
              BigDecimal total = BigDecimal.ZERO;
              for (TransactionLine line : t.getLines()) {
                if (line.isDebit()) {
                  total = total.add(line.getAmount());
                }
              }
              return "$" + total.setScale(2).toPlainString();
            })
        .setHeader("Amount")
        .setAutoWidth(true);

    candidateGrid.setItems(candidates);

    Span info =
        new Span(
            "Select a transaction to match with bank item: "
                + item.getPostedDate().format(DATE_FORMAT)
                + " / "
                + formatAmount(item));

    Button matchBtn = new Button("Match Selected");
    matchBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    matchBtn.setEnabled(false);

    candidateGrid.addSelectionListener(
        e -> {
          matchBtn.setEnabled(e.getFirstSelectedItem().isPresent());
        });

    matchBtn.addClickListener(
        e -> {
          Optional<Transaction> selected = candidateGrid.asSingleSelect().getOptionalValue();
          if (selected.isPresent()) {
            try {
              User currentUser = companyContextService.getCurrentUser();
              bankImportService.matchItem(
                  item,
                  selected.get(),
                  com.example.application.domain.ReconciliationMatch.MatchType.MANUAL,
                  currentUser);

              Notification.show(
                      "Item matched successfully", 3000, Notification.Position.BOTTOM_START)
                  .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

              dialog.close();
              loadFeedItems();
              showEmptyDetailPanel();

            } catch (Exception ex) {
              Notification.show("Error: " + ex.getMessage(), 3000, Notification.Position.MIDDLE)
                  .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
          }
        });

    Button cancelBtn = new Button("Cancel", e -> dialog.close());

    VerticalLayout content = new VerticalLayout(info, candidateGrid);
    content.setSizeFull();
    content.setPadding(false);
    content.setFlexGrow(1, candidateGrid);

    dialog.add(content);
    dialog.getFooter().add(cancelBtn, matchBtn);
    dialog.open();
  }

  private void ignoreItem(BankFeedItem item) {
    try {
      bankImportService.ignoreItem(item);

      Notification.show("Item ignored", 3000, Notification.Position.BOTTOM_START)
          .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

      loadFeedItems();
      showEmptyDetailPanel();

    } catch (Exception e) {
      Notification.show("Error: " + e.getMessage(), 3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
  }
}
