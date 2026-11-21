-- When the vehicle last reported progress on an update (or was instructed, before its first report).
-- OtaUpdateTimeoutSweep fails updates that haven't moved for too long, so a silent vehicle can't
-- hold its rollout stage open forever.
ALTER TABLE vehicle_update_status ADD COLUMN last_progress_at TIMESTAMP WITH TIME ZONE;

-- Existing rows: the latest time known for them. Unfinished ones without a start time count from
-- now, so they get the full timeout instead of failing on the first sweep after the upgrade.
UPDATE vehicle_update_status SET last_progress_at = COALESCE(completed_at, started_at, now());

ALTER TABLE vehicle_update_status ALTER COLUMN last_progress_at SET NOT NULL;

-- The sweep only looks at unfinished updates; finished ones (most rows) stay out of the index.
CREATE INDEX idx_vehicle_update_status_unfinished_last_progress
    ON vehicle_update_status (last_progress_at)
    WHERE status IN ('PENDING', 'DOWNLOADING', 'DOWNLOADED', 'INSTALLING');
