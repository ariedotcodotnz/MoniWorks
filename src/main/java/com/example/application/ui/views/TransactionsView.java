package com.example.application.ui.views;

import com.example.application.domain.*;
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
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * View for managing transactions (Payments, Receipts, Journals).
 * Supports creating, viewing, posting, and reversing transactions.
 */
@Route(value = "transactions", layout = MainLayout.class)
@PageTitle("Transactions | MoniWorks")
@PermitAll
public class TransactionsView extends VerticalLayout {

    private final TransactionService transactionService;
    private final PostingService postingService;
    private final AccountService accountService;
    private final TaxCodeService taxCodeService;
    private final CompanyContextService companyContextService;

    private final Grid<Transaction> grid = new Grid<>();
    private final ComboBox<TransactionType> typeFilter = new ComboBox<>();
    private final ComboBox<Transaction.Status> statusFilter = new ComboBox<>();

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public TransactionsView(TransactionService transactionService,
                            PostingService postingService,
                            AccountService accountService,
                            TaxCodeService taxCodeService,
                            CompanyContextService companyContextService) {
        this.transactionService = transactionService;
        this.postingService = postingService;
        this.accountService = accountService;
        this.taxCodeService = taxCodeService;
        this.companyContextService = companyContextService;

        addClassName("transactions-view");
        setSizeFull();

        configureGrid();
        add(createToolbar(), grid);

        loadTransactions();
    }

    private void configureGrid() {
        grid.addClassNames("transactions-grid");
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        grid.addColumn(t -> t.getTransactionDate().format(DATE_FORMAT))
            .setHeader("Date")
            .setSortable(true)
            .setAutoWidth(true);

        grid.addColumn(t -> t.getType().name())
            .setHeader("Type")
            .setSortable(true)
            .setAutoWidth(true);

        grid.addColumn(Transaction::getReference)
            .setHeader("Reference")
            .setAutoWidth(true);

        grid.addColumn(Transaction::getDescription)
            .setHeader("Description")
            .setFlexGrow(1);

        grid.addColumn(this::calculateTotal)
            .setHeader("Amount")
            .setAutoWidth(true);

        grid.addColumn(t -> t.getStatus().name())
            .setHeader("Status")
            .setSortable(true)
            .setAutoWidth(true);

        grid.addComponentColumn(this::createActionButtons)
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
            postBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS);
            postBtn.addClickListener(e -> postTransaction(transaction));
            postBtn.getElement().setAttribute("title", "Post");
            actions.add(postBtn);

