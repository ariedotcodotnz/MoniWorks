package com.example.application.ui.views;

import com.example.application.domain.*;
import com.example.application.domain.TaxReturn.Basis;
import com.example.application.domain.TaxReturn.Status;
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
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * View for generating and managing GST returns.
 *
 * Features:
 * - Generate new GST returns for a period
 * - View return summary and box details
 * - Drilldown to transactions for each box
 * - Finalize and mark returns as filed
 */
@Route(value = "gst-returns", layout = MainLayout.class)
@PageTitle("GST Returns | MoniWorks")
@PermitAll
public class GstReturnsView extends VerticalLayout {

    private final TaxReturnService taxReturnService;
    private final CompanyContextService companyContextService;

    private final Grid<TaxReturn> returnsGrid = new Grid<>();

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public GstReturnsView(TaxReturnService taxReturnService,
                          CompanyContextService companyContextService) {
        this.taxReturnService = taxReturnService;
        this.companyContextService = companyContextService;

        addClassName("gst-returns-view");
        setSizeFull();

        configureGrid();
        add(createToolbar(), returnsGrid);

        loadReturns();
    }

    private HorizontalLayout createToolbar() {
        H2 title = new H2("GST Returns");

        Button generateBtn = new Button("Generate Return", VaadinIcon.PLUS.create());
        generateBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        generateBtn.addClickListener(e -> openGenerateDialog());

        Button refreshBtn = new Button(VaadinIcon.REFRESH.create());
        refreshBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        refreshBtn.addClickListener(e -> loadReturns());
        refreshBtn.getElement().setAttribute("title", "Refresh");

        HorizontalLayout buttons = new HorizontalLayout(generateBtn, refreshBtn);
        buttons.setAlignItems(FlexComponent.Alignment.CENTER);

        HorizontalLayout toolbar = new HorizontalLayout(title, buttons);
        toolbar.setWidthFull();
        toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        toolbar.setAlignItems(FlexComponent.Alignment.CENTER);
        toolbar.setPadding(true);

        return toolbar;
    }

    private void configureGrid() {
        returnsGrid.addClassNames("returns-grid");
        returnsGrid.setSizeFull();
        returnsGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        returnsGrid.addColumn(r -> r.getStartDate().format(DATE_FORMAT) + " - " +
            r.getEndDate().format(DATE_FORMAT))
            .setHeader("Period")
            .setSortable(true)
            .setAutoWidth(true);

        returnsGrid.addColumn(r -> r.getBasis().name())
            .setHeader("Basis")
            .setAutoWidth(true);

        returnsGrid.addColumn(r -> formatCurrency(r.getTotalSales()))
            .setHeader("Total Sales")
            .setAutoWidth(true);

        returnsGrid.addColumn(r -> formatCurrency(r.getOutputTax()))
            .setHeader("Output Tax")
            .setAutoWidth(true);

        returnsGrid.addColumn(r -> formatCurrency(r.getInputTax()))
            .setHeader("Input Tax")
            .setAutoWidth(true);

        returnsGrid.addColumn(r -> formatCurrency(r.getTaxPayable()))
            .setHeader("Tax Payable")
            .setAutoWidth(true);

        returnsGrid.addColumn(r -> r.getStatus().name())
            .setHeader("Status")
            .setSortable(true)
            .setAutoWidth(true);

        returnsGrid.addComponentColumn(this::createActionButtons)
            .setHeader("Actions")
            .setAutoWidth(true)
            .setFlexGrow(0);
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "$0.00";
        String prefix = amount.compareTo(BigDecimal.ZERO) < 0 ? "-$" : "$";
        return prefix + amount.abs().setScale(2).toPlainString();
    }

