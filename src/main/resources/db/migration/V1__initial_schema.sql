-- MoniWorks Initial Schema
-- This migration creates all core tables for the accounting system

-- Company (tenant)
CREATE TABLE company (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    country VARCHAR(2) NOT NULL,
    base_currency VARCHAR(3) NOT NULL,
    fiscal_year_start DATE NOT NULL,
    settings_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- User
CREATE TABLE app_user (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Permission
CREATE TABLE permission (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    category VARCHAR(50)
);

-- Role
CREATE TABLE role (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    company_id BIGINT,
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (company_id) REFERENCES company(id)
);

-- Role-Permission junction
CREATE TABLE role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES role(id),
    FOREIGN KEY (permission_id) REFERENCES permission(id)
);

-- Company membership (user-company-role)
CREATE TABLE company_membership (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, company_id),
    FOREIGN KEY (user_id) REFERENCES app_user(id),
    FOREIGN KEY (company_id) REFERENCES company(id),
    FOREIGN KEY (role_id) REFERENCES role(id)
);

-- Department (cost centre)
CREATE TABLE department (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    code VARCHAR(5) NOT NULL,
    name VARCHAR(50) NOT NULL,
    group_name VARCHAR(50),
    classification VARCHAR(50),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (company_id, code),
    FOREIGN KEY (company_id) REFERENCES company(id)
);

-- Account (Chart of Accounts)
CREATE TABLE account (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    code VARCHAR(7) NOT NULL,
    alt_code VARCHAR(20),
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    parent_id BIGINT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    tax_default_code VARCHAR(10),
    security_level INT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (company_id, code),
    FOREIGN KEY (company_id) REFERENCES company(id),
    FOREIGN KEY (parent_id) REFERENCES account(id)
);

-- Fiscal Year
CREATE TABLE fiscal_year (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    label VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (company_id) REFERENCES company(id)
);

-- Period (within fiscal year)
CREATE TABLE period (
    id BIGSERIAL PRIMARY KEY,
    fiscal_year_id BIGINT NOT NULL,
    period_index INT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (fiscal_year_id) REFERENCES fiscal_year(id)
);

-- Tax Code
CREATE TABLE tax_code (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    code VARCHAR(10) NOT NULL,
    name VARCHAR(50) NOT NULL,
    rate DECIMAL(5,4) NOT NULL,
    type VARCHAR(20) NOT NULL,
    report_box VARCHAR(20),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (company_id, code),
    FOREIGN KEY (company_id) REFERENCES company(id)
);

-- Transaction
CREATE TABLE transaction (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    transaction_date DATE NOT NULL,
    description VARCHAR(255),
    reference VARCHAR(50),
    status VARCHAR(10) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT,
    posted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (company_id) REFERENCES company(id),
    FOREIGN KEY (created_by) REFERENCES app_user(id)
);

-- Transaction Line
CREATE TABLE transaction_line (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL,
    line_index INT NOT NULL,
    account_id BIGINT NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    tax_code VARCHAR(10),
    department_id BIGINT,
    memo VARCHAR(255),
    FOREIGN KEY (transaction_id) REFERENCES transaction(id),
    FOREIGN KEY (account_id) REFERENCES account(id),
    FOREIGN KEY (department_id) REFERENCES department(id)
);

-- Ledger Entry (immutable)
CREATE TABLE ledger_entry (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    transaction_id BIGINT NOT NULL,
    transaction_line_id BIGINT NOT NULL,
    entry_date DATE NOT NULL,
    account_id BIGINT NOT NULL,
    amount_dr DECIMAL(19,2) NOT NULL DEFAULT 0,
    amount_cr DECIMAL(19,2) NOT NULL DEFAULT 0,
    tax_code VARCHAR(10),
    department_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (company_id) REFERENCES company(id),
    FOREIGN KEY (transaction_id) REFERENCES transaction(id),
    FOREIGN KEY (transaction_line_id) REFERENCES transaction_line(id),
    FOREIGN KEY (account_id) REFERENCES account(id),
    FOREIGN KEY (department_id) REFERENCES department(id)
);

CREATE INDEX idx_ledger_company_date ON ledger_entry(company_id, entry_date);
CREATE INDEX idx_ledger_account ON ledger_entry(account_id);

-- Audit Event
CREATE TABLE audit_event (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_id BIGINT,
    event_type VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT,
    summary VARCHAR(255),
    details_json TEXT,
    FOREIGN KEY (company_id) REFERENCES company(id),
    FOREIGN KEY (actor_id) REFERENCES app_user(id)
);

CREATE INDEX idx_audit_company_time ON audit_event(company_id, created_at);
CREATE INDEX idx_audit_entity ON audit_event(entity_type, entity_id);

-- Insert default permissions
INSERT INTO permission (name, description, category) VALUES
('ADMIN', 'Full system administration', 'SYSTEM'),
('MANAGE_USERS', 'Manage users and roles', 'ADMIN'),
('MANAGE_COMPANY', 'Manage company settings', 'ADMIN'),
('MANAGE_COA', 'Manage chart of accounts', 'ACCOUNTING'),
('VIEW_COA', 'View chart of accounts', 'ACCOUNTING'),
('CREATE_TRANSACTION', 'Create transactions', 'TRANSACTIONS'),
('POST_TRANSACTION', 'Post transactions', 'TRANSACTIONS'),
('VIEW_TRANSACTION', 'View transactions', 'TRANSACTIONS'),
('RECONCILE_BANK', 'Perform bank reconciliation', 'BANKING'),
('VIEW_REPORTS', 'View financial reports', 'REPORTING'),
('MANAGE_TAX', 'Manage tax codes and returns', 'TAX'),
('MANAGE_CONTACTS', 'Manage customers and suppliers', 'CONTACTS'),
('MANAGE_PRODUCTS', 'Manage products and services', 'PRODUCTS');

-- Insert default system roles
INSERT INTO role (name, description, is_system) VALUES
('ADMIN', 'Full administrative access', TRUE),
('BOOKKEEPER', 'Standard bookkeeper access', TRUE),
('READONLY', 'View-only access', TRUE);

-- Assign permissions to ADMIN role
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p WHERE r.name = 'ADMIN';

-- Assign permissions to BOOKKEEPER role
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.name = 'BOOKKEEPER'
AND p.name IN ('VIEW_COA', 'CREATE_TRANSACTION', 'POST_TRANSACTION', 'VIEW_TRANSACTION',
               'RECONCILE_BANK', 'VIEW_REPORTS', 'MANAGE_CONTACTS', 'MANAGE_PRODUCTS');

-- Assign permissions to READONLY role
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.name = 'READONLY'
AND p.name IN ('VIEW_COA', 'VIEW_TRANSACTION', 'VIEW_REPORTS');
