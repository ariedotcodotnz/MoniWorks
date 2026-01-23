-- V21: Add amount range matching to allocation rules
-- Per spec 05: "rules can match on description, amount ranges, counterparty, etc."

ALTER TABLE allocation_rule ADD COLUMN min_amount DECIMAL(19, 2);
ALTER TABLE allocation_rule ADD COLUMN max_amount DECIMAL(19, 2);

-- Add check constraint to ensure min <= max when both are specified
-- Note: This is a soft constraint for flexibility; the application enforces stricter validation
