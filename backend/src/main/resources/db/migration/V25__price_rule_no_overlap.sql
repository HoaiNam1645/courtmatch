-- =============================================================================
-- V25: 2.3.5 - Bang gia cua mot san con khong duoc chong lan
-- =============================================================================
-- Hai quy tac gia dang hieu luc cua cung san con khong duoc cung ap dung cho mot thoi diem:
-- trung ngay trong tuan (NULL = moi ngay), giao khung gio va giao thoi gian hieu luc.
-- PricingCalculator lay quy tac dau tien khop, chong lan se cho ra gia khong xac dinh.
-- Tang nghiep vu da kiem tra truoc; rang buoc nay chan hai request dong thoi.

ALTER TABLE court_price_rules ADD CONSTRAINT cpr_no_overlap EXCLUDE USING gist (
    court_id WITH =,
    int4range(COALESCE(day_of_week, 1), COALESCE(day_of_week, 7), '[]') WITH &&,
    tsrange(DATE '2000-01-01' + start_time, DATE '2000-01-01' + end_time) WITH &&,
    daterange(effective_from, effective_to, '[]') WITH &&
) WHERE (status = 'active');

COMMENT ON CONSTRAINT cpr_no_overlap ON court_price_rules IS
    'Quy tac gia dang hieu luc cua mot san con khong chong lan ngay, gio va thoi gian hieu luc (2.3.5)';
