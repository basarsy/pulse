-- 1. Software Versions
CREATE TABLE software_versions (
    id UUID PRIMARY KEY,
    version_label VARCHAR(50) NOT NULL UNIQUE,
    release_notes TEXT,
    checksum VARCHAR(64) NOT NULL,
    package_url VARCHAR(500) NOT NULL,
    mandatory BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Rollout Campaigns
CREATE TABLE rollout_campaigns (
    id UUID PRIMARY KEY,
    software_version_id UUID NOT NULL REFERENCES software_versions(id),
    target_model VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    current_stage_index INT NOT NULL DEFAULT 0,
    failure_threshold_percent DOUBLE PRECISION NOT NULL DEFAULT 10.0,
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. Rollout Stages
CREATE TABLE rollout_stages (
    id UUID PRIMARY KEY,
    campaign_id UUID NOT NULL REFERENCES rollout_campaigns(id) ON DELETE CASCADE,
    percentage INT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE
);

-- 4. Vehicle Update Status
CREATE TABLE vehicle_update_status (
    id UUID PRIMARY KEY,
    vehicle_id UUID NOT NULL,
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

-- 5. Audit Log
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
