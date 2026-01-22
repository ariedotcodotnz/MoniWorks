package com.example.application.security;

/**
 * Constants for all permission names in the system.
 * Use these instead of string literals for compile-time safety.
 */
public final class Permissions {

    private Permissions() {
        // Utility class - no instantiation
    }

    // System permissions
    public static final String ADMIN = "ADMIN";

    // Admin permissions
    public static final String MANAGE_USERS = "MANAGE_USERS";
    public static final String MANAGE_COMPANY = "MANAGE_COMPANY";

    // Accounting permissions
    public static final String MANAGE_COA = "MANAGE_COA";
    public static final String VIEW_COA = "VIEW_COA";

    // Transaction permissions
    public static final String CREATE_TRANSACTION = "CREATE_TRANSACTION";
    public static final String POST_TRANSACTION = "POST_TRANSACTION";
    public static final String VIEW_TRANSACTION = "VIEW_TRANSACTION";

    // Banking permissions
    public static final String RECONCILE_BANK = "RECONCILE_BANK";

    // Reporting permissions
    public static final String VIEW_REPORTS = "VIEW_REPORTS";

    // Tax permissions
    public static final String MANAGE_TAX = "MANAGE_TAX";

    // Contact permissions
    public static final String MANAGE_CONTACTS = "MANAGE_CONTACTS";

    // Product permissions
    public static final String MANAGE_PRODUCTS = "MANAGE_PRODUCTS";

    // Accounts Receivable permissions (new)
    public static final String MANAGE_INVOICES = "MANAGE_INVOICES";
    public static final String VIEW_INVOICES = "VIEW_INVOICES";

    // Accounts Payable permissions (new)
    public static final String MANAGE_BILLS = "MANAGE_BILLS";
    public static final String VIEW_BILLS = "VIEW_BILLS";

    // Budget/KPI permissions (new)
    public static final String MANAGE_BUDGETS = "MANAGE_BUDGETS";
    public static final String MANAGE_KPIS = "MANAGE_KPIS";

    // Department permissions (new)
    public static final String MANAGE_DEPARTMENTS = "MANAGE_DEPARTMENTS";

    // Recurring template permissions (new)
    public static final String MANAGE_RECURRING = "MANAGE_RECURRING";

    // Audit trail permission (new)
    public static final String VIEW_AUDIT_LOG = "VIEW_AUDIT_LOG";

    // Export permissions (new)
    public static final String EXPORT_REPORTS = "EXPORT_REPORTS";

    // Allocation permissions (new)
    public static final String MANAGE_ALLOCATIONS = "MANAGE_ALLOCATIONS";

    // Statement permissions (new)
    public static final String MANAGE_STATEMENTS = "MANAGE_STATEMENTS";

    // Period management permissions (new)
    public static final String MANAGE_PERIODS = "MANAGE_PERIODS";
}