            Button deleteBtn = new Button(VaadinIcon.TRASH.create());
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
            deleteBtn.addClickListener(e -> deleteTransaction(transaction));
            deleteBtn.getElement().setAttribute("title", "Delete");
            actions.add(deleteBtn);
        } else {
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

        Button paymentBtn = new Button("Payment", VaadinIcon.MINUS.create());
        paymentBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        paymentBtn.addClickListener(e -> openNewTransactionDialog(TransactionType.PAYMENT));

        Button receiptBtn = new Button("Receipt", VaadinIcon.PLUS.create());
        receiptBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        receiptBtn.addClickListener(e -> openNewTransactionDialog(TransactionType.RECEIPT));

        Button journalBtn = new Button("Journal", VaadinIcon.BOOK.create());
        journalBtn.addClickListener(e -> openNewTransactionDialog(TransactionType.JOURNAL));

        Button refreshBtn = new Button(VaadinIcon.REFRESH.create());
        refreshBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        refreshBtn.addClickListener(e -> loadTransactions());
        refreshBtn.getElement().setAttribute("title", "Refresh");

        HorizontalLayout filters = new HorizontalLayout(typeFilter, statusFilter);
        filters.setAlignItems(FlexComponent.Alignment.BASELINE);

        HorizontalLayout buttons = new HorizontalLayout(paymentBtn, receiptBtn, journalBtn, refreshBtn);
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
            transactions = transactions.stream()
                .filter(t -> t.getType() == typeFilter.getValue())
                .toList();
        }
        if (statusFilter.getValue() != null) {
            transactions = transactions.stream()
                .filter(t -> t.getStatus() == statusFilter.getValue())
                .toList();
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
        List<Account> accounts = accountService.findActiveByCompany(company);
        List<TaxCode> taxCodes = taxCodeService.findActiveByCompany(company);

        // Header form
        FormLayout headerForm = new FormLayout();

        DatePicker datePicker = new DatePicker("Date");
        datePicker.setValue(transaction.getTransactionDate() != null ?
            transaction.getTransactionDate() : LocalDate.now());
        datePicker.setRequired(true);

        TextField descriptionField = new TextField("Description");
        descriptionField.setValue(transaction.getDescription() != null ?
            transaction.getDescription() : "");
        descriptionField.setWidthFull();

        TextField referenceField = new TextField("Reference");
        referenceField.setValue(transaction.getReference() != null ?
            transaction.getReference() : "");

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

        linesGrid.addColumn(le -> le.account != null ? le.account.getCode() : "")
            .setHeader("Account")
            .setAutoWidth(true);
        linesGrid.addColumn(le -> le.account != null ? le.account.getName() : "")
            .setHeader("Name")
            .setFlexGrow(1);
        linesGrid.addColumn(le -> le.direction != null ? le.direction.name() : "")
            .setHeader("DR/CR")
            .setAutoWidth(true);
        linesGrid.addColumn(le -> le.amount != null ? le.amount.toPlainString() : "")
            .setHeader("Amount")
            .setAutoWidth(true);
        linesGrid.addColumn(le -> le.taxCode)
            .setHeader("Tax")
            .setAutoWidth(true);
        linesGrid.addColumn(le -> le.memo)
            .setHeader("Memo")
            .setAutoWidth(true);
        linesGrid.addComponentColumn(le -> {
            Button removeBtn = new Button(VaadinIcon.CLOSE_SMALL.create());
            removeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
            removeBtn.addClickListener(e -> {
                lineEntries.remove(le);
                linesGrid.setItems(lineEntries);
                updateBalance(lineEntries, balanceSpan);
            });
            return removeBtn;
        }).setHeader("").setAutoWidth(true).setFlexGrow(0);

        linesGrid.setItems(lineEntries);

        // Add line form
        HorizontalLayout addLineForm = new HorizontalLayout();
        addLineForm.setWidthFull();
        addLineForm.setAlignItems(FlexComponent.Alignment.END);

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

        TextField memoField = new TextField("Memo");
        memoField.setWidth("200px");

        Button addLineBtn = new Button("Add", VaadinIcon.PLUS.create());
        addLineBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addLineBtn.addClickListener(e -> {
            if (accountCombo.isEmpty() || directionCombo.isEmpty() || amountField.isEmpty()) {
                Notification.show("Please fill in account, direction, and amount",
                    3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            LineEntry newLine = new LineEntry();
            newLine.account = accountCombo.getValue();
            newLine.direction = directionCombo.getValue();
            newLine.amount = amountField.getValue();
            newLine.taxCode = taxCodeCombo.getValue() != null ?
                taxCodeCombo.getValue().getCode() : null;
            newLine.memo = memoField.getValue();

            lineEntries.add(newLine);
            linesGrid.setItems(lineEntries);
            updateBalance(lineEntries, balanceSpan);

            // Clear fields for next entry
            accountCombo.clear();
            directionCombo.clear();
            amountField.clear();
            taxCodeCombo.clear();
            memoField.clear();
            accountCombo.focus();
        });

        addLineForm.add(accountCombo, directionCombo, amountField, taxCodeCombo, memoField, addLineBtn);

        updateBalance(lineEntries, balanceSpan);

        // Footer buttons
        Button saveBtn = new Button("Save as Draft");
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveBtn.addClickListener(e -> {
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
                transaction.setReference(referenceField.getValue().isBlank() ?
                    null : referenceField.getValue());

                // Update lines
                transaction.getLines().clear();
                for (LineEntry le : lineEntries) {
                    TransactionLine line = new TransactionLine(
                        le.account, le.amount, le.direction);
                    line.setTaxCode(le.taxCode);
                    line.setMemo(le.memo);
                    transaction.addLine(line);
                }

                transactionService.save(transaction);

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
        saveAndPostBtn.addClickListener(e -> {
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
                Notification.show("Transaction is unbalanced by " + balance,
                    3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                transaction.setTransactionDate(datePicker.getValue());
                transaction.setDescription(descriptionField.getValue());
                transaction.setReference(referenceField.getValue().isBlank() ?
                    null : referenceField.getValue());

                // Update lines
                transaction.getLines().clear();
                for (LineEntry le : lineEntries) {
                    TransactionLine line = new TransactionLine(
                        le.account, le.amount, le.direction);
                    line.setTaxCode(le.taxCode);
                    line.setMemo(le.memo);
                    transaction.addLine(line);
                }

                Transaction saved = transactionService.save(transaction);
                postingService.postTransaction(saved, null); // TODO: get current user

                Notification.show("Transaction posted successfully", 3000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                loadTransactions();
            } catch (IllegalStateException ex) {
                Notification.show(ex.getMessage(), 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        Button cancelBtn = new Button("Cancel", e -> dialog.close());

        VerticalLayout content = new VerticalLayout(
            headerForm,
            linesTitle,
            linesGrid,
            addLineForm,
            balanceSpan
        );
        content.setPadding(false);
        content.setSpacing(true);

        dialog.add(content);
        dialog.getFooter().add(cancelBtn, saveBtn, saveAndPostBtn);
        dialog.open();
    }

    private void updateBalance(List<LineEntry> lines, Span balanceSpan) {
        BigDecimal balance = calculateLineBalance(lines);
        if (balance.compareTo(BigDecimal.ZERO) == 0) {
            balanceSpan.setText("Balance: $0.00 (Balanced)");
            balanceSpan.getStyle().set("color", "var(--lumo-success-text-color)");
        } else {
            balanceSpan.setText("Balance: $" + balance.abs().setScale(2).toPlainString() +
                (balance.compareTo(BigDecimal.ZERO) > 0 ? " DR" : " CR"));
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
        dialog.setHeaderTitle(transaction.getType().name() + " - " +
            transaction.getTransactionDate().format(DATE_FORMAT));
        dialog.setWidth("700px");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);

        content.add(new Span("Status: " + transaction.getStatus().name()));
        content.add(new Span("Reference: " + (transaction.getReference() != null ?
            transaction.getReference() : "-")));
        content.add(new Span("Description: " + (transaction.getDescription() != null ?
            transaction.getDescription() : "-")));

        H3 linesTitle = new H3("Lines");
        content.add(linesTitle);

        Grid<TransactionLine> linesGrid = new Grid<>();
        linesGrid.setHeight("200px");
        linesGrid.addThemeVariants(GridVariant.LUMO_COMPACT);

        linesGrid.addColumn(l -> l.getAccount().getCode())
            .setHeader("Account");
        linesGrid.addColumn(l -> l.getAccount().getName())
            .setHeader("Name")
            .setFlexGrow(1);
        linesGrid.addColumn(l -> l.getDirection().name())
            .setHeader("DR/CR");
        linesGrid.addColumn(l -> l.getAmount().toPlainString())
            .setHeader("Amount");
        linesGrid.addColumn(TransactionLine::getTaxCode)
            .setHeader("Tax");
        linesGrid.addColumn(TransactionLine::getMemo)
            .setHeader("Memo");

        linesGrid.setItems(transaction.getLines());
        content.add(linesGrid);

        Button closeBtn = new Button("Close", e -> dialog.close());

        dialog.add(content);
        dialog.getFooter().add(closeBtn);
        dialog.open();
    }

    private void postTransaction(Transaction transaction) {
        try {
            postingService.postTransaction(transaction, null); // TODO: get current user
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
        reverseBtn.addClickListener(e -> {
            try {
                postingService.reverseTransaction(transaction, null, reasonField.getValue());
                Notification.show("Transaction reversed successfully", 3000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                loadTransactions();
            } catch (IllegalStateException ex) {
                Notification.show(ex.getMessage(), 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        Button cancelBtn = new Button("Cancel", e -> dialog.close());

        dialog.add(new Span("This will create a new reversal transaction with inverted debits/credits."));
        dialog.add(reasonField);
        dialog.getFooter().add(cancelBtn, reverseBtn);
        dialog.open();
    }

    /**
     * Helper class to hold line entry data in the UI before saving.
     */
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
}
