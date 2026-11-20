-- F7 GST invoicing.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Gapless, monotonic invoice numbers per financial year (Apr-Mar). The UPSERT
-- ... RETURNING pattern hands out each number exactly once with no gaps.
CREATE TABLE invoice_sequences (
    fy        TEXT PRIMARY KEY,   -- e.g. 2026-27
    next_val  BIGINT NOT NULL
);

CREATE TABLE invoices (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    number              TEXT NOT NULL UNIQUE,
    fy                  TEXT NOT NULL,
    type                TEXT NOT NULL,          -- RECEIPT | GST_INVOICE | ORG_GST_INVOICE
    booking_ref         TEXT,
    org_id              UUID,
    period              TEXT,                   -- YYYY-MM for org invoices
    seller_gstin        TEXT,
    buyer_gstin         TEXT,
    hsn_sac             TEXT,
    place_of_supply     TEXT,                   -- destination state code
    taxable_value_paise BIGINT NOT NULL,
    cgst_paise          BIGINT NOT NULL DEFAULT 0,
    sgst_paise          BIGINT NOT NULL DEFAULT 0,
    igst_paise          BIGINT NOT NULL DEFAULT 0,
    total_paise         BIGINT NOT NULL,
    pdf_ref             TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- One invoice per booking, and one org GST invoice per org+period: generating
    -- twice is idempotent and never burns a second number.
    CONSTRAINT uq_invoices_booking UNIQUE (booking_ref),
    CONSTRAINT uq_invoices_org_period UNIQUE (org_id, period)
);

CREATE INDEX idx_invoices_org ON invoices (org_id);
