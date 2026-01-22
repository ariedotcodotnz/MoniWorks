package com.example.application.ui.views;

import com.example.application.domain.Company;
import com.example.application.domain.FiscalYear;
import com.example.application.domain.Period;
import com.example.application.service.CompanyContextService;
import com.example.application.service.FiscalYearService;
import com.example.application.ui.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
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
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * View for managing Fiscal Years and Periods.
 * Allows creating fiscal years and locking/unlocking periods.
 */
@Route(value = "periods", layout = MainLayout.class)
@PageTitle("Fiscal Periods | MoniWorks")
@PermitAll
public class PeriodsView extends VerticalLayout {

    private final FiscalYearService fiscalYearService;
    private final CompanyContextService companyContextService;

    private final ComboBox<FiscalYear> fiscalYearCombo = new ComboBox<>("Fiscal Year");
    private final Grid<Period> periodsGrid = new Grid<>();

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public PeriodsView(FiscalYearService fiscalYearService,
                       CompanyContextService companyContextService) {
        this.fiscalYearService = fiscalYearService;
        this.companyContextService = companyContextService;

        addClassName("periods-view");
        setSizeFull();

        add(createToolbar(), createContent());

        loadFiscalYears();
    }

    private HorizontalLayout createToolbar() {
        H2 title = new H2("Fiscal Periods");

        Button addYearButton = new Button("Add Fiscal Year", VaadinIcon.PLUS.create());
        addYearButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addYearButton.addClickListener(e -> openAddFiscalYearDialog());

        Button addNextYearButton = new Button("Add Next Year", VaadinIcon.ARROW_RIGHT.create());
        addNextYearButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        addNextYearButton.addClickListener(e -> addNextFiscalYear());

        HorizontalLayout actions = new HorizontalLayout(addYearButton, addNextYearButton);
        actions.setAlignItems(FlexComponent.Alignment.BASELINE);

        HorizontalLayout toolbar = new HorizontalLayout(title, actions);
        toolbar.setWidthFull();
        toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        toolbar.setAlignItems(FlexComponent.Alignment.CENTER);

        return toolbar;
    }

    private VerticalLayout createContent() {
        // Fiscal year selector
        fiscalYearCombo.setItemLabelGenerator(fy ->
            fy.getLabel() + " (" + fy.getStartDate().format(DATE_FORMAT) +
            " - " + fy.getEndDate().format(DATE_FORMAT) + ")");
        fiscalYearCombo.setWidth("400px");
        fiscalYearCombo.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                loadPeriods(e.getValue());
            }
        });

        // Periods grid
        configurePeriodsGrid();

        VerticalLayout content = new VerticalLayout(fiscalYearCombo, periodsGrid);
        content.setSizeFull();
        content.setPadding(false);
        content.setSpacing(true);

        return content;
    }

    private void configurePeriodsGrid() {
        periodsGrid.addClassNames("periods-grid");
        periodsGrid.setSizeFull();
        periodsGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        periodsGrid.addColumn(p -> "Period " + p.getPeriodIndex())
            .setHeader("Period")
            .setAutoWidth(true)
            .setFlexGrow(0);

        periodsGrid.addColumn(p -> p.getStartDate().format(DATE_FORMAT))
            .setHeader("Start Date")
            .setAutoWidth(true);

        periodsGrid.addColumn(p -> p.getEndDate().format(DATE_FORMAT))
            .setHeader("End Date")
            .setAutoWidth(true);

        periodsGrid.addColumn(p -> p.getStatus().name())
            .setHeader("Status")
            .setAutoWidth(true);

        periodsGrid.addComponentColumn(this::createPeriodActions)
            .setHeader("Actions")
            .setAutoWidth(true)
            .setFlexGrow(0);
    }

    private HorizontalLayout createPeriodActions(Period period) {
        Button toggleButton;
        if (period.isOpen()) {
            toggleButton = new Button("Lock", VaadinIcon.LOCK.create());
            toggleButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
            toggleButton.addClickListener(e -> lockPeriod(period));
        } else {
            toggleButton = new Button("Unlock", VaadinIcon.UNLOCK.create());
            toggleButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS);
            toggleButton.addClickListener(e -> unlockPeriod(period));
        }

        HorizontalLayout actions = new HorizontalLayout(toggleButton);
        actions.setSpacing(false);
        actions.setPadding(false);
        return actions;
    }

    private void loadFiscalYears() {
        Company company = companyContextService.getCurrentCompany();
        List<FiscalYear> fiscalYears = fiscalYearService.findByCompany(company);

        fiscalYearCombo.setItems(fiscalYears);

        // Select the latest fiscal year by default
        if (!fiscalYears.isEmpty()) {
            fiscalYearCombo.setValue(fiscalYears.get(fiscalYears.size() - 1));
        }
    }

    private void loadPeriods(FiscalYear fiscalYear) {
        List<Period> periods = fiscalYearService.findPeriodsByFiscalYear(fiscalYear);
        periodsGrid.setItems(periods);
    }

    private void lockPeriod(Period period) {
        try {
            fiscalYearService.lockPeriod(period);
            Notification.show("Period locked successfully", 3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            loadPeriods(fiscalYearCombo.getValue());
        } catch (IllegalStateException ex) {
            Notification.show(ex.getMessage(), 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void unlockPeriod(Period period) {
        try {
            fiscalYearService.unlockPeriod(period);
            Notification.show("Period unlocked successfully", 3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            loadPeriods(fiscalYearCombo.getValue());
        } catch (IllegalStateException ex) {
            Notification.show(ex.getMessage(), 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void openAddFiscalYearDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Add Fiscal Year");
        dialog.setWidth("400px");

        FormLayout form = new FormLayout();

        DatePicker startDatePicker = new DatePicker("Start Date");
        startDatePicker.setRequired(true);

        // Default to next logical start date
        Company company = companyContextService.getCurrentCompany();
        fiscalYearService.findLatestByCompany(company)
            .ifPresentOrElse(
                latest -> startDatePicker.setValue(latest.getEndDate().plusDays(1)),
                () -> startDatePicker.setValue(company.getFiscalYearStart())
            );

        TextField labelField = new TextField("Label");
        labelField.setRequired(true);
        labelField.setHelperText("e.g., 2025-2026");

        // Auto-generate label based on dates
        startDatePicker.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                LocalDate end = e.getValue().plusYears(1).minusDays(1);
                labelField.setValue(e.getValue().getYear() + "-" + end.getYear());
            }
        });

        form.add(startDatePicker, labelField);

        Button saveButton = new Button("Create");
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.addClickListener(e -> {
            if (startDatePicker.isEmpty() || labelField.isEmpty()) {
                Notification.show("Please fill in all required fields", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                fiscalYearService.createFiscalYear(
                    company,
                    startDatePicker.getValue(),
                    labelField.getValue().trim()
                );

                Notification.show("Fiscal year created successfully", 3000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                dialog.close();
                loadFiscalYears();
            } catch (Exception ex) {
                Notification.show("Error creating fiscal year: " + ex.getMessage(),
                    3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        Button cancelButton = new Button("Cancel", e -> dialog.close());

        dialog.add(form);
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private void addNextFiscalYear() {
        Company company = companyContextService.getCurrentCompany();

        try {
            FiscalYear newYear = fiscalYearService.createNextFiscalYear(company);
            Notification.show("Fiscal year " + newYear.getLabel() + " created",
                3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            loadFiscalYears();
        } catch (IllegalStateException ex) {
            Notification.show(ex.getMessage(), 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}
