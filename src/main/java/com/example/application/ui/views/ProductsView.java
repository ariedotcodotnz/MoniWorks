package com.example.application.ui.views;

import com.example.application.domain.Account;
import com.example.application.domain.Company;
import com.example.application.domain.Product;
import com.example.application.domain.SavedView.EntityType;
import com.example.application.domain.User;
import com.example.application.service.AccountService;
import com.example.application.service.CompanyContextService;
import com.example.application.service.ProductImportService;
import com.example.application.service.ProductService;
import com.example.application.service.SavedViewService;
import com.example.application.service.TaxCodeService;
import com.example.application.ui.MainLayout;
import com.example.application.ui.components.GridCustomizer;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
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
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import jakarta.annotation.security.PermitAll;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * View for managing Products and Services.
 * Features a master-detail layout with:
 * - Left: searchable product list with category filter
 * - Right: product detail view with pricing, tax, and account defaults
 */
@Route(value = "products", layout = MainLayout.class)
@PageTitle("Products | MoniWorks")
@PermitAll
public class ProductsView extends VerticalLayout {

    private final ProductService productService;
    private final AccountService accountService;
    private final TaxCodeService taxCodeService;
    private final CompanyContextService companyContextService;
    private final SavedViewService savedViewService;
    private final ProductImportService productImportService;

    private final Grid<Product> grid = new Grid<>();
    private final TextField searchField = new TextField();
    private final ComboBox<String> categoryFilter = new ComboBox<>();
    private GridCustomizer<Product> gridCustomizer;

    private final VerticalLayout detailLayout = new VerticalLayout();
    private Product selectedProduct;

    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(new Locale("en", "NZ"));

    public ProductsView(ProductService productService,
                        AccountService accountService,
                        TaxCodeService taxCodeService,
                        CompanyContextService companyContextService,
                        SavedViewService savedViewService,
                        ProductImportService productImportService) {
        this.productService = productService;
        this.accountService = accountService;
        this.taxCodeService = taxCodeService;
        this.companyContextService = companyContextService;
        this.savedViewService = savedViewService;
        this.productImportService = productImportService;

        addClassName("products-view");
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

        loadProducts();
        loadCategories();
    }

    private void configureGrid() {
        grid.addClassNames("products-grid");
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        grid.addColumn(Product::getCode)
            .setHeader("Code")
            .setKey("code")
            .setSortable(true)
            .setResizable(true)
            .setAutoWidth(true)
            .setFlexGrow(0);

        grid.addColumn(Product::getName)
            .setHeader("Name")
            .setKey("name")
            .setSortable(true)
            .setResizable(true)
            .setFlexGrow(1);

        grid.addColumn(Product::getCategory)
            .setHeader("Category")
            .setKey("category")
            .setSortable(true)
            .setResizable(true)
            .setAutoWidth(true);

        grid.addColumn(p -> formatPrice(p.getSellPrice()))
            .setHeader("Sell Price")
            .setKey("sellPrice")
            .setResizable(true)
            .setAutoWidth(true);

        grid.addColumn(p -> formatPrice(p.getBuyPrice()))
            .setHeader("Buy Price")
            .setKey("buyPrice")
            .setResizable(true)
            .setAutoWidth(true);

        grid.addColumn(p -> p.isActive() ? "Active" : "Inactive")
            .setHeader("Status")
            .setKey("status")
            .setResizable(true)
            .setAutoWidth(true);

        grid.asSingleSelect().addValueChangeListener(e -> {
            selectedProduct = e.getValue();
            if (selectedProduct != null) {
                showProductDetail(selectedProduct);
            } else {
                showNoSelection();
            }
        });
    }

    private String formatPrice(BigDecimal price) {
        if (price == null) return "";
        return CURRENCY_FORMAT.format(price);
    }

