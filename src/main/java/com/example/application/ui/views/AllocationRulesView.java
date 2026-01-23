package com.example.application.ui.views;

import com.example.application.domain.*;
import com.example.application.repository.AllocationRuleRepository;
import com.example.application.service.AccountService;
import com.example.application.service.AuditService;
import com.example.application.service.CompanyContextService;
import com.example.application.service.TaxCodeService;
import com.example.application.ui.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.util.List;

/**
 * View for managing allocation rules used during bank reconciliation.
 * Rules auto-suggest coding (account and tax code) based on transaction descriptions.
 *
 * Features:
 * - List all allocation rules with priority ordering
 * - Create, edit, and delete rules
 * - Enable/disable rules
 * - Test rules against sample descriptions
 * - Priority-based ordering for rule application
 */
@Route(value = "allocation-rules", layout = MainLayout.class)
@PageTitle("Allocation Rules | MoniWorks")
@PermitAll
public class AllocationRulesView extends VerticalLayout {

    private final AllocationRuleRepository allocationRuleRepository;
    private final AccountService accountService;
    private final TaxCodeService taxCodeService;
    private final CompanyContextService companyContextService;
    private final AuditService auditService;

    private final Grid<AllocationRule> grid = new Grid<>();
    private final Binder<AllocationRule> binder = new Binder<>(AllocationRule.class);

    // Detail form fields
    private TextField ruleNameField;
    private TextArea matchExpressionField;
    private ComboBox<Account> targetAccountCombo;
    private ComboBox<TaxCode> targetTaxCodeCombo;
    private IntegerField priorityField;
    private Checkbox enabledCheckbox;
    private TextField memoTemplateField;

    private VerticalLayout detailLayout;
    private AllocationRule currentRule;
    private boolean isNewRule = false;

    public AllocationRulesView(AllocationRuleRepository allocationRuleRepository,
                                AccountService accountService,
                                TaxCodeService taxCodeService,
                                CompanyContextService companyContextService,
                                AuditService auditService) {
        this.allocationRuleRepository = allocationRuleRepository;
        this.accountService = accountService;
        this.taxCodeService = taxCodeService;
        this.companyContextService = companyContextService;
        this.auditService = auditService;

        addClassName("allocation-rules-view");
        setSizeFull();

        configureGrid();
        configureDetailForm();

        SplitLayout splitLayout = new SplitLayout(createListLayout(), detailLayout);
        splitLayout.setSizeFull();
        splitLayout.setSplitterPosition(50);

        add(createToolbar(), splitLayout);
        expand(splitLayout);

        loadRules();
    }

    private HorizontalLayout createToolbar() {
        H2 title = new H2("Allocation Rules");

        Button addBtn = new Button("New Rule", VaadinIcon.PLUS.create());
        addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addBtn.addClickListener(e -> createNewRule());

        Button testBtn = new Button("Test Rules", VaadinIcon.COGS.create());
        testBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        testBtn.addClickListener(e -> openTestDialog());

        Button refreshBtn = new Button(VaadinIcon.REFRESH.create());
        refreshBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        refreshBtn.addClickListener(e -> loadRules());
        refreshBtn.getElement().setAttribute("title", "Refresh");

        HorizontalLayout buttons = new HorizontalLayout(addBtn, testBtn, refreshBtn);
        buttons.setAlignItems(FlexComponent.Alignment.CENTER);

        HorizontalLayout toolbar = new HorizontalLayout(title, buttons);
        toolbar.setWidthFull();
        toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        toolbar.setAlignItems(FlexComponent.Alignment.CENTER);
        toolbar.setPadding(true);

        return toolbar;
    }

    private VerticalLayout createListLayout() {
        VerticalLayout listLayout = new VerticalLayout();
        listLayout.setSizeFull();
        listLayout.setPadding(false);

        // Help text
        Paragraph helpText = new Paragraph(
            "Allocation rules auto-suggest coding during bank reconciliation. " +
            "Rules are applied in priority order (highest first). " +
            "Click a rule to edit.");
        helpText.getStyle().set("color", "var(--lumo-secondary-text-color)")
            .set("font-size", "var(--lumo-font-size-s)")
            .set("margin", "0 0 8px 0");

        listLayout.add(helpText, grid);
        listLayout.expand(grid);

        return listLayout;
    }

