-- =============================================================================
-- V1: Extension va ham dung chung
-- =============================================================================

-- Kiem tra PostGIS truoc khi chay de bao loi de hieu thay vi loi cua Postgres.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'postgis') THEN
        RAISE EXCEPTION 'PostGIS chua duoc cai tren PostgreSQL server nay. Xem backend/README.md muc "Chuan bi database".';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'btree_gist') THEN
        RAISE EXCEPTION 'btree_gist chua co (can cho rang buoc chong trung lich booking). Cai postgresql-contrib.';
    END IF;
END $$;

CREATE EXTENSION IF NOT EXISTS pgcrypto;    -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS citext;      -- so sanh email khong phan biet hoa thuong
CREATE EXTENSION IF NOT EXISTS btree_gist;  -- exclusion constraint tren (uuid, tstzrange)
CREATE EXTENSION IF NOT EXISTS postgis;     -- geography(Point, 4326)

-- Trigger tu dong cap nhat updated_at khi ghi truc tiep bang SQL.
CREATE OR REPLACE FUNCTION courtly_set_updated_at()
RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Dong bo cot location (geography) tu latitude/longitude.
-- Dung cho venues va player_preferred_locations de khong phai set 2 noi.
CREATE OR REPLACE FUNCTION courtly_sync_location()
RETURNS trigger AS $$
BEGIN
    IF NEW.latitude IS NOT NULL AND NEW.longitude IS NOT NULL THEN
        NEW.location = ST_SetSRID(ST_MakePoint(NEW.longitude, NEW.latitude), 4326)::geography;
    ELSE
        NEW.location = NULL;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
