# Vehicle writes don't overwrite each other; nothing waits forever for a vehicle

## Why

The full code review of 2026-11-21 found three correctness gaps:

1. **Lost updates on `vehicles`.** Heartbeats (`HeartbeatKafkaConsumer`), telemetry
   (`TelemetryService`) and OTA installs (`RolloutCampaignService`) each loaded the vehicle and
   saved it back. Hibernate writes every column on save, and `Vehicle` has no `@Version`, so a
   writer that loaded the row before another committed put that one's columns back. For example a
   heartbeat reverted a just-installed `currentSoftwareVersion`: the dashboard showed the old version
   and the next rollout stage instructed the vehicle again.
2. **OTA updates never time out.** A stage advances only when all of its updates are finished. A
   vehicle that went offline mid-download held the rollout `IN_PROGRESS` forever, with no signal.
3. **Queued commands can be stranded.** The reconnect flush was a synchronous `@EventListener`
   inside heartbeat handling. If it threw, the retried heartbeat saw the vehicle already `ONLINE`,
   no reconnect event fired again, and the commands sat until they expired after 15 minutes. A
   command whose transaction committed just after the flush read the queue was missed in the same way.

## Goals

- Each writer of a vehicle's row writes only its own columns.
- An OTA update with no progress for a configurable time (default 30 minutes) becomes `TIMED_OUT`.
  That finishes it for its stage, so the stage can complete, but it is not a failure: only installs
  that didn't succeed count towards the threshold (decided with the project owner on 2026-11-21).
  Timed-out vehicles are left out of the failure rate entirely, so they don't dilute real failures.
- If the vehicle reports its outcome after the timeout, the outcome replaces `TIMED_OUT`.
- A failed reconnect flush is retried, and queued commands of an online vehicle are sent even when
  they missed the flush. No command is sent twice.

## Non-goals

- Cancelling the instruction on the vehicle when its update times out. After a timeout only an
  outcome (`INSTALLED`, `FAILED`, `ROLLED_BACK`) is applied; intermediate progress is ignored, so
  an update never reopens.
- Re-evaluating a stage that already advanced when a late outcome arrives. Its counts change; its
  decision stands.

## Approaches

**Vehicle writes**
- *Targeted `@Modifying` UPDATEs, one per writer (picked).* Each sets only its own columns, with no
  read before. The heartbeat's `UPDATE … WHERE connectivity_state <> 'ONLINE'` also tells it,
  atomically, whether this heartbeat is the reconnect.
- *`@Version` on `Vehicle`*: rejected. A heartbeat every 15s per vehicle would make telemetry and
  installs fail and retry constantly.
- *`@DynamicUpdate`*: rejected. It still writes from a loaded copy, and is only correct while no
  writer sets a column it doesn't own.

**OTA timeout**
- *Per-update `last_progress_at` and a locked sweep (picked).* It re-checks under the campaign's row
  lock (as progress events do), so progress that arrives between the sweep's query and the lock
  keeps the update running. It reuses the progress path, so metrics, live updates and stage
  evaluation behave as for a reported outcome.
- *A `TIMED_OUT` update status (picked)* rather than storing the timeout as `FAILED`: the rollout
  must tell the two apart, since a parked car isn't evidence of bad software. The dashboard shows
  it as its own count, and `CampaignResponse` gains `vehiclesTimedOut`.
- *Timeout counted as `FAILED`*: rejected (first version of this change). In a real fleet a stage
  holds cars parked for hours, so rollouts would pause on unreachable cars.
- *Timed-out vehicles kept in the failure rate's denominator*: rejected. 1 failed and 9 timed out of
  10 would read as 10% and hide that the only vehicle that reported failed.
- *Per-stage timeout*: rejected. One slow vehicle would hold a whole stage, and a fast stage would
  still wait the full time.

**Queued commands**
- *Outbox the reconnect event, plus a sweep (picked).* With `@ApplicationModuleListener`, Spring
  Modulith records `VehicleReconnectedEvent` with the heartbeat's transaction and resubmits it if
  the flush fails. The sweep finds online vehicles with `PENDING` commands, which covers the
  commit-after-flush race. Optimistic locking on `RemoteCommand` makes the loser roll back if both
  handle the same command, outbox row included.
- *Sweep only*: rejected. Every reconnect would wait for the next sweep (up to 30s).

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `pulse.ota.update-timeout` | `PT30M` | No progress for this long times an update out |
| `pulse.ota.timeout-check-interval-ms` | `60000` | How often the OTA timeout job runs |
| `pulse.commands.pending-sweep-interval-ms` | `30000` | How often queued commands of online vehicles are sent |

## Migration

`V10__vehicle_update_status_last_progress.sql` adds `last_progress_at` (backfilled from
`completed_at`, then `started_at`, then the migration time, so unfinished rows get the full
timeout), and a partial index on unfinished updates for the sweep.

## Tests

- `VehicleConcurrentWritesTest`: an uncommitted install holds the vehicle's row lock while a
  heartbeat, then a telemetry reading, reads the row and waits on the lock. After the install
  commits, its version is still there. Against the previous telemetry code the test fails
  (`expected "2.0.0" but was "1.0.0"`).
- `OtaUpdateTimeoutTest`: a silent update times out and one that still reports doesn't; timeouts
  never pause a rollout, and are left out of the failure rate (1 failed, 2 timed out of 4 pauses at
  40%); a late install replaces the timeout; progress after the sweep's query keeps an update
  running.
- `QueuedCommandFlushTest`: a reconnect sends a queued command and its outbox publication
  completes; the sweep sends a queued command of a vehicle that was already online.
- The scheduled-job lock tests cover the two new jobs (`command-pending-flush`, `ota-update-timeout`).
