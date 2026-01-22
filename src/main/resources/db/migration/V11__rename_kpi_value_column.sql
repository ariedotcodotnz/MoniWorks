-- V11: Rename 'value' column in kpi_value table to avoid H2 reserved keyword conflict
-- The word 'value' is a reserved keyword in H2 database

ALTER TABLE kpi_value RENAME COLUMN value TO metric_value;