    private void configureGrid() {
        grid.addClassNames("allocation-rules-grid");
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        grid.addColumn(AllocationRule::getPriority)
            .setHeader("Priority")
            .setWidth("80px")
            .setFlexGrow(0)
            .setSortable(true);

        grid.addColumn(AllocationRule::getRuleName)
            .setHeader("Rule Name")
            .setFlexGrow(1)
            .setSortable(true);

        grid.addColumn(AllocationRule::getMatchExpression)
            .setHeader("Match Expression")
            .setFlexGrow(1);

        grid.addColumn(rule -> rule.getTargetAccount().getCode() + " - " + rule.getTargetAccount().getName())
            .setHeader("Target Account")
            .setAutoWidth(true);

        grid.addColumn(rule -> rule.getTargetTaxCode() != null ? rule.getTargetTaxCode() : "-")
            .setHeader("Tax Code")
            .setWidth("100px")
            .setFlexGrow(0);

        grid.addComponentColumn(rule -> {
            Span badge = new Span(rule.isEnabled() ? "Active" : "Disabled");
            badge.getStyle().set("padding", "2px 8px")
                .set("border-radius", "4px")
                .set("font-size", "var(--lumo-font-size-xs)");
            if (rule.isEnabled()) {
                badge.getStyle().set("background-color", "var(--lumo-success-color-10pct)")
                    .set("color", "var(--lumo-success-text-color)");
            } else {
                badge.getStyle().set("background-color", "var(--lumo-contrast-10pct)")
                    .set("color", "var(--lumo-secondary-text-color)");
            }
            return badge;
        })
            .setHeader("Status")
            .setWidth("100px")
            .setFlexGrow(0);

        grid.asSingleSelect().addValueChangeListener(event -> {
            if (event.getValue() != null) {
                editRule(event.getValue());
            } else {
                clearForm();
            }
        });
    }

    private void configureDetailForm() {
        detailLayout = new VerticalLayout();
        detailLayout.setSizeFull();
        detailLayout.setPadding(true);

        FormLayout formLayout = new FormLayout();
        formLayout.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("500px", 2)
        );

        ruleNameField = new TextField("Rule Name");
        ruleNameField.setRequired(true);
        ruleNameField.setMaxLength(100);
        ruleNameField.setPlaceholder("e.g., Electricity Bills");

        matchExpressionField = new TextArea("Match Expression");
        matchExpressionField.setRequired(true);
        matchExpressionField.setMaxLength(500);
        matchExpressionField.setHelperText(
            "Enter text to match in transaction descriptions. Case-insensitive. " +
            "Use 'CONTAINS text' for explicit contains matching."
        );
        matchExpressionField.setPlaceholder("e.g., ELECTRICITY or CONTAINS 'power company'");

        Company company = companyContextService.getCurrentCompany();

        targetAccountCombo = new ComboBox<>("Target Account");
        targetAccountCombo.setRequired(true);
        int securityLevel = companyContextService.getCurrentSecurityLevel();
        List<Account> accounts = accountService.findActiveByCompanyWithSecurityLevel(company, securityLevel);
        targetAccountCombo.setItems(accounts);
        targetAccountCombo.setItemLabelGenerator(a -> a.getCode() + " - " + a.getName());
        targetAccountCombo.setPlaceholder("Select account...");

        targetTaxCodeCombo = new ComboBox<>("Tax Code (Optional)");
        List<TaxCode> taxCodes = taxCodeService.findByCompany(company);
        targetTaxCodeCombo.setItems(taxCodes);
        targetTaxCodeCombo.setItemLabelGenerator(tc -> tc.getCode() + " - " + tc.getName());
        targetTaxCodeCombo.setClearButtonVisible(true);
        targetTaxCodeCombo.setPlaceholder("Select tax code...");

