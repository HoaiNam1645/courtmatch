-- =============================================================================
-- V2: Nhom 1 - Tai khoan, dang nhap, phan quyen (11 bang)
-- =============================================================================

-- 1.1 users ---------------------------------------------------------------
CREATE TABLE users (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name          varchar(150) NOT NULL,
    email              varchar(255),
    phone              varchar(20),
    password_hash      text,
    avatar_url         text,
    email_verified_at  timestamptz,
    phone_verified_at  timestamptz,
    status             varchar(30) NOT NULL DEFAULT 'pending',
    last_login_at      timestamptz,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT users_status_check CHECK (status IN ('active', 'inactive', 'banned', 'pending')),
    CONSTRAINT users_identity_check CHECK (email IS NOT NULL OR phone IS NOT NULL)
);
CREATE UNIQUE INDEX users_email_unique ON users (lower(email)) WHERE email IS NOT NULL;
CREATE UNIQUE INDEX users_phone_unique ON users (phone) WHERE phone IS NOT NULL;
CREATE INDEX users_status_idx ON users (status);
CREATE TRIGGER users_set_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION courtly_set_updated_at();

COMMENT ON TABLE users IS 'Tai khoan chung cua moi nguoi dung (nguoi choi, chu san, nhan vien, admin)';
COMMENT ON COLUMN users.password_hash IS 'Nullable de ho tro tai khoan chi dang nhap bang Google';

-- 1.2 auth_providers ------------------------------------------------------
CREATE TABLE auth_providers (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider_name           varchar(50) NOT NULL,
    provider_user_id        varchar(255) NOT NULL,
    provider_email          varchar(255),
    provider_email_verified boolean NOT NULL DEFAULT false,
    provider_avatar_url     text,
    access_token_encrypted  text,
    refresh_token_encrypted text,
    token_expires_at        timestamptz,
    linked_at               timestamptz NOT NULL DEFAULT now(),
    last_used_at            timestamptz,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT auth_providers_provider_unique UNIQUE (provider_name, provider_user_id),
    CONSTRAINT auth_providers_user_provider_unique UNIQUE (user_id, provider_name)
);
CREATE INDEX auth_providers_user_idx ON auth_providers (user_id);
CREATE TRIGGER auth_providers_set_updated_at BEFORE UPDATE ON auth_providers
    FOR EACH ROW EXECUTE FUNCTION courtly_set_updated_at();

COMMENT ON TABLE auth_providers IS 'Danh tinh dang nhap ben ngoai, truoc mat la Google OAuth';

-- 1.3 password_reset_requests ---------------------------------------------
CREATE TABLE password_reset_requests (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    channel           varchar(20) NOT NULL,
    destination       varchar(255) NOT NULL,
    verification_hash text NOT NULL,
    expires_at        timestamptz NOT NULL,
    verified_at       timestamptz,
    used_at           timestamptz,
    attempt_count     smallint NOT NULL DEFAULT 0,
    resend_count      smallint NOT NULL DEFAULT 0,
    requested_ip      inet,
    user_agent        text,
    created_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT prr_channel_check CHECK (channel IN ('email', 'phone')),
    CONSTRAINT prr_attempt_check CHECK (attempt_count >= 0),
    CONSTRAINT prr_resend_check CHECK (resend_count >= 0)
);
CREATE INDEX prr_user_created_idx ON password_reset_requests (user_id, created_at DESC);
CREATE INDEX prr_destination_created_idx ON password_reset_requests (destination, created_at DESC);
CREATE INDEX prr_expires_idx ON password_reset_requests (expires_at) WHERE used_at IS NULL;

COMMENT ON COLUMN password_reset_requests.verification_hash IS 'Chi luu hash cua OTP/token, khong bao gio luu ma goc';

-- 1.4 roles ---------------------------------------------------------------
CREATE TABLE roles (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code        varchar(50) NOT NULL UNIQUE,
    name        varchar(100) NOT NULL,
    description text,
    created_at  timestamptz NOT NULL DEFAULT now()
);

