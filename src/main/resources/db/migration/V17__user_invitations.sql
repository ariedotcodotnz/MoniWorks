-- User invitations for email-based onboarding
CREATE TABLE user_invitation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    display_name VARCHAR(100),
    token VARCHAR(64) NOT NULL UNIQUE,
    company_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMP NOT NULL,
    invited_by BIGINT,
    accepted_at TIMESTAMP,
    accepted_user_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_invitation_company FOREIGN KEY (company_id) REFERENCES company(id),
    CONSTRAINT fk_invitation_role FOREIGN KEY (role_id) REFERENCES role(id),
    CONSTRAINT fk_invitation_invited_by FOREIGN KEY (invited_by) REFERENCES app_user(id),
    CONSTRAINT fk_invitation_accepted_user FOREIGN KEY (accepted_user_id) REFERENCES app_user(id)
);

-- Indexes for efficient lookups
CREATE INDEX idx_invitation_token ON user_invitation(token);
CREATE INDEX idx_invitation_email ON user_invitation(email);
CREATE INDEX idx_invitation_company ON user_invitation(company_id);
CREATE INDEX idx_invitation_status ON user_invitation(status);
CREATE INDEX idx_invitation_expires ON user_invitation(expires_at);