        priorityField = new IntegerField("Priority");
        priorityField.setMin(0);
        priorityField.setMax(9999);
        priorityField.setValue(0);
        priorityField.setStepButtonsVisible(true);
        priorityField.setHelperText("Higher priority rules are applied first (0-9999)");

        enabledCheckbox = new Checkbox("Enabled");
        enabledCheckbox.setValue(true);

        memoTemplateField = new TextField("Memo Template (Optional)");
        memoTemplateField.setMaxLength(255);
        memoTemplateField.setPlaceholder("Optional memo for created transactions");

        formLayout.add(ruleNameField, priorityField);
        formLayout.add(matchExpressionField, 2);
        formLayout.add(targetAccountCombo, targetTaxCodeCombo);
        formLayout.add(memoTemplateField, enabledCheckbox);

        // Buttons
        Button saveBtn = new Button("Save", VaadinIcon.CHECK.create());
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveBtn.addClickListener(e -> saveRule());

        Button deleteBtn = new Button("Delete", VaadinIcon.TRASH.create());
        deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);
        deleteBtn.addClickListener(e -> deleteRule());

        Button cancelBtn = new Button("Cancel");
        cancelBtn.addClickListener(e -> {
            clearForm();
            grid.asSingleSelect().clear();
        });

        HorizontalLayout buttonLayout = new HorizontalLayout(saveBtn, cancelBtn, deleteBtn);
        buttonLayout.setSpacing(true);

        // Configure binder
        binder.forField(ruleNameField)
            .asRequired("Rule name is required")
            .bind(AllocationRule::getRuleName, AllocationRule::setRuleName);

        binder.forField(matchExpressionField)
            .asRequired("Match expression is required")
            .bind(AllocationRule::getMatchExpression, AllocationRule::setMatchExpression);

        binder.forField(targetAccountCombo)
            .asRequired("Target account is required")
            .bind(AllocationRule::getTargetAccount, AllocationRule::setTargetAccount);

        binder.forField(priorityField)
            .bind(AllocationRule::getPriority, AllocationRule::setPriority);

        binder.forField(enabledCheckbox)
            .bind(AllocationRule::isEnabled, AllocationRule::setEnabled);

        binder.forField(memoTemplateField)
            .bind(AllocationRule::getMemoTemplate, AllocationRule::setMemoTemplate);

        detailLayout.add(formLayout, buttonLayout);

        clearForm();
    }

    private void loadRules() {
        Company company = companyContextService.getCurrentCompany();
        List<AllocationRule> rules = allocationRuleRepository.findByCompanyOrderByPriorityDesc(company);
        grid.setItems(rules);
    }

    private void createNewRule() {
        grid.asSingleSelect().clear();
        currentRule = new AllocationRule();
        currentRule.setCompany(companyContextService.getCurrentCompany());
        currentRule.setPriority(0);
        currentRule.setEnabled(true);
        isNewRule = true;

        binder.readBean(currentRule);
        ruleNameField.focus();
        detailLayout.setVisible(true);
    }

    private void editRule(AllocationRule rule) {
        currentRule = rule;
        isNewRule = false;

        binder.readBean(currentRule);

        // Set tax code by matching code string
        if (rule.getTargetTaxCode() != null) {
            Company company = companyContextService.getCurrentCompany();
            taxCodeService.findByCompany(company).stream()
                .filter(tc -> tc.getCode().equals(rule.getTargetTaxCode()))
                .findFirst()
                .ifPresent(targetTaxCodeCombo::setValue);
        } else {
            targetTaxCodeCombo.clear();
        }

        detailLayout.setVisible(true);
    }

    private void saveRule() {
        try {
            binder.writeBean(currentRule);

            // Set tax code string from combo selection
            TaxCode selectedTaxCode = targetTaxCodeCombo.getValue();
            currentRule.setTargetTaxCode(selectedTaxCode != null ? selectedTaxCode.getCode() : null);

            allocationRuleRepository.save(currentRule);

            // Audit log
            String eventType = isNewRule ? "ALLOCATION_RULE_CREATED" : "ALLOCATION_RULE_UPDATED";
            auditService.logEvent(
                currentRule.getCompany(),
                companyContextService.getCurrentUser(),
                eventType,
                "AllocationRule",
                currentRule.getId(),
                "Rule: " + currentRule.getRuleName()
            );

            Notification.show("Rule saved successfully",
                3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            loadRules();
            clearForm();
            grid.asSingleSelect().clear();

        } catch (ValidationException ex) {
            Notification.show("Please fix validation errors",
                3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void deleteRule() {
        if (currentRule == null || currentRule.getId() == null) {
            return;
        }

        Dialog confirmDialog = new Dialog();
        confirmDialog.setHeaderTitle("Delete Rule");

        Paragraph message = new Paragraph(
            "Are you sure you want to delete the rule '" + currentRule.getRuleName() + "'?");

        Button deleteBtn = new Button("Delete", e -> {
            auditService.logEvent(
                currentRule.getCompany(),
                companyContextService.getCurrentUser(),
                "ALLOCATION_RULE_DELETED",
                "AllocationRule",
                currentRule.getId(),
                "Rule: " + currentRule.getRuleName()
            );

            allocationRuleRepository.delete(currentRule);

            Notification.show("Rule deleted",
                3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            confirmDialog.close();
            loadRules();
            clearForm();
            grid.asSingleSelect().clear();
        });
        deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button("Cancel", e -> confirmDialog.close());

        confirmDialog.add(message);
        confirmDialog.getFooter().add(cancelBtn, deleteBtn);
        confirmDialog.open();
    }

    private void clearForm() {
        currentRule = null;
        isNewRule = false;
        binder.readBean(null);
        targetTaxCodeCombo.clear();
        priorityField.setValue(0);
        enabledCheckbox.setValue(true);
        detailLayout.setVisible(false);
    }

    private void openTestDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Test Allocation Rules");
        dialog.setWidth("500px");

        TextField testInput = new TextField("Test Description");
        testInput.setWidthFull();
        testInput.setPlaceholder("Enter a bank transaction description to test...");

        VerticalLayout resultLayout = new VerticalLayout();
        resultLayout.setPadding(false);
        resultLayout.setSpacing(true);

        Button testBtn = new Button("Test", VaadinIcon.SEARCH.create());
        testBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        testBtn.addClickListener(e -> {
            resultLayout.removeAll();

            String description = testInput.getValue();
            if (description == null || description.isBlank()) {
                resultLayout.add(new Span("Please enter a description to test."));
                return;
            }

            Company company = companyContextService.getCurrentCompany();
            List<AllocationRule> rules = allocationRuleRepository.findEnabledByCompanyOrderByPriority(company);

            boolean foundMatch = false;
            for (AllocationRule rule : rules) {
                if (rule.matches(description)) {
                    Span matchResult = new Span(
                        "Match found: " + rule.getRuleName() +
                        " -> Account: " + rule.getTargetAccount().getCode() +
                        (rule.getTargetTaxCode() != null ? ", Tax: " + rule.getTargetTaxCode() : "")
                    );
                    matchResult.getStyle().set("color", "var(--lumo-success-text-color)")
                        .set("font-weight", "bold");
                    resultLayout.add(matchResult);
                    foundMatch = true;
                    break;  // First matching rule wins (priority order)
                }
            }

            if (!foundMatch) {
                Span noMatch = new Span("No matching rule found for this description.");
                noMatch.getStyle().set("color", "var(--lumo-secondary-text-color)");
                resultLayout.add(noMatch);
            }
        });

        Paragraph helpText = new Paragraph(
            "Enter a sample transaction description to see which rule would match. " +
            "Rules are tested in priority order (highest first).");
        helpText.getStyle().set("color", "var(--lumo-secondary-text-color)")
            .set("font-size", "var(--lumo-font-size-s)");

        VerticalLayout content = new VerticalLayout(testInput, testBtn, resultLayout, helpText);
        content.setPadding(false);
        content.setSpacing(true);

        Button closeBtn = new Button("Close", e -> dialog.close());

        dialog.add(content);
        dialog.getFooter().add(closeBtn);
        dialog.open();
    }
}