-- 1.5 permissions ---------------------------------------------------------
CREATE TABLE permissions (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code        varchar(100) NOT NULL UNIQUE,
    name        varchar(150) NOT NULL,
    description text,
    created_at  timestamptz NOT NULL DEFAULT now()
);

-- 1.6 role_permissions ----------------------------------------------------
CREATE TABLE role_permissions (
    role_id       uuid NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_id uuid NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);
CREATE INDEX role_permissions_permission_idx ON role_permissions (permission_id);

-- 1.7 user_roles ----------------------------------------------------------
CREATE TABLE user_roles (
    user_id     uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id     uuid NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    assigned_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role_id)
);
CREATE INDEX user_roles_role_idx ON user_roles (role_id);

-- 1.8 player_profiles -----------------------------------------------------
CREATE TABLE player_profiles (
    user_id            uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    gender             varchar(20),
    date_of_birth      date,
    skill_level        varchar(30),
    skill_score        numeric(8,2),
    dominant_hand      varchar(20),
    playing_style      varchar(50),
    preferred_play_type varchar(30),
    bio                text,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT player_profiles_gender_check CHECK (gender IS NULL OR gender IN ('male', 'female', 'other')),
    CONSTRAINT player_profiles_skill_level_check CHECK (skill_level IS NULL OR skill_level IN ('beginner', 'intermediate', 'advanced', 'professional')),
    CONSTRAINT player_profiles_hand_check CHECK (dominant_hand IS NULL OR dominant_hand IN ('left', 'right', 'both')),
    CONSTRAINT player_profiles_play_type_check CHECK (preferred_play_type IS NULL OR preferred_play_type IN ('single', 'double', 'mixed'))
);
CREATE INDEX player_profiles_skill_level_idx ON player_profiles (skill_level);
CREATE TRIGGER player_profiles_set_updated_at BEFORE UPDATE ON player_profiles
    FOR EACH ROW EXECUTE FUNCTION courtly_set_updated_at();

-- 1.9 court_owner_profiles ------------------------------------------------
CREATE TABLE court_owner_profiles (
    user_id             uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    business_name       varchar(200),
    tax_code            varchar(50),
    identity_number     varchar(50),
    bank_name           varchar(100),
    bank_account_no     varchar(50),
    bank_account_name   varchar(150),
    verification_status varchar(30) NOT NULL DEFAULT 'pending',
    verified_at         timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT cop_verification_check CHECK (verification_status IN ('pending', 'verified', 'rejected'))
);
CREATE INDEX cop_verification_idx ON court_owner_profiles (verification_status);
CREATE TRIGGER cop_set_updated_at BEFORE UPDATE ON court_owner_profiles
    FOR EACH ROW EXECUTE FUNCTION courtly_set_updated_at();

-- 1.10 player_availability_slots ------------------------------------------
CREATE TABLE player_availability_slots (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    day_of_week smallint NOT NULL,
    start_time  time NOT NULL,
    end_time    time NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pas_day_check CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT pas_time_check CHECK (end_time > start_time)
);
CREATE INDEX pas_user_day_idx ON player_availability_slots (user_id, day_of_week);

COMMENT ON COLUMN player_availability_slots.day_of_week IS '1 = Thu hai ... 7 = Chu nhat';

-- 1.11 player_preferred_locations -----------------------------------------
CREATE TABLE player_preferred_locations (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    label      varchar(100),
    address    text,
    latitude   numeric(10,7),
    longitude  numeric(10,7),
    location   geography(Point, 4326),
    radius_km  numeric(6,2) NOT NULL DEFAULT 5,
    is_default boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ppl_radius_check CHECK (radius_km > 0)
);
CREATE INDEX ppl_location_gix ON player_preferred_locations USING gist (location);
CREATE INDEX ppl_user_idx ON player_preferred_locations (user_id);
CREATE TRIGGER ppl_sync_location BEFORE INSERT OR UPDATE ON player_preferred_locations
    FOR EACH ROW EXECUTE FUNCTION courtly_sync_location();

COMMENT ON COLUMN player_preferred_locations.location IS 'Tu dong sinh tu latitude/longitude boi trigger courtly_sync_location';
