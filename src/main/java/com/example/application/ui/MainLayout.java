package com.example.application.ui;

import com.example.application.ui.views.AccountsView;
import com.example.application.ui.views.BankReconciliationView;
import com.example.application.ui.views.DashboardView;
import com.example.application.ui.views.GstReturnsView;
import com.example.application.ui.views.PeriodsView;
import com.example.application.ui.views.ReportsView;
import com.example.application.ui.views.TaxCodesView;
import com.example.application.ui.views.TransactionsView;
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
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.PermitAll;

/**
 * Main application layout with side navigation.
 * Uses Vaadin AppLayout with a drawer for navigation and header for branding.
 */
@Layout
@PermitAll
public class MainLayout extends AppLayout {

    public MainLayout() {
        createHeader();
        createDrawer();
    }

    private void createHeader() {
        H1 logo = new H1("MoniWorks");
        logo.addClassNames(
            LumoUtility.FontSize.LARGE,
            LumoUtility.Margin.MEDIUM
        );

        Header header = new Header(new DrawerToggle(), logo);
        header.addClassNames(
            LumoUtility.Display.FLEX,
            LumoUtility.AlignItems.CENTER,
            LumoUtility.Padding.End.MEDIUM
        );

        addToNavbar(header);
    }

    private void createDrawer() {
        SideNav nav = new SideNav();

        nav.addItem(new SideNavItem("Dashboard", DashboardView.class,
            VaadinIcon.DASHBOARD.create()));
        nav.addItem(new SideNavItem("Transactions", TransactionsView.class,
            VaadinIcon.EXCHANGE.create()));
        nav.addItem(new SideNavItem("Bank Reconciliation", BankReconciliationView.class,
            VaadinIcon.PIGGY_BANK.create()));
        nav.addItem(new SideNavItem("Accounts", AccountsView.class,
            VaadinIcon.BOOK.create()));
        nav.addItem(new SideNavItem("Tax Codes", TaxCodesView.class,
            VaadinIcon.CALC.create()));
        nav.addItem(new SideNavItem("GST Returns", GstReturnsView.class,
            VaadinIcon.FILE_TEXT_O.create()));
        nav.addItem(new SideNavItem("Reports", ReportsView.class,
            VaadinIcon.CHART.create()));
        nav.addItem(new SideNavItem("Periods", PeriodsView.class,
            VaadinIcon.CALENDAR.create()));

        Scroller scroller = new Scroller(nav);
        scroller.addClassNames(LumoUtility.Padding.SMALL);

        addToDrawer(scroller);
    }
}
