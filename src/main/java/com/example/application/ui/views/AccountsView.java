package com.example.application.ui.views;

import java.util.List;

import com.example.application.domain.Account;
import com.example.application.domain.Account.AccountType;
import com.example.application.domain.Company;
import com.example.application.domain.TaxCode;
import com.example.application.service.AccountService;
import com.example.application.service.CompanyContextService;
import com.example.application.service.TaxCodeService;
import com.example.application.ui.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.data.provider.hierarchy.TreeData;
import com.vaadin.flow.data.provider.hierarchy.TreeDataProvider;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.PermitAll;

/**
 * View for managing the Chart of Accounts. Displays accounts in a hierarchical tree grid with CRUD
 * operations.
 */
@Route(value = "accounts", layout = MainLayout.class)
@PageTitle("Chart of Accounts | MoniWorks")
@PermitAll
public class AccountsView extends VerticalLayout {

  private final AccountService accountService;
  private final CompanyContextService companyContextService;
  private final TaxCodeService taxCodeService;

  private final TreeGrid<Account> grid = new TreeGrid<>();
  private final TextField searchField = new TextField();
  private TreeDataProvider<Account> dataProvider;

  public AccountsView(
      AccountService accountService,
      CompanyContextService companyContextService,
      TaxCodeService taxCodeService) {
    this.accountService = accountService;
    this.companyContextService = companyContextService;
    this.taxCodeService = taxCodeService;

    addClassName("accounts-view");
    setSizeFull();

    configureGrid();
    add(createToolbar(), grid);

    loadAccounts();
  }

  private void configureGrid() {
    grid.addClassNames("accounts-grid");
    grid.setSizeFull();
    grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

    // Hierarchical column for account code
    grid.addHierarchyColumn(Account::getCode)
        .setHeader("Code")
        .setSortable(true)
        .setAutoWidth(true)
        .setFlexGrow(0);

    grid.addColumn(Account::getName).setHeader("Name").setSortable(true).setFlexGrow(1);

    grid.addColumn(Account::getAltCode).setHeader("Alt Code").setSortable(true).setAutoWidth(true);

    grid.addColumn(account -> account.getType().name())
        .setHeader("Type")
        .setSortable(true)
        .setAutoWidth(true);

    grid.addColumn(Account::getTaxDefaultCode).setHeader("Tax Code").setAutoWidth(true);

    grid.addColumn(account -> account.isBankAccount() ? "Bank" : "")
        .setHeader("Bank")
        .setAutoWidth(true);

    grid.addColumn(account -> account.isActive() ? "Active" : "Inactive")
        .setHeader("Status")
        .setAutoWidth(true);

    // Action column
    grid.addComponentColumn(account -> createActionButtons(account))
        .setHeader("Actions")
        .setAutoWidth(true)
        .setFlexGrow(0);

    grid.asSingleSelect()
        .addValueChangeListener(
            event -> {
              // Could show account details in a side panel in future
            });
  }

  private HorizontalLayout createActionButtons(Account account) {
    Button editBtn = new Button(VaadinIcon.EDIT.create());
    editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
    editBtn.addClickListener(e -> openAccountDialog(account));
    editBtn.getElement().setAttribute("title", "Edit account");

    Button addChildBtn = new Button(VaadinIcon.PLUS.create());
    addChildBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
    addChildBtn.addClickListener(e -> openAddChildDialog(account));
    addChildBtn.getElement().setAttribute("title", "Add child account");

    HorizontalLayout actions = new HorizontalLayout(editBtn, addChildBtn);
    actions.setSpacing(false);
    actions.setPadding(false);
    return actions;
  }

  private HorizontalLayout createToolbar() {
    H2 title = new H2("Chart of Accounts");

    searchField.setPlaceholder("Search accounts...");
    searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
    searchField.setClearButtonVisible(true);
    searchField.addValueChangeListener(e -> filterAccounts(e.getValue()));
    searchField.setWidth("250px");

    Button addButton = new Button("Add Account", VaadinIcon.PLUS.create());
    addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    addButton.addClickListener(e -> openAccountDialog(null));

    Button refreshButton = new Button(VaadinIcon.REFRESH.create());
    refreshButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    refreshButton.addClickListener(e -> loadAccounts());
    refreshButton.getElement().setAttribute("title", "Refresh");

    HorizontalLayout actions = new HorizontalLayout(searchField, addButton, refreshButton);
    actions.setAlignItems(FlexComponent.Alignment.BASELINE);

    HorizontalLayout toolbar = new HorizontalLayout(title, actions);
    toolbar.setWidthFull();
    toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
    toolbar.setAlignItems(FlexComponent.Alignment.CENTER);

    return toolbar;
  }