    private HorizontalLayout createActionButtons(TaxReturn taxReturn) {
        HorizontalLayout actions = new HorizontalLayout();
        actions.setSpacing(false);
        actions.setPadding(false);

        Button viewBtn = new Button(VaadinIcon.EYE.create());
        viewBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        viewBtn.addClickListener(e -> openViewDialog(taxReturn));
        viewBtn.getElement().setAttribute("title", "View details");
        actions.add(viewBtn);

        if (taxReturn.isDraft()) {
            Button finalizeBtn = new Button(VaadinIcon.CHECK.create());
            finalizeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS);
            finalizeBtn.addClickListener(e -> finalizeReturn(taxReturn));
            finalizeBtn.getElement().setAttribute("title", "Finalize");
            actions.add(finalizeBtn);

            Button deleteBtn = new Button(VaadinIcon.TRASH.create());
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
            deleteBtn.addClickListener(e -> deleteReturn(taxReturn));
            deleteBtn.getElement().setAttribute("title", "Delete");
            actions.add(deleteBtn);
        } else if (taxReturn.getStatus() == Status.FINALIZED) {
            Button fileBtn = new Button(VaadinIcon.PAPERPLANE.create());
            fileBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            fileBtn.addClickListener(e -> markAsFiled(taxReturn));
            fileBtn.getElement().setAttribute("title", "Mark as filed");
            actions.add(fileBtn);
        }

