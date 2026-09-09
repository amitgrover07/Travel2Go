CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE payments (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_ref            TEXT NOT NULL,
    owner_user_id          TEXT,
    status                 TEXT NOT NULL,
    amount_paise           BIGINT NOT NULL,
    fee_paise              BIGINT NOT NULL DEFAULT 0,
    method                 TEXT,
    provider_order_id      TEXT,
    provider_payment_id    TEXT,
    quote_token_validated  BOOLEAN,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ,
    CONSTRAINT uq_payments_provider_order_id UNIQUE (provider_order_id)
);

CREATE UNIQUE INDEX uq_payments_provider_payment_id
    ON payments (provider_payment_id)
    WHERE provider_payment_id IS NOT NULL;

CREATE INDEX idx_payments_booking_ref ON payments (booking_ref);
CREATE INDEX idx_payments_owner_user_id ON payments (owner_user_id);

CREATE TABLE processed_webhook_events (
    event_id     TEXT PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE refunds (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id            UUID NOT NULL REFERENCES payments(id),
    provider_refund_id    TEXT,
    amount_paise          BIGINT NOT NULL,
    status                TEXT NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refunds_payment_id ON refunds (payment_id);

CREATE TABLE settlements (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id               UUID NOT NULL REFERENCES payments(id),
    provider_settlement_id   TEXT,
    amount_paise             BIGINT NOT NULL,
    settled_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    status                   TEXT NOT NULL
);

CREATE INDEX idx_settlements_payment_id ON settlements (payment_id);
