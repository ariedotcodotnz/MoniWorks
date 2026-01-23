-- MoniWorks Bank Accounts Migration
-- Adds bank account functionality to the accounting system

-- Add bank account fields to existing account table
ALTER TABLE account ADD COLUMN is_bank_account BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE account ADD COLUMN bank_name VARCHAR(100);
ALTER TABLE account ADD COLUMN bank_number VARCHAR(50);
ALTER TABLE account ADD COLUMN bank_currency VARCHAR(3);

-- Bank Statement Import - tracks imported bank statement files
CREATE TABLE bank_statement_import (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    imported_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source_type VARCHAR(20) NOT NULL,  -- QIF, OFX, QFX, QBO, CSV
    source_name VARCHAR(255) NOT NULL, -- Original filename
    file_hash VARCHAR(64) NOT NULL,    -- SHA-256 hash for deduplication
    total_items INT NOT NULL DEFAULT 0,
    UNIQUE (company_id, account_id, file_hash),
    FOREIGN KEY (company_id) REFERENCES company(id),
    FOREIGN KEY (account_id) REFERENCES account(id)
);

-- Bank Feed Item - individual lines from imported bank statements
CREATE TABLE bank_feed_item (
    id BIGSERIAL PRIMARY KEY,
    import_id BIGINT NOT NULL,
    posted_date DATE NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    description VARCHAR(500),
    fit_id VARCHAR(100),              -- Bank's unique transaction ID
    raw_json TEXT,                     -- Original import data for reference
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',  -- NEW, MATCHED, CREATED, IGNORED
    matched_transaction_id BIGINT,     -- Links to matched/created transaction
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (import_id) REFERENCES bank_statement_import(id),
    FOREIGN KEY (matched_transaction_id) REFERENCES txn(id)
);

-- Index for efficient lookup of unmatched items
CREATE INDEX idx_bank_feed_item_status ON bank_feed_item(import_id, status);

-- Index for FITID deduplication
CREATE INDEX idx_bank_feed_item_fitid ON bank_feed_item(import_id, fit_id);

-- Allocation Rule - rules for auto-suggesting coding during reconciliation
CREATE TABLE allocation_rule (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    priority INT NOT NULL DEFAULT 0,
    rule_name VARCHAR(100) NOT NULL,
    match_expression VARCHAR(500) NOT NULL,  -- e.g., "description CONTAINS 'UBER'"
    target_account_id BIGINT NOT NULL,
    target_tax_code VARCHAR(10),
    memo_template VARCHAR(255),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (company_id) REFERENCES company(id),
    FOREIGN KEY (target_account_id) REFERENCES account(id)
);

-- Index for rule lookup by company and priority
CREATE INDEX idx_allocation_rule_lookup ON allocation_rule(company_id, enabled, priority);