    private HorizontalLayout createToolbar() {
        H2 title = new H2("Products");

        searchField.setPlaceholder("Search products...");
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setClearButtonVisible(true);
        searchField.addValueChangeListener(e -> filterProducts());
        searchField.setWidth("250px");

        categoryFilter.setPlaceholder("All Categories");
        categoryFilter.setClearButtonVisible(true);
        categoryFilter.addValueChangeListener(e -> filterProducts());
        categoryFilter.setWidth("150px");

        // Grid customizer for column visibility and saved views
        Company company = companyContextService.getCurrentCompany();
        User user = companyContextService.getCurrentUser();
        if (company != null && user != null) {
            gridCustomizer = new GridCustomizer<>(
                grid, EntityType.PRODUCT, savedViewService, company, user
            );
        }

        Button addButton = new Button("Add Product", VaadinIcon.PLUS.create());
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addButton.addClickListener(e -> openProductDialog(null));

        Button importButton = new Button("Import CSV", VaadinIcon.UPLOAD.create());
        importButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        importButton.addClickListener(e -> openImportDialog());

        Button refreshButton = new Button(VaadinIcon.REFRESH.create());
        refreshButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        refreshButton.addClickListener(e -> {
            loadProducts();
            loadCategories();
        });
        refreshButton.getElement().setAttribute("title", "Refresh");

        HorizontalLayout filters = new HorizontalLayout(searchField, categoryFilter);
        if (gridCustomizer != null) {
            filters.add(gridCustomizer);
        }
        filters.add(addButton, importButton, refreshButton);
        filters.setAlignItems(FlexComponent.Alignment.BASELINE);

        HorizontalLayout toolbar = new HorizontalLayout(title, filters);
        toolbar.setWidthFull();
        toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        toolbar.setAlignItems(FlexComponent.Alignment.CENTER);
        toolbar.setPadding(true);

        return toolbar;
    }

    private void loadProducts() {
        Company company = companyContextService.getCurrentCompany();
        List<Product> products = productService.findByCompany(company);
        grid.setItems(products);
    }

    private void loadCategories() {
        Company company = companyContextService.getCurrentCompany();
        List<String> categories = productService.getCategories(company);
        categoryFilter.setItems(categories);
    }

    private void filterProducts() {
        Company company = companyContextService.getCurrentCompany();
        String search = searchField.getValue();
        String category = categoryFilter.getValue();

        List<Product> products;
        if (search != null && !search.isBlank()) {
            products = productService.searchByCompany(company, search);
        } else if (category != null && !category.isBlank()) {
            products = productService.findByCompanyAndCategory(company, category);
        } else {
            products = productService.findByCompany(company);
        }

        // Apply category filter if both search and category are specified
        if (search != null && !search.isBlank() && category != null && !category.isBlank()) {
            products = products.stream()
                .filter(p -> category.equals(p.getCategory()))
                .toList();
        }

        grid.setItems(products);
    }

    private void showNoSelection() {
        detailLayout.removeAll();
        Span message = new Span("Select a product to view details");
        message.getStyle().set("color", "var(--lumo-secondary-text-color)");
        detailLayout.add(message);
        detailLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        detailLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
    }

    private void showProductDetail(Product product) {
        detailLayout.removeAll();
        detailLayout.setAlignItems(FlexComponent.Alignment.STRETCH);
        detailLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.START);

        // Header with name and actions
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        H3 nameLabel = new H3(product.getName());

        if (!product.isActive()) {
            Span inactiveLabel = new Span("INACTIVE");
            inactiveLabel.getElement().getThemeList().add("badge error");
            header.add(nameLabel, inactiveLabel);
        } else {
            header.add(nameLabel);
        }

        Button editButton = new Button("Edit", VaadinIcon.EDIT.create());
        editButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        editButton.addClickListener(e -> openProductDialog(product));

        HorizontalLayout spacer = new HorizontalLayout();
        spacer.setWidthFull();
        header.add(spacer, editButton);
        header.expand(spacer);

        detailLayout.add(header);

