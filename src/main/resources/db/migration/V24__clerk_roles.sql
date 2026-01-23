-- Add specialized clerk roles for AR and AP operations
-- Per spec 02: "AP_CLERK / AR_CLERK (optional): subset of AP/AR"

-- Insert AR_CLERK role
INSERT INTO role (name, description, is_system) VALUES
('AR_CLERK', 'Accounts Receivable specialist - invoices, receipts, statements', TRUE);

-- Insert AP_CLERK role
INSERT INTO role (name, description, is_system) VALUES
('AP_CLERK', 'Accounts Payable specialist - bills, payments, remittances', TRUE);

-- Assign permissions to AR_CLERK role
-- Focus on customer-facing operations: invoices, receipts, statements, allocations
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.name = 'AR_CLERK' AND r.is_system = TRUE
AND p.name IN (
    -- Core viewing permissions
    'VIEW_COA',
    'VIEW_TRANSACTION',
    'VIEW_REPORTS',
    -- AR-specific permissions
    'MANAGE_INVOICES',
    'VIEW_INVOICES',
    'MANAGE_STATEMENTS',
    'MANAGE_ALLOCATIONS',
    -- Transaction permissions for receipts
    'CREATE_TRANSACTION',
    'POST_TRANSACTION',
    -- Contact management (customers)
    'MANAGE_CONTACTS',
    -- Export for statements and reports
    'EXPORT_REPORTS'
);

-- Assign permissions to AP_CLERK role
-- Focus on supplier-facing operations: bills, payments, bank reconciliation
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.name = 'AP_CLERK' AND r.is_system = TRUE
AND p.name IN (
    -- Core viewing permissions
    'VIEW_COA',
    'VIEW_TRANSACTION',
    'VIEW_REPORTS',
    -- AP-specific permissions
    'MANAGE_BILLS',
    'VIEW_BILLS',
    'MANAGE_ALLOCATIONS',
    -- Transaction permissions for payments
    'CREATE_TRANSACTION',
    'POST_TRANSACTION',
    -- Bank reconciliation (often AP responsibility)
    'RECONCILE_BANK',
    -- Contact management (suppliers)
    'MANAGE_CONTACTS',
    -- Export for remittances and reports
    'EXPORT_REPORTS'
);
