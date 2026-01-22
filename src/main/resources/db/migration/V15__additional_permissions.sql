-- Additional permissions for comprehensive RBAC coverage
-- These extend the base permissions from V1 to cover all features

-- Insert new permissions
INSERT INTO permission (name, description, category) VALUES
('MANAGE_INVOICES', 'Create and manage sales invoices', 'AR'),
('VIEW_INVOICES', 'View sales invoices', 'AR'),
('MANAGE_BILLS', 'Create and manage supplier bills', 'AP'),
('VIEW_BILLS', 'View supplier bills', 'AP'),
('MANAGE_BUDGETS', 'Create and manage budgets', 'BUDGETING'),
('MANAGE_KPIS', 'Create and manage KPIs', 'BUDGETING'),
('MANAGE_DEPARTMENTS', 'Create and manage departments', 'ADMIN'),
('MANAGE_RECURRING', 'Create and manage recurring templates', 'AUTOMATION'),
('VIEW_AUDIT_LOG', 'View audit trail', 'ADMIN'),
('EXPORT_REPORTS', 'Export reports to PDF/Excel', 'REPORTING'),
('MANAGE_ALLOCATIONS', 'Manage payment and receipt allocations', 'TRANSACTIONS'),
('MANAGE_STATEMENTS', 'Generate customer statements', 'AR'),
('MANAGE_PERIODS', 'Manage fiscal years and periods', 'ADMIN');

-- Assign new permissions to ADMIN role (gets all permissions)
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.name = 'ADMIN' AND r.is_system = TRUE
AND p.name IN ('MANAGE_INVOICES', 'VIEW_INVOICES', 'MANAGE_BILLS', 'VIEW_BILLS',
               'MANAGE_BUDGETS', 'MANAGE_KPIS', 'MANAGE_DEPARTMENTS', 'MANAGE_RECURRING',
               'VIEW_AUDIT_LOG', 'EXPORT_REPORTS', 'MANAGE_ALLOCATIONS', 'MANAGE_STATEMENTS',
               'MANAGE_PERIODS')
AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- Assign appropriate permissions to BOOKKEEPER role
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.name = 'BOOKKEEPER' AND r.is_system = TRUE
AND p.name IN ('MANAGE_INVOICES', 'VIEW_INVOICES', 'MANAGE_BILLS', 'VIEW_BILLS',
               'MANAGE_BUDGETS', 'MANAGE_KPIS', 'MANAGE_RECURRING',
               'EXPORT_REPORTS', 'MANAGE_ALLOCATIONS', 'MANAGE_STATEMENTS')
AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- Assign view permissions to READONLY role
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.name = 'READONLY' AND r.is_system = TRUE
AND p.name IN ('VIEW_INVOICES', 'VIEW_BILLS')
AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