        // Product details
        FormLayout form = new FormLayout();
        form.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("500px", 2)
        );

        addReadOnlyField(form, "Code", product.getCode());
        addReadOnlyField(form, "Name", product.getName());
        addReadOnlyField(form, "Category", product.getCategory());
        addReadOnlyField(form, "Description", product.getDescription());

        detailLayout.add(form);

        // Pricing section
        FormLayout pricingForm = new FormLayout();
        pricingForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));

        addReadOnlyField(pricingForm, "Sell Price", formatPrice(product.getSellPrice()));
        addReadOnlyField(pricingForm, "Buy Price", formatPrice(product.getBuyPrice()));

        detailLayout.add(new H3("Pricing"), pricingForm);

        // Tax & Accounts section
        FormLayout accountsForm = new FormLayout();
        accountsForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));

        addReadOnlyField(accountsForm, "Tax Code", product.getTaxCode());
        addReadOnlyField(accountsForm, "Sales Account",
            product.getSalesAccount() != null
                ? product.getSalesAccount().getCode() + " - " + product.getSalesAccount().getName()
                : "Not set");
        addReadOnlyField(accountsForm, "Purchase Account",
            product.getPurchaseAccount() != null
                ? product.getPurchaseAccount().getCode() + " - " + product.getPurchaseAccount().getName()
                : "Not set");

        detailLayout.add(new H3("Tax & Accounts"), accountsForm);

        // Other details
        FormLayout otherForm = new FormLayout();
        otherForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));

        addReadOnlyField(otherForm, "Barcode", product.getBarcode());
        addReadOnlyField(otherForm, "Inventoried", product.isInventoried() ? "Yes" : "No");

        detailLayout.add(new H3("Other"), otherForm);

        // Sticky note (if present)
        if (product.getStickyNote() != null && !product.getStickyNote().isBlank()) {
            VerticalLayout noteLayout = new VerticalLayout();
            noteLayout.setPadding(true);
            noteLayout.getStyle()
                .set("background-color", "var(--lumo-contrast-5pct)")
                .set("border-radius", "var(--lumo-border-radius-m)");

            Span noteLabel = new Span("Sticky Note:");
            noteLabel.getStyle().set("font-weight", "bold");
            Span noteText = new Span(product.getStickyNote());

            noteLayout.add(noteLabel, noteText);
            detailLayout.add(noteLayout);
        }
    }

    private void addReadOnlyField(FormLayout form, String label, String value) {
        TextField field = new TextField(label);
        field.setValue(value != null ? value : "");
        field.setReadOnly(true);
        form.add(field);
    }

    private void openProductDialog(Product product) {
        boolean isNew = product == null;

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(isNew ? "Add Product" : "Edit Product");
        dialog.setWidth("600px");

        FormLayout form = new FormLayout();
        form.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 2)
        );

        TextField codeField = new TextField("Code");
        codeField.setMaxLength(31);
        codeField.setRequired(true);
        codeField.setHelperText("Up to 31 characters");
        if (!isNew) {
            codeField.setValue(product.getCode());
            codeField.setReadOnly(true);
        }

        TextField nameField = new TextField("Name");
        nameField.setMaxLength(100);
        nameField.setRequired(true);
        if (!isNew) nameField.setValue(product.getName());

        TextArea descriptionField = new TextArea("Description");
        descriptionField.setMaxLength(500);
        descriptionField.setHeight("100px");
        if (!isNew && product.getDescription() != null) descriptionField.setValue(product.getDescription());

        TextField categoryField = new TextField("Category");
        categoryField.setMaxLength(50);
        if (!isNew && product.getCategory() != null) categoryField.setValue(product.getCategory());

        // Pricing
        BigDecimalField sellPriceField = new BigDecimalField("Sell Price");
        sellPriceField.setPrefixComponent(new Span("$"));
        if (!isNew && product.getSellPrice() != null) sellPriceField.setValue(product.getSellPrice());

        BigDecimalField buyPriceField = new BigDecimalField("Buy Price");
        buyPriceField.setPrefixComponent(new Span("$"));
        if (!isNew && product.getBuyPrice() != null) buyPriceField.setValue(product.getBuyPrice());

        // Tax code
        Company company = companyContextService.getCurrentCompany();
        ComboBox<String> taxCodeCombo = new ComboBox<>("Tax Code");
        List<String> taxCodes = taxCodeService.findActiveByCompany(company).stream()
            .map(tc -> tc.getCode())
            .toList();
        taxCodeCombo.setItems(taxCodes);
        if (!isNew && product.getTaxCode() != null) taxCodeCombo.setValue(product.getTaxCode());

        // Accounts - filtered by security level
        int securityLevel = companyContextService.getCurrentSecurityLevel();
        List<Account> incomeAccounts = accountService.findByTypeWithSecurityLevel(company.getId(), Account.AccountType.INCOME, securityLevel);
        ComboBox<Account> salesAccountCombo = new ComboBox<>("Sales Account");
        salesAccountCombo.setItems(incomeAccounts);
        salesAccountCombo.setItemLabelGenerator(a -> a.getCode() + " - " + a.getName());
        if (!isNew && product.getSalesAccount() != null) salesAccountCombo.setValue(product.getSalesAccount());

        List<Account> expenseAccounts = accountService.findByTypeWithSecurityLevel(company.getId(), Account.AccountType.EXPENSE, securityLevel);
        ComboBox<Account> purchaseAccountCombo = new ComboBox<>("Purchase Account");
        purchaseAccountCombo.setItems(expenseAccounts);
        purchaseAccountCombo.setItemLabelGenerator(a -> a.getCode() + " - " + a.getName());
        if (!isNew && product.getPurchaseAccount() != null) purchaseAccountCombo.setValue(product.getPurchaseAccount());

        // Other fields
        TextField barcodeField = new TextField("Barcode");
        barcodeField.setMaxLength(50);
        if (!isNew && product.getBarcode() != null) barcodeField.setValue(product.getBarcode());

        Checkbox inventoriedCheckbox = new Checkbox("Track Inventory");
        inventoriedCheckbox.setValue(!isNew && product.isInventoried());
        inventoriedCheckbox.setHelperText("Phase 2 feature - for future use");

        TextArea stickyNoteField = new TextArea("Sticky Note");
        stickyNoteField.setMaxLength(500);
        stickyNoteField.setHeight("80px");
        stickyNoteField.setHelperText("Appears when product is selected on invoices/bills");
        if (!isNew && product.getStickyNote() != null) stickyNoteField.setValue(product.getStickyNote());

        Checkbox activeCheckbox = new Checkbox("Active");
        activeCheckbox.setValue(isNew || product.isActive());

        form.add(codeField, nameField);
        form.add(descriptionField, 2);
        form.add(categoryField, 2);
        form.add(new H3("Pricing"), 2);
        form.add(sellPriceField, buyPriceField);
        form.add(new H3("Tax & Accounts"), 2);
        form.add(taxCodeCombo, 2);
        form.add(salesAccountCombo, purchaseAccountCombo);
        form.add(new H3("Other"), 2);
        form.add(barcodeField, inventoriedCheckbox);
        form.add(stickyNoteField, 2);
        form.add(activeCheckbox, 2);

        Button saveButton = new Button("Save");
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.addClickListener(e -> {
            if (codeField.isEmpty() || nameField.isEmpty()) {
                Notification.show("Please fill in all required fields", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                Product p;
                if (isNew) {
                    p = productService.createProduct(
                        company,
                        codeField.getValue().trim().toUpperCase(),
                        nameField.getValue().trim()
                    );
                } else {
                    p = product;
                    p.setName(nameField.getValue().trim());
                }

                p.setDescription(emptyToNull(descriptionField.getValue()));
                p.setCategory(emptyToNull(categoryField.getValue()));
                p.setSellPrice(sellPriceField.getValue());
                p.setBuyPrice(buyPriceField.getValue());
                p.setTaxCode(taxCodeCombo.getValue());
                p.setSalesAccount(salesAccountCombo.getValue());
                p.setPurchaseAccount(purchaseAccountCombo.getValue());
                p.setBarcode(emptyToNull(barcodeField.getValue()));
                p.setInventoried(inventoriedCheckbox.getValue());
                p.setStickyNote(emptyToNull(stickyNoteField.getValue()));
                p.setActive(activeCheckbox.getValue());

                productService.save(p);

                Notification.show(isNew ? "Product created" : "Product updated", 3000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                dialog.close();
                loadProducts();
                loadCategories();

                // Re-select the product to refresh detail view
                if (!isNew) {
                    grid.select(p);
                }
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

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void openImportDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Import Products from CSV");
        dialog.setWidth("600px");

        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);

        // Instructions
        Span instructions = new Span("Upload a CSV file with product data. Required columns: code, name. " +
            "Optional columns: description, category, buyPrice, sellPrice, taxCode, barcode, " +
            "salesAccountCode, purchaseAccountCode, stickyNote.");
        instructions.getStyle()
            .set("color", "var(--lumo-secondary-text-color)")
            .set("font-size", "var(--lumo-font-size-s)");

        // Download sample CSV link
        String sampleCsv = productImportService.getSampleCsvContent();
        StreamResource sampleResource = new StreamResource("products_sample.csv",
            () -> new ByteArrayInputStream(sampleCsv.getBytes(StandardCharsets.UTF_8)));
        sampleResource.setContentType("text/csv");
        Anchor downloadSample = new Anchor(sampleResource, "Download sample CSV");
        downloadSample.getElement().setAttribute("download", true);
        downloadSample.getStyle().set("font-size", "var(--lumo-font-size-s)");

        // Update existing checkbox
        Checkbox updateExisting = new Checkbox("Update existing products (match by code)");
        updateExisting.setValue(false);
        updateExisting.setHelperText("If unchecked, products with existing codes will be skipped");

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

        upload.addSucceededListener(event -> {
            try {
                // Read the stream into bytes for multiple uses
                uploadedBytes[0] = buffer.getInputStream().readAllBytes();

                // Preview the import
                Company company = companyContextService.getCurrentCompany();
                ProductImportService.ImportResult preview = productImportService.previewImport(
                    new ByteArrayInputStream(uploadedBytes[0]),
                    company,
                    updateExisting.getValue()
                );

                resultArea.removeAll();
                if (preview.success()) {
                    Span previewText = new Span(String.format(
                        "Preview: %d products to import, %d to update, %d to skip",
                        preview.imported(), preview.updated(), preview.skipped()));
                    previewText.getStyle().set("color", "var(--lumo-success-text-color)");
                    resultArea.add(previewText);

                    if (!preview.warnings().isEmpty()) {
                        for (String warning : preview.warnings()) {
                            Span warningSpan = new Span(warning);
                            warningSpan.getStyle().set("color", "var(--lumo-warning-text-color)")
                                .set("font-size", "var(--lumo-font-size-s)");
                            resultArea.add(warningSpan);
                        }
                    }

                    importButton.setEnabled(preview.imported() > 0 || preview.updated() > 0);
                } else {
                    for (String error : preview.errors()) {
                        Span errorSpan = new Span(error);
                        errorSpan.getStyle().set("color", "var(--lumo-error-text-color)");
                        resultArea.add(errorSpan);
                    }
                    importButton.setEnabled(false);
                }
                resultArea.setVisible(true);
            } catch (IOException e) {
                Notification.show("Error reading file: " + e.getMessage(), 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        upload.addFileRejectedListener(event -> {
            Notification.show("Invalid file: " + event.getErrorMessage(), 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        });

        importButton.addClickListener(e -> {
            if (uploadedBytes[0] == null) {
                Notification.show("Please upload a file first", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                Company company = companyContextService.getCurrentCompany();
                User user = companyContextService.getCurrentUser();
                ProductImportService.ImportResult result = productImportService.importProducts(
                    new ByteArrayInputStream(uploadedBytes[0]),
                    company,
                    user,
                    updateExisting.getValue()
                );

                if (result.success()) {
                    Notification.show(String.format(
                        "Import complete: %d imported, %d updated, %d skipped",
                        result.imported(), result.updated(), result.skipped()),
                        5000, Notification.Position.BOTTOM_START)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    dialog.close();
                    loadProducts();
                    loadCategories();
                } else {
                    resultArea.removeAll();
                    for (String error : result.errors()) {
                        Span errorSpan = new Span(error);
                        errorSpan.getStyle().set("color", "var(--lumo-error-text-color)");
                        resultArea.add(errorSpan);
                    }
                }
            } catch (IOException ex) {
                Notification.show("Import failed: " + ex.getMessage(), 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        Button cancelButton = new Button("Cancel", e -> dialog.close());

        layout.add(instructions, downloadSample, updateExisting, upload, resultArea);

        dialog.add(layout);
        dialog.getFooter().add(cancelButton, importButton);
        dialog.open();
    }
}
