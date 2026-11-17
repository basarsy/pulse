-- Orders live updates: every change to a vehicle's OTA status raises the version, and browsers
-- drop an update with a lower version than the one they show.
ALTER TABLE vehicle_update_status ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
