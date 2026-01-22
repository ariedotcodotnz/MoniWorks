package com.example.application.ui;

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
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Main application layout with side navigation and global search.
 * Uses Vaadin AppLayout with a drawer for navigation and header for branding + search.
 */
@Layout
@PermitAll
public class MainLayout extends AppLayout {

    private final TextField globalSearchField;

    public MainLayout() {
        globalSearchField = createGlobalSearchField();
        createHeader();
        createDrawer();
    }

    private TextField createGlobalSearchField() {
        TextField searchField = new TextField();
        searchField.setPlaceholder("Search (Ctrl+K)");
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setWidth("300px");
        searchField.setClearButtonVisible(true);
        searchField.addClassNames(LumoUtility.Margin.Left.AUTO);

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

    private void createHeader() {
        H1 logo = new H1("MoniWorks");
        logo.addClassNames(
            LumoUtility.FontSize.LARGE,
            LumoUtility.Margin.MEDIUM
        );

        HorizontalLayout headerContent = new HorizontalLayout();
        headerContent.setWidthFull();
        headerContent.setAlignItems(FlexComponent.Alignment.CENTER);
        headerContent.add(new DrawerToggle(), logo, globalSearchField);
        headerContent.expand(globalSearchField);

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

        Scroller scroller = new Scroller(nav);
        scroller.addClassNames(LumoUtility.Padding.SMALL);

        addToDrawer(scroller);
    }
}
