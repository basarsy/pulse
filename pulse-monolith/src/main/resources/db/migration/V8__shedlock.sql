-- ShedLock's lock table: one row per scheduled job. An instance runs a job only after taking its
-- row (lock_until in the past), so with several replicas each job runs once per interval.
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
