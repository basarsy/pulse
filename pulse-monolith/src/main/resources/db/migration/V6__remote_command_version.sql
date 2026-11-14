-- Optimistic locking: a result, a cancel and the timeout sweep can change the same command at the
-- same time; the loser must fail (and re-read) instead of overwriting a final state.
ALTER TABLE remote_commands ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
