-- Rollout evaluation runs one grouped query per progress event:
--   SELECT stage_index, status, COUNT(*) ... WHERE campaign_id = ? GROUP BY stage_index, status
-- This index answers it without reading the rows.
CREATE INDEX idx_vehicle_update_status_campaign_stage_status
    ON vehicle_update_status (campaign_id, stage_index, status);
