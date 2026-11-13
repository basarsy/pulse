-- Stage in which each vehicle was targeted, so failure rates are evaluated per stage.
ALTER TABLE vehicle_update_status ADD COLUMN stage_index INT NOT NULL DEFAULT 0;

-- A vehicle is targeted at most once per campaign.
ALTER TABLE vehicle_update_status
    ADD CONSTRAINT uq_vehicle_update_status_vehicle_campaign UNIQUE (vehicle_id, campaign_id);

-- Optimistic locking for campaign state changes.
ALTER TABLE rollout_campaigns ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Rollout targeting looks vehicles up by model, case-insensitively (upper(model) = upper(?)).
CREATE INDEX IF NOT EXISTS idx_vehicles_model_upper ON vehicles (upper(model));
