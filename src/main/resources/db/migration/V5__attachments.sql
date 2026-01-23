-- MoniWorks Attachments Schema
-- This migration adds tables for file attachment storage and entity linking

-- Attachment (file metadata)
CREATE TABLE attachment (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    filename VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    size BIGINT NOT NULL,
    checksum_sha256 VARCHAR(64) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    uploaded_by BIGINT,
    FOREIGN KEY (company_id) REFERENCES company(id),
    FOREIGN KEY (uploaded_by) REFERENCES app_user(id)
);

CREATE INDEX idx_attachment_company ON attachment(company_id);
CREATE INDEX idx_attachment_checksum ON attachment(company_id, checksum_sha256);

-- Attachment Link (many-to-many link between attachments and entities)
CREATE TABLE attachment_link (
    id BIGSERIAL PRIMARY KEY,
    attachment_id BIGINT NOT NULL,
    entity_type VARCHAR(20) NOT NULL,
    entity_id BIGINT NOT NULL,
    UNIQUE (attachment_id, entity_type, entity_id),
    FOREIGN KEY (attachment_id) REFERENCES attachment(id) ON DELETE CASCADE
);

CREATE INDEX idx_attachment_link_entity ON attachment_link(entity_type, entity_id);
CREATE INDEX idx_attachment_link_attachment ON attachment_link(attachment_id);
