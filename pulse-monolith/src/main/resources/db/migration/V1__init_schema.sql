-- Pulse Initial Database Schema (V1)

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

-- 3. Remote Commands
CREATE TABLE remote_commands (
    id UUID PRIMARY KEY,
    vehicle_id UUID NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    issued_by UUID NOT NULL REFERENCES users(id),
    type VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP WITH TIME ZONE,
    result_message VARCHAR(500),
    idempotency_key VARCHAR(100) UNIQUE
);

CREATE INDEX idx_remote_commands_vehicle_id ON remote_commands(vehicle_id);
CREATE INDEX idx_remote_commands_status ON remote_commands(status);

-- 4. OTA & Software Updates
CREATE TABLE software_versions (
    id UUID PRIMARY KEY,
    version_label VARCHAR(50) NOT NULL UNIQUE,
    release_notes TEXT,
    checksum VARCHAR(64) NOT NULL,
    package_url VARCHAR(500) NOT NULL,
    mandatory BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE rollout_campaigns (
    id UUID PRIMARY KEY,
    software_version_id UUID NOT NULL REFERENCES software_versions(id),
    target_model VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    current_stage_index INT NOT NULL DEFAULT 0,
    failure_threshold_percent DOUBLE PRECISION NOT NULL DEFAULT 10.0,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE rollout_stages (
    id UUID PRIMARY KEY,
    campaign_id UUID NOT NULL REFERENCES rollout_campaigns(id) ON DELETE CASCADE,
    percentage INT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE vehicle_update_status (
    id UUID PRIMARY KEY,
    vehicle_id UUID NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    campaign_id UUID NOT NULL REFERENCES rollout_campaigns(id) ON DELETE CASCADE,
    target_version_id UUID NOT NULL REFERENCES software_versions(id),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    progress_percent INT NOT NULL DEFAULT 0,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    error_message VARCHAR(500)
);

CREATE INDEX idx_vehicle_update_status_vehicle ON vehicle_update_status(vehicle_id);
CREATE INDEX idx_vehicle_update_status_campaign ON vehicle_update_status(campaign_id);

-- 5. Telemetry Snapshots
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

-- 6. Audit Log
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
