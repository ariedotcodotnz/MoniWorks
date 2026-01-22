-- V14: Statement Runs for batch statement generation
-- Tracks batches of customer statement generation with criteria and output

CREATE TABLE statement_run (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES company(id),
    run_date DATE NOT NULL,
    as_of_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    criteria_json VARCHAR(4000),
    statement_count INTEGER,
    output_attachment_id BIGINT,
    error_message VARCHAR(500),
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

-- Indexes for common queries
CREATE INDEX idx_statement_run_company ON statement_run(company_id);
CREATE INDEX idx_statement_run_status ON statement_run(company_id, status);
CREATE INDEX idx_statement_run_date ON statement_run(company_id, run_date);
