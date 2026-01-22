-- V12: Add recurring templates for automated transaction generation
-- Supports spec 11: Recurring Transactions and Allocation Rules

-- Recurring template table - stores template configuration
CREATE TABLE recurring_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    template_type VARCHAR(20) NOT NULL,
    source_entity_id BIGINT,
    payload_json TEXT NOT NULL,
    frequency VARCHAR(20) NOT NULL,
    frequency_interval INT NOT NULL DEFAULT 1,
    start_date DATE NOT NULL,
    end_date DATE,
    max_occurrences INT,
    occurrences_count INT NOT NULL DEFAULT 0,
    next_run_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    execution_mode VARCHAR(20) NOT NULL DEFAULT 'CREATE_DRAFT',
    contact_id BIGINT,
    bank_account_id BIGINT,
    description VARCHAR(255),
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (company_id) REFERENCES company(id),
    FOREIGN KEY (contact_id) REFERENCES contact(id),
    FOREIGN KEY (bank_account_id) REFERENCES account(id),
    FOREIGN KEY (created_by) REFERENCES app_user(id)
);

CREATE INDEX idx_recurring_template_company ON recurring_template(company_id);
CREATE INDEX idx_recurring_template_next_run ON recurring_template(company_id, status, next_run_date);
CREATE INDEX idx_recurring_template_status ON recurring_template(company_id, status);

-- Recurrence execution log table - tracks execution history
CREATE TABLE recurrence_execution_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT NOT NULL,
    run_at TIMESTAMP NOT NULL,
    result VARCHAR(20) NOT NULL,
    created_entity_id BIGINT,
    created_entity_type VARCHAR(20),
    error VARCHAR(1000),
    FOREIGN KEY (template_id) REFERENCES recurring_template(id)
);

CREATE INDEX idx_recurrence_log_template ON recurrence_execution_log(template_id);
CREATE INDEX idx_recurrence_log_run_at ON recurrence_execution_log(template_id, run_at);
