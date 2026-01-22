package com.example.application.ui;

import com.example.application.domain.Company;
import com.example.application.domain.User;
import com.example.application.security.Permissions;
import com.example.application.service.CompanyContextService;
import com.example.application.ui.views.AccountsView;
import com.example.application.ui.views.AuditEventsView;
import com.example.application.ui.views.BankReconciliationView;
import com.example.application.ui.views.ContactsView;
import com.example.application.ui.views.DashboardView;
import com.example.application.ui.views.BudgetsView;
import com.example.application.ui.views.DepartmentsView;
import com.example.application.ui.views.GlobalSearchView;
import com.example.application.ui.views.GstReturnsView;
import com.example.application.ui.views.KPIsView;
import com.example.application.ui.views.PeriodsView;
import com.example.application.ui.views.ProductsView;
import com.example.application.ui.views.RecurringTemplatesView;
import com.example.application.ui.views.ReportsView;
import com.example.application.ui.views.SalesInvoicesView;
import com.example.application.ui.views.StatementRunsView;
import com.example.application.ui.views.SupplierBillsView;
import com.example.application.ui.views.TaxCodesView;
import com.example.application.ui.views.TransactionsView;
import com.example.application.ui.views.UsersView;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.context.SecurityContextHolder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Main application layout with side navigation and global search.
 * Uses Vaadin AppLayout with a drawer for navigation and header for branding + search.
 */
@Layout
@PermitAll
public class MainLayout extends AppLayout {

    private final CompanyContextService companyContextService;
    private final TextField globalSearchField;
    private final ComboBox<Company> companySwitcher;

    public MainLayout(CompanyContextService companyContextService) {
        this.companyContextService = companyContextService;
        globalSearchField = createGlobalSearchField();
        companySwitcher = createCompanySwitcher();
        createHeader();
        createDrawer();
    }

    private TextField createGlobalSearchField() {
        TextField searchField = new TextField();
        searchField.setPlaceholder("Search (Ctrl+K)");
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setWidth("300px");
        searchField.setClearButtonVisible(true);

        // Navigate to search view on Enter
        searchField.addKeyDownListener(Key.ENTER, e -> {
            String query = searchField.getValue();
            if (query != null && !query.isBlank()) {
                String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
                UI.getCurrent().navigate("search?q=" + encodedQuery);
                searchField.clear();
            } else {
                UI.getCurrent().navigate(GlobalSearchView.class);
            }
        });

        return searchField;
    }

    private ComboBox<Company> createCompanySwitcher() {
        ComboBox<Company> switcher = new ComboBox<>();
        switcher.setPlaceholder("Select company");
        switcher.setWidth("200px");
        switcher.setItemLabelGenerator(Company::getName);

        // Load accessible companies
        List<Company> companies = companyContextService.getAccessibleCompanies();
        switcher.setItems(companies);

        // Set current company
        Company currentCompany = companyContextService.getCurrentCompany();
        if (currentCompany != null && companies.contains(currentCompany)) {
            switcher.setValue(currentCompany);
        } else if (!companies.isEmpty()) {
            switcher.setValue(companies.get(0));
            companyContextService.setCurrentCompany(companies.get(0));
        }

        // Handle company switching
        switcher.addValueChangeListener(event -> {
            Company selected = event.getValue();
            if (selected != null && !selected.equals(companyContextService.getCurrentCompany())) {
                companyContextService.setCurrentCompany(selected);
                companyContextService.clearUserCache();
                // Refresh the current page
                UI.getCurrent().getPage().reload();
            }
        });

        return switcher;
    }

    private HorizontalLayout createUserInfo() {
        User currentUser = companyContextService.getCurrentUser();
        String userName = currentUser != null ? currentUser.getDisplayName() : "Guest";
        String roleName = companyContextService.getCurrentRoleName();

        Span userLabel = new Span(userName);
        userLabel.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.FontWeight.MEDIUM);

