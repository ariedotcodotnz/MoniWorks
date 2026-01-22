package com.example.application.ui.views;

import com.example.application.domain.*;
import com.example.application.domain.Contact.ContactType;
import com.example.application.domain.SavedView.EntityType;
import com.example.application.service.AccountService;
import com.example.application.service.CompanyContextService;
import com.example.application.service.ContactImportService;
import com.example.application.service.ContactService;
import com.example.application.service.SavedViewService;
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
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import jakarta.annotation.security.PermitAll;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * View for managing Contacts (Customers and Suppliers).
 * Features a master-detail layout with:
 * - Left: searchable contact list
 * - Right: tabbed detail view (General, People, Defaults, Notes)
 */
@Route(value = "contacts", layout = MainLayout.class)
@PageTitle("Contacts | MoniWorks")
@PermitAll
public class ContactsView extends VerticalLayout {

    private final ContactService contactService;
    private final AccountService accountService;
    private final CompanyContextService companyContextService;
    private final SavedViewService savedViewService;
    private final ContactImportService contactImportService;

    private final Grid<Contact> grid = new Grid<>();
    private final TextField searchField = new TextField();
    private final ComboBox<String> typeFilter = new ComboBox<>();
    private GridCustomizer<Contact> gridCustomizer;

    private final VerticalLayout detailLayout = new VerticalLayout();
    private Contact selectedContact;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    public ContactsView(ContactService contactService,
                        AccountService accountService,
                        CompanyContextService companyContextService,
                        SavedViewService savedViewService,
                        ContactImportService contactImportService) {
        this.contactService = contactService;
        this.accountService = accountService;
        this.companyContextService = companyContextService;
        this.savedViewService = savedViewService;
        this.contactImportService = contactImportService;

        addClassName("contacts-view");
        setSizeFull();
        setPadding(false);

        add(createToolbar());

        SplitLayout splitLayout = new SplitLayout();
        splitLayout.setSizeFull();
        splitLayout.setSplitterPosition(40);

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

        loadContacts();
    }

    private void configureGrid() {
        grid.addClassNames("contacts-grid");
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        grid.addColumn(Contact::getCode)
            .setHeader("Code")
            .setKey("code")
            .setSortable(true)
            .setAutoWidth(true)
            .setResizable(true)
            .setFlexGrow(0);

        grid.addColumn(Contact::getName)
            .setHeader("Name")
            .setKey("name")
            .setSortable(true)
            .setResizable(true)
            .setFlexGrow(1);

        grid.addColumn(c -> c.getType().name())
            .setHeader("Type")
            .setKey("type")
            .setSortable(true)
            .setResizable(true)
            .setAutoWidth(true);

        grid.addColumn(Contact::getEmail)
            .setHeader("Email")
            .setKey("email")
            .setResizable(true)
            .setAutoWidth(true);

        grid.addColumn(Contact::getPhone)
            .setHeader("Phone")
            .setKey("phone")
            .setResizable(true)
            .setAutoWidth(true);

        grid.addColumn(Contact::getCategory)
            .setHeader("Category")
            .setKey("category")
            .setResizable(true)
            .setAutoWidth(true);

        grid.addColumn(c -> c.isActive() ? "Active" : "Inactive")
            .setHeader("Status")
            .setKey("status")
            .setResizable(true)
            .setAutoWidth(true);

        grid.asSingleSelect().addValueChangeListener(e -> {
            selectedContact = e.getValue();
            if (selectedContact != null) {
                showContactDetail(selectedContact);
            } else {
                showNoSelection();
            }
        });
    }

