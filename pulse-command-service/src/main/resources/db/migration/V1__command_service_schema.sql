CREATE TABLE remote_commands (
    id UUID PRIMARY KEY,
    vehicle_id UUID NOT NULL,
    issued_by UUID NOT NULL,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP WITH TIME ZONE,
    result_message VARCHAR(500),
    idempotency_key VARCHAR(100) UNIQUE
);

CREATE INDEX idx_remote_commands_vehicle_id ON remote_commands(vehicle_id);
CREATE INDEX idx_remote_commands_status ON remote_commands(status);

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
