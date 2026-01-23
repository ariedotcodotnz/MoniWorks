package com.example.application.ui.views;

import com.example.application.domain.Company;
import com.example.application.domain.Permission;
import com.example.application.domain.Role;
import com.example.application.service.CompanyContextService;
import com.example.application.service.PermissionService;
import com.example.application.service.RoleService;
import com.example.application.ui.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
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
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.RolesAllowed;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * View for managing roles and their permissions.
 * Admin users can view system roles and create/edit company-specific roles.
 */
@Route(value = "roles", layout = MainLayout.class)
@PageTitle("Roles | MoniWorks")
@RolesAllowed({"ADMIN", "ROLE_ADMIN"})
public class RolesView extends VerticalLayout {

    private final RoleService roleService;
    private final PermissionService permissionService;
    private final CompanyContextService companyContextService;

    private final Grid<Role> rolesGrid;
    private final VerticalLayout detailLayout;
    private Role selectedRole;

    public RolesView(RoleService roleService,
                     PermissionService permissionService,
                     CompanyContextService companyContextService) {
        this.roleService = roleService;
        this.permissionService = permissionService;
        this.companyContextService = companyContextService;

        setSizeFull();
        setPadding(false);
        setSpacing(false);

        // Create components
        rolesGrid = createRolesGrid();
        detailLayout = createDetailLayout();

        // Create split layout
        SplitLayout splitLayout = new SplitLayout();
        splitLayout.setSizeFull();
        splitLayout.setSplitterPosition(50);

        // Primary side: Toolbar + Grid
        VerticalLayout primaryLayout = new VerticalLayout();
        primaryLayout.setSizeFull();
        primaryLayout.setPadding(false);
        primaryLayout.setSpacing(false);
        primaryLayout.add(createToolbar(), rolesGrid);

        splitLayout.addToPrimary(primaryLayout);
        splitLayout.addToSecondary(detailLayout);

        add(splitLayout);

        // Load data
        refreshGrid();
    }

    private HorizontalLayout createToolbar() {
        H2 title = new H2("Roles");
        title.addClassNames(LumoUtility.Margin.NONE);

        Button createBtn = new Button("Create Role", VaadinIcon.PLUS.create());
        createBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        createBtn.addClickListener(e -> showCreateRoleDialog());

        HorizontalLayout toolbar = new HorizontalLayout(title, createBtn);
        toolbar.setWidthFull();
        toolbar.setAlignItems(FlexComponent.Alignment.CENTER);
        toolbar.expand(title);
        toolbar.addClassNames(LumoUtility.Padding.MEDIUM);

        return toolbar;
    }

    private Grid<Role> createRolesGrid() {
        Grid<Role> grid = new Grid<>();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setSizeFull();

        grid.addColumn(Role::getName)
            .setHeader("Name")
            .setSortable(true)
            .setKey("name")
            .setResizable(true);

        grid.addColumn(Role::getDescription)
            .setHeader("Description")
            .setSortable(true)
            .setKey("description")
            .setResizable(true);

        grid.addComponentColumn(role -> {
            if (role.isSystem()) {
                Span badge = new Span("System");
                badge.getElement().getThemeList().add("badge");
                return badge;
            } else {
                Span badge = new Span("Custom");
                badge.getElement().getThemeList().add("badge contrast");
                return badge;
            }
        }).setHeader("Type")
          .setKey("type")
          .setWidth("100px")
          .setFlexGrow(0);

        grid.addColumn(role -> role.getPermissions().size())
            .setHeader("Permissions")
            .setKey("permissions")
            .setWidth("120px")
            .setFlexGrow(0);

        grid.asSingleSelect().addValueChangeListener(e -> {
            selectedRole = e.getValue();
            updateDetailLayout();
        });

        return grid;
    }

    private VerticalLayout createDetailLayout() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSizeFull();
        layout.addClassNames(LumoUtility.Padding.MEDIUM, LumoUtility.Background.CONTRAST_5);

        Span placeholder = new Span("Select a role to view details");
        placeholder.addClassNames(LumoUtility.TextColor.SECONDARY);
        layout.add(placeholder);

