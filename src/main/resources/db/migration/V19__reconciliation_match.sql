-- V19: Add ReconciliationMatch entity for audit trail of bank reconciliation
-- Per spec 05 (Bank Import and Reconciliation), this provides tracking of who matched what and when

CREATE TABLE reconciliation_match (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    bank_feed_item_id BIGINT NOT NULL,
    transaction_id BIGINT NOT NULL,
    match_type VARCHAR(20) NOT NULL,
    matched_by_id BIGINT,
    matched_at TIMESTAMP NOT NULL,
    match_notes VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    unmatched_by_id BIGINT,
    unmatched_at TIMESTAMP,
    CONSTRAINT fk_recon_match_company FOREIGN KEY (company_id) REFERENCES company(id),
    CONSTRAINT fk_recon_match_bank_feed FOREIGN KEY (bank_feed_item_id) REFERENCES bank_feed_item(id),
    CONSTRAINT fk_recon_match_transaction FOREIGN KEY (transaction_id) REFERENCES transaction(id),
    CONSTRAINT fk_recon_match_matched_by FOREIGN KEY (matched_by_id) REFERENCES app_user(id),
    CONSTRAINT fk_recon_match_unmatched_by FOREIGN KEY (unmatched_by_id) REFERENCES app_user(id)
);

CREATE INDEX idx_recon_match_bank_feed ON reconciliation_match(bank_feed_item_id);
CREATE INDEX idx_recon_match_transaction ON reconciliation_match(transaction_id);
CREATE INDEX idx_recon_match_company ON reconciliation_match(company_id);
CREATE INDEX idx_recon_match_active ON reconciliation_match(company_id, is_active);
