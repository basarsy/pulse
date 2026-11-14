# OTA rollouts: safe with parallel progress events, cheap per event

## Why

Rollouts must stay correct when progress events are handled in parallel, and must keep working for
large fleets. Today:

- Progress events (`ota.vehicle-update-status`, 3 partitions, keyed by vehicle) are read by one
  instance with one consumer thread, so they're handled one at a time. With more threads, two
  vehicles finishing together can each miss the other's update: the stage never advances
  (**stall**), or both advance it (**double start**).
- Per progress event, `RolloutCampaignService` loads every update row of the campaign (twice) and
  runs 4 COUNT queries per broadcast. Starting a stage loads the model's whole fleet into memory.
  For 10,000 vehicles × ~5 events each, that's on the order of 500 million row reads per rollout.

## Goals

1. Progress events for one campaign are evaluated one after another, even with several consumer
   threads; events for different campaigns still run in parallel. No stall, no double advance.
2. Operator actions (start, pause, resume, abort) serialise with progress events the same way.
3. Per progress event, a constant number of queries, none of which loads all update rows.
4. Starting a stage loads only the vehicles it instructs, not the whole fleet.
5. The OTA progress listener runs with 3 consumer threads (one per partition), configurable.

## Non-goals

- Running more than one instance (decided: one instance). The scheduled jobs and the k8s
  deployment stay as they are.
- Changing rollout rules (cumulative VIN-ordered stages, per-stage failure threshold, auto-pause).
- Running counters on stage rows.
- Changing listener concurrency for other topics.

## Approaches

**Concurrency**
- *Lock the campaign row while evaluating* (`SELECT … FOR UPDATE`): **chosen.** Same-campaign
  events wait their turn and each one sees the previous one's committed update. The transactions
  are short, so the wait is cheap.
- *Optimistic collide-and-retry* (bump the version on every event): rejected. Constant retries
  under load, and events risk the dead-letter topic once the 10s retry window runs out.
- *Key events by campaign*: rejected. Vehicles shouldn't know their campaign, one big rollout
  would be pinned to one thread, and operator actions could still collide.

**Per-event cost**
- *One grouped summary query* (counts per stage and status) feeding both the stage check and the
  response, plus a single targeted query when a stage starts: **chosen.**
- *Running counters on the stage row*: rejected. Needs a migration, backfill and careful counting
  rules, the counts can drift, and it only pays off at a much larger scale.

## Design

- **Locking.** `RolloutCampaignRepository.findByIdForUpdate(UUID)` with
  `@Lock(PESSIMISTIC_WRITE)`. `handleVehicleUpdateProgress` resolves the update row's campaign id,
  locks the campaign, and only then loads and changes the update row and evaluates the stage.
  `startCampaign`, `pauseCampaign`, `resumeCampaign` and `abortCampaign` load the campaign through
  the same locked lookup. The existing `@Version` stays as a second line of defence.
- **Summary query.** One JPQL query on `VehicleUpdateStatus` grouped by `stageIndex` and `status`
  for a campaign, returning rows of (stageIndex, status, count). A small `CampaignProgress` value
  derived from those rows answers:
  - for the current stage: total, failed (`FAILED` + `ROLLED_BACK`), finished (`INSTALLED` +
    failed);
  - for the whole campaign: total, installed, failed.

  `evaluateCampaignStage`, `advanceIfStageFinished` and `mapToCampaignResponse` use it in place of
  `findAllByCampaignId` and the 4 COUNT queries. The behaviour and response fields are unchanged.
- **Stage start.** Two queries in the OTA module, both on vehicles of the campaign's model
  (case-insensitive):
  - a count (for the stage's share, `ceil(fleet × pct / 100)`);
  - the vehicles to instruct: within the first `targetCount` by VIN, not already targeted in this
    campaign, and not on the target version.

  The same cumulative rule as today, applied in SQL.
- **Index.** Flyway V7:
  `CREATE INDEX idx_vehicle_update_status_campaign_stage_status ON vehicle_update_status (campaign_id, stage_index, status)`.
- **Listener concurrency.** `@KafkaListener(concurrency = "${pulse.ota.progress-consumer-concurrency:3}")`
  on `OtaProgressKafkaConsumer` only *(my call: the setting this work makes safe, scoped to OTA)*.

## How it's proven

- A concurrency integration test with real Postgres: one stage, many vehicles, all final progress
  events submitted from parallel threads. The stage advances exactly once and the campaign
  finishes. Before the lock this stalls or double-advances.
- Unit tests for `CampaignProgress`.
- The existing `RolloutCampaignServiceTest` cases keep passing; they pin the rollout rules.
- A query-count check that one progress event runs a constant number of statements, not one that
  grows with the fleet.

## Left to the builder

The exact JPQL for the stage-start query (subquery or VIN cutoff), the query-count mechanism
(e.g. Hibernate statistics), log wording, and test layout, following existing patterns.
