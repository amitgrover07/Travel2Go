-- F3: Refund SLA engine.
-- Turns the existing Razorpay refund passthrough into a guaranteed, tracked,
-- auto-triggered refund with a ring-fenced float.

-- One refund per payment: the race guard that makes refunds exactly-once under
-- concurrency (INSERT ... ON CONFLICT (payment_id) DO NOTHING).
ALTER TABLE refunds ADD CONSTRAINT uq_refunds_payment_id UNIQUE (payment_id);

-- SLA lifecycle tracked separately from the provider outcome in `status`:
--   status     = did the provider accept the refund (PENDING/SUCCESS/FAILED)
--   sla_status = have we met the < 72h settlement promise (INITIATED/SETTLED/BREACHED)
ALTER TABLE refunds ADD COLUMN sla_status   TEXT;
ALTER TABLE refunds ADD COLUMN sla_due_at   TIMESTAMPTZ;
ALTER TABLE refunds ADD COLUMN trigger      TEXT;   -- USER_CANCEL | DISRUPTION | MANUAL
ALTER TABLE refunds ADD COLUMN float_txn_id UUID;

-- Find refunds whose SLA is still open and already past due (breach sweep).
CREATE INDEX idx_refunds_sla_open ON refunds (sla_due_at) WHERE sla_status = 'INITIATED';

-- Ring-fenced refund float: an internal liability ledger, held separate from
-- operating funds. Every refund debits it; every top-up credits it. The
-- authoritative balance is always the signed SUM over this table; balance_after
-- is a convenience snapshot.
CREATE TABLE refund_float_ledger (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    direction      TEXT   NOT NULL,          -- DEBIT | CREDIT
    amount_paise   BIGINT NOT NULL,
    balance_after  BIGINT NOT NULL,
    refund_id      UUID REFERENCES refunds(id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refund_float_ledger_created_at ON refund_float_ledger (created_at);