        return layout;
    }

    private void updateDetailLayout() {
        detailLayout.removeAll();

        if (selectedRole == null) {
            Span placeholder = new Span("Select a role to view details");
            placeholder.addClassNames(LumoUtility.TextColor.SECONDARY);
            detailLayout.add(placeholder);
            return;
        }

        // Role header with name and type badge
        HorizontalLayout header = new HorizontalLayout();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setWidthFull();

        H3 roleName = new H3(selectedRole.getName());
        roleName.addClassNames(LumoUtility.Margin.NONE);

        Span typeBadge;
        if (selectedRole.isSystem()) {
            typeBadge = new Span("System Role");
            typeBadge.getElement().getThemeList().add("badge");
        } else {
            typeBadge = new Span("Custom Role");
            typeBadge.getElement().getThemeList().add("badge contrast");
        }

        header.add(roleName, typeBadge);
        header.expand(roleName);
        detailLayout.add(header);

        // Description
        if (selectedRole.getDescription() != null && !selectedRole.getDescription().isBlank()) {
            Span desc = new Span(selectedRole.getDescription());
            desc.addClassNames(LumoUtility.TextColor.SECONDARY);
            detailLayout.add(desc);
        }

        // Actions for custom roles
        if (!selectedRole.isSystem()) {
            HorizontalLayout actions = new HorizontalLayout();
            actions.addClassNames(LumoUtility.Margin.Top.MEDIUM);

            Button editBtn = new Button("Edit Role", VaadinIcon.EDIT.create());
            editBtn.addClickListener(e -> showEditRoleDialog(selectedRole));

            Button editPermsBtn = new Button("Edit Permissions", VaadinIcon.KEY.create());
            editPermsBtn.addClickListener(e -> showEditPermissionsDialog(selectedRole));

            Button deleteBtn = new Button("Delete", VaadinIcon.TRASH.create());
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);
            deleteBtn.addClickListener(e -> confirmDeleteRole(selectedRole));

            actions.add(editBtn, editPermsBtn, deleteBtn);
            detailLayout.add(actions);
        } else {
            // For system roles, show a view-only permissions button
            Button viewPermsBtn = new Button("View Permissions", VaadinIcon.KEY.create());
            viewPermsBtn.addClickListener(e -> showViewPermissionsDialog(selectedRole));
            detailLayout.add(viewPermsBtn);
        }

        // Permissions section
        H3 permsHeader = new H3("Permissions (" + selectedRole.getPermissions().size() + ")");
        permsHeader.addClassNames(LumoUtility.Margin.Top.LARGE, LumoUtility.Margin.Bottom.SMALL);
        detailLayout.add(permsHeader);

        // Group permissions by category
        Map<String, List<Permission>> permsByCategory = selectedRole.getPermissions().stream()
            .collect(Collectors.groupingBy(
                p -> p.getCategory() != null ? p.getCategory() : "Other",
                Collectors.toList()
            ));

        // Sort categories and display
        permsByCategory.keySet().stream()
            .sorted()
            .forEach(category -> {
                Span categoryLabel = new Span(category);
                categoryLabel.addClassNames(LumoUtility.FontWeight.BOLD, LumoUtility.FontSize.SMALL);
                detailLayout.add(categoryLabel);

                VerticalLayout permsList = new VerticalLayout();
                permsList.setPadding(false);
                permsList.setSpacing(false);
                permsList.addClassNames(LumoUtility.Margin.Left.MEDIUM, LumoUtility.Margin.Bottom.SMALL);

                permsByCategory.get(category).stream()
                    .sorted((a, b) -> a.getName().compareTo(b.getName()))
                    .forEach(perm -> {
                        HorizontalLayout permRow = new HorizontalLayout();
                        permRow.setAlignItems(FlexComponent.Alignment.CENTER);
                        permRow.setSpacing(true);

                        Span permName = new Span(perm.getName());
                        permName.addClassNames(LumoUtility.FontSize.SMALL);

                        if (perm.getDescription() != null && !perm.getDescription().isBlank()) {
                            Span permDesc = new Span(" - " + perm.getDescription());
                            permDesc.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY);
                            permRow.add(permName, permDesc);
                        } else {
                            permRow.add(permName);
                        }

                        permsList.add(permRow);
                    });

                detailLayout.add(permsList);
            });
    }

    private void showCreateRoleDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Create New Role");
        dialog.setWidth("450px");

        FormLayout form = new FormLayout();

        TextField nameField = new TextField("Role Name");
        nameField.setRequired(true);
        nameField.setMaxLength(50);
        nameField.setPlaceholder("e.g., AP_CLERK");

        TextArea descField = new TextArea("Description");
        descField.setMaxLength(255);
        descField.setPlaceholder("Description of this role's purpose");

        form.add(nameField, descField);
        form.setColspan(descField, 2);
        dialog.add(form);

        Button createBtn = new Button("Create", e -> {
            String name = nameField.getValue();
            if (name == null || name.isBlank()) {
                Notification.show("Role name is required", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                Company company = companyContextService.getCurrentCompany();
                roleService.createCompanyRole(company, name.trim(), descField.getValue(), new HashSet<>());
                refreshGrid();
                dialog.close();
                Notification.show("Role created. Now add permissions to it.", 3000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        createBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button("Cancel", e -> dialog.close());

        dialog.getFooter().add(cancelBtn, createBtn);
        dialog.open();
    }

    private void showEditRoleDialog(Role role) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Edit Role");
        dialog.setWidth("450px");

        FormLayout form = new FormLayout();

        TextField nameField = new TextField("Role Name");
        nameField.setRequired(true);
        nameField.setMaxLength(50);
        nameField.setValue(role.getName());

        TextArea descField = new TextArea("Description");
        descField.setMaxLength(255);
        descField.setValue(role.getDescription() != null ? role.getDescription() : "");

        form.add(nameField, descField);
        form.setColspan(descField, 2);
        dialog.add(form);

        Button saveBtn = new Button("Save", e -> {
            String name = nameField.getValue();
            if (name == null || name.isBlank()) {
                Notification.show("Role name is required", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                role.setName(name.trim());
                role.setDescription(descField.getValue());
                roleService.save(role);
                refreshGrid();
                updateDetailLayout();
                dialog.close();
                Notification.show("Role updated", 3000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
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

    private void showEditPermissionsDialog(Role role) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Edit Permissions - " + role.getName());
        dialog.setWidth("600px");
        dialog.setHeight("80vh");

        // Get all permissions grouped by category
        List<Permission> allPermissions = permissionService.findAll();
        Map<String, List<Permission>> permsByCategory = allPermissions.stream()
            .collect(Collectors.groupingBy(
                p -> p.getCategory() != null ? p.getCategory() : "Other",
                Collectors.toList()
            ));

        // Track checkbox selections
        Map<Permission, Checkbox> checkboxMap = new HashMap<>();
        Set<String> currentPermNames = role.getPermissions().stream()
            .map(Permission::getName)
            .collect(Collectors.toSet());

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);

        // Create category sections
        permsByCategory.keySet().stream()
            .sorted()
            .forEach(category -> {
                // Category header with select all/none
                HorizontalLayout categoryHeader = new HorizontalLayout();
                categoryHeader.setAlignItems(FlexComponent.Alignment.CENTER);
                categoryHeader.setWidthFull();

                Span categoryLabel = new Span(category);
                categoryLabel.addClassNames(LumoUtility.FontWeight.BOLD);

                Button selectAllBtn = new Button("All");
                selectAllBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

                Button selectNoneBtn = new Button("None");
                selectNoneBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

                categoryHeader.add(categoryLabel);
                categoryHeader.expand(categoryLabel);
                categoryHeader.add(selectAllBtn, selectNoneBtn);

                content.add(categoryHeader);

                // Permission checkboxes
                VerticalLayout permsLayout = new VerticalLayout();
                permsLayout.setPadding(false);
                permsLayout.setSpacing(false);
                permsLayout.addClassNames(LumoUtility.Margin.Left.MEDIUM, LumoUtility.Margin.Bottom.MEDIUM);

                List<Checkbox> categoryCheckboxes = new ArrayList<>();

                permsByCategory.get(category).stream()
                    .sorted((a, b) -> a.getName().compareTo(b.getName()))
                    .forEach(perm -> {
                        Checkbox cb = new Checkbox();
                        cb.setValue(currentPermNames.contains(perm.getName()));

                        String label = perm.getName();
                        if (perm.getDescription() != null && !perm.getDescription().isBlank()) {
                            label += " - " + perm.getDescription();
                        }
                        cb.setLabel(label);

                        checkboxMap.put(perm, cb);
                        categoryCheckboxes.add(cb);
                        permsLayout.add(cb);
                    });

                // Wire up select all/none buttons
                selectAllBtn.addClickListener(e -> categoryCheckboxes.forEach(cb -> cb.setValue(true)));
                selectNoneBtn.addClickListener(e -> categoryCheckboxes.forEach(cb -> cb.setValue(false)));

                content.add(permsLayout);
            });

        // Wrap in a scrollable div
        Div scrollWrapper = new Div(content);
        scrollWrapper.getStyle().set("overflow-y", "auto");
        scrollWrapper.getStyle().set("max-height", "calc(80vh - 150px)");
        scrollWrapper.setWidthFull();

        dialog.add(scrollWrapper);

        Button saveBtn = new Button("Save Permissions", e -> {
            try {
                Set<String> selectedPerms = checkboxMap.entrySet().stream()
                    .filter(entry -> entry.getValue().getValue())
                    .map(entry -> entry.getKey().getName())
                    .collect(Collectors.toSet());

                roleService.updatePermissions(role, selectedPerms);
                refreshGrid();
                updateDetailLayout();
                dialog.close();
                Notification.show("Permissions updated", 3000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
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

    private void showViewPermissionsDialog(Role role) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Permissions - " + role.getName());
        dialog.setWidth("500px");
        dialog.setHeight("70vh");

        // Group permissions by category
        Map<String, List<Permission>> permsByCategory = role.getPermissions().stream()
            .collect(Collectors.groupingBy(
                p -> p.getCategory() != null ? p.getCategory() : "Other",
                Collectors.toList()
            ));

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);

        permsByCategory.keySet().stream()
            .sorted()
            .forEach(category -> {
                Span categoryLabel = new Span(category);
                categoryLabel.addClassNames(LumoUtility.FontWeight.BOLD);
                content.add(categoryLabel);

                VerticalLayout permsLayout = new VerticalLayout();
                permsLayout.setPadding(false);
                permsLayout.setSpacing(false);
                permsLayout.addClassNames(LumoUtility.Margin.Left.MEDIUM, LumoUtility.Margin.Bottom.MEDIUM);

                permsByCategory.get(category).stream()
                    .sorted((a, b) -> a.getName().compareTo(b.getName()))
                    .forEach(perm -> {
                        HorizontalLayout permRow = new HorizontalLayout();
                        permRow.setAlignItems(FlexComponent.Alignment.CENTER);

                        Span permName = new Span(perm.getName());
                        permName.addClassNames(LumoUtility.FontSize.SMALL);

                        if (perm.getDescription() != null && !perm.getDescription().isBlank()) {
                            Span permDesc = new Span(" - " + perm.getDescription());
                            permDesc.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY);
                            permRow.add(permName, permDesc);
                        } else {
                            permRow.add(permName);
                        }

                        permsLayout.add(permRow);
                    });

                content.add(permsLayout);
            });

        // Wrap in a scrollable div
        Div scrollWrapper = new Div(content);
        scrollWrapper.getStyle().set("overflow-y", "auto");
        scrollWrapper.getStyle().set("max-height", "calc(70vh - 100px)");
        scrollWrapper.setWidthFull();

        dialog.add(scrollWrapper);

        Span notice = new Span("System roles cannot be modified. Contact an administrator to create a custom role.");
        notice.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        notice.getStyle().set("font-style", "italic");
        dialog.add(notice);

        Button closeBtn = new Button("Close", e -> dialog.close());
        closeBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.getFooter().add(closeBtn);
        dialog.open();
    }

    private void confirmDeleteRole(Role role) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Delete Role");
        dialog.setWidth("400px");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);

        Span message = new Span("Are you sure you want to delete the role \"" + role.getName() + "\"?");
        Span warning = new Span("Users with this role will need to be reassigned to another role.");
        warning.addClassNames(LumoUtility.TextColor.ERROR, LumoUtility.FontSize.SMALL);

        content.add(message, warning);
        dialog.add(content);

        Button deleteBtn = new Button("Delete", e -> {
            try {
                roleService.delete(role);
                selectedRole = null;
                refreshGrid();
                updateDetailLayout();
                dialog.close();
                Notification.show("Role deleted", 3000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        deleteBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);

        Button cancelBtn = new Button("Cancel", e -> dialog.close());

        dialog.getFooter().add(cancelBtn, deleteBtn);
        dialog.open();
    }

    private void refreshGrid() {
        Company company = companyContextService.getCurrentCompany();
        if (company != null) {
            List<Role> roles = roleService.findAvailableRolesForCompany(company);
            rolesGrid.setItems(roles);
        } else {
            rolesGrid.setItems();
        }
    }
}
