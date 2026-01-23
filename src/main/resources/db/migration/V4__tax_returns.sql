-- MoniWorks Tax Return Schema
-- This migration adds tables for GST/VAT return generation and storage

-- Tax Return
CREATE TABLE tax_return (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    basis VARCHAR(10) NOT NULL DEFAULT 'INVOICE',
    status VARCHAR(15) NOT NULL DEFAULT 'DRAFT',
    total_sales DECIMAL(19,2) DEFAULT 0,
    total_purchases DECIMAL(19,2) DEFAULT 0,
    output_tax DECIMAL(19,2) DEFAULT 0,
    input_tax DECIMAL(19,2) DEFAULT 0,
    tax_payable DECIMAL(19,2) DEFAULT 0,
    generated_at TIMESTAMP,
    generated_by BIGINT,
    finalized_at TIMESTAMP,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (company_id) REFERENCES company(id),
    FOREIGN KEY (generated_by) REFERENCES app_user(id)
);

CREATE INDEX idx_tax_return_company_period ON tax_return(company_id, start_date, end_date);

-- Tax Return Line (boxes)
CREATE TABLE tax_return_line (
    id BIGSERIAL PRIMARY KEY,
    tax_return_id BIGINT NOT NULL,
    box_code VARCHAR(20) NOT NULL,
    box_description VARCHAR(100),
    amount DECIMAL(19,2) NOT NULL DEFAULT 0,
    transaction_count INT DEFAULT 0,
    FOREIGN KEY (tax_return_id) REFERENCES tax_return(id) ON DELETE CASCADE
);
