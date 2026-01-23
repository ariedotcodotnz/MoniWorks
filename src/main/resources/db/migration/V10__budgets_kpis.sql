-- V10: Add budgets and KPIs for departmental reporting
-- Supports spec 12: Budgeting, Departments, and KPIs

-- Budget table - stores budget metadata
CREATE TABLE budget (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    budget_type VARCHAR(1) NOT NULL,
    currency VARCHAR(3),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (company_id) REFERENCES company(id),
    CONSTRAINT uq_budget_company_name UNIQUE (company_id, name)
);

CREATE INDEX idx_budget_company ON budget(company_id);
CREATE INDEX idx_budget_company_type ON budget(company_id, budget_type);

-- Budget line table - stores budget amounts per account/period/department
CREATE TABLE budget_line (
    id BIGSERIAL PRIMARY KEY,
    budget_id BIGINT NOT NULL,
    period_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    department_id BIGINT,
    amount DECIMAL(19,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (budget_id) REFERENCES budget(id),
    FOREIGN KEY (period_id) REFERENCES period(id),
    FOREIGN KEY (account_id) REFERENCES account(id),
    FOREIGN KEY (department_id) REFERENCES department(id),
    CONSTRAINT uq_budget_line UNIQUE (budget_id, period_id, account_id, department_id)
);

CREATE INDEX idx_budget_line_budget ON budget_line(budget_id);
CREATE INDEX idx_budget_line_period ON budget_line(period_id);
CREATE INDEX idx_budget_line_account ON budget_line(account_id);
CREATE INDEX idx_budget_line_dept ON budget_line(department_id);

-- KPI table - stores KPI definitions
CREATE TABLE kpi (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    unit VARCHAR(20),
    description VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (company_id) REFERENCES company(id),
    CONSTRAINT uq_kpi_company_code UNIQUE (company_id, code)
);

CREATE INDEX idx_kpi_company ON kpi(company_id);

-- KPI value table - stores KPI values per period
CREATE TABLE kpi_value (
    id BIGSERIAL PRIMARY KEY,
    kpi_id BIGINT NOT NULL,
    period_id BIGINT NOT NULL,
    value DECIMAL(19,4) NOT NULL DEFAULT 0,
    notes VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (kpi_id) REFERENCES kpi(id),
    FOREIGN KEY (period_id) REFERENCES period(id),
    CONSTRAINT uq_kpi_value UNIQUE (kpi_id, period_id)
);

CREATE INDEX idx_kpi_value_kpi ON kpi_value(kpi_id);
CREATE INDEX idx_kpi_value_period ON kpi_value(period_id);
