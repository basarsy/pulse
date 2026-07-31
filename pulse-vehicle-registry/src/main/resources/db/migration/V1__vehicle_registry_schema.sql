-- Pulse Vehicle Registry Database Schema (V1)

-- 1. Users & Roles
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE roles (
    id VARCHAR(50) PRIMARY KEY
);

INSERT INTO roles (id) VALUES
    ('OWNER'),
    ('FAMILY_MEMBER'),
    ('DEALER_STAFF'),
    ('FLEET_ADMIN'),
    ('SYSTEM_ADMIN');

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id VARCHAR(50) NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- 2. Vehicles & Access
CREATE TABLE vehicles (
    id UUID PRIMARY KEY,
    vin VARCHAR(17) NOT NULL UNIQUE,
    model VARCHAR(100) NOT NULL,
    model_year INT NOT NULL,
    owner_id UUID REFERENCES users(id) ON DELETE SET NULL,
    connectivity_state VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
    current_software_version VARCHAR(50) NOT NULL DEFAULT '1.0.0',
    last_seen_at TIMESTAMP WITH TIME ZONE,
    battery_or_fuel_level DOUBLE PRECISION DEFAULT 100.0,
    odometer_km DOUBLE PRECISION DEFAULT 0.0,
    last_lat DOUBLE PRECISION,
    last_lng DOUBLE PRECISION,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE vehicle_authorized_users (
    vehicle_id UUID NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (vehicle_id, user_id)
);

-- 3. Telemetry Snapshots
CREATE TABLE telemetry_snapshots (
    id UUID PRIMARY KEY,
    vehicle_id UUID NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    battery_or_fuel_level DOUBLE PRECISION,
    odometer_km DOUBLE PRECISION,
    engine_temp_c DOUBLE PRECISION,
    lat DOUBLE PRECISION,
    lng DOUBLE PRECISION
);

CREATE INDEX idx_telemetry_vehicle_time ON telemetry_snapshots(vehicle_id, recorded_at DESC);

-- 4. Audit Log
CREATE TABLE audit_log (
    id UUID PRIMARY KEY,
    actor_user_id UUID,
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id VARCHAR(100),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB
);

CREATE INDEX idx_audit_log_timestamp ON audit_log(timestamp DESC);