    private HorizontalLayout createToolbar() {
        H2 title = new H2("Contacts");

        searchField.setPlaceholder("Search contacts...");
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setClearButtonVisible(true);
        searchField.addValueChangeListener(e -> filterContacts());
        searchField.setWidth("250px");

        typeFilter.setPlaceholder("All Types");
        typeFilter.setItems("All", "Customer", "Supplier", "Both");
        typeFilter.setValue("All");
        typeFilter.addValueChangeListener(e -> filterContacts());
        typeFilter.setWidth("130px");

        // Grid customizer for column visibility and saved views
        Company company = companyContextService.getCurrentCompany();
        User user = companyContextService.getCurrentUser();
        if (company != null && user != null) {
            gridCustomizer = new GridCustomizer<>(
                grid, EntityType.CONTACT, savedViewService, company, user
            );
        }

        Button addButton = new Button("Add Contact", VaadinIcon.PLUS.create());
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addButton.addClickListener(e -> openContactDialog(null));

        Button importButton = new Button("Import CSV", VaadinIcon.UPLOAD.create());
        importButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        importButton.addClickListener(e -> openImportDialog());

        Button refreshButton = new Button(VaadinIcon.REFRESH.create());
        refreshButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        refreshButton.addClickListener(e -> loadContacts());
        refreshButton.getElement().setAttribute("title", "Refresh");

        HorizontalLayout filters = new HorizontalLayout(searchField, typeFilter);
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

    private void loadContacts() {
        Company company = companyContextService.getCurrentCompany();
        List<Contact> contacts = contactService.findByCompany(company);
        grid.setItems(contacts);
    }

    private void filterContacts() {
        Company company = companyContextService.getCurrentCompany();
        String search = searchField.getValue();
        String typeFilterValue = typeFilter.getValue();

        List<Contact> contacts;
        if (search != null && !search.isBlank()) {
            contacts = contactService.searchByCompany(company, search);
        } else {
            contacts = contactService.findByCompany(company);
        }

        // Filter by type if not "All"
        if (typeFilterValue != null && !"All".equals(typeFilterValue)) {
            ContactType filterType = ContactType.valueOf(typeFilterValue.toUpperCase());
            contacts = contacts.stream()
                .filter(c -> c.getType() == filterType || c.getType() == ContactType.BOTH)
                .toList();
        }

        grid.setItems(contacts);
    }

    private void showNoSelection() {
        detailLayout.removeAll();
        Span message = new Span("Select a contact to view details");
        message.getStyle().set("color", "var(--lumo-secondary-text-color)");
        detailLayout.add(message);
        detailLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        detailLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
    }

    private void showContactDetail(Contact contact) {
        detailLayout.removeAll();
        detailLayout.setAlignItems(FlexComponent.Alignment.STRETCH);
        detailLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.START);

        // Header with name and actions
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        H3 nameLabel = new H3(contact.getName());
        Span typeLabel = new Span(contact.getType().name());
        typeLabel.getElement().getThemeList().add("badge");
        if (!contact.isActive()) {
            Span inactiveLabel = new Span("INACTIVE");
            inactiveLabel.getElement().getThemeList().add("badge error");
            header.add(nameLabel, typeLabel, inactiveLabel);
        } else {
            header.add(nameLabel, typeLabel);
        }

        Button editButton = new Button("Edit", VaadinIcon.EDIT.create());
        editButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        editButton.addClickListener(e -> openContactDialog(contact));

        HorizontalLayout spacer = new HorizontalLayout();
        spacer.setWidthFull();
        header.add(spacer, editButton);
        header.expand(spacer);

        detailLayout.add(header);

        // Tabsheet with detail sections
        TabSheet tabSheet = new TabSheet();
        tabSheet.setSizeFull();

        tabSheet.add("General", createGeneralTab(contact));
        tabSheet.add("People", createPeopleTab(contact));
        tabSheet.add("Defaults", createDefaultsTab(contact));
        tabSheet.add("Notes", createNotesTab(contact));

        detailLayout.add(tabSheet);
        detailLayout.expand(tabSheet);
    }