  private void loadAccounts() {
    Company company = companyContextService.getCurrentCompany();
    int securityLevel = companyContextService.getCurrentSecurityLevel();
    List<Account> allAccounts =
        accountService.findByCompanyWithSecurityLevel(company, securityLevel);

    TreeData<Account> treeData = new TreeData<>();

    // Build hierarchy: add root accounts first, then children
    List<Account> rootAccounts = allAccounts.stream().filter(a -> a.getParent() == null).toList();

    for (Account root : rootAccounts) {
      treeData.addItem(null, root);
      addChildrenRecursively(treeData, root, allAccounts);
    }

    dataProvider = new TreeDataProvider<>(treeData);
    grid.setDataProvider(dataProvider);

    // Expand all by default for better visibility
    grid.expandRecursively(rootAccounts, 10);
  }

  private void addChildrenRecursively(
      TreeData<Account> treeData, Account parent, List<Account> allAccounts) {
    List<Account> children =
        allAccounts.stream()
            .filter(a -> a.getParent() != null && a.getParent().getId().equals(parent.getId()))
            .toList();

    for (Account child : children) {
      treeData.addItem(parent, child);
      addChildrenRecursively(treeData, child, allAccounts);
    }
  }

  private void filterAccounts(String searchTerm) {
    if (dataProvider == null) return;

    if (searchTerm == null || searchTerm.isBlank()) {
      dataProvider.clearFilters();
    } else {
      String lowerSearch = searchTerm.toLowerCase();
      dataProvider.setFilter(
          account ->
              account.getCode().toLowerCase().contains(lowerSearch)
                  || account.getName().toLowerCase().contains(lowerSearch)
                  || (account.getAltCode() != null
                      && account.getAltCode().toLowerCase().contains(lowerSearch)));
    }
  }

