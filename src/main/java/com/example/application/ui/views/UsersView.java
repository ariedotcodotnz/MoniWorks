package com.example.application.ui.views;

import com.example.application.domain.Company;
import com.example.application.domain.CompanyMembership;
import com.example.application.domain.Role;
import com.example.application.domain.User;
import com.example.application.security.Permissions;
import com.example.application.service.CompanyContextService;
import com.example.application.service.RoleService;
import com.example.application.service.UserService;
import com.example.application.ui.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
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
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

/**
 * View for managing users and company memberships.
 * Only accessible to users with MANAGE_USERS permission.
 */
@Route(value = "users", layout = MainLayout.class)
@PageTitle("Users | MoniWorks")
@RolesAllowed({"ADMIN", "ROLE_ADMIN"})
public class UsersView extends VerticalLayout {

    private final UserService userService;
    private final RoleService roleService;
    private final CompanyContextService companyContextService;

    private final Grid<CompanyMembership> membershipGrid;
    private final VerticalLayout detailLayout;
    private CompanyMembership selectedMembership;

    public UsersView(UserService userService, RoleService roleService,
                     CompanyContextService companyContextService) {
        this.userService = userService;
        this.roleService = roleService;
        this.companyContextService = companyContextService;

        setSizeFull();
        setPadding(false);
        setSpacing(false);

        membershipGrid = createMembershipGrid();
        detailLayout = createDetailLayout();

        // Create toolbar
        HorizontalLayout toolbar = createToolbar();

        // Create split layout
        SplitLayout splitLayout = new SplitLayout();
        splitLayout.setSizeFull();
        splitLayout.setSplitterPosition(60);

        VerticalLayout masterLayout = new VerticalLayout(toolbar, membershipGrid);
        masterLayout.setSizeFull();
        masterLayout.setPadding(false);
        masterLayout.setSpacing(false);

        splitLayout.addToPrimary(masterLayout);
        splitLayout.addToSecondary(detailLayout);

        add(splitLayout);

        refreshGrid();
    }

    private HorizontalLayout createToolbar() {
        H2 title = new H2("User Management");
        title.addClassNames(LumoUtility.Margin.NONE);

        Button addUserBtn = new Button("Add User", VaadinIcon.PLUS.create());
        addUserBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addUserBtn.addClickListener(e -> showAddUserDialog());

        Button inviteBtn = new Button("Invite to Company", VaadinIcon.ENVELOPE.create());
        inviteBtn.addClickListener(e -> showInviteDialog());

        HorizontalLayout toolbar = new HorizontalLayout(title, addUserBtn, inviteBtn);
        toolbar.setAlignItems(FlexComponent.Alignment.CENTER);
        toolbar.setWidthFull();
        toolbar.addClassNames(LumoUtility.Padding.MEDIUM);
        toolbar.expand(title);

        return toolbar;
    }

    private Grid<CompanyMembership> createMembershipGrid() {
        Grid<CompanyMembership> grid = new Grid<>();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setSizeFull();

        grid.addColumn(m -> m.getUser().getEmail())
            .setHeader("Email")
            .setSortable(true)
            .setKey("email")
            .setResizable(true);

        grid.addColumn(m -> m.getUser().getDisplayName())
            .setHeader("Name")
            .setSortable(true)
            .setKey("name")
            .setResizable(true);

        grid.addColumn(m -> m.getRole().getName())
            .setHeader("Role")
            .setSortable(true)
            .setKey("role")
            .setResizable(true);

        grid.addColumn(m -> m.getUser().getStatus().name())
            .setHeader("User Status")
            .setSortable(true)
            .setKey("userStatus")
            .setResizable(true);

        grid.addColumn(m -> m.getStatus().name())
            .setHeader("Membership")
            .setSortable(true)
            .setKey("membershipStatus")
            .setResizable(true);

        grid.asSingleSelect().addValueChangeListener(e -> {
            selectedMembership = e.getValue();
            updateDetailLayout();
        });

        return grid;
    }