        Span roleLabel = new Span(roleName != null ? "(" + roleName + ")" : "");
        roleLabel.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY);

        Button logoutBtn = new Button(VaadinIcon.SIGN_OUT.create());
        logoutBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        logoutBtn.setTooltipText("Sign out");
        logoutBtn.addClickListener(e -> {
            SecurityContextHolder.clearContext();
            UI.getCurrent().getPage().setLocation("/logout");
        });

        HorizontalLayout userInfo = new HorizontalLayout(userLabel, roleLabel, logoutBtn);
        userInfo.setAlignItems(FlexComponent.Alignment.CENTER);
        userInfo.setSpacing(true);
        userInfo.addClassNames(LumoUtility.Padding.Horizontal.SMALL);

        return userInfo;
    }

    private void createHeader() {
        H1 logo = new H1("MoniWorks");
        logo.addClassNames(
            LumoUtility.FontSize.LARGE,
            LumoUtility.Margin.MEDIUM
        );

        // Spacer to push elements to the right
        Span spacer = new Span();
        spacer.addClassNames(LumoUtility.Flex.GROW);

        HorizontalLayout headerContent = new HorizontalLayout();
        headerContent.setWidthFull();
        headerContent.setAlignItems(FlexComponent.Alignment.CENTER);
        headerContent.add(new DrawerToggle(), logo, companySwitcher, spacer, globalSearchField, createUserInfo());

        Header header = new Header(headerContent);
        header.addClassNames(
            LumoUtility.Display.FLEX,
            LumoUtility.AlignItems.CENTER,
            LumoUtility.Padding.End.MEDIUM,
            LumoUtility.Width.FULL
        );

        addToNavbar(header);
    }

    private void createDrawer() {
        SideNav nav = new SideNav();

        nav.addItem(new SideNavItem("Dashboard", DashboardView.class,
            VaadinIcon.DASHBOARD.create()));
        nav.addItem(new SideNavItem("Transactions", TransactionsView.class,
            VaadinIcon.EXCHANGE.create()));
        nav.addItem(new SideNavItem("Sales Invoices", SalesInvoicesView.class,
            VaadinIcon.INVOICE.create()));
        nav.addItem(new SideNavItem("Statement Runs", StatementRunsView.class,
            VaadinIcon.PRINT.create()));
        nav.addItem(new SideNavItem("Supplier Bills", SupplierBillsView.class,
            VaadinIcon.RECORDS.create()));
        nav.addItem(new SideNavItem("Contacts", ContactsView.class,
            VaadinIcon.USERS.create()));
        nav.addItem(new SideNavItem("Products", ProductsView.class,
            VaadinIcon.PACKAGE.create()));
        nav.addItem(new SideNavItem("Bank Reconciliation", BankReconciliationView.class,
            VaadinIcon.PIGGY_BANK.create()));
        nav.addItem(new SideNavItem("Accounts", AccountsView.class,
            VaadinIcon.BOOK.create()));
        nav.addItem(new SideNavItem("Tax Codes", TaxCodesView.class,
            VaadinIcon.CALC.create()));
        nav.addItem(new SideNavItem("GST Returns", GstReturnsView.class,
            VaadinIcon.FILE_TEXT_O.create()));
        nav.addItem(new SideNavItem("Departments", DepartmentsView.class,
            VaadinIcon.SITEMAP.create()));
        nav.addItem(new SideNavItem("Budgets", BudgetsView.class,
            VaadinIcon.MONEY.create()));
        nav.addItem(new SideNavItem("KPIs", KPIsView.class,
            VaadinIcon.TRENDING_UP.create()));
        nav.addItem(new SideNavItem("Recurring", RecurringTemplatesView.class,
            VaadinIcon.TIME_FORWARD.create()));
        nav.addItem(new SideNavItem("Reports", ReportsView.class,
            VaadinIcon.CHART.create()));
        nav.addItem(new SideNavItem("Periods", PeriodsView.class,
            VaadinIcon.CALENDAR.create()));
        nav.addItem(new SideNavItem("Audit Trail", AuditEventsView.class,
            VaadinIcon.LIST.create()));
        nav.addItem(new SideNavItem("Search", GlobalSearchView.class,
            VaadinIcon.SEARCH.create()));

        // Admin-only menu items
        if (companyContextService.hasPermission(Permissions.MANAGE_USERS)) {
            nav.addItem(new SideNavItem("Users", UsersView.class,
                VaadinIcon.GROUP.create()));
        }

        Scroller scroller = new Scroller(nav);
        scroller.addClassNames(LumoUtility.Padding.SMALL);

        addToDrawer(scroller);
    }
}
