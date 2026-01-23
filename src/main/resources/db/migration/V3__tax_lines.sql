-- MoniWorks Tax Line Schema
-- This migration adds the tax_line table for recording calculated tax amounts
-- Each posted transaction line with a tax code creates a corresponding tax line
-- Used for GST/VAT return generation and tax reporting

CREATE TABLE tax_line (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    ledger_entry_id BIGINT NOT NULL,
    entry_date DATE NOT NULL,
    tax_code VARCHAR(10) NOT NULL,
    tax_rate DECIMAL(5,4) NOT NULL,
    taxable_amount DECIMAL(19,2) NOT NULL,
    tax_amount DECIMAL(19,2) NOT NULL,
    report_box VARCHAR(20),
    jurisdiction VARCHAR(10),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (company_id) REFERENCES company(id),
    FOREIGN KEY (ledger_entry_id) REFERENCES ledger_entry(id)
);

CREATE INDEX idx_tax_line_company_date ON tax_line(company_id, entry_date);
CREATE INDEX idx_tax_line_tax_code ON tax_line(company_id, tax_code);
CREATE INDEX idx_tax_line_report_box ON tax_line(company_id, report_box);
