-- Bind coupons to a specific plan so a promo for one plan cannot be applied to
-- another (e.g. the Family+ FOUNDING price must not discount the Business plan).
ALTER TABLE coupons ADD COLUMN plan_code TEXT;

UPDATE coupons SET plan_code = 'FAMILY_PLUS' WHERE code = 'FOUNDING';

ALTER TABLE coupons ALTER COLUMN plan_code SET NOT NULL;