    private VerticalLayout createDetailLayout() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setPadding(true);
        layout.addClassName(LumoUtility.Background.CONTRAST_5);

        Span placeholder = new Span("Select a user to view details");
        placeholder.addClassNames(LumoUtility.TextColor.SECONDARY);
        layout.add(placeholder);
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        layout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        return layout;
    }

    private void updateDetailLayout() {
        detailLayout.removeAll();

        if (selectedMembership == null) {
            Span placeholder = new Span("Select a user to view details");
            placeholder.addClassNames(LumoUtility.TextColor.SECONDARY);
            detailLayout.add(placeholder);
            detailLayout.setAlignItems(FlexComponent.Alignment.CENTER);
            detailLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
            return;
        }

        detailLayout.setAlignItems(FlexComponent.Alignment.STRETCH);
        detailLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.START);

        User user = selectedMembership.getUser();

        H3 header = new H3(user.getDisplayName());
        header.addClassNames(LumoUtility.Margin.NONE);

        // User info
        Span emailLabel = new Span("Email: " + user.getEmail());
        Span statusLabel = new Span("User Status: " + user.getStatus().name());
        Span roleLabel = new Span("Role: " + selectedMembership.getRole().getName());
        Span membershipStatusLabel = new Span("Membership: " + selectedMembership.getStatus().name());

        // Role permissions
        H3 permissionsHeader = new H3("Role Permissions");
        permissionsHeader.addClassNames(LumoUtility.Margin.Top.LARGE);

        VerticalLayout permissionsLayout = new VerticalLayout();
        permissionsLayout.setPadding(false);
        permissionsLayout.setSpacing(false);

        selectedMembership.getRole().getPermissions().forEach(perm -> {
            Span permSpan = new Span("• " + perm.getName() + " - " +
                (perm.getDescription() != null ? perm.getDescription() : ""));
            permSpan.addClassNames(LumoUtility.FontSize.SMALL);
            permissionsLayout.add(permSpan);
        });

        // Actions
        HorizontalLayout actions = new HorizontalLayout();
        actions.addClassNames(LumoUtility.Margin.Top.LARGE);

        Button changeRoleBtn = new Button("Change Role", VaadinIcon.EDIT.create());
        changeRoleBtn.addClickListener(e -> showChangeRoleDialog(selectedMembership));

        Button removeBtn = new Button("Remove from Company", VaadinIcon.TRASH.create());
        removeBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);
        removeBtn.setEnabled(selectedMembership.getStatus() == CompanyMembership.MembershipStatus.ACTIVE);
        removeBtn.addClickListener(e -> removeMembership(selectedMembership));

        Button reactivateBtn = new Button("Reactivate", VaadinIcon.CHECK.create());
        reactivateBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        reactivateBtn.setVisible(selectedMembership.getStatus() != CompanyMembership.MembershipStatus.ACTIVE);
        reactivateBtn.addClickListener(e -> reactivateMembership(selectedMembership));

        actions.add(changeRoleBtn, removeBtn, reactivateBtn);

        detailLayout.add(header, emailLabel, statusLabel, roleLabel, membershipStatusLabel,
            permissionsHeader, permissionsLayout, actions);
    }

    private void refreshGrid() {
        Company company = companyContextService.getCurrentCompany();
        List<CompanyMembership> memberships = userService.getMembershipsByCompany(company);
        membershipGrid.setItems(memberships);
    }

    private void showAddUserDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Create New User");
        dialog.setWidth("400px");

        FormLayout form = new FormLayout();

        EmailField emailField = new EmailField("Email");
        emailField.setRequired(true);
        emailField.setWidthFull();

        TextField nameField = new TextField("Display Name");
        nameField.setRequired(true);
        nameField.setWidthFull();

        PasswordField passwordField = new PasswordField("Password");
        passwordField.setRequired(true);
        passwordField.setWidthFull();

        ComboBox<Role> roleCombo = new ComboBox<>("Role");
        roleCombo.setWidthFull();
        roleCombo.setItemLabelGenerator(Role::getName);
        roleCombo.setItems(roleService.findAvailableRolesForCompany(companyContextService.getCurrentCompany()));
        roleCombo.setRequired(true);

        form.add(emailField, nameField, passwordField, roleCombo);
        dialog.add(form);

        Button saveBtn = new Button("Create", e -> {
            if (emailField.isEmpty() || nameField.isEmpty() ||
                passwordField.isEmpty() || roleCombo.isEmpty()) {
                Notification.show("Please fill all required fields", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                User user = userService.createUser(
                    emailField.getValue(),
                    nameField.getValue(),
                    passwordField.getValue()
                );

                userService.addToCompany(user, companyContextService.getCurrentCompany(), roleCombo.getValue());

                Notification.show("User created successfully", 3000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshGrid();
            } catch (Exception ex) {
                Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button("Cancel", e -> dialog.close());

        dialog.getFooter().add(cancelBtn, saveBtn);
        dialog.open();
    }

    private void showInviteDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Invite Existing User to Company");
        dialog.setWidth("400px");

        FormLayout form = new FormLayout();

        EmailField emailField = new EmailField("User Email");
        emailField.setRequired(true);
        emailField.setWidthFull();
        emailField.setHelperText("Enter the email of an existing user");

        ComboBox<Role> roleCombo = new ComboBox<>("Role");
        roleCombo.setWidthFull();
        roleCombo.setItemLabelGenerator(Role::getName);
        roleCombo.setItems(roleService.findAvailableRolesForCompany(companyContextService.getCurrentCompany()));
        roleCombo.setRequired(true);

        form.add(emailField, roleCombo);
        dialog.add(form);

        Button inviteBtn = new Button("Invite", e -> {
            if (emailField.isEmpty() || roleCombo.isEmpty()) {
                Notification.show("Please fill all required fields", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                User user = userService.findByEmail(emailField.getValue())
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + emailField.getValue()));

                userService.addToCompany(user, companyContextService.getCurrentCompany(), roleCombo.getValue());

                Notification.show("User invited successfully", 3000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshGrid();
            } catch (Exception ex) {
                Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        inviteBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button("Cancel", e -> dialog.close());

        dialog.getFooter().add(cancelBtn, inviteBtn);
        dialog.open();
    }

    private void showChangeRoleDialog(CompanyMembership membership) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Change Role");
        dialog.setWidth("300px");

        ComboBox<Role> roleCombo = new ComboBox<>("New Role");
        roleCombo.setWidthFull();
        roleCombo.setItemLabelGenerator(Role::getName);
        roleCombo.setItems(roleService.findAvailableRolesForCompany(companyContextService.getCurrentCompany()));
        roleCombo.setValue(membership.getRole());

        dialog.add(roleCombo);

        Button saveBtn = new Button("Save", e -> {
            if (roleCombo.isEmpty()) {
                Notification.show("Please select a role", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                userService.updateMembershipRole(membership, roleCombo.getValue());
                Notification.show("Role updated", 3000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshGrid();
                updateDetailLayout();
            } catch (Exception ex) {
                Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button("Cancel", e -> dialog.close());

        dialog.getFooter().add(cancelBtn, saveBtn);
        dialog.open();
    }

    private void removeMembership(CompanyMembership membership) {
        // Don't allow removing yourself
        User currentUser = companyContextService.getCurrentUser();
        if (currentUser != null && currentUser.getId().equals(membership.getUser().getId())) {
            Notification.show("You cannot remove yourself from the company", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        userService.removeFromCompany(membership);
        Notification.show("User removed from company", 3000, Notification.Position.BOTTOM_START)
            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        refreshGrid();
        updateDetailLayout();
    }

    private void reactivateMembership(CompanyMembership membership) {
        userService.reactivateMembership(membership);
        Notification.show("Membership reactivated", 3000, Notification.Position.BOTTOM_START)
            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        refreshGrid();
        updateDetailLayout();
    }
}
