-- When a command was dispatched to the vehicle; the execution timeout counts from here.
ALTER TABLE remote_commands ADD COLUMN sent_at TIMESTAMP WITH TIME ZONE;

-- Commands already in flight: best available approximation, so they still time out.
UPDATE remote_commands SET sent_at = requested_at WHERE status IN ('SENT', 'ACKNOWLEDGED');
