-- F5 Family+ subscription (and the shared plan/subscription/charge model reused by F6).
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE plans (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code          TEXT NOT NULL UNIQUE,     -- FAMILY_PLUS | BUSINESS
    name          TEXT NOT NULL,
    price_paise   BIGINT NOT NULL,
    period_days   INT NOT NULL,
    entitlements  TEXT NOT NULL DEFAULT ''  -- comma-separated entitlement keys
);

CREATE TABLE coupons (
    code          TEXT PRIMARY KEY,
    price_paise   BIGINT NOT NULL,          -- the effective price when applied
    active        BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE subscriptions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             TEXT NOT NULL,
    plan_id             UUID NOT NULL REFERENCES plans(id),
    status              TEXT NOT NULL,       -- ACTIVE | PAST_DUE | CANCELLED | EXPIRED
    current_period_end  TIMESTAMPTZ NOT NULL,
    grace_until         TIMESTAMPTZ,         -- entitlements survive until here while PAST_DUE
    price_paise         BIGINT NOT NULL,
    coupon_code         TEXT,
    period_index        INT NOT NULL DEFAULT 1,
    auto_renew          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ
);

CREATE INDEX idx_subscriptions_user_id ON subscriptions (user_id);

-- Idempotent billing: a given (subscription, period) is charged at most once, so a
-- renewal retry never double-charges.
CREATE TABLE subscription_charges (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key    TEXT NOT NULL UNIQUE,
    subscription_id    UUID NOT NULL REFERENCES subscriptions(id),
    amount_paise       BIGINT NOT NULL,
    status             TEXT NOT NULL,        -- PENDING | SUCCESS | FAILED
    provider_charge_id TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_subscription_charges_subscription_id ON subscription_charges (subscription_id);

-- Seed the Family+ plan (INR 499/yr) and the founding-offer coupon (INR 299),
-- so the promo price is data, not hardcoded.
INSERT INTO plans (code, name, price_paise, period_days, entitlements) VALUES
  ('FAMILY_PLUS', 'Family+', 49900, 365,
   'WAITLIST_AUTO_REBOOK,FREE_CANCEL_48H,GROUP_UP_TO_9,PRIORITY_REFUND_72H,LOCAL_LANGUAGE_SUPPORT');

INSERT INTO coupons (code, price_paise, active) VALUES ('FOUNDING', 29900, TRUE);