    private VerticalLayout createGeneralTab(Contact contact) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);

        FormLayout form = new FormLayout();
        form.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("500px", 2)
        );

        addReadOnlyField(form, "Code", contact.getCode());
        addReadOnlyField(form, "Name", contact.getName());
        addReadOnlyField(form, "Type", contact.getType().name());
        addReadOnlyField(form, "Category", contact.getCategory());

        layout.add(new H3("Contact Information"), form);

        // Address section
        FormLayout addressForm = new FormLayout();
        addressForm.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("500px", 2)
        );
        addReadOnlyField(addressForm, "Address", contact.getFormattedAddress());
        addReadOnlyField(addressForm, "Phone", contact.getPhone());
        addReadOnlyField(addressForm, "Mobile", contact.getMobile());
        addReadOnlyField(addressForm, "Email", contact.getEmail());
        addReadOnlyField(addressForm, "Website", contact.getWebsite());

        layout.add(new H3("Address & Contact"), addressForm);

        // Bank details section
        if (contact.getBankName() != null || contact.getBankAccountNumber() != null) {
            FormLayout bankForm = new FormLayout();
            bankForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
            addReadOnlyField(bankForm, "Bank Name", contact.getBankName());
            addReadOnlyField(bankForm, "Account Number", contact.getBankAccountNumber());
            addReadOnlyField(bankForm, "Routing", contact.getBankRouting());
            layout.add(new H3("Bank Details"), bankForm);
        }

        return layout;
    }

    private VerticalLayout createPeopleTab(Contact contact) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSizeFull();

        Button addPersonButton = new Button("Add Person", VaadinIcon.PLUS.create());
        addPersonButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
        addPersonButton.addClickListener(e -> openPersonDialog(contact, null));

        Grid<ContactPerson> peopleGrid = new Grid<>();
        peopleGrid.setHeight("300px");
        peopleGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

        peopleGrid.addColumn(ContactPerson::getName).setHeader("Name").setFlexGrow(1);
        peopleGrid.addColumn(ContactPerson::getEmail).setHeader("Email").setAutoWidth(true);
        peopleGrid.addColumn(ContactPerson::getPhone).setHeader("Phone").setAutoWidth(true);
        peopleGrid.addColumn(ContactPerson::getRoleLabel).setHeader("Role").setAutoWidth(true);
        peopleGrid.addColumn(p -> p.isPrimary() ? "Primary" : "").setHeader("").setAutoWidth(true);

        peopleGrid.addComponentColumn(person -> {
            Button editBtn = new Button(VaadinIcon.EDIT.create());
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            editBtn.addClickListener(e -> openPersonDialog(contact, person));

            Button deleteBtn = new Button(VaadinIcon.TRASH.create());
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
            deleteBtn.addClickListener(e -> {
                contactService.deletePerson(person);
                peopleGrid.setItems(contactService.findPeopleByContact(contact));
                Notification.show("Person removed", 2000, Notification.Position.BOTTOM_START);
            });

            HorizontalLayout actions = new HorizontalLayout(editBtn, deleteBtn);
            actions.setSpacing(false);
            return actions;
        }).setHeader("Actions").setAutoWidth(true);

        List<ContactPerson> people = contactService.findPeopleByContact(contact);
        peopleGrid.setItems(people);

        layout.add(addPersonButton, peopleGrid);
        layout.expand(peopleGrid);

        return layout;
    }

    private VerticalLayout createDefaultsTab(Contact contact) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);

        FormLayout form = new FormLayout();
        form.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("500px", 2)
        );

        addReadOnlyField(form, "Default Account",
            contact.getDefaultAccount() != null
                ? contact.getDefaultAccount().getCode() + " - " + contact.getDefaultAccount().getName()
                : "Not set");
        addReadOnlyField(form, "Payment Terms", contact.getPaymentTerms());
        addReadOnlyField(form, "Tax Override", contact.getTaxOverrideCode());
        addReadOnlyField(form, "Credit Limit",
            contact.getCreditLimit() != null ? "$" + contact.getCreditLimit().toPlainString() : "No limit");

        layout.add(form);
        return layout;
    }

    private VerticalLayout createNotesTab(Contact contact) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSizeFull();

        Button addNoteButton = new Button("Add Note", VaadinIcon.PLUS.create());
        addNoteButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
        addNoteButton.addClickListener(e -> openNoteDialog(contact, null));

        Grid<ContactNote> notesGrid = new Grid<>();
        notesGrid.setHeight("300px");
        notesGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

        notesGrid.addColumn(n -> formatTimestamp(n.getCreatedAt()))
            .setHeader("Date")
            .setAutoWidth(true)
            .setFlexGrow(0);

        notesGrid.addColumn(n -> n.getCreatedBy() != null ? n.getCreatedBy().getDisplayName() : "")
            .setHeader("By")
            .setAutoWidth(true);

        notesGrid.addColumn(ContactNote::getNoteText)
            .setHeader("Note")
            .setFlexGrow(1);

        notesGrid.addColumn(n -> n.getFollowUpDate() != null ? n.getFollowUpDate().format(DATE_FORMATTER) : "")
            .setHeader("Follow-up")
            .setAutoWidth(true);

        notesGrid.addComponentColumn(note -> {
            Button deleteBtn = new Button(VaadinIcon.TRASH.create());
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
            deleteBtn.addClickListener(e -> {
                contactService.deleteNote(note);
                notesGrid.setItems(contactService.findNotesByContact(contact));
                Notification.show("Note deleted", 2000, Notification.Position.BOTTOM_START);
            });
            return deleteBtn;
        }).setHeader("").setAutoWidth(true);

        List<ContactNote> notes = contactService.findNotesByContact(contact);
        notesGrid.setItems(notes);

        layout.add(addNoteButton, notesGrid);
        layout.expand(notesGrid);

        return layout;
    }

    private String formatTimestamp(java.time.Instant instant) {
        if (instant == null) return "";
        return DATETIME_FORMATTER.format(instant.atZone(ZoneId.systemDefault()));
    }

    private void addReadOnlyField(FormLayout form, String label, String value) {
        TextField field = new TextField(label);
        field.setValue(value != null ? value : "");
        field.setReadOnly(true);
        form.add(field);
    }

    private void openContactDialog(Contact contact) {
        boolean isNew = contact == null;

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(isNew ? "Add Contact" : "Edit Contact");
        dialog.setWidth("700px");

        FormLayout form = new FormLayout();
        form.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 2)
        );

        TextField codeField = new TextField("Code");
        codeField.setMaxLength(11);
        codeField.setRequired(true);
        codeField.setHelperText("Up to 11 characters");
        if (!isNew) {
            codeField.setValue(contact.getCode());
            codeField.setReadOnly(true);
        }

        TextField nameField = new TextField("Name");
        nameField.setMaxLength(100);
        nameField.setRequired(true);
        if (!isNew) nameField.setValue(contact.getName());

        ComboBox<ContactType> typeCombo = new ComboBox<>("Type");
        typeCombo.setItems(ContactType.values());
        typeCombo.setRequired(true);
        typeCombo.setValue(isNew ? ContactType.CUSTOMER : contact.getType());

        TextField categoryField = new TextField("Category");
        categoryField.setMaxLength(50);
        if (!isNew && contact.getCategory() != null) categoryField.setValue(contact.getCategory());

        // Address fields
        TextField addressLine1Field = new TextField("Address Line 1");
        addressLine1Field.setMaxLength(255);
        if (!isNew && contact.getAddressLine1() != null) addressLine1Field.setValue(contact.getAddressLine1());

        TextField addressLine2Field = new TextField("Address Line 2");
        addressLine2Field.setMaxLength(255);
        if (!isNew && contact.getAddressLine2() != null) addressLine2Field.setValue(contact.getAddressLine2());

        TextField cityField = new TextField("City");
        cityField.setMaxLength(100);
        if (!isNew && contact.getCity() != null) cityField.setValue(contact.getCity());

        TextField regionField = new TextField("Region/State");
        regionField.setMaxLength(100);
        if (!isNew && contact.getRegion() != null) regionField.setValue(contact.getRegion());

        TextField postalCodeField = new TextField("Postal Code");
        postalCodeField.setMaxLength(20);
        if (!isNew && contact.getPostalCode() != null) postalCodeField.setValue(contact.getPostalCode());

        TextField countryField = new TextField("Country (2-letter)");
        countryField.setMaxLength(2);
        if (!isNew && contact.getCountry() != null) countryField.setValue(contact.getCountry());

        // Contact details
        TextField phoneField = new TextField("Phone");
        phoneField.setMaxLength(50);
        if (!isNew && contact.getPhone() != null) phoneField.setValue(contact.getPhone());

        TextField mobileField = new TextField("Mobile");
        mobileField.setMaxLength(50);
        if (!isNew && contact.getMobile() != null) mobileField.setValue(contact.getMobile());

        TextField emailField = new TextField("Email");
        emailField.setMaxLength(100);
        if (!isNew && contact.getEmail() != null) emailField.setValue(contact.getEmail());

        TextField websiteField = new TextField("Website");
        websiteField.setMaxLength(255);
        if (!isNew && contact.getWebsite() != null) websiteField.setValue(contact.getWebsite());

        // Bank details
        TextField bankNameField = new TextField("Bank Name");
        bankNameField.setMaxLength(100);
        if (!isNew && contact.getBankName() != null) bankNameField.setValue(contact.getBankName());

        TextField bankAccountField = new TextField("Bank Account Number");
        bankAccountField.setMaxLength(50);
        if (!isNew && contact.getBankAccountNumber() != null) bankAccountField.setValue(contact.getBankAccountNumber());

        TextField bankRoutingField = new TextField("Bank Routing");
        bankRoutingField.setMaxLength(50);
        if (!isNew && contact.getBankRouting() != null) bankRoutingField.setValue(contact.getBankRouting());

        // Defaults
        TextField taxOverrideField = new TextField("Tax Override Code");
        taxOverrideField.setMaxLength(10);
        if (!isNew && contact.getTaxOverrideCode() != null) taxOverrideField.setValue(contact.getTaxOverrideCode());

        Company company = companyContextService.getCurrentCompany();
        ComboBox<Account> defaultAccountCombo = new ComboBox<>("Default Account");
        List<Account> accounts = accountService.findActiveByCompany(company);
        defaultAccountCombo.setItems(accounts);
        defaultAccountCombo.setItemLabelGenerator(a -> a.getCode() + " - " + a.getName());
        if (!isNew && contact.getDefaultAccount() != null) {
            defaultAccountCombo.setValue(contact.getDefaultAccount());
        }

        TextField paymentTermsField = new TextField("Payment Terms");
        paymentTermsField.setMaxLength(50);
        paymentTermsField.setHelperText("e.g., Net 30, Due on Receipt");
        if (!isNew && contact.getPaymentTerms() != null) paymentTermsField.setValue(contact.getPaymentTerms());

        BigDecimalField creditLimitField = new BigDecimalField("Credit Limit");
        if (!isNew && contact.getCreditLimit() != null) creditLimitField.setValue(contact.getCreditLimit());

        Checkbox activeCheckbox = new Checkbox("Active");
        activeCheckbox.setValue(isNew || contact.isActive());

        form.add(codeField, nameField, typeCombo, categoryField);
        form.add(new H3("Address"));
        form.add(addressLine1Field, addressLine2Field, cityField, regionField, postalCodeField, countryField);
        form.add(new H3("Contact Details"));
        form.add(phoneField, mobileField, emailField, websiteField);
        form.add(new H3("Bank Details"));
        form.add(bankNameField, bankAccountField, bankRoutingField);
        form.add(new H3("Defaults"));
        form.add(defaultAccountCombo, taxOverrideField, paymentTermsField, creditLimitField, activeCheckbox);

        Button saveButton = new Button("Save");
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.addClickListener(e -> {
            if (codeField.isEmpty() || nameField.isEmpty() || typeCombo.isEmpty()) {
                Notification.show("Please fill in all required fields", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                Contact c;
                if (isNew) {
                    c = contactService.createContact(
                        company,
                        codeField.getValue().trim().toUpperCase(),
                        nameField.getValue().trim(),
                        typeCombo.getValue()
                    );
                } else {
                    c = contact;
                    c.setName(nameField.getValue().trim());
                    c.setType(typeCombo.getValue());
                }

                c.setCategory(emptyToNull(categoryField.getValue()));
                c.setAddressLine1(emptyToNull(addressLine1Field.getValue()));
                c.setAddressLine2(emptyToNull(addressLine2Field.getValue()));
                c.setCity(emptyToNull(cityField.getValue()));
                c.setRegion(emptyToNull(regionField.getValue()));
                c.setPostalCode(emptyToNull(postalCodeField.getValue()));
                c.setCountry(emptyToNull(countryField.getValue()));
                c.setPhone(emptyToNull(phoneField.getValue()));
                c.setMobile(emptyToNull(mobileField.getValue()));
                c.setEmail(emptyToNull(emailField.getValue()));
                c.setWebsite(emptyToNull(websiteField.getValue()));
                c.setBankName(emptyToNull(bankNameField.getValue()));
                c.setBankAccountNumber(emptyToNull(bankAccountField.getValue()));
                c.setBankRouting(emptyToNull(bankRoutingField.getValue()));
                c.setTaxOverrideCode(emptyToNull(taxOverrideField.getValue()));
                c.setDefaultAccount(defaultAccountCombo.getValue());
                c.setPaymentTerms(emptyToNull(paymentTermsField.getValue()));
                c.setCreditLimit(creditLimitField.getValue());
                c.setActive(activeCheckbox.getValue());

                contactService.save(c);

                Notification.show(isNew ? "Contact created" : "Contact updated", 3000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                dialog.close();
                loadContacts();

                // Re-select the contact to refresh detail view
                if (!isNew) {
                    grid.select(c);
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

    private void openPersonDialog(Contact contact, ContactPerson person) {
        boolean isNew = person == null;

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(isNew ? "Add Person" : "Edit Person");
        dialog.setWidth("500px");

        FormLayout form = new FormLayout();

        TextField nameField = new TextField("Name");
        nameField.setMaxLength(100);
        nameField.setRequired(true);
        if (!isNew) nameField.setValue(person.getName());

        TextField emailField = new TextField("Email");
        emailField.setMaxLength(100);
        if (!isNew && person.getEmail() != null) emailField.setValue(person.getEmail());

        TextField phoneField = new TextField("Phone");
        phoneField.setMaxLength(50);
        if (!isNew && person.getPhone() != null) phoneField.setValue(person.getPhone());

        TextField roleField = new TextField("Role");
        roleField.setMaxLength(50);
        roleField.setHelperText("e.g., Accountant, Decision Maker");
        if (!isNew && person.getRoleLabel() != null) roleField.setValue(person.getRoleLabel());

        Checkbox primaryCheckbox = new Checkbox("Primary Contact");
        primaryCheckbox.setValue(!isNew && person.isPrimary());

        form.add(nameField, emailField, phoneField, roleField, primaryCheckbox);

        Button saveButton = new Button("Save");
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.addClickListener(e -> {
            if (nameField.isEmpty()) {
                Notification.show("Name is required", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            ContactPerson p = isNew ? contactService.createPerson(contact, nameField.getValue().trim()) : person;
            if (!isNew) p.setName(nameField.getValue().trim());
            p.setEmail(emptyToNull(emailField.getValue()));
            p.setPhone(emptyToNull(phoneField.getValue()));
            p.setRoleLabel(emptyToNull(roleField.getValue()));
            p.setPrimary(primaryCheckbox.getValue());

            contactService.savePerson(p);

            Notification.show(isNew ? "Person added" : "Person updated", 2000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            dialog.close();
            showContactDetail(contact);
        });

        Button cancelButton = new Button("Cancel", e -> dialog.close());

        dialog.add(form);
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private void openNoteDialog(Contact contact, ContactNote note) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Add Note");
        dialog.setWidth("500px");

        TextArea noteTextArea = new TextArea("Note");
        noteTextArea.setMaxLength(2000);
        noteTextArea.setWidthFull();
        noteTextArea.setHeight("150px");

        DatePicker followUpPicker = new DatePicker("Follow-up Date");
        followUpPicker.setHelperText("Optional reminder date");

        VerticalLayout layout = new VerticalLayout(noteTextArea, followUpPicker);
        layout.setPadding(false);

        Button saveButton = new Button("Save");
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.addClickListener(e -> {
            if (noteTextArea.isEmpty()) {
                Notification.show("Note text is required", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            ContactNote n = contactService.createNote(contact, noteTextArea.getValue().trim(), null);
            n.setFollowUpDate(followUpPicker.getValue());
            contactService.saveNote(n);

            Notification.show("Note added", 2000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            dialog.close();
            showContactDetail(contact);
        });

        Button cancelButton = new Button("Cancel", e -> dialog.close());

        dialog.add(layout);
        dialog.getFooter().add(cancelButton, saveButton);
        dialog.open();
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void openImportDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Import Contacts from CSV");
        dialog.setWidth("600px");

        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);

        // Instructions
        Span instructions = new Span("Upload a CSV file with contact data. Required columns: code, name. " +
            "Optional columns: type, email, phone, mobile, addressLine1, addressLine2, city, region, " +
            "postalCode, country, category, paymentTerms, creditLimit, bankName, bankAccountNumber, " +
            "bankRouting, taxOverrideCode.");
        instructions.getStyle()
            .set("color", "var(--lumo-secondary-text-color)")
            .set("font-size", "var(--lumo-font-size-s)");

        // Download sample CSV link
        String sampleCsv = contactImportService.getSampleCsvContent();
        StreamResource sampleResource = new StreamResource("contacts_sample.csv",
            () -> new ByteArrayInputStream(sampleCsv.getBytes(StandardCharsets.UTF_8)));
        sampleResource.setContentType("text/csv");
        Anchor downloadSample = new Anchor(sampleResource, "Download sample CSV");
        downloadSample.getElement().setAttribute("download", true);
        downloadSample.getStyle().set("font-size", "var(--lumo-font-size-s)");

        // Update existing checkbox
        Checkbox updateExisting = new Checkbox("Update existing contacts (match by code)");
        updateExisting.setValue(false);
        updateExisting.setHelperText("If unchecked, contacts with existing codes will be skipped");

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

        // Store the uploaded stream for import
        final InputStream[] uploadedStream = {null};
        final byte[][] uploadedBytes = {null};

        upload.addSucceededListener(event -> {
            try {
                // Read the stream into bytes for multiple uses
                uploadedBytes[0] = buffer.getInputStream().readAllBytes();

                // Preview the import
                Company company = companyContextService.getCurrentCompany();
                ContactImportService.ImportResult preview = contactImportService.previewImport(
                    new ByteArrayInputStream(uploadedBytes[0]),
                    company,
                    updateExisting.getValue()
                );

                resultArea.removeAll();
                if (preview.success()) {
                    Span previewText = new Span(String.format(
                        "Preview: %d contacts to import, %d to update, %d to skip",
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
                ContactImportService.ImportResult result = contactImportService.importContacts(
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
                    loadContacts();
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
