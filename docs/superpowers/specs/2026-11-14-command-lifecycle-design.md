# Command lifecycle: offline detection, timeouts, final states

## Why

The README promises that commands for offline vehicles are queued and flushed on reconnect.
Today that does not work:

- Nothing ever sets a vehicle to `OFFLINE`, so after its first heartbeat a vehicle is ONLINE
  forever and commands are sent to vehicles that are not listening.
- The stale-command sweep times out `PENDING` and `SENT` commands 60s after `requestedAt`, so a
  queued command dies long before its vehicle could reconnect. `ACKNOWLEDGED` commands never
  time out at all.
- `processCommandResult` overwrites any status, so a late result revives a `TIMED_OUT` or
  cancelled command, and the result's `vehicleId` is ignored.
- Cancelling records `FAILED`, which inflates `pulse.commands.failed`.

## Goals

1. Vehicles become `OFFLINE` after missing heartbeats.
2. A queued (`PENDING`) command expires 15 min after it was requested if the vehicle never comes
   online. A dispatched (`SENT`/`ACKNOWLEDGED`) command times out 60s after it was sent.
3. Final states (`COMPLETED`, `FAILED`, `TIMED_OUT`, `CANCELLED`) are final: later results are
   ignored and logged. Results from a vehicle other than the command's are ignored.
4. A new `CANCELLED` status for user cancellation, shown in the dashboard.

## Non-goals

- Telling the vehicle that a `SENT` command was cancelled (no cancel message on the wire yet).
- Multi-replica safety of the scheduled sweeps (deployment runs one replica; separate step).
- Idempotency/Redis fixes (separate step).
- A live WebSocket push of connectivity changes (needs a new topic and authoriser rule).

## Approaches

**Offline detection**
- *Scheduled sweep that writes `OFFLINE`* — **chosen**. Existing reconnect detection
  (`HeartbeatKafkaConsumer` compares previous state) and dispatch-or-queue logic keep working
  unchanged.
- *Derive connectivity from `lastSeenAt` on read* — rejected: stored state becomes meaningless,
  reconnect detection and dispatch would need rework.

**Command timeouts**
- *New `sent_at` column, two cutoffs in the sweep* — **chosen**. Thresholds live in config and
  apply to commands already in flight; `sent_at` is useful history.
- *Per-command `expires_at` deadline* — rejected: the deadline is frozen at write time, so a
  config change does not affect in-flight commands.

## Design

- **Config** (`pulse.connectivity.offline-after`, default `PT60S`;
  `pulse.commands.queue-expiry`, default `PT15M`; `pulse.commands.execution-timeout`,
  default `PT60S`).
- **Vehicle module**: a scheduled job (every 15s) bulk-selects ONLINE vehicles with
  `last_seen_at` older than `offline-after` (or null), sets them `OFFLINE` and logs it.
  Heartbeat handling is unchanged. *(Builder's call: no live push. There is no connectivity
  WebSocket topic today; the dashboard shows the new state on its next load.)*
- **Migration V5**: `ALTER TABLE remote_commands ADD COLUMN sent_at TIMESTAMPTZ`.
- **Command dispatch** sets `sentAt` when moving to `SENT`.
- **Sweep** (`checkStaleCommands`): `PENDING` with `requestedAt < now - queue-expiry` →
  `TIMED_OUT`, message "Vehicle did not come online within 15 minutes";
  `SENT`/`ACKNOWLEDGED` with `sentAt < now - execution-timeout` → `TIMED_OUT`, message
  "Command execution timed out after 60 seconds".
- **Transitions**: a `CommandStatusTransitions` helper mirroring `ota/UpdateStatusTransitions`:
  `PENDING → SENT`, `SENT → ACKNOWLEDGED`, any non-final → final. Results that are not allowed
  are logged and dropped (not an error, so they are not dead-lettered).
- **Cancel** sets `CANCELLED` (message "Cancelled by user"); allowed from `PENDING`/`SENT`
  as today. Metric `pulse.commands.cancelled`.
- **Dashboard**: add `CANCELLED` to `CommandStatus`, its badge colour (gray) and the final
  states in `LiveNotifications`.

## Left to the builder

Exact log wording, query shape (JPQL vs. derived), test layout — following existing patterns.
