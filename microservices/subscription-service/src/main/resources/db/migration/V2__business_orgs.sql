-- F6 Business subscription: org-level plan with spend controls, approvals, a credit
-- line and a consolidated monthly bill. This is a B2B subscription + credit, entirely
-- separate from the (always-free) consumer booking path (G1).

INSERT INTO plans (code, name, price_paise, period_days, entitlements) VALUES
  ('BUSINESS', 'Business', 99900, 30, 'SPEND_CONTROLS,CONSOLIDATED_BILLING,CREDIT_LINE,NAMED_CONTACT');

CREATE TABLE orgs (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name           TEXT NOT NULL,
    status         TEXT NOT NULL DEFAULT 'ACTIVE',
    named_contact  TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE org_members (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id    UUID NOT NULL REFERENCES orgs(id),
    user_id   TEXT NOT NULL,
    role      TEXT NOT NULL,          -- ADMIN | APPROVER | TRAVELLER
    CONSTRAINT uq_org_members UNIQUE (org_id, user_id)
);

CREATE TABLE spend_policies (
    org_id             UUID PRIMARY KEY REFERENCES orgs(id),
    per_trip_cap_paise BIGINT NOT NULL,
    monthly_cap_paise  BIGINT NOT NULL
);

CREATE TABLE credit_accounts (
    org_id           UUID PRIMARY KEY REFERENCES orgs(id),
    limit_paise      BIGINT NOT NULL DEFAULT 0,
    outstanding_paise BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE approvals (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id           UUID NOT NULL REFERENCES orgs(id),
    booking_ref      TEXT NOT NULL,
    member_user_id   TEXT NOT NULL,
    amount_paise     BIGINT NOT NULL,
    status           TEXT NOT NULL,   -- PENDING | APPROVED | REJECTED
    decided_by       TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_approvals_booking UNIQUE (org_id, booking_ref)
);

-- Confirmed org bookings, the source of the consolidated monthly bill.
CREATE TABLE org_bookings (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id       UUID NOT NULL REFERENCES orgs(id),
    booking_ref  TEXT NOT NULL,
    amount_paise BIGINT NOT NULL,
    period       TEXT NOT NULL,       -- YYYY-MM
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_org_bookings UNIQUE (org_id, booking_ref)
);

CREATE INDEX idx_org_bookings_period ON org_bookings (org_id, period);
