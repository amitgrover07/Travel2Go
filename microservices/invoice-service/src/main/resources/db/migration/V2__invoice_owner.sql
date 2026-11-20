-- F7 hardening: record who owns a booking invoice so reads can be ownership-checked
-- (guards against reading another user's invoice). Org invoices are org-level and
-- remain owner-less (admin/org-scoped access).
ALTER TABLE invoices ADD COLUMN owner_user_id TEXT;

CREATE INDEX idx_invoices_owner ON invoices (owner_user_id);
