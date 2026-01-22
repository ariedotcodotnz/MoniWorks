-- Sales Invoices (A/R) tables for Release 2

-- Main sales invoice header
CREATE TABLE sales_invoice (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    invoice_number VARCHAR(20) NOT NULL,
    contact_id BIGINT NOT NULL,
    issue_date DATE NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'DRAFT',
    currency VARCHAR(3),
    reference VARCHAR(255),
    notes VARCHAR(500),
    subtotal DECIMAL(19,2) DEFAULT 0,
    tax_total DECIMAL(19,2) DEFAULT 0,
    total DECIMAL(19,2) DEFAULT 0,
    amount_paid DECIMAL(19,2) DEFAULT 0,
    posted_transaction_id BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    issued_at TIMESTAMP,
    UNIQUE (company_id, invoice_number),
    FOREIGN KEY (company_id) REFERENCES company(id),
    FOREIGN KEY (contact_id) REFERENCES contact(id),
    FOREIGN KEY (posted_transaction_id) REFERENCES transaction(id),
    FOREIGN KEY (created_by) REFERENCES app_user(id)
);

-- Sales invoice line items
CREATE TABLE sales_invoice_line (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sales_invoice_id BIGINT NOT NULL,
    line_index INT NOT NULL,
    product_id BIGINT,
    description VARCHAR(500),
    quantity DECIMAL(19,4) NOT NULL DEFAULT 1,
    unit_price DECIMAL(19,2) NOT NULL DEFAULT 0,
    account_id BIGINT NOT NULL,
    tax_code VARCHAR(10),
    tax_rate DECIMAL(5,2),
    tax_amount DECIMAL(19,2) DEFAULT 0,
    line_total DECIMAL(19,2) DEFAULT 0,
    department_id BIGINT,
    FOREIGN KEY (sales_invoice_id) REFERENCES sales_invoice(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES product(id),
    FOREIGN KEY (account_id) REFERENCES account(id),
    FOREIGN KEY (department_id) REFERENCES department(id)
);

-- Receipt-to-invoice allocation tracking
CREATE TABLE receivable_allocation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    receipt_transaction_id BIGINT NOT NULL,
    sales_invoice_id BIGINT NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    allocated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    allocated_by BIGINT,
    FOREIGN KEY (company_id) REFERENCES company(id),
    FOREIGN KEY (receipt_transaction_id) REFERENCES transaction(id),
    FOREIGN KEY (sales_invoice_id) REFERENCES sales_invoice(id),
    FOREIGN KEY (allocated_by) REFERENCES app_user(id)
);

-- Indexes for performance
CREATE INDEX idx_sales_invoice_company ON sales_invoice(company_id);
CREATE INDEX idx_sales_invoice_contact ON sales_invoice(contact_id);
CREATE INDEX idx_sales_invoice_status ON sales_invoice(company_id, status);
CREATE INDEX idx_sales_invoice_due_date ON sales_invoice(company_id, due_date);
CREATE INDEX idx_sales_invoice_issue_date ON sales_invoice(company_id, issue_date);

CREATE INDEX idx_sales_invoice_line_invoice ON sales_invoice_line(sales_invoice_id);

CREATE INDEX idx_receivable_allocation_company ON receivable_allocation(company_id);
CREATE INDEX idx_receivable_allocation_receipt ON receivable_allocation(receipt_transaction_id);
CREATE INDEX idx_receivable_allocation_invoice ON receivable_allocation(sales_invoice_id);
