# Scheduled job locks

## Why

Pulse runs as a single replica because every `@Scheduled` job runs on every instance
(`k8s/app/pulse-app-deployment.yaml` says so). There are seven jobs:

| Job | Where | Interval (default) | Effect of a second instance |
|---|---|---|---|
| resubmit failed publications | `OutboxMaintenance` | 60s, fixed delay | **duplicate Kafka messages** |
| purge completed publications | `OutboxMaintenance` | 1h, fixed delay | duplicate work |
| stale-command sweep | `CommandService.checkStaleCommands` | 30s, fixed rate | duplicate work (already race-safe) |
| offline detection | `VehicleConnectivityMonitor` | 15s, fixed delay | duplicate work (already race-safe) |
| simulator heartbeats / telemetry | `VehicleSimulatorRunner` | 15s / 20s, fixed rate | the fleet sends 2× the events |

This is the first of two PRs that make `replicas: 2` work. The second shares live updates across
instances over Redis.

## Goals

- Across all instances, each job runs **at most once per interval**.
- An instance that crashes while holding a lock blocks the job for a bounded time only.
- A test against real Postgres shows that two concurrent runs of the same job execute it once.
- A test fails if someone adds a new `@Scheduled` method without a lock.

## Non-goals

- Live updates across instances, and changing `replicas` (PR B).
- Electing one instance to run every job.
- Changing what any job does.

## Approaches

- **ShedLock with its JDBC provider on Postgres:** **chosen.** It's the standard library for this,
  it needs no new infrastructure, and the deployment comment already plans for it.
- *Hand-written Postgres advisory locks:* rejected. It means maintaining custom lock code to
  replace a well-known library.
- *Electing one instance to run every job:* rejected. It's heavy for seven small jobs, and a stuck
  leader would stop all of them.

## Design

- **Dependencies:** `shedlock-spring` and `shedlock-provider-jdbc-template`, at a version that
  supports Spring Boot 3.3 and Java 21.
- **Migration `V8__shedlock.sql`:** the standard ShedLock table `shedlock(name varchar(64) primary
  key, lock_until timestamp, locked_at timestamp, locked_by varchar(255))`.
- **`config/SchedulingLockConfig`:**
  - `@EnableSchedulerLock` with a default `lockAtMostFor` of `PT5M`;
  - a `JdbcTemplateLockProvider` bean configured with `usingDbTime()`, so the instances' clocks
    don't matter.
- **One `@SchedulerLock` per job, each with a unique `name`** (for example `outbox-resubmit`).
  - **`lockAtLeastFor`** is set a little shorter than the job's default interval (interval minus
    ~2s). A second instance whose tick falls inside the same interval skips, and the owner's next
    tick still finds the lock released.
  - **`lockAtMostFor`** is the default unless a job needs longer.
- **Transactions:** the lock is acquired and released outside the job's own transaction. Two jobs
  are `@Transactional`: the stale-command sweep and offline detection. Their locks must still
  commit independently, which the concurrency test proves.
- **Tests:**
  - **Concurrency test** (Testcontainers Postgres): two threads call the same locked job at the
    same moment, with the job held mid-run. The job body runs exactly once, and the `shedlock` row
    exists.
  - **Coverage test:** scan `com.pulse.connected` for `@Scheduled` methods. Each one must also have
    `@SchedulerLock`, and all lock names must be unique.

## Calls I made (you can override them)

- The simulator jobs are locked too. Without locks, a second instance doubles the simulated fleet.
- The lock config lives in the existing `config` module, next to `MetricsConfig`.
- `lockAtLeastFor` follows the jobs' *default* intervals. If someone sets a much shorter interval
  through config, the job simply runs less often than that interval. Nothing breaks.

## Left to the builder

- The exact ShedLock version.
- The exact lock names.
- How the concurrency test holds the job mid-run, for example a latch inside a test double for the
  job's dependency.
