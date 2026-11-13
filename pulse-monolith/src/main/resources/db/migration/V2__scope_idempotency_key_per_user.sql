-- Idempotency keys are chosen by clients, so they must be unique per issuing user,
-- not globally (otherwise one user's key can collide with another user's).
ALTER TABLE remote_commands DROP CONSTRAINT remote_commands_idempotency_key_key;
ALTER TABLE remote_commands
    ADD CONSTRAINT uq_remote_commands_issuer_idempotency_key UNIQUE (issued_by, idempotency_key);
