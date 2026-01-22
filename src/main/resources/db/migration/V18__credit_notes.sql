-- Add credit note support to sales_invoice table
ALTER TABLE sales_invoice ADD COLUMN invoice_type VARCHAR(15) DEFAULT 'INVOICE' NOT NULL;
ALTER TABLE sales_invoice ADD COLUMN original_invoice_id BIGINT;

-- Add foreign key for credit note -> original invoice relationship
ALTER TABLE sales_invoice ADD CONSTRAINT fk_invoice_original_invoice
    FOREIGN KEY (original_invoice_id) REFERENCES sales_invoice(id);

-- Index for finding credit notes by original invoice
CREATE INDEX idx_invoice_original ON sales_invoice(original_invoice_id);

-- Index for filtering by invoice type
CREATE INDEX idx_invoice_type ON sales_invoice(invoice_type);
