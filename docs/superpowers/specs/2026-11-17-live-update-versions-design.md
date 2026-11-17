# Versions on live updates

## Why

Live updates are sent after the database commit (`LiveUpdates`, PR #15). Two commits milliseconds
apart, on different threads or pods, can reach a browser in either order: each instance sends its
own, and updates from other instances arrive later through Redis. The dashboard applies whatever
arrives last (`upsertById`, `setData`), so a stale update can overwrite a newer one: a command
shown as `SENT` after `ACKNOWLEDGED` (sent from the request thread and from the Kafka listener
thread), or a rollout shown at a stage it already left (its updates come from different vehicles'
events, on different threads). One vehicle's own OTA updates keep their order: its events share a
Kafka key, so one thread handles them in turn. The README lists this under "Next".

The same race exists between a live update and a REST load. The page subscribes and fetches at the
same time, and a fetch that read the database before a commit can resolve after that commit's live
update.

## Goals

- Every response for a command, a vehicle's OTA status and a rollout (live update and REST alike)
  carries a `version` that is higher for a later committed state of that item.
- The dashboard never replaces an item with a lower version, whether the item arrives over the
  WebSocket or from a REST load.
- Tests that fail today prove both halves.

## Non-goals

- Gap detection or replay of missed updates. A dropped update is not recovered here; the next one,
  or a reload, brings the item up to date, as today.
- Ordering across different items (two commands of one vehicle). Each item is ordered on its own.
- Pushing rollout counts on every vehicle event. Today a rollout's update is sent on status and
  stage changes only; that stays.

## Where the version comes from

| Option | Verdict |
|---|---|
| **The row's own JPA `@Version`** | **Chosen.** Apart from the demo seeder at startup (it inserts rows and adjusts creation dates), every writer to a row either holds its row lock (rollouts are loaded `FOR UPDATE`, and vehicle OTA statuses are only changed under their rollout's lock) or is checked by optimistic locking (commands, since V6). So versions grow in commit order, on any pod, with no new infrastructure. |
| A Postgres sequence or Redis `INCR` taken at publish time | Rejected. The number is taken before commit, so two transactions can take numbers in the opposite order to their commits. |
| Timestamps | Rejected. Clocks differ between pods, and two updates can share a millisecond. |

`remote_commands` and `rollout_campaigns` already have a `version` column. `vehicle_update_status`
gets one in Flyway V9, with `@Version` on `VehicleUpdateStatus`.

## The two traps

**1. The version moves at flush, after the response is built.** Hibernate increments `@Version`
when it writes the row, at flush or commit. Services build the response right after `save()`,
which does not flush, so the response would carry the version from *before* this change. Example:
`createCommand` inserts (version 0), then `dispatchToKafka` sets `SENT`; the commit writes version
1, but the response says 0.

Fix: every path that publishes flushes first (`saveAndFlush`), so the response carries the
version the commit writes. A test that compares each published version with the row's version
after commit catches a path that forgets.

**2. A rollout's response changes without its row changing.** Stage status lives in
`rollout_stages`, and the counts (`vehiclesInstalled`, `vehiclesFailed`) come from
`vehicle_update_status`. A stage finishing, or a vehicle finishing its update, changes what
`CampaignResponse` shows while `rollout_campaigns.version` stays put.

Fix: a rollout's version is the version of the whole rollout as shown. It is raised explicitly,
on the row the transaction already holds `FOR UPDATE`: before each rollout update is published,
and once per applied vehicle progress event (which changes the counts). Each raise flushes pending
changes, runs `UPDATE … SET version = version + 1`, and refreshes the entity so it holds the new
version. That costs an `UPDATE` and a re-read per raise, on a row already locked.

Not `lock(PESSIMISTIC_FORCE_INCREMENT)`, the first version of this change: Hibernate 6.5 runs it
once per transaction and skips later calls (the entity already holds that lock mode). Two rollout
updates in one transaction then only got different versions when the rollout row itself changed in
between, which every current path happens to do, but nothing enforced. `RolloutVersionTest`
publishes twice in one transaction without a change in between and failed with it.

## Dashboard

- **Live updates:** `upsertById` becomes version-aware: an incoming item with a **lower** version
  than the one shown is ignored. Equal versions are accepted; they are the same committed state
  (for example an action's REST response and its own live update). The rollout detail page uses
  the same rule for its single rollout.
- **REST loads:** `useAsync` takes an optional `merge(shown, loaded)` (and `pollMs` moves into the
  same options object). The vehicle page's commands and updates, the rollout list, and the rollout
  page pass a version-aware merge: the loaded list decides which items are shown and their order,
  and for an item already shown the higher version wins. Items shown but missing from the load are
  not kept: `useAsync` keeps the previous page's data while it reloads, so keeping them would carry
  one vehicle's commands over to the next vehicle's page. `newer` also only keeps the shown state
  when it is the same item, for the same reason on the rollout page.
- `version` is added to `Command`, `Campaign` and `VehicleUpdate` in `api/types.ts`.

## Testing

Backend, against real Postgres (`AbstractIntegrationTest`), receiving updates over a real STOMP
connection as in `LiveUpdatesWebSocketTest`:

- **Commands:** a LOCK command runs through its lifecycle. Each status arrives with a higher version
  than the one before it in lifecycle order, and the last version equals the one a REST read returns.
  Fails today: the payloads have no version, and after the field is added, trap 1 makes the
  published version lag the row's.
- **Vehicle OTA status:** the same for one vehicle's update, `PENDING` to `INSTALLED`.
- **Rollouts:** a rollout with two stages runs to `COMPLETED`; every published rollout update has a
  distinct, increasing version in lifecycle order, and the last equals the REST read.

- **Two rollout updates in one transaction** (`RolloutVersionTest`): published without a change to
  the rollout in between, they still get two versions, and the second is the one committed.

`ResponseMappingContractTest` and `DtoJsonContractTest` pin the JSON shape, so they gain `version`.

Dashboard (Vitest, `utils/versions.test.ts`): version-aware upsert ignores lower versions, accepts
equal and higher, and inserts new items; the REST merge keeps the newer of each item and shows
exactly the loaded items.

## README

Remove the "Next" item, add the change to the done list, and describe the ordering rule where the
README explains live updates. Every sentence is checked against the code.