  private void openAccountDialog(Account account) {
    boolean isNew = account == null;
    Account editAccount = isNew ? new Account() : account;

    Dialog dialog = new Dialog();
    dialog.setHeaderTitle(isNew ? "Add Account" : "Edit Account");
    dialog.setWidth("500px");

    FormLayout form = new FormLayout();

    TextField codeField = new TextField("Code");
    codeField.setMaxLength(7);
    codeField.setRequired(true);
    codeField.setHelperText("Up to 7 characters");

    TextField nameField = new TextField("Name");
    nameField.setMaxLength(100);
    nameField.setRequired(true);

    ComboBox<AccountType> typeCombo = new ComboBox<>("Type");
    typeCombo.setItems(AccountType.values());
    typeCombo.setItemLabelGenerator(AccountType::name);
    typeCombo.setRequired(true);

    ComboBox<Account> parentCombo = new ComboBox<>("Parent Account");
    Company company = companyContextService.getCurrentCompany();
    int securityLevel = companyContextService.getCurrentSecurityLevel();
    List<Account> possibleParents =
        accountService.findByCompanyWithSecurityLevel(company, securityLevel).stream()
            .filter(a -> !a.equals(account)) // Can't be own parent
            .toList();
    parentCombo.setItems(possibleParents);
    parentCombo.setItemLabelGenerator(a -> a.getCode() + " - " + a.getName());
    parentCombo.setClearButtonVisible(true);

    ComboBox<TaxCode> taxCodeCombo = new ComboBox<>("Default Tax Code");
    List<TaxCode> taxCodes = taxCodeService.findActiveByCompany(company);
    taxCodeCombo.setItems(taxCodes);
    taxCodeCombo.setItemLabelGenerator(tc -> tc.getCode() + " - " + tc.getName());
    taxCodeCombo.setClearButtonVisible(true);

    TextField altCodeField = new TextField("Alternate Code");
    altCodeField.setMaxLength(20);

    Checkbox activeCheckbox = new Checkbox("Active");
    activeCheckbox.setValue(true);

    // Bank account fields
    Checkbox bankAccountCheckbox = new Checkbox("Bank Account");
    bankAccountCheckbox.setHelperText("Enable for accounts linked to bank feeds");

    TextField bankNameField = new TextField("Bank Name");
    bankNameField.setMaxLength(100);
    bankNameField.setVisible(false);

    TextField bankNumberField = new TextField("Bank/Account Number");
    bankNumberField.setMaxLength(50);
    bankNumberField.setVisible(false);

    TextField bankCurrencyField = new TextField("Currency");
    bankCurrencyField.setMaxLength(3);
    bankCurrencyField.setHelperText("e.g., NZD, USD");
    bankCurrencyField.setVisible(false);

    // Show/hide bank fields based on checkbox
    bankAccountCheckbox.addValueChangeListener(
        e -> {
          boolean isBankAccount = e.getValue();
          bankNameField.setVisible(isBankAccount);
          bankNumberField.setVisible(isBankAccount);
          bankCurrencyField.setVisible(isBankAccount);
        });

    form.add(
        codeField,
        nameField,
        typeCombo,
        parentCombo,
        taxCodeCombo,
        altCodeField,
        activeCheckbox,
        bankAccountCheckbox,
        bankNameField,
        bankNumberField,
        bankCurrencyField);
    form.setResponsiveSteps(
        new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("400px", 2));

    // Populate fields if editing
    if (!isNew) {
      codeField.setValue(account.getCode() != null ? account.getCode() : "");
      nameField.setValue(account.getName() != null ? account.getName() : "");
      typeCombo.setValue(account.getType());
      parentCombo.setValue(account.getParent());
      activeCheckbox.setValue(account.isActive());
      altCodeField.setValue(account.getAltCode() != null ? account.getAltCode() : "");

      // Bank account fields
      bankAccountCheckbox.setValue(account.isBankAccount());
      bankNameField.setValue(account.getBankName() != null ? account.getBankName() : "");
      bankNumberField.setValue(account.getBankNumber() != null ? account.getBankNumber() : "");
      bankCurrencyField.setValue(
          account.getBankCurrency() != null ? account.getBankCurrency() : "");
      // Trigger visibility update
      boolean isBankAccount = account.isBankAccount();
      bankNameField.setVisible(isBankAccount);
      bankNumberField.setVisible(isBankAccount);
      bankCurrencyField.setVisible(isBankAccount);

      // Find matching tax code
      if (account.getTaxDefaultCode() != null) {
        taxCodes.stream()
            .filter(tc -> tc.getCode().equals(account.getTaxDefaultCode()))
            .findFirst()
            .ifPresent(taxCodeCombo::setValue);
      }
    }

    Button saveButton = new Button("Save");
    saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    saveButton.addClickListener(
        e -> {
          // Validate
          if (codeField.isEmpty() || nameField.isEmpty() || typeCombo.isEmpty()) {
            Notification.show(
                    "Please fill in all required fields", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }

          try {
            if (isNew) {
              Account newAccount =
                  accountService.createAccount(
                      company,
                      codeField.getValue().trim(),
                      nameField.getValue().trim(),
                      typeCombo.getValue());
              newAccount.setParent(parentCombo.getValue());
              newAccount.setAltCode(
                  altCodeField.getValue().isBlank() ? null : altCodeField.getValue().trim());
              newAccount.setActive(activeCheckbox.getValue());
              if (taxCodeCombo.getValue() != null) {
                newAccount.setTaxDefaultCode(taxCodeCombo.getValue().getCode());
              }
              // Bank account fields
              newAccount.setBankAccount(bankAccountCheckbox.getValue());
              if (bankAccountCheckbox.getValue()) {
                newAccount.setBankName(
                    bankNameField.getValue().isBlank() ? null : bankNameField.getValue().trim());
                newAccount.setBankNumber(
                    bankNumberField.getValue().isBlank()
                        ? null
                        : bankNumberField.getValue().trim());
                newAccount.setBankCurrency(
                    bankCurrencyField.getValue().isBlank()
                        ? null
                        : bankCurrencyField.getValue().trim().toUpperCase());
              }
              accountService.save(newAccount);

              Notification.show(
                      "Account created successfully", 3000, Notification.Position.BOTTOM_START)
                  .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } else {
              editAccount.setCode(codeField.getValue().trim());
              editAccount.setName(nameField.getValue().trim());
              editAccount.setType(typeCombo.getValue());
              editAccount.setParent(parentCombo.getValue());
              editAccount.setAltCode(
                  altCodeField.getValue().isBlank() ? null : altCodeField.getValue().trim());
              editAccount.setActive(activeCheckbox.getValue());
              if (taxCodeCombo.getValue() != null) {
                editAccount.setTaxDefaultCode(taxCodeCombo.getValue().getCode());
              } else {
                editAccount.setTaxDefaultCode(null);
              }
              // Bank account fields
              editAccount.setBankAccount(bankAccountCheckbox.getValue());
              if (bankAccountCheckbox.getValue()) {
                editAccount.setBankName(
                    bankNameField.getValue().isBlank() ? null : bankNameField.getValue().trim());
                editAccount.setBankNumber(
                    bankNumberField.getValue().isBlank()
                        ? null
                        : bankNumberField.getValue().trim());
                editAccount.setBankCurrency(
                    bankCurrencyField.getValue().isBlank()
                        ? null
                        : bankCurrencyField.getValue().trim().toUpperCase());
              } else {
                editAccount.setBankName(null);
                editAccount.setBankNumber(null);
                editAccount.setBankCurrency(null);
              }
              accountService.save(editAccount);

              Notification.show(
                      "Account updated successfully", 3000, Notification.Position.BOTTOM_START)
                  .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            }

            dialog.close();
            loadAccounts();
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

  private void openAddChildDialog(Account parent) {
    Account child = new Account();
    child.setParent(parent);
    child.setType(parent.getType()); // Default to parent's type

    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Add Child Account under " + parent.getCode());
    dialog.setWidth("500px");

    FormLayout form = new FormLayout();

    TextField codeField = new TextField("Code");
    codeField.setMaxLength(7);
    codeField.setRequired(true);
    codeField.setHelperText("Up to 7 characters");

    TextField nameField = new TextField("Name");
    nameField.setMaxLength(100);
    nameField.setRequired(true);

    ComboBox<AccountType> typeCombo = new ComboBox<>("Type");
    typeCombo.setItems(AccountType.values());
    typeCombo.setItemLabelGenerator(AccountType::name);
    typeCombo.setRequired(true);
    typeCombo.setValue(parent.getType());

    Span parentInfo = new Span("Parent: " + parent.getCode() + " - " + parent.getName());
    parentInfo.getStyle().set("color", "var(--lumo-secondary-text-color)");

    Company company = companyContextService.getCurrentCompany();

    ComboBox<TaxCode> taxCodeCombo = new ComboBox<>("Default Tax Code");
    List<TaxCode> taxCodes = taxCodeService.findActiveByCompany(company);
    taxCodeCombo.setItems(taxCodes);
    taxCodeCombo.setItemLabelGenerator(tc -> tc.getCode() + " - " + tc.getName());
    taxCodeCombo.setClearButtonVisible(true);

    // Default to parent's tax code
    if (parent.getTaxDefaultCode() != null) {
      taxCodes.stream()
          .filter(tc -> tc.getCode().equals(parent.getTaxDefaultCode()))
          .findFirst()
          .ifPresent(taxCodeCombo::setValue);
    }

    form.add(parentInfo, codeField, nameField, typeCombo, taxCodeCombo);
    form.setColspan(parentInfo, 2);

    Button saveButton = new Button("Save");
    saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    saveButton.addClickListener(
        e -> {
          if (codeField.isEmpty() || nameField.isEmpty() || typeCombo.isEmpty()) {
            Notification.show(
                    "Please fill in all required fields", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
          }

          try {
            Account newAccount =
                accountService.createAccount(
                    company,
                    codeField.getValue().trim(),
                    nameField.getValue().trim(),
                    typeCombo.getValue(),
                    parent);
            if (taxCodeCombo.getValue() != null) {
              newAccount.setTaxDefaultCode(taxCodeCombo.getValue().getCode());
              accountService.save(newAccount);
            }

            Notification.show(
                    "Child account created successfully", 3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            dialog.close();
            loadAccounts();
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
}