        return actions;
    }

    private void loadReturns() {
        Company company = companyContextService.getCurrentCompany();
        List<TaxReturn> returns = taxReturnService.findByCompany(company);
        returnsGrid.setItems(returns);
    }

    private void openGenerateDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Generate GST Return");
        dialog.setWidth("450px");

        FormLayout form = new FormLayout();

        DatePicker startDatePicker = new DatePicker("Period Start");
        startDatePicker.setRequired(true);
        // Default to start of current quarter
        LocalDate now = LocalDate.now();
        int quarterStart = ((now.getMonthValue() - 1) / 3) * 3 + 1;
        startDatePicker.setValue(LocalDate.of(now.getYear(), quarterStart, 1));

        DatePicker endDatePicker = new DatePicker("Period End");
        endDatePicker.setRequired(true);
        // Default to end of current quarter
        LocalDate startDefault = startDatePicker.getValue();
        endDatePicker.setValue(startDefault.plusMonths(3).minusDays(1));

        ComboBox<Basis> basisCombo = new ComboBox<>("Tax Basis");
        basisCombo.setItems(Basis.values());
        basisCombo.setValue(Basis.INVOICE);
        basisCombo.setRequired(true);

        // Update end date when start date changes
        startDatePicker.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                endDatePicker.setValue(e.getValue().plusMonths(2).withDayOfMonth(1)
                    .plusMonths(1).minusDays(1));
            }
        });

        form.add(startDatePicker, endDatePicker, basisCombo);

        Paragraph helpText = new Paragraph(
            "This will generate a GST return based on posted transactions " +
            "with tax codes in the selected period. You can review and finalize " +
            "the return before filing.");
        helpText.getStyle().set("color", "var(--lumo-secondary-text-color)")
            .set("font-size", "var(--lumo-font-size-s)");

        Button generateBtn = new Button("Generate");
        generateBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        generateBtn.addClickListener(e -> {
            if (startDatePicker.isEmpty() || endDatePicker.isEmpty()) {
                Notification.show("Please select start and end dates",
                    3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                Company company = companyContextService.getCurrentCompany();
                TaxReturn taxReturn = taxReturnService.generateReturn(
                    company,
                    startDatePicker.getValue(),
                    endDatePicker.getValue(),
                    basisCombo.getValue(),
                    null // TODO: get current user
                );

                Notification.show("GST return generated successfully",
                    3000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                dialog.close();
                loadReturns();

                // Open the view dialog for the new return
                openViewDialog(taxReturn);

            } catch (Exception ex) {
                Notification.show("Error: " + ex.getMessage(),
                    3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        Button cancelBtn = new Button("Cancel", e -> dialog.close());

        VerticalLayout content = new VerticalLayout(form, helpText);
        content.setPadding(false);
        content.setSpacing(true);

        dialog.add(content);
        dialog.getFooter().add(cancelBtn, generateBtn);
        dialog.open();
    }

    private void openViewDialog(TaxReturn taxReturn) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("GST Return: " + taxReturn.getStartDate().format(DATE_FORMAT) +
            " to " + taxReturn.getEndDate().format(DATE_FORMAT));
        dialog.setWidth("700px");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);

        // Summary section
        H3 summaryTitle = new H3("Return Summary");

        Grid<SummaryRow> summaryGrid = new Grid<>();
        summaryGrid.setHeight("250px");
        summaryGrid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_NO_BORDER);

        summaryGrid.addColumn(SummaryRow::label)
            .setHeader("Box")
            .setFlexGrow(1);

        summaryGrid.addColumn(r -> formatCurrency(r.amount()))
            .setHeader("Amount")
            .setAutoWidth(true);

        // Populate summary from return lines
        List<SummaryRow> summaryRows = taxReturn.getLines().stream()
            .map(line -> new SummaryRow(
                "Box " + line.getBoxCode() + ": " + line.getBoxDescription(),
                line.getAmount()
            ))
            .toList();

        summaryGrid.setItems(summaryRows);

        // Status and dates
        Div statusInfo = new Div();
        statusInfo.add(new Paragraph("Status: " + taxReturn.getStatus().name()));
        statusInfo.add(new Paragraph("Basis: " + taxReturn.getBasis().name()));
        if (taxReturn.getGeneratedAt() != null) {
            statusInfo.add(new Paragraph("Generated: " +
                taxReturn.getGeneratedAt().toString().substring(0, 16)));
        }
        if (taxReturn.getFinalizedAt() != null) {
            statusInfo.add(new Paragraph("Finalized: " +
                taxReturn.getFinalizedAt().toString().substring(0, 16)));
        }

        // Totals highlight
        HorizontalLayout totalsLayout = new HorizontalLayout();
        totalsLayout.setWidthFull();
        totalsLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.AROUND);

        Div taxPayable = new Div();
        taxPayable.add(new Span("Tax Payable/Refund"));
        H2 payableAmount = new H2(formatCurrency(taxReturn.getTaxPayable()));
        if (taxReturn.getTaxPayable().compareTo(BigDecimal.ZERO) >= 0) {
            payableAmount.getStyle().set("color", "var(--lumo-error-text-color)");
        } else {
            payableAmount.getStyle().set("color", "var(--lumo-success-text-color)");
        }
        taxPayable.add(payableAmount);
        taxPayable.getStyle().set("text-align", "center");

        totalsLayout.add(taxPayable);

        content.add(summaryTitle, summaryGrid, statusInfo, totalsLayout);

        Button closeBtn = new Button("Close", e -> dialog.close());

        dialog.add(content);
        dialog.getFooter().add(closeBtn);
        dialog.open();
    }

    private void finalizeReturn(TaxReturn taxReturn) {
        try {
            taxReturnService.finalizeReturn(taxReturn, null);
            Notification.show("Return finalized",
                3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            loadReturns();
        } catch (IllegalStateException ex) {
            Notification.show(ex.getMessage(),
                3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void deleteReturn(TaxReturn taxReturn) {
        try {
            taxReturnService.deleteReturn(taxReturn);
            Notification.show("Return deleted",
                3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            loadReturns();
        } catch (IllegalStateException ex) {
            Notification.show(ex.getMessage(),
                3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void markAsFiled(TaxReturn taxReturn) {
        try {
            taxReturnService.markAsFiled(taxReturn, null);
            Notification.show("Return marked as filed",
                3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            loadReturns();
        } catch (IllegalStateException ex) {
            Notification.show(ex.getMessage(),
                3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private record SummaryRow(String label, BigDecimal amount) {}
}
