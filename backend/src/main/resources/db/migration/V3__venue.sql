-- =============================================================================
-- V3: Nhom 2 - San cau long, ban do, tim kiem (9 bang)
-- =============================================================================

-- 2.1 venues --------------------------------------------------------------
CREATE TABLE venues (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id        uuid NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    name            varchar(200) NOT NULL,
    slug            varchar(220) NOT NULL UNIQUE,
    description     text,
    address         text,
    ward            varchar(100),
    district        varchar(100),
    province        varchar(100),
    country         varchar(100) NOT NULL DEFAULT 'VN',
    latitude        numeric(10,7),
    longitude       numeric(10,7),
    location        geography(Point, 4326),
    phone           varchar(30),
    email           varchar(255),
    status          varchar(30) NOT NULL DEFAULT 'pending',
    approval_status varchar(30) NOT NULL DEFAULT 'pending',
    average_rating  numeric(3,2) NOT NULL DEFAULT 0,
    review_count    int NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT venues_status_check CHECK (status IN ('pending', 'active', 'inactive', 'rejected')),
    CONSTRAINT venues_approval_check CHECK (approval_status IN ('pending', 'approved', 'rejected')),
    CONSTRAINT venues_rating_check CHECK (average_rating >= 0 AND average_rating <= 5),
    CONSTRAINT venues_review_count_check CHECK (review_count >= 0)
);
CREATE INDEX venues_location_gix ON venues USING gist (location);
CREATE INDEX venues_owner_idx ON venues (owner_id);
CREATE INDEX venues_status_idx ON venues (status, approval_status);
CREATE INDEX venues_area_idx ON venues (province, district);
CREATE TRIGGER venues_sync_location BEFORE INSERT OR UPDATE ON venues
    FOR EACH ROW EXECUTE FUNCTION courtly_sync_location();
CREATE TRIGGER venues_set_updated_at BEFORE UPDATE ON venues
    FOR EACH ROW EXECUTE FUNCTION courtly_set_updated_at();

COMMENT ON TABLE venues IS 'Dia diem / cum san cau long thuoc mot chu san';
COMMENT ON COLUMN venues.location IS 'Tu dong sinh tu latitude/longitude; dung cho tim kiem quanh vi tri (2.1.14)';

-- 2.2 courts --------------------------------------------------------------
CREATE TABLE courts (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    venue_id     uuid NOT NULL REFERENCES venues (id) ON DELETE CASCADE,
    name         varchar(100) NOT NULL,
    court_code   varchar(50) NOT NULL,
    court_type   varchar(50) NOT NULL DEFAULT 'standard',
    surface_type varchar(50),
    indoor       boolean NOT NULL DEFAULT true,
    status       varchar(30) NOT NULL DEFAULT 'active',
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT courts_code_unique UNIQUE (venue_id, court_code),
    CONSTRAINT courts_status_check CHECK (status IN ('active', 'inactive', 'maintenance'))
);
CREATE INDEX courts_venue_status_idx ON courts (venue_id, status);
CREATE TRIGGER courts_set_updated_at BEFORE UPDATE ON courts
    FOR EACH ROW EXECUTE FUNCTION courtly_set_updated_at();

-- 2.3 venue_images --------------------------------------------------------
CREATE TABLE venue_images (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    venue_id      uuid NOT NULL REFERENCES venues (id) ON DELETE CASCADE,
    image_url     text NOT NULL,
    caption       varchar(255),
    display_order int NOT NULL DEFAULT 0,
    is_cover      boolean NOT NULL DEFAULT false,
    created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX venue_images_order_idx ON venue_images (venue_id, display_order);
-- Moi venue chi co toi da mot anh bia.
CREATE UNIQUE INDEX venue_images_single_cover ON venue_images (venue_id) WHERE is_cover;

-- 2.4 services ------------------------------------------------------------
CREATE TABLE services (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code        varchar(80) NOT NULL UNIQUE,
    name        varchar(150) NOT NULL,
    description text
);

-- 2.5 venue_services ------------------------------------------------------
CREATE TABLE venue_services (
    venue_id   uuid NOT NULL REFERENCES venues (id) ON DELETE CASCADE,
    service_id uuid NOT NULL REFERENCES services (id) ON DELETE CASCADE,
    price      numeric(12,2),
    note       text,
    PRIMARY KEY (venue_id, service_id),
    CONSTRAINT venue_services_price_check CHECK (price IS NULL OR price >= 0)
);
CREATE INDEX venue_services_service_idx ON venue_services (service_id);

-- 2.6 venue_operating_hours -----------------------------------------------
CREATE TABLE venue_operating_hours (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    venue_id    uuid NOT NULL REFERENCES venues (id) ON DELETE CASCADE,
    day_of_week smallint NOT NULL,
    open_time   time,
    close_time  time,
    is_closed   boolean NOT NULL DEFAULT false,
    CONSTRAINT voh_day_unique UNIQUE (venue_id, day_of_week),
    CONSTRAINT voh_day_check CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT voh_time_check CHECK (
        is_closed OR (open_time IS NOT NULL AND close_time IS NOT NULL AND close_time > open_time)
    )
);

COMMENT ON COLUMN venue_operating_hours.day_of_week IS '1 = Thu hai ... 7 = Chu nhat';

-- 2.7 court_price_rules ---------------------------------------------------
CREATE TABLE court_price_rules (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    court_id       uuid NOT NULL REFERENCES courts (id) ON DELETE CASCADE,
    day_of_week    smallint,
    start_time     time NOT NULL,
    end_time       time NOT NULL,
    price_per_hour numeric(12,2) NOT NULL,
    effective_from date NOT NULL DEFAULT CURRENT_DATE,
    effective_to   date,
    status         varchar(30) NOT NULL DEFAULT 'active',
    created_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT cpr_day_check CHECK (day_of_week IS NULL OR day_of_week BETWEEN 1 AND 7),
    CONSTRAINT cpr_time_check CHECK (end_time > start_time),
    CONSTRAINT cpr_price_check CHECK (price_per_hour >= 0),
    CONSTRAINT cpr_status_check CHECK (status IN ('active', 'inactive')),
    CONSTRAINT cpr_effective_check CHECK (effective_to IS NULL OR effective_to >= effective_from)
);
CREATE INDEX cpr_lookup_idx ON court_price_rules (court_id, status, effective_from, effective_to);

COMMENT ON COLUMN court_price_rules.day_of_week IS 'NULL = ap dung cho moi ngay trong tuan';

-- 2.8 court_blocks --------------------------------------------------------
CREATE TABLE court_blocks (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    court_id   uuid NOT NULL REFERENCES courts (id) ON DELETE CASCADE,
    start_time timestamptz NOT NULL,
    end_time   timestamptz NOT NULL,
    reason     text,
    status     varchar(30) NOT NULL DEFAULT 'active',
    created_by uuid REFERENCES users (id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT court_blocks_time_check CHECK (end_time > start_time),
    CONSTRAINT court_blocks_status_check CHECK (status IN ('active', 'cancelled'))
);
CREATE INDEX court_blocks_court_time_idx ON court_blocks (court_id, start_time, end_time);

COMMENT ON TABLE court_blocks IS 'Chu san khoa khung gio vi bao tri hoac su kien (2.3.10, 2.3.11)';

-- 2.9 favorite_venues -----------------------------------------------------
CREATE TABLE favorite_venues (
    user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    venue_id   uuid NOT NULL REFERENCES venues (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, venue_id)
);
CREATE INDEX favorite_venues_venue_idx ON favorite_venues (venue_id);
