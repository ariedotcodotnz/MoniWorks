package com.example.application.ui.views;

import com.example.application.domain.*;
import com.example.application.domain.RecurringTemplate.*;
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
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * View for managing Recurring Transaction Templates.
 * Allows creating, editing, pausing, and managing recurring templates.
 */
@Route(value = "recurring", layout = MainLayout.class)
@PageTitle("Recurring Templates | MoniWorks")
@PermitAll
public class RecurringTemplatesView extends VerticalLayout {

    private final RecurringTemplateService templateService;
    private final CompanyContextService companyContextService;
    private final ContactService contactService;

    private final Grid<RecurringTemplate> templateGrid = new Grid<>();
    private final Grid<RecurrenceExecutionLog> logGrid = new Grid<>();
    private final VerticalLayout detailPanel = new VerticalLayout();

    private RecurringTemplate selectedTemplate;
    private ComboBox<Status> statusFilter;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm");

    public RecurringTemplatesView(RecurringTemplateService templateService,
                                   CompanyContextService companyContextService,
                                   ContactService contactService) {
        this.templateService = templateService;
        this.companyContextService = companyContextService;
        this.contactService = contactService;

        addClassName("recurring-templates-view");
        setSizeFull();

        SplitLayout splitLayout = new SplitLayout();
        splitLayout.setSizeFull();
        splitLayout.setSplitterPosition(40);

        VerticalLayout masterPanel = createMasterPanel();
        splitLayout.addToPrimary(masterPanel);
        splitLayout.addToSecondary(detailPanel);

        add(splitLayout);
        loadTemplates();
    }

    private VerticalLayout createMasterPanel() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setPadding(false);
        layout.setSpacing(false);

        HorizontalLayout toolbar = createToolbar();
        configureGrid();

        layout.add(toolbar, templateGrid);
        layout.setFlexGrow(1, templateGrid);

