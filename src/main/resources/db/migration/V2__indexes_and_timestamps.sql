CREATE INDEX entry_category_result ON entry(user_id,category_id,status,competence);
CREATE INDEX entry_pending_due ON entry(user_id,status,due_date);
CREATE INDEX installment_owner_invoice ON installment(user_id,invoice_id);
CREATE INDEX purchase_owner_category ON purchase(user_id,category_id,status);
ALTER TABLE budget ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE budget ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE installment ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE invoice_payment ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
