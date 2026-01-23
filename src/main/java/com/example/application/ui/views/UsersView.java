package com.example.application.ui.views;

import com.example.application.domain.Company;
import com.example.application.domain.CompanyMembership;
import com.example.application.domain.Role;
import com.example.application.domain.User;
import com.example.application.domain.UserInvitation;
import com.example.application.domain.UserSecurityLevel;
import com.example.application.repository.UserSecurityLevelRepository;
import com.example.application.security.Permissions;
import com.example.application.service.CompanyContextService;
import com.example.application.service.InvitationService;
import com.example.application.service.InvitationService.InvitationResult;
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
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.RolesAllowed;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
    private final InvitationService invitationService;
    private final UserSecurityLevelRepository securityLevelRepository;

    private final Grid<CompanyMembership> membershipGrid;
    private final Grid<UserInvitation> invitationsGrid;
    private final VerticalLayout detailLayout;
    private CompanyMembership selectedMembership;
    private UserInvitation selectedInvitation;
    private TabSheet tabSheet;

    public UsersView(UserService userService, RoleService roleService,
                     CompanyContextService companyContextService,
                     InvitationService invitationService,
                     UserSecurityLevelRepository securityLevelRepository) {
        this.userService = userService;
        this.roleService = roleService;
        this.companyContextService = companyContextService;
        this.invitationService = invitationService;
        this.securityLevelRepository = securityLevelRepository;

        setSizeFull();
        setPadding(false);
        setSpacing(false);

        membershipGrid = createMembershipGrid();
        invitationsGrid = createInvitationsGrid();
        detailLayout = createDetailLayout();

        // Create toolbar
        HorizontalLayout toolbar = createToolbar();

        // Create tab sheet for members and invitations
        tabSheet = new TabSheet();
        tabSheet.setSizeFull();

        VerticalLayout membersTab = new VerticalLayout(membershipGrid);
        membersTab.setSizeFull();
        membersTab.setPadding(false);
        membersTab.setSpacing(false);

        VerticalLayout invitationsTab = new VerticalLayout(invitationsGrid);
        invitationsTab.setSizeFull();
        invitationsTab.setPadding(false);
        invitationsTab.setSpacing(false);

        tabSheet.add("Members", membersTab);
        tabSheet.add("Pending Invitations", invitationsTab);

        tabSheet.addSelectedChangeListener(e -> {
            selectedMembership = null;
            selectedInvitation = null;
            updateDetailLayout();
        });

        // Create split layout
        SplitLayout splitLayout = new SplitLayout();
        splitLayout.setSizeFull();
        splitLayout.setSplitterPosition(60);

        VerticalLayout masterLayout = new VerticalLayout(toolbar, tabSheet);
        masterLayout.setSizeFull();
        masterLayout.setPadding(false);
        masterLayout.setSpacing(false);

        splitLayout.addToPrimary(masterLayout);
        splitLayout.addToSecondary(detailLayout);

        add(splitLayout);

        refreshGrid();
        refreshInvitationsGrid();
    }

    private HorizontalLayout createToolbar() {
        H2 title = new H2("User Management");
        title.addClassNames(LumoUtility.Margin.NONE);

        Button addUserBtn = new Button("Add User", VaadinIcon.PLUS.create());
        addUserBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addUserBtn.addClickListener(e -> showAddUserDialog());

        Button inviteByEmailBtn = new Button("Invite by Email", VaadinIcon.ENVELOPE.create());
        inviteByEmailBtn.addClickListener(e -> showInviteByEmailDialog());

        Button inviteExistingBtn = new Button("Add Existing User", VaadinIcon.USER.create());
        inviteExistingBtn.addClickListener(e -> showInviteExistingUserDialog());

        HorizontalLayout toolbar = new HorizontalLayout(title, addUserBtn, inviteByEmailBtn, inviteExistingBtn);
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
            selectedInvitation = null;
            updateDetailLayout();
        });

        return grid;
    }

    private Grid<UserInvitation> createInvitationsGrid() {
        Grid<UserInvitation> grid = new Grid<>();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setSizeFull();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

        grid.addColumn(UserInvitation::getEmail)
            .setHeader("Email")
            .setSortable(true)
            .setKey("email")
            .setResizable(true);

        grid.addColumn(i -> i.getDisplayName() != null ? i.getDisplayName() : "-")
            .setHeader("Name")
            .setSortable(true)
            .setKey("name")
            .setResizable(true);

        grid.addColumn(i -> i.getRole().getName())
            .setHeader("Role")
            .setSortable(true)
            .setKey("role")
            .setResizable(true);

        grid.addColumn(i -> i.getStatus().name())
            .setHeader("Status")
            .setSortable(true)
            .setKey("status")
            .setResizable(true);

        grid.addColumn(i -> formatter.format(i.getExpiresAt()))
            .setHeader("Expires")
            .setSortable(true)
            .setKey("expires")
            .setResizable(true);

        grid.addColumn(i -> i.getInvitedBy() != null ? i.getInvitedBy().getDisplayName() : "-")
            .setHeader("Invited By")
            .setSortable(true)
            .setKey("invitedBy")
            .setResizable(true);

        grid.asSingleSelect().addValueChangeListener(e -> {
            selectedInvitation = e.getValue();
            selectedMembership = null;
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

        if (selectedMembership == null && selectedInvitation == null) {
            Span placeholder = new Span("Select a user or invitation to view details");
            placeholder.addClassNames(LumoUtility.TextColor.SECONDARY);
            detailLayout.add(placeholder);
            detailLayout.setAlignItems(FlexComponent.Alignment.CENTER);
            detailLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
            return;
        }

        detailLayout.setAlignItems(FlexComponent.Alignment.STRETCH);
        detailLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.START);

        if (selectedMembership != null) {
            updateMembershipDetail();
        } else if (selectedInvitation != null) {
            updateInvitationDetail();
        }
    }

    private void updateMembershipDetail() {
        User user = selectedMembership.getUser();
        Company company = companyContextService.getCurrentCompany();

        H3 header = new H3(user.getDisplayName());
        header.addClassNames(LumoUtility.Margin.NONE);

        // User info
        Span emailLabel = new Span("Email: " + user.getEmail());
        Span statusLabel = new Span("User Status: " + user.getStatus().name());
        Span roleLabel = new Span("Role: " + selectedMembership.getRole().getName());
        Span membershipStatusLabel = new Span("Membership: " + selectedMembership.getStatus().name());

        // Security level section
        H3 securityHeader = new H3("Account Security Level");
        securityHeader.addClassNames(LumoUtility.Margin.Top.MEDIUM);

        UserSecurityLevel securityLevel = securityLevelRepository
            .findByUserAndCompany(user, company)
            .orElse(null);

        int currentLevel = securityLevel != null ? securityLevel.getMaxLevel() : 0;

        Span securityInfo = new Span("Current level: " + currentLevel +
            " (can view accounts with security level ≤ " + currentLevel + ")");
        securityInfo.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

        Button setSecurityBtn = new Button("Set Security Level", VaadinIcon.LOCK.create());
        setSecurityBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        setSecurityBtn.addClickListener(e -> showSecurityLevelDialog(selectedMembership, securityLevel));

        HorizontalLayout securityLayout = new HorizontalLayout(securityInfo, setSecurityBtn);
        securityLayout.setAlignItems(FlexComponent.Alignment.CENTER);

        // Role permissions
        H3 permissionsHeader = new H3("Role Permissions");
        permissionsHeader.addClassNames(LumoUtility.Margin.Top.MEDIUM);

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
            securityHeader, securityLayout,
            permissionsHeader, permissionsLayout, actions);
    }

    private void updateInvitationDetail() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a")
            .withZone(ZoneId.systemDefault());

        H3 header = new H3("Invitation Details");
        header.addClassNames(LumoUtility.Margin.NONE);

        Span emailLabel = new Span("Email: " + selectedInvitation.getEmail());
        Span nameLabel = new Span("Name: " + (selectedInvitation.getDisplayName() != null ? selectedInvitation.getDisplayName() : "-"));
        Span roleLabel = new Span("Role: " + selectedInvitation.getRole().getName());
        Span statusLabel = new Span("Status: " + selectedInvitation.getStatus().name());
        Span expiresLabel = new Span("Expires: " + formatter.format(selectedInvitation.getExpiresAt()));
        Span invitedByLabel = new Span("Invited by: " +
            (selectedInvitation.getInvitedBy() != null ? selectedInvitation.getInvitedBy().getDisplayName() : "-"));
        Span createdLabel = new Span("Created: " + formatter.format(selectedInvitation.getCreatedAt()));

        // Status-specific info
        if (selectedInvitation.isExpired() && selectedInvitation.getStatus() == UserInvitation.InvitationStatus.PENDING) {
            Span expiredWarning = new Span("This invitation has expired");
            expiredWarning.getStyle().set("color", "var(--lumo-error-color)");
            detailLayout.add(header, emailLabel, nameLabel, roleLabel, statusLabel, expiresLabel,
                invitedByLabel, createdLabel, expiredWarning);
        } else {
            detailLayout.add(header, emailLabel, nameLabel, roleLabel, statusLabel, expiresLabel,
                invitedByLabel, createdLabel);
        }

        if (selectedInvitation.getStatus() == UserInvitation.InvitationStatus.ACCEPTED) {
            Span acceptedAtLabel = new Span("Accepted: " + formatter.format(selectedInvitation.getAcceptedAt()));
            Span acceptedUserLabel = new Span("Accepted by user: " + selectedInvitation.getAcceptedUser().getEmail());
            detailLayout.add(acceptedAtLabel, acceptedUserLabel);
        }

        // Actions for pending invitations
        if (selectedInvitation.getStatus() == UserInvitation.InvitationStatus.PENDING) {
            HorizontalLayout actions = new HorizontalLayout();
            actions.addClassNames(LumoUtility.Margin.Top.LARGE);

            Button resendBtn = new Button("Resend Invitation", VaadinIcon.ENVELOPE.create());
            resendBtn.setEnabled(!selectedInvitation.isExpired());
            resendBtn.addClickListener(e -> resendInvitation(selectedInvitation));

            Button cancelBtn = new Button("Cancel Invitation", VaadinIcon.CLOSE.create());
            cancelBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);
            cancelBtn.addClickListener(e -> cancelInvitation(selectedInvitation));

            // Show invitation link
            Button copyLinkBtn = new Button("Copy Invitation Link", VaadinIcon.LINK.create());
            copyLinkBtn.setEnabled(!selectedInvitation.isExpired());
            copyLinkBtn.addClickListener(e -> {
                String url = invitationService.getInvitationUrl(selectedInvitation);
                com.vaadin.flow.component.UI.getCurrent().getPage()
                    .executeJs("navigator.clipboard.writeText($0)", url);
                Notification.show("Invitation link copied to clipboard", 3000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            });

            actions.add(resendBtn, copyLinkBtn, cancelBtn);
            detailLayout.add(actions);
        }
    }

    private void refreshGrid() {
        Company company = companyContextService.getCurrentCompany();
        List<CompanyMembership> memberships = userService.getMembershipsByCompany(company);
        membershipGrid.setItems(memberships);
    }

    private void refreshInvitationsGrid() {
        Company company = companyContextService.getCurrentCompany();
        List<UserInvitation> invitations = invitationService.getInvitationsByCompany(company);
        invitationsGrid.setItems(invitations);
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

    private void showInviteByEmailDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Invite User by Email");
        dialog.setWidth("450px");

        FormLayout form = new FormLayout();

        EmailField emailField = new EmailField("Email Address");
        emailField.setRequired(true);
        emailField.setWidthFull();
        emailField.setHelperText("An invitation link will be sent to this email");

        TextField nameField = new TextField("Name (Optional)");
        nameField.setWidthFull();
        nameField.setHelperText("Pre-fill the user's name for their account");

        ComboBox<Role> roleCombo = new ComboBox<>("Role");
        roleCombo.setWidthFull();
        roleCombo.setItemLabelGenerator(Role::getName);
        roleCombo.setItems(roleService.findAvailableRolesForCompany(companyContextService.getCurrentCompany()));
        roleCombo.setRequired(true);

        form.add(emailField, nameField, roleCombo);
        dialog.add(form);

        Span infoText = new Span("The recipient will receive an email with a link to create their account " +
            "(for new users) or join this company (for existing users). The invitation expires in 7 days.");
        infoText.getStyle().set("font-size", "var(--lumo-font-size-s)");
        infoText.getStyle().set("color", "var(--lumo-secondary-text-color)");
        dialog.add(infoText);

        Button sendBtn = new Button("Send Invitation", e -> {
            if (emailField.isEmpty() || roleCombo.isEmpty()) {
                Notification.show("Please fill all required fields", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                User currentUser = companyContextService.getCurrentUser();
                Company company = companyContextService.getCurrentCompany();
                String displayName = nameField.getValue().isBlank() ? null : nameField.getValue().trim();

                InvitationResult result = invitationService.createInvitation(
                    emailField.getValue(),
                    displayName,
                    company,
                    roleCombo.getValue(),
                    currentUser
                );

                if (result.success()) {
                    Notification.show("Invitation sent to " + emailField.getValue(), 3000, Notification.Position.BOTTOM_START)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    dialog.close();
                    refreshInvitationsGrid();
                    // Switch to invitations tab
                    tabSheet.setSelectedIndex(1);
                } else {
                    Notification.show(result.message(), 5000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
            } catch (Exception ex) {
                Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        sendBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button("Cancel", e -> dialog.close());

        dialog.getFooter().add(cancelBtn, sendBtn);
        dialog.open();
    }

    private void showInviteExistingUserDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Add Existing User to Company");
        dialog.setWidth("400px");

        FormLayout form = new FormLayout();

        EmailField emailField = new EmailField("User Email");
        emailField.setRequired(true);
        emailField.setWidthFull();
        emailField.setHelperText("Enter the email of an existing MoniWorks user");

        ComboBox<Role> roleCombo = new ComboBox<>("Role");
        roleCombo.setWidthFull();
        roleCombo.setItemLabelGenerator(Role::getName);
        roleCombo.setItems(roleService.findAvailableRolesForCompany(companyContextService.getCurrentCompany()));
        roleCombo.setRequired(true);

        form.add(emailField, roleCombo);
        dialog.add(form);

        Button inviteBtn = new Button("Add to Company", e -> {
            if (emailField.isEmpty() || roleCombo.isEmpty()) {
                Notification.show("Please fill all required fields", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                User user = userService.findByEmail(emailField.getValue())
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + emailField.getValue()));

                userService.addToCompany(user, companyContextService.getCurrentCompany(), roleCombo.getValue());

                Notification.show("User added to company", 3000, Notification.Position.BOTTOM_START)
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

    private void resendInvitation(UserInvitation invitation) {
        User currentUser = companyContextService.getCurrentUser();
        InvitationResult result = invitationService.resendInvitation(invitation, currentUser);

        if (result.success()) {
            Notification.show("Invitation resent to " + invitation.getEmail(), 3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } else {
            Notification.show(result.message(), 5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void cancelInvitation(UserInvitation invitation) {
        User currentUser = companyContextService.getCurrentUser();
        InvitationResult result = invitationService.cancelInvitation(invitation, currentUser);

        if (result.success()) {
            Notification.show("Invitation cancelled", 3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            refreshInvitationsGrid();
            selectedInvitation = null;
            updateDetailLayout();
        } else {
            Notification.show(result.message(), 5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
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

    private void showSecurityLevelDialog(CompanyMembership membership, UserSecurityLevel existingLevel) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Set Security Level for " + membership.getUser().getDisplayName());
        dialog.setWidth("400px");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);

        Span description = new Span("Security levels control which accounts a user can view. " +
            "Accounts with a security level higher than the user's max level will be hidden.");
        description.getStyle().set("font-size", "var(--lumo-font-size-s)");
        description.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Span examples = new Span("Examples: Level 0 = basic accounts, Level 1+ = restricted accounts (e.g., payroll)");
        examples.getStyle().set("font-size", "var(--lumo-font-size-xs)");
        examples.getStyle().set("color", "var(--lumo-secondary-text-color)");

        IntegerField levelField = new IntegerField("Max Security Level");
        levelField.setMin(0);
        levelField.setMax(99);
        levelField.setStepButtonsVisible(true);
        levelField.setValue(existingLevel != null ? existingLevel.getMaxLevel() : 0);
        levelField.setWidthFull();
        levelField.setHelperText("User can view accounts with security level ≤ this value (0 = basic only)");

        content.add(description, examples, levelField);
        dialog.add(content);

        Button saveBtn = new Button("Save", e -> {
            if (levelField.getValue() == null) {
                Notification.show("Please enter a security level", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                Company company = companyContextService.getCurrentCompany();
                User user = membership.getUser();
                int newLevel = levelField.getValue();

                UserSecurityLevel level = existingLevel;
                if (level == null) {
                    level = new UserSecurityLevel(user, company, newLevel);
                } else {
                    level.setMaxLevel(newLevel);
                }
                securityLevelRepository.save(level);

                Notification.show("Security level updated to " + newLevel, 3000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
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
}
