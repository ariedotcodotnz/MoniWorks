-- V23: Add ReversalLink table per spec 04 domain model
-- Links original transactions to their reversal transactions for audit trail

CREATE TABLE reversal_link (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    original_transaction_id BIGINT NOT NULL,
    reversing_transaction_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    reason VARCHAR(500),
    CONSTRAINT fk_reversal_link_original FOREIGN KEY (original_transaction_id) REFERENCES transaction(id),
    CONSTRAINT fk_reversal_link_reversing FOREIGN KEY (reversing_transaction_id) REFERENCES transaction(id),
    CONSTRAINT fk_reversal_link_created_by FOREIGN KEY (created_by) REFERENCES app_user(id),
    CONSTRAINT uk_reversal_link_pair UNIQUE (original_transaction_id, reversing_transaction_id)
);

CREATE INDEX idx_reversal_link_original ON reversal_link(original_transaction_id);
CREATE INDEX idx_reversal_link_reversing ON reversal_link(reversing_transaction_id);
