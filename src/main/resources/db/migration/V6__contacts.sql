-- Contacts table for customers and suppliers
CREATE TABLE contact (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    code VARCHAR(11) NOT NULL,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
    category VARCHAR(50),
    color_tag VARCHAR(20),
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city VARCHAR(100),
    region VARCHAR(100),
    postal_code VARCHAR(20),
    country VARCHAR(2),
    phone VARCHAR(50),
    mobile VARCHAR(50),
    email VARCHAR(100),
    website VARCHAR(255),
    bank_name VARCHAR(100),
    bank_account_number VARCHAR(50),
    bank_routing VARCHAR(50),
    tax_override_code VARCHAR(10),
    default_account_id BIGINT,
    payment_terms VARCHAR(50),
    credit_limit DECIMAL(19,2),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (company_id, code),
    FOREIGN KEY (company_id) REFERENCES company(id),
    FOREIGN KEY (default_account_id) REFERENCES account(id)
);

CREATE INDEX idx_contact_company ON contact(company_id);
CREATE INDEX idx_contact_type ON contact(company_id, type);
CREATE INDEX idx_contact_active ON contact(company_id, active);

-- Contact people (multiple people per contact)
CREATE TABLE contact_person (
    id BIGSERIAL PRIMARY KEY,
    contact_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(50),
    role_label VARCHAR(50),
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (contact_id) REFERENCES contact(id) ON DELETE CASCADE
);

CREATE INDEX idx_contact_person_contact ON contact_person(contact_id);

-- Contact notes and interaction history
CREATE TABLE contact_note (
    id BIGSERIAL PRIMARY KEY,
    contact_id BIGINT NOT NULL,
    note_text VARCHAR(2000) NOT NULL,
    follow_up_date DATE,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (contact_id) REFERENCES contact(id) ON DELETE CASCADE,
    FOREIGN KEY (created_by) REFERENCES app_user(id)
);

CREATE INDEX idx_contact_note_contact ON contact_note(contact_id);
CREATE INDEX idx_contact_note_follow_up ON contact_note(follow_up_date);
