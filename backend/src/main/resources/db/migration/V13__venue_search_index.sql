-- =============================================================================
-- V13: Toi uu tim kiem san (2.1.13)
--
-- Hai van de cua tim kiem theo ten/quan/dia chi:
--   1. ILIKE '%tu khoa%' khong dung duoc B-tree index -> quet toan bang.
--   2. Nguoi dung Viet thuong go khong dau: "cau giay" phai ra "Cầu Giấy".
--
-- Giai quyet bang unaccent (bo dau) + trigram index (GIN) tren chuoi da bo dau.
-- =============================================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'pg_trgm') THEN
        RAISE EXCEPTION 'Thieu extension pg_trgm (postgresql-contrib), can cho tim kiem san.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'unaccent') THEN
        RAISE EXCEPTION 'Thieu extension unaccent (postgresql-contrib), can cho tim kiem khong dau.';
    END IF;
END $$;

CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;

-- unaccent() goc duoc danh dau STABLE nen khong dung truc tiep trong index duoc.
-- Boc lai thanh ham IMMUTABLE bang cach chi ro tu dien, day la cach lam chuan.
CREATE OR REPLACE FUNCTION courtly_unaccent(text)
RETURNS text AS
$$ SELECT public.unaccent('public.unaccent', $1) $$
LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT;

COMMENT ON FUNCTION courtly_unaccent(text) IS
    'Bo dau tieng Viet, IMMUTABLE de dung duoc trong index';

CREATE INDEX venues_name_unaccent_trgm_idx
    ON venues USING gin (courtly_unaccent(name) gin_trgm_ops);
CREATE INDEX venues_address_unaccent_trgm_idx
    ON venues USING gin (courtly_unaccent(address) gin_trgm_ops);
CREATE INDEX venues_district_unaccent_trgm_idx
    ON venues USING gin (courtly_unaccent(district) gin_trgm_ops);

-- Loc theo gia thap nhat cua san doc nhieu tu bang nay.
CREATE INDEX cpr_active_price_idx ON court_price_rules (court_id, price_per_hour)
    WHERE status = 'active';

COMMENT ON INDEX venues_name_unaccent_trgm_idx IS
    'Ho tro ILIKE %tu khoa% khong dau khi tim san theo ten (2.1.13)';
