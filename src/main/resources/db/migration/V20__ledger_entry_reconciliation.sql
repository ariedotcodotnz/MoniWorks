-- V20: Add reconciliation status tracking to ledger_entry
-- Per spec 05 (Bank Import and Reconciliation): "Reconciliation state is stored per ledger transaction line"

-- Add reconciliation tracking columns to ledger_entry
ALTER TABLE ledger_entry ADD COLUMN is_reconciled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ledger_entry ADD COLUMN reconciliation_status VARCHAR(20) DEFAULT 'UNRECONCILED';
ALTER TABLE ledger_entry ADD COLUMN reconciled_bank_feed_item_id BIGINT;
ALTER TABLE ledger_entry ADD COLUMN reconciled_at TIMESTAMP;
ALTER TABLE ledger_entry ADD COLUMN reconciled_by_id BIGINT;

-- Foreign key constraints
ALTER TABLE ledger_entry ADD CONSTRAINT fk_ledger_reconciled_bank_feed
    FOREIGN KEY (reconciled_bank_feed_item_id) REFERENCES bank_feed_item(id);
ALTER TABLE ledger_entry ADD CONSTRAINT fk_ledger_reconciled_by
    FOREIGN KEY (reconciled_by_id) REFERENCES app_user(id);

-- Index for querying unreconciled entries by account
CREATE INDEX idx_ledger_reconciled ON ledger_entry(account_id, is_reconciled);
