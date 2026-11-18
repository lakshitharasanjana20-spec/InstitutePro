SET NAMES utf8mb4;
-- Run ONCE. Adds cancel/refund tracking to payments.
ALTER TABLE payments ADD COLUMN voided_at DATETIME NULL, ADD COLUMN voided_by INT NULL, ADD COLUMN void_reason VARCHAR(255) NULL;
