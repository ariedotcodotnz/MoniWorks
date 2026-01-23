-- Supplier Bills (A/P) tables for Release 2

-- Main supplier bill header
CREATE TABLE supplier_bill (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    bill_number VARCHAR(50) NOT NULL,
    contact_id BIGINT NOT NULL,
    bill_date DATE NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'DRAFT',
    currency VARCHAR(3),
    supplier_reference VARCHAR(100),
    notes VARCHAR(500),
    subtotal DECIMAL(19,2) DEFAULT 0,
    tax_total DECIMAL(19,2) DEFAULT 0,
    total DECIMAL(19,2) DEFAULT 0,
    amount_paid DECIMAL(19,2) DEFAULT 0,
    posted_transaction_id BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    posted_at TIMESTAMP,
    UNIQUE (company_id, bill_number),
    FOREIGN KEY (company_id) REFERENCES company(id),
    FOREIGN KEY (contact_id) REFERENCES contact(id),
    FOREIGN KEY (posted_transaction_id) REFERENCES transaction(id),
    FOREIGN KEY (created_by) REFERENCES app_user(id)
);

-- Supplier bill line items
CREATE TABLE supplier_bill_line (
    id BIGSERIAL PRIMARY KEY,
    supplier_bill_id BIGINT NOT NULL,
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
    FOREIGN KEY (supplier_bill_id) REFERENCES supplier_bill(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES product(id),
    FOREIGN KEY (account_id) REFERENCES account(id),
    FOREIGN KEY (department_id) REFERENCES department(id)
);

-- Payment-to-bill allocation tracking
CREATE TABLE payable_allocation (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    payment_transaction_id BIGINT NOT NULL,
    supplier_bill_id BIGINT NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    allocated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    allocated_by BIGINT,
    FOREIGN KEY (company_id) REFERENCES company(id),
    FOREIGN KEY (payment_transaction_id) REFERENCES transaction(id),
    FOREIGN KEY (supplier_bill_id) REFERENCES supplier_bill(id),
    FOREIGN KEY (allocated_by) REFERENCES app_user(id)
);

-- Payment run for batch payments
CREATE TABLE payment_run (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    run_date DATE NOT NULL,
    bank_account_id BIGINT NOT NULL,
    status VARCHAR(15) NOT NULL DEFAULT 'DRAFT',
    items_json TEXT,
    output_attachment_id BIGINT,
    notes VARCHAR(500),
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    FOREIGN KEY (company_id) REFERENCES company(id),
    FOREIGN KEY (bank_account_id) REFERENCES account(id),
    FOREIGN KEY (output_attachment_id) REFERENCES attachment(id),
    FOREIGN KEY (created_by) REFERENCES app_user(id)
);

-- Indexes for performance
CREATE INDEX idx_supplier_bill_company ON supplier_bill(company_id);
CREATE INDEX idx_supplier_bill_contact ON supplier_bill(contact_id);
CREATE INDEX idx_supplier_bill_status ON supplier_bill(company_id, status);
CREATE INDEX idx_supplier_bill_due_date ON supplier_bill(company_id, due_date);
CREATE INDEX idx_supplier_bill_bill_date ON supplier_bill(company_id, bill_date);

CREATE INDEX idx_supplier_bill_line_bill ON supplier_bill_line(supplier_bill_id);

CREATE INDEX idx_payable_allocation_company ON payable_allocation(company_id);
CREATE INDEX idx_payable_allocation_payment ON payable_allocation(payment_transaction_id);
CREATE INDEX idx_payable_allocation_bill ON payable_allocation(supplier_bill_id);

CREATE INDEX idx_payment_run_company ON payment_run(company_id);
CREATE INDEX idx_payment_run_status ON payment_run(company_id, status);
