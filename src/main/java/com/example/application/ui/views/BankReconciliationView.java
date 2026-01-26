package com.example.application.ui.views;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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

    // Check for suggested allocation - now includes amount range matching per spec 05
    Company company = companyContextService.getCurrentCompany();
    Optional<AllocationRule> suggestedRule =
        bankImportService.findMatchingRule(company, item.getDescription(), item.getAmount());

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

    // Action buttons - different depending on whether item is matched or not
    H4 actionsTitle = new H4("Actions");
    VerticalLayout actionsLayout = new VerticalLayout();
    actionsLayout.setPadding(false);
    actionsLayout.setSpacing(true);

    boolean isMatched =
        item.getStatus() == FeedItemStatus.MATCHED
            || item.getStatus() == FeedItemStatus.CREATED
            || item.getStatus() == FeedItemStatus.SPLIT
            || item.getMatchedTransaction() != null;

    if (isMatched) {
      // Show matched transaction info
      Transaction matched = item.getMatchedTransaction();
      if (matched != null) {
        infoSection.add(
            createInfoRow(
                "Matched To",
                matched.getType().name()
                    + " #"
                    + matched.getId()
                    + " - "
                    + matched.getDescription()));
      }

      // Unmatch button for matched items
      Button unmatchBtn = new Button("Unmatch", VaadinIcon.UNLINK.create());
      unmatchBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
      unmatchBtn.setWidthFull();
      unmatchBtn.addClickListener(e -> unmatchItem(item));
      actionsLayout.add(unmatchBtn);

    } else if (item.getStatus() == FeedItemStatus.IGNORED) {
      // Un-ignore button for ignored items
      Button unignoreBtn = new Button("Un-ignore (Make New)", VaadinIcon.REFRESH.create());
      unignoreBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
      unignoreBtn.setWidthFull();
      unignoreBtn.addClickListener(e -> unignoreItem(item));
      actionsLayout.add(unignoreBtn);

    } else {
      // Normal actions for new items
      Button createTransactionBtn = new Button("Create Transaction", VaadinIcon.PLUS.create());
      createTransactionBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
      createTransactionBtn.setWidthFull();
      createTransactionBtn.addClickListener(
          e -> openCreateTransactionDialog(item, suggestedRule.orElse(null)));

      Button splitBtn = new Button("Split Across Accounts", VaadinIcon.SPLIT.create());
      splitBtn.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
      splitBtn.setWidthFull();
      splitBtn.addClickListener(e -> openSplitTransactionDialog(item));

      Button matchBtn = new Button("Match to Existing", VaadinIcon.LINK.create());
      matchBtn.setWidthFull();
      matchBtn.addClickListener(e -> openMatchDialog(item));

      Button ignoreBtn = new Button("Ignore", VaadinIcon.BAN.create());
      ignoreBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
      ignoreBtn.setWidthFull();
      ignoreBtn.addClickListener(e -> ignoreItem(item));

      actionsLayout.add(createTransactionBtn, splitBtn, matchBtn, ignoreBtn);
    }

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
    // Apply memo template if available, otherwise use original description
    String initialDescription = item.getDescription() != null ? item.getDescription() : "";
    if (suggestedRule != null && suggestedRule.getMemoTemplate() != null) {
      initialDescription = suggestedRule.applyMemoTemplate(item.getDescription(), item.getAmount());
    }
    descriptionField.setValue(initialDescription);
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

    // Configuration for matching criteria per spec 05: "match by amount/date range/description
    // similarity"
    int dateRangeDays = 7; // Transactions within ±7 days of bank item date

    // Find candidate transactions for matching
    // Per spec 05 line 17: match by amount, date range, and description similarity
    List<Transaction> candidates =
        transactionService.findByCompany(company).stream()
            .filter(t -> t.isPosted())
            .filter(
                t ->
                    t.getLines().stream()
                        .anyMatch(l -> l.getAccount().getId().equals(selectedBankAccount.getId())))
            .filter(
                t -> {
                  // Match by amount: exact amount matching on bank account line
                  BigDecimal targetAmount = item.getAmount().abs();
                  boolean amountMatches = false;
                  for (TransactionLine line : t.getLines()) {
                    if (line.getAccount().getId().equals(selectedBankAccount.getId())) {
                      if (line.getAmount().abs().compareTo(targetAmount) == 0) {
                        amountMatches = true;
                        break;
                      }
                    }
                  }
                  if (!amountMatches) {
                    return false;
                  }

                  // Match by date range: transaction date within ±N days of bank item posted date
                  LocalDate bankDate = item.getPostedDate();
                  LocalDate txDate = t.getTransactionDate();
                  long daysDiff = Math.abs(ChronoUnit.DAYS.between(bankDate, txDate));
                  if (daysDiff > dateRangeDays) {
                    return false;
                  }

                  return true;
                })
            .toList();

    // If no exact matches found, also search by description similarity (broader search)
    // This supports spec 05 "description similarity" matching
    if (candidates.isEmpty()) {
      String bankDesc =
          item.getDescription() != null ? item.getDescription().toLowerCase().trim() : "";
      candidates =
          transactionService.findByCompany(company).stream()
              .filter(t -> t.isPosted())
              .filter(
                  t ->
                      t.getLines().stream()
                          .anyMatch(
                              l -> l.getAccount().getId().equals(selectedBankAccount.getId())))
              .filter(
                  t -> {
                    // Match by date range only for description similarity search
                    LocalDate bankDate = item.getPostedDate();
                    LocalDate txDate = t.getTransactionDate();
                    long daysDiff = Math.abs(ChronoUnit.DAYS.between(bankDate, txDate));
                    if (daysDiff > dateRangeDays * 2) { // Allow wider date range for fuzzy matches
                      return false;
                    }

                    // Description similarity: check if transaction description contains any
                    // significant
                    // words from bank description (or vice versa)
                    if (bankDesc.isEmpty()) {
                      return false;
                    }
                    String txDesc =
                        t.getDescription() != null ? t.getDescription().toLowerCase().trim() : "";
                    if (txDesc.isEmpty()) {
                      return false;
                    }

                    // Simple similarity: check if descriptions share significant words (3+ chars)
                    String[] bankWords = bankDesc.split("\\s+");
                    for (String word : bankWords) {
                      if (word.length() >= 3 && txDesc.contains(word)) {
                        return true;
                      }
                    }

                    // Also check reverse direction
                    String[] txWords = txDesc.split("\\s+");
                    for (String word : txWords) {
                      if (word.length() >= 3 && bankDesc.contains(word)) {
                        return true;
                      }
                    }

                    return false;
                  })
              .toList();
    }

    // Capture for use in lambda
    final BigDecimal bankAmount = item.getAmount().abs();
    final LocalDate bankItemDate = item.getPostedDate();
    final String finalBankDesc =
        item.getDescription() != null ? item.getDescription().toLowerCase().trim() : "";

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

    // Match Score column - helps users understand why this transaction was suggested
    candidateGrid
        .addColumn(
            t -> {
              StringBuilder matchReason = new StringBuilder();

              // Check amount match
              boolean amountMatches = false;
              for (TransactionLine line : t.getLines()) {
                if (line.getAccount().getId().equals(selectedBankAccount.getId())) {
                  if (line.getAmount().abs().compareTo(bankAmount) == 0) {
                    amountMatches = true;
                    break;
                  }
                }
              }
              if (amountMatches) {
                matchReason.append("Amount");
              }

              // Check date proximity
              long daysDiff =
                  Math.abs(ChronoUnit.DAYS.between(bankItemDate, t.getTransactionDate()));
              if (daysDiff == 0) {
                if (matchReason.length() > 0) matchReason.append(" + ");
                matchReason.append("Same Date");
              } else if (daysDiff <= 3) {
                if (matchReason.length() > 0) matchReason.append(" + ");
                matchReason.append("±").append(daysDiff).append("d");
              }

              // Check description similarity
              String txDesc =
                  t.getDescription() != null ? t.getDescription().toLowerCase().trim() : "";
              if (!finalBankDesc.isEmpty() && !txDesc.isEmpty()) {
                // Check for shared significant words
                boolean descMatches = false;
                String[] bankWords = finalBankDesc.split("\\s+");
                for (String word : bankWords) {
                  if (word.length() >= 3 && txDesc.contains(word)) {
                    descMatches = true;
                    break;
                  }
                }
                if (descMatches) {
                  if (matchReason.length() > 0) matchReason.append(" + ");
                  matchReason.append("Desc");
                }
              }

              return matchReason.length() > 0 ? matchReason.toString() : "—";
            })
        .setHeader("Match")
        .setAutoWidth(true);

    candidateGrid.setItems(candidates);

    Span info =
        new Span(
            "Select a transaction to match with bank item: "
                + item.getPostedDate().format(DATE_FORMAT)
                + " / "
                + formatAmount(item)
                + " — Candidates matched by amount, date range (±7 days), or description similarity");

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

  private void unmatchItem(BankFeedItem item) {
    try {
      User currentUser = companyContextService.getCurrentUser();
      bankImportService.unmatchItem(item, currentUser);

      Notification.show("Item unmatched successfully", 3000, Notification.Position.BOTTOM_START)
          .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

      loadFeedItems();
      showEmptyDetailPanel();

    } catch (Exception e) {
      Notification.show("Error: " + e.getMessage(), 3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
  }

  private void unignoreItem(BankFeedItem item) {
    try {
      bankImportService.unignoreItem(item);

      Notification.show("Item status reset to NEW", 3000, Notification.Position.BOTTOM_START)
          .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

      loadFeedItems();
      showEmptyDetailPanel();

    } catch (Exception e) {
      Notification.show("Error: " + e.getMessage(), 3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
  }

  /**
   * Opens a dialog to split a bank feed item across multiple accounts. This allows users to
   * allocate a single bank transaction to multiple expense/income accounts, which is useful for
   * transactions like utility bills that need to be split between cost centers.
   *
   * <p>Per spec 05: "Actions: match, split, create transaction, ignore"
   */
  private void openSplitTransactionDialog(BankFeedItem item) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Split Transaction Across Accounts");
    dialog.setWidth("800px");
    dialog.setHeight("600px");

    Company company = companyContextService.getCurrentCompany();
    int securityLevel = companyContextService.getCurrentSecurityLevel();
    List<Account> accounts =
        accountService.findActiveByCompanyWithSecurityLevel(company, securityLevel);
    List<TaxCode> taxCodes = taxCodeService.findActiveByCompany(company);

    // Transaction header info
    boolean isReceipt = item.isInflow();
    TransactionType type = isReceipt ? TransactionType.RECEIPT : TransactionType.PAYMENT;
    BigDecimal totalAmount = item.getAmount().abs();

    FormLayout headerForm = new FormLayout();

    Span typeSpan = new Span("Type: " + type.name());
    typeSpan.getStyle().set("font-weight", "bold");

    DatePicker datePicker = new DatePicker("Date");
    datePicker.setValue(item.getPostedDate());
    datePicker.setRequired(true);

    TextField descriptionField = new TextField("Description");
    descriptionField.setValue(item.getDescription() != null ? item.getDescription() : "");
    descriptionField.setWidthFull();

    Span totalAmountSpan = new Span("Total Amount: $" + totalAmount.setScale(2).toPlainString());
    totalAmountSpan.getStyle().set("font-weight", "bold").set("font-size", "1.1em");

    headerForm.add(typeSpan, datePicker, descriptionField, totalAmountSpan);
    headerForm.setColspan(descriptionField, 2);

    // Split allocations grid
    H4 allocationsTitle = new H4("Allocations");

    // Use a simple data holder for allocation rows
    List<SplitAllocationRow> allocationRows = new ArrayList<>();

    Grid<SplitAllocationRow> allocationsGrid = new Grid<>();
    allocationsGrid.setHeight("200px");
    allocationsGrid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);

    allocationsGrid
        .addColumn(
            row -> row.account != null ? row.account.getCode() + " - " + row.account.getName() : "")
        .setHeader("Account")
        .setFlexGrow(2);

    allocationsGrid
        .addColumn(row -> row.amount != null ? "$" + row.amount.setScale(2).toPlainString() : "")
        .setHeader("Amount")
        .setAutoWidth(true);

    allocationsGrid
        .addColumn(row -> row.taxCode != null ? row.taxCode : "")
        .setHeader("Tax Code")
        .setAutoWidth(true);

    allocationsGrid
        .addColumn(row -> row.memo != null ? row.memo : "")
        .setHeader("Memo")
        .setFlexGrow(1);

    allocationsGrid
        .addComponentColumn(
            row -> {
              Button removeBtn = new Button(VaadinIcon.TRASH.create());
              removeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
              removeBtn.addClickListener(
                  e -> {
                    allocationRows.remove(row);
                    allocationsGrid.setItems(allocationRows);
                    updateRemainingAmount(allocationRows, totalAmount, dialog);
                  });
              return removeBtn;
            })
        .setHeader("")
        .setAutoWidth(true);

    // Input form for adding new allocations
    HorizontalLayout addAllocationForm = new HorizontalLayout();
    addAllocationForm.setWidthFull();
    addAllocationForm.setAlignItems(FlexComponent.Alignment.END);

    ComboBox<Account> accountCombo = new ComboBox<>("Account");
    accountCombo.setItems(accounts);
    accountCombo.setItemLabelGenerator(a -> a.getCode() + " - " + a.getName());
    accountCombo.setWidth("250px");

    BigDecimalField amountField = new BigDecimalField("Amount");
    amountField.setWidth("120px");

    ComboBox<TaxCode> taxCodeCombo = new ComboBox<>("Tax Code");
    taxCodeCombo.setItems(taxCodes);
    taxCodeCombo.setItemLabelGenerator(TaxCode::getCode);
    taxCodeCombo.setClearButtonVisible(true);
    taxCodeCombo.setWidth("100px");

    TextField memoField = new TextField("Memo");
    memoField.setWidth("150px");

    Button addBtn = new Button("Add", VaadinIcon.PLUS.create());
    addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    addBtn.addClickListener(
        e -> {
          if (accountCombo.isEmpty() || amountField.isEmpty() || amountField.getValue() == null) {
            Notification.show("Account and Amount are required", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }

          BigDecimal amount = amountField.getValue();
          if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            Notification.show("Amount must be positive", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }

          SplitAllocationRow row = new SplitAllocationRow();
          row.account = accountCombo.getValue();
          row.amount = amount;
          row.taxCode = taxCodeCombo.getValue() != null ? taxCodeCombo.getValue().getCode() : null;
          row.memo = memoField.getValue();

          allocationRows.add(row);
          allocationsGrid.setItems(allocationRows);
          updateRemainingAmount(allocationRows, totalAmount, dialog);

          // Clear inputs for next entry
          accountCombo.clear();
          amountField.clear();
          taxCodeCombo.clear();
          memoField.clear();
          accountCombo.focus();
        });

    addAllocationForm.add(accountCombo, amountField, taxCodeCombo, memoField, addBtn);

    // Remaining amount indicator
    Span remainingAmountSpan = new Span();
    remainingAmountSpan.setId("remaining-amount");
    updateRemainingAmountSpan(remainingAmountSpan, totalAmount, BigDecimal.ZERO);

    // Quick split button for even distribution
    Button evenSplitBtn = new Button("Split Remaining Evenly");
    evenSplitBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    evenSplitBtn.addClickListener(
        e -> {
          if (accountCombo.isEmpty()) {
            Notification.show("Select an account first", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
          }

          BigDecimal allocated =
              allocationRows.stream()
                  .map(row -> row.amount)
                  .reduce(BigDecimal.ZERO, BigDecimal::add);
          BigDecimal remaining = totalAmount.subtract(allocated);

          if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            amountField.setValue(remaining);
          }
        });

    HorizontalLayout remainingLayout = new HorizontalLayout(remainingAmountSpan, evenSplitBtn);
    remainingLayout.setAlignItems(FlexComponent.Alignment.CENTER);
    remainingLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
    remainingLayout.setWidthFull();

    // Create button
    Button createBtn = new Button("Create & Post Split Transaction");
    createBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    createBtn.addClickListener(
        e -> {
          if (allocationRows.isEmpty()) {
            Notification.show("Add at least one allocation", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }

          BigDecimal allocated =
              allocationRows.stream()
                  .map(row -> row.amount)
                  .reduce(BigDecimal.ZERO, BigDecimal::add);

          if (allocated.compareTo(totalAmount) != 0) {
            Notification.show(
                    String.format(
                        "Allocations ($%s) must equal total ($%s)",
                        allocated.setScale(2), totalAmount.setScale(2)),
                    3000,
                    Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }

          try {
            // Convert rows to SplitAllocation records
            List<BankImportService.SplitAllocation> allocations =
                allocationRows.stream()
                    .map(
                        row ->
                            new BankImportService.SplitAllocation(
                                row.account, row.amount, row.taxCode, row.memo))
                    .toList();

            // Create the split transaction
            User currentUser = companyContextService.getCurrentUser();
            Transaction transaction =
                bankImportService.splitItem(
                    item,
                    selectedBankAccount,
                    allocations,
                    datePicker.getValue(),
                    descriptionField.getValue(),
                    currentUser);

            // Save and post the transaction
            Transaction saved = transactionService.save(transaction);
            postingService.postTransaction(saved, currentUser);

            // Update the bank feed item reference to the saved transaction
            item.setMatchedTransaction(saved);

            // Reconcile the ledger entries
            bankImportService.reconcileSplitTransaction(
                item, saved, selectedBankAccount, currentUser);

            Notification.show(
                    "Split transaction created with " + allocations.size() + " allocations",
                    3000,
                    Notification.Position.BOTTOM_START)
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

    VerticalLayout content =
        new VerticalLayout(
            headerForm,
            new Hr(),
            allocationsTitle,
            allocationsGrid,
            addAllocationForm,
            remainingLayout);
    content.setSizeFull();
    content.setPadding(false);
    content.setSpacing(true);

    dialog.add(content);
    dialog.getFooter().add(cancelBtn, createBtn);
    dialog.open();
  }

  /** Helper class to hold split allocation row data. */
  private static class SplitAllocationRow {
    Account account;
    BigDecimal amount;
    String taxCode;
    String memo;
  }

  private void updateRemainingAmount(
      List<SplitAllocationRow> rows, BigDecimal total, Dialog dialog) {
    BigDecimal allocated =
        rows.stream().map(row -> row.amount).reduce(BigDecimal.ZERO, BigDecimal::add);

    dialog
        .getElement()
        .executeJs(
            "const span = document.getElementById('remaining-amount'); "
                + "if (span) { span.textContent = arguments[0]; "
                + "span.style.color = arguments[1]; }",
            formatRemainingText(total, allocated),
            allocated.compareTo(total) == 0
                ? "var(--lumo-success-text-color)"
                : "var(--lumo-primary-text-color)");
  }

  private void updateRemainingAmountSpan(Span span, BigDecimal total, BigDecimal allocated) {
    span.setText(formatRemainingText(total, allocated));
    if (allocated.compareTo(total) == 0) {
      span.getStyle().set("color", "var(--lumo-success-text-color)");
    } else {
      span.getStyle().set("color", "var(--lumo-primary-text-color)");
    }
  }

  private String formatRemainingText(BigDecimal total, BigDecimal allocated) {
    BigDecimal remaining = total.subtract(allocated);
    return String.format(
        "Allocated: $%s / $%s (Remaining: $%s)",
        allocated.setScale(2).toPlainString(),
        total.setScale(2).toPlainString(),
        remaining.setScale(2).toPlainString());
  }
}