        return layout;
    }

    private void configureGrid() {
        templateGrid.addClassNames("recurring-templates-grid");
        templateGrid.setSizeFull();
        templateGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        templateGrid.addColumn(RecurringTemplate::getName)
            .setHeader("Name")
            .setSortable(true)
            .setFlexGrow(1);

        templateGrid.addColumn(t -> t.getTemplateType().name())
            .setHeader("Type")
            .setSortable(true)
            .setAutoWidth(true);

        templateGrid.addColumn(RecurringTemplate::getScheduleDescription)
            .setHeader("Schedule")
            .setSortable(false)
            .setAutoWidth(true);

        templateGrid.addColumn(t -> formatDate(t.getNextRunDate()))
            .setHeader("Next Run")
            .setSortable(true)
            .setAutoWidth(true);

        templateGrid.addComponentColumn(this::createStatusBadge)
            .setHeader("Status")
            .setAutoWidth(true);

        templateGrid.asSingleSelect().addValueChangeListener(e -> {
            selectedTemplate = e.getValue();
            updateDetailPanel();
        });
    }

    private Span createStatusBadge(RecurringTemplate template) {
        Span badge = new Span(template.getStatus().name());
        badge.getElement().getThemeList().add("badge");

        switch (template.getStatus()) {
            case ACTIVE -> badge.getElement().getThemeList().add("success");
            case PAUSED -> badge.getElement().getThemeList().add("contrast");
            case COMPLETED -> badge.getElement().getThemeList().add("primary");
            case CANCELLED -> badge.getElement().getThemeList().add("error");
        }

        return badge;
    }

    private HorizontalLayout createToolbar() {
        H2 title = new H2("Recurring Templates");

        statusFilter = new ComboBox<>();
        statusFilter.setPlaceholder("All Statuses");
        statusFilter.setItems(Status.values());
        statusFilter.setClearButtonVisible(true);
        statusFilter.addValueChangeListener(e -> loadTemplates());
        statusFilter.setWidth("150px");

        Button addButton = new Button("Add Template", VaadinIcon.PLUS.create());
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addButton.addClickListener(e -> openCreateDialog());

        Button runDueButton = new Button("Run Due Now", VaadinIcon.PLAY.create());
        runDueButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        runDueButton.addClickListener(e -> runDueTemplates());

        Button refreshButton = new Button(VaadinIcon.REFRESH.create());
        refreshButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        refreshButton.addClickListener(e -> loadTemplates());
        refreshButton.getElement().setAttribute("title", "Refresh");

        HorizontalLayout filters = new HorizontalLayout(statusFilter);
        filters.setAlignItems(FlexComponent.Alignment.BASELINE);

        HorizontalLayout actions = new HorizontalLayout(addButton, runDueButton, refreshButton);
        actions.setAlignItems(FlexComponent.Alignment.BASELINE);

        HorizontalLayout toolbar = new HorizontalLayout(title, filters, actions);
        toolbar.setWidthFull();
        toolbar.expand(filters);
        toolbar.setAlignItems(FlexComponent.Alignment.CENTER);
        toolbar.setPadding(true);

        return toolbar;
    }

    private void loadTemplates() {
        Company company = companyContextService.getCurrentCompany();
        List<RecurringTemplate> templates;

        if (statusFilter.getValue() != null) {
            templates = templateService.findByCompanyAndStatus(company.getId(), statusFilter.getValue());
        } else {
            templates = templateService.findByCompany(company.getId());
        }

        templateGrid.setItems(templates);
    }

    private void updateDetailPanel() {
        detailPanel.removeAll();
        detailPanel.setPadding(true);
        detailPanel.setSpacing(true);

        if (selectedTemplate == null) {
            detailPanel.add(new Span("Select a template to view details"));
            return;
        }

        // Header with template info
        H3 header = new H3(selectedTemplate.getName());

        // Action buttons based on status
        HorizontalLayout actionButtons = new HorizontalLayout();
        actionButtons.setSpacing(true);

        Button editBtn = new Button("Edit", VaadinIcon.EDIT.create());
        editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        editBtn.addClickListener(e -> openEditDialog(selectedTemplate));
        actionButtons.add(editBtn);

        if (selectedTemplate.getStatus() == Status.ACTIVE) {
            Button pauseBtn = new Button("Pause", VaadinIcon.PAUSE.create());
            pauseBtn.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
            pauseBtn.addClickListener(e -> pauseTemplate());
            actionButtons.add(pauseBtn);

            Button runNowBtn = new Button("Run Now", VaadinIcon.PLAY.create());
            runNowBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
            runNowBtn.addClickListener(e -> runTemplateNow());
            actionButtons.add(runNowBtn);
        } else if (selectedTemplate.getStatus() == Status.PAUSED) {
            Button resumeBtn = new Button("Resume", VaadinIcon.PLAY.create());
            resumeBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
            resumeBtn.addClickListener(e -> resumeTemplate());
            actionButtons.add(resumeBtn);
        }

        if (selectedTemplate.getStatus() != Status.CANCELLED) {
            Button cancelBtn = new Button("Cancel", VaadinIcon.CLOSE.create());
            cancelBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            cancelBtn.addClickListener(e -> cancelTemplate());
            actionButtons.add(cancelBtn);
        }

        Button deleteBtn = new Button("Delete", VaadinIcon.TRASH.create());
        deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        deleteBtn.addClickListener(e -> deleteTemplate());
        actionButtons.add(deleteBtn);

        HorizontalLayout headerLayout = new HorizontalLayout(header, actionButtons);
        headerLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        headerLayout.setWidthFull();
        headerLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        // Template details
        VerticalLayout details = new VerticalLayout();
        details.setPadding(false);
        details.setSpacing(false);

        details.add(createDetailRow("Type:", selectedTemplate.getTemplateType().name()));
        details.add(createDetailRow("Status:", selectedTemplate.getStatus().name()));
        details.add(createDetailRow("Schedule:", selectedTemplate.getScheduleDescription()));
        details.add(createDetailRow("Start Date:", formatDate(selectedTemplate.getStartDate())));
        if (selectedTemplate.getEndDate() != null) {
            details.add(createDetailRow("End Date:", formatDate(selectedTemplate.getEndDate())));
        }
        details.add(createDetailRow("Next Run:", formatDate(selectedTemplate.getNextRunDate())));
        details.add(createDetailRow("Executions:", String.valueOf(selectedTemplate.getOccurrencesCount())));
        details.add(createDetailRow("Mode:", selectedTemplate.getExecutionMode() == ExecutionMode.AUTO_POST
                ? "Auto-post" : "Create as draft"));

        if (selectedTemplate.getContact() != null) {
            details.add(createDetailRow("Contact:", selectedTemplate.getContact().getName()));
        }
        if (selectedTemplate.getDescription() != null) {
            details.add(createDetailRow("Description:", selectedTemplate.getDescription()));
        }

        // Execution history
        H3 historyHeader = new H3("Execution History");
        configureLogGrid();
        loadExecutionHistory();

        detailPanel.add(headerLayout, details, historyHeader, logGrid);
        detailPanel.setFlexGrow(1, logGrid);
    }

    private HorizontalLayout createDetailRow(String label, String value) {
        Span labelSpan = new Span(label);
        labelSpan.getStyle().set("font-weight", "bold");
        labelSpan.setWidth("120px");

        Span valueSpan = new Span(value);

        HorizontalLayout row = new HorizontalLayout(labelSpan, valueSpan);
        row.setSpacing(true);
        row.setPadding(false);
        return row;
    }

    private void configureLogGrid() {
        logGrid.removeAllColumns();
        logGrid.addClassNames("execution-log-grid");
        logGrid.setSizeFull();
        logGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

        logGrid.addColumn(log -> formatDateTime(log.getRunAt()))
            .setHeader("Run At")
            .setSortable(true)
            .setAutoWidth(true);

        logGrid.addComponentColumn(this::createResultBadge)
            .setHeader("Result")
            .setAutoWidth(true);

        logGrid.addColumn(log -> log.getCreatedEntityId() != null
                ? log.getCreatedEntityType() + " #" + log.getCreatedEntityId() : "")
            .setHeader("Created Entity")
            .setAutoWidth(true);

        logGrid.addColumn(RecurrenceExecutionLog::getError)
            .setHeader("Error")
            .setFlexGrow(1);
    }

    private Span createResultBadge(RecurrenceExecutionLog log) {
        Span badge = new Span(log.getResult().name());
        badge.getElement().getThemeList().add("badge");
        badge.getElement().getThemeList().add("small");

        if (log.isSuccess()) {
            badge.getElement().getThemeList().add("success");
        } else {
            badge.getElement().getThemeList().add("error");
        }

        return badge;
    }

    private void loadExecutionHistory() {
        if (selectedTemplate == null) {
            logGrid.setItems(List.of());
            return;
        }

        List<RecurrenceExecutionLog> logs = templateService.getExecutionHistory(selectedTemplate);
        logGrid.setItems(logs);
    }

    private String formatDate(LocalDate date) {
        return date != null ? DATE_FORMAT.format(date) : "";
    }

    private String formatDateTime(java.time.Instant instant) {
        if (instant == null) return "";
        return DATETIME_FORMAT.format(instant.atZone(ZoneId.systemDefault()));
    }

    // Template Actions

    private void pauseTemplate() {
        templateService.pause(selectedTemplate, null);
        Notification.show("Template paused", 3000, Notification.Position.BOTTOM_START)
            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        loadTemplates();
        updateDetailPanel();
    }

    private void resumeTemplate() {
        templateService.resume(selectedTemplate, null);
        Notification.show("Template resumed", 3000, Notification.Position.BOTTOM_START)
            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        loadTemplates();
        updateDetailPanel();
    }

    private void cancelTemplate() {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Cancel Template");
        dialog.setText("Are you sure you want to cancel this recurring template? This action cannot be undone.");
        dialog.setCancelable(true);
        dialog.setConfirmText("Cancel Template");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(e -> {
            templateService.cancel(selectedTemplate, null);
            Notification.show("Template cancelled", 3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            loadTemplates();
            updateDetailPanel();
        });
        dialog.open();
    }

    private void deleteTemplate() {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Delete Template");
        dialog.setText("Are you sure you want to delete this recurring template? All execution history will also be deleted.");
        dialog.setCancelable(true);
        dialog.setConfirmText("Delete");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(e -> {
            templateService.delete(selectedTemplate, null);
            selectedTemplate = null;
            Notification.show("Template deleted", 3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            loadTemplates();
            updateDetailPanel();
        });
        dialog.open();
    }

    private void runTemplateNow() {
        RecurrenceExecutionLog result = templateService.executeNow(selectedTemplate, null);

        if (result.isSuccess()) {
            Notification.show("Template executed successfully. Created " +
                    result.getCreatedEntityType() + " #" + result.getCreatedEntityId(),
                    5000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } else {
            Notification.show("Template execution failed: " + result.getError(),
                    5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }

        loadTemplates();
        updateDetailPanel();
    }

    private void runDueTemplates() {
        Company company = companyContextService.getCurrentCompany();
        List<RecurringTemplate> dueTemplates = templateService.findDueTemplates(
                company.getId(), LocalDate.now());

        if (dueTemplates.isEmpty()) {
            Notification.show("No templates are due to run", 3000, Notification.Position.MIDDLE);
            return;
        }

        int successCount = 0;
        int failCount = 0;

        for (RecurringTemplate template : dueTemplates) {
            RecurrenceExecutionLog result = templateService.executeNow(template, null);
            if (result.isSuccess()) {
                successCount++;
            } else {
                failCount++;
            }
        }

        String message = String.format("Executed %d templates: %d successful, %d failed",
                dueTemplates.size(), successCount, failCount);
        Notification.show(message, 5000, Notification.Position.BOTTOM_START)
            .addThemeVariants(failCount > 0 ? NotificationVariant.LUMO_CONTRAST : NotificationVariant.LUMO_SUCCESS);

        loadTemplates();
        if (selectedTemplate != null) {
            updateDetailPanel();
        }
    }

    // Create/Edit Dialogs

    private void openCreateDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Create Recurring Template");
        dialog.setWidth("600px");

        FormLayout form = new FormLayout();

        TextField nameField = new TextField("Name");
        nameField.setMaxLength(100);
        nameField.setRequired(true);
        nameField.setWidthFull();

        ComboBox<TemplateType> typeCombo = new ComboBox<>("Type");
        typeCombo.setItems(TemplateType.values());
        typeCombo.setRequired(true);

        ComboBox<Frequency> frequencyCombo = new ComboBox<>("Frequency");
        frequencyCombo.setItems(Frequency.values());
        frequencyCombo.setRequired(true);
        frequencyCombo.setValue(Frequency.MONTHLY);

        IntegerField intervalField = new IntegerField("Interval");
        intervalField.setValue(1);
        intervalField.setMin(1);
        intervalField.setMax(99);
        intervalField.setStepButtonsVisible(true);
        intervalField.setHelperText("Every N frequency units");

        DatePicker startDateField = new DatePicker("Start Date");
        startDateField.setRequired(true);
        startDateField.setValue(LocalDate.now());

        DatePicker endDateField = new DatePicker("End Date (Optional)");
        endDateField.setClearButtonVisible(true);

        IntegerField maxOccurrencesField = new IntegerField("Max Occurrences (Optional)");
        maxOccurrencesField.setMin(1);
        maxOccurrencesField.setStepButtonsVisible(true);
        maxOccurrencesField.setClearButtonVisible(true);

        ComboBox<ExecutionMode> modeCombo = new ComboBox<>("Execution Mode");
        modeCombo.setItems(ExecutionMode.values());
        modeCombo.setItemLabelGenerator(m -> m == ExecutionMode.AUTO_POST ? "Auto-post" : "Create as draft");
        modeCombo.setValue(ExecutionMode.CREATE_DRAFT);
        modeCombo.setRequired(true);

        // Contact selector (for invoices/bills)
        Company company = companyContextService.getCurrentCompany();
        List<Contact> contacts = contactService.findByCompany(company);
        ComboBox<Contact> contactCombo = new ComboBox<>("Contact (for Invoices/Bills)");
        contactCombo.setItems(contacts);
        contactCombo.setItemLabelGenerator(Contact::getName);
        contactCombo.setClearButtonVisible(true);

        TextArea descriptionArea = new TextArea("Description");
        descriptionArea.setMaxLength(255);
        descriptionArea.setWidthFull();

        // Placeholder for payload - in a real implementation, this would have a more
        // sophisticated line editor
        TextArea payloadArea = new TextArea("Template Data (JSON)");
        payloadArea.setRequired(true);
        payloadArea.setWidthFull();
        payloadArea.setHeight("150px");
        payloadArea.setHelperText("JSON format: {\"description\":\"...\",\"lines\":[{\"accountId\":1,\"amount\":\"100.00\",\"direction\":\"DEBIT\"}]}");
        payloadArea.setValue("{\"description\":\"\",\"lines\":[]}");

        // Show/hide contact based on type
        typeCombo.addValueChangeListener(e -> {
            TemplateType type = e.getValue();
            boolean needsContact = type == TemplateType.INVOICE || type == TemplateType.BILL;
            contactCombo.setVisible(needsContact);
            if (needsContact) {
                contactCombo.setRequired(true);
            }
        });
        contactCombo.setVisible(false);

        form.add(nameField, typeCombo, frequencyCombo, intervalField,
                startDateField, endDateField, maxOccurrencesField, modeCombo,
                contactCombo, descriptionArea, payloadArea);
        form.setColspan(nameField, 2);
        form.setColspan(descriptionArea, 2);
        form.setColspan(payloadArea, 2);
        form.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 2)
        );

        Button saveButton = new Button("Create");
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.addClickListener(e -> {
            if (nameField.isEmpty() || typeCombo.isEmpty() || frequencyCombo.isEmpty()
                    || startDateField.isEmpty() || payloadArea.isEmpty()) {
                Notification.show("Please fill in all required fields", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            TemplateType type = typeCombo.getValue();
            if ((type == TemplateType.INVOICE || type == TemplateType.BILL) && contactCombo.isEmpty()) {
                Notification.show("Contact is required for Invoice/Bill templates", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                RecurringTemplate template = templateService.createTemplate(
                        company,
                        nameField.getValue().trim(),
                        typeCombo.getValue(),
                        frequencyCombo.getValue(),
                        startDateField.getValue(),
                        payloadArea.getValue(),
                        null);

                template.setFrequencyInterval(intervalField.getValue() != null ? intervalField.getValue() : 1);
                template.setEndDate(endDateField.getValue());
                template.setMaxOccurrences(maxOccurrencesField.getValue());
                template.setExecutionMode(modeCombo.getValue());
                template.setContact(contactCombo.getValue());
                template.setDescription(descriptionArea.getValue());

                templateService.save(template);

                Notification.show("Template created successfully", 3000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                dialog.close();
                loadTemplates();
            } catch (Exception ex) {
                Notification.show("Error: " + ex.getMessage(), 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        Button cancelButton = new Button("Cancel", e -> dialog.close());

        dialog.add(form);
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private void openEditDialog(RecurringTemplate template) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Edit Recurring Template");
        dialog.setWidth("600px");

        FormLayout form = new FormLayout();

        TextField nameField = new TextField("Name");
        nameField.setMaxLength(100);
        nameField.setRequired(true);
        nameField.setValue(template.getName());
        nameField.setWidthFull();

        // Type is read-only after creation
        TextField typeField = new TextField("Type");
        typeField.setValue(template.getTemplateType().name());
        typeField.setReadOnly(true);

        ComboBox<Frequency> frequencyCombo = new ComboBox<>("Frequency");
        frequencyCombo.setItems(Frequency.values());
        frequencyCombo.setRequired(true);
        frequencyCombo.setValue(template.getFrequency());

        IntegerField intervalField = new IntegerField("Interval");
        intervalField.setValue(template.getFrequencyInterval());
        intervalField.setMin(1);
        intervalField.setMax(99);
        intervalField.setStepButtonsVisible(true);

        DatePicker endDateField = new DatePicker("End Date (Optional)");
        endDateField.setClearButtonVisible(true);
        endDateField.setValue(template.getEndDate());

        IntegerField maxOccurrencesField = new IntegerField("Max Occurrences (Optional)");
        maxOccurrencesField.setMin(1);
        maxOccurrencesField.setStepButtonsVisible(true);
        maxOccurrencesField.setClearButtonVisible(true);
        maxOccurrencesField.setValue(template.getMaxOccurrences());

        ComboBox<ExecutionMode> modeCombo = new ComboBox<>("Execution Mode");
        modeCombo.setItems(ExecutionMode.values());
        modeCombo.setItemLabelGenerator(m -> m == ExecutionMode.AUTO_POST ? "Auto-post" : "Create as draft");
        modeCombo.setValue(template.getExecutionMode());
        modeCombo.setRequired(true);

        TextArea descriptionArea = new TextArea("Description");
        descriptionArea.setMaxLength(255);
        descriptionArea.setWidthFull();
        descriptionArea.setValue(template.getDescription() != null ? template.getDescription() : "");

        form.add(nameField, typeField, frequencyCombo, intervalField,
                endDateField, maxOccurrencesField, modeCombo, descriptionArea);
        form.setColspan(nameField, 2);
        form.setColspan(descriptionArea, 2);
        form.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 2)
        );

        Button saveButton = new Button("Save");
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.addClickListener(e -> {
            if (nameField.isEmpty() || frequencyCombo.isEmpty()) {
                Notification.show("Please fill in all required fields", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                template.setName(nameField.getValue().trim());
                template.setFrequency(frequencyCombo.getValue());
                template.setFrequencyInterval(intervalField.getValue() != null ? intervalField.getValue() : 1);
                template.setEndDate(endDateField.getValue());
                template.setMaxOccurrences(maxOccurrencesField.getValue());
                template.setExecutionMode(modeCombo.getValue());
                template.setDescription(descriptionArea.getValue());

                templateService.save(template);

                Notification.show("Template updated successfully", 3000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                dialog.close();
                loadTemplates();
                updateDetailPanel();
            } catch (Exception ex) {
                Notification.show("Error: " + ex.getMessage(), 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        Button cancelButton = new Button("Cancel", e -> dialog.close());

        dialog.add(form);
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }
}
