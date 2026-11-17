# Versions on live updates — plan

Design: `docs/superpowers/specs/2026-11-17-live-update-versions-design.md`.
Branch: `feat/live-update-versions`. Integration tests need Docker Desktop running.

1. **Failing integration test** `LiveUpdateVersionsTest extends AbstractIntegrationTest`: a real
   STOMP client collects updates for a command, a vehicle's OTA status and a two-stage rollout.
   For each topic: versions increase strictly in lifecycle order, and the last equals the REST
   read. Run it; it fails (no `version` in payloads).
2. **`version` in the DTOs**: `CommandResponse`, `VehicleUpdateStatusResponse`, `CampaignResponse`
   gain `long version`, mapped from the entity. Flyway `V9__vehicle_update_status_version.sql`
   adds the column (`BIGINT NOT NULL DEFAULT 0`), `@Version` on `VehicleUpdateStatus`. Update
   `ResponseMappingContractTest` / `DtoJsonContractTest`. Re-run step 1: it still fails, now on
   versions that lag the row (trap 1) or repeat (trap 2).
3. **Trap 1, flush before mapping**: `CommandService` publish paths and `dispatchToKafka`, and
   the vehicle OTA status save in `RolloutCampaignService`, use `saveAndFlush`.
4. **Trap 2, rollout version**: `RolloutCampaignService` raises the rollout's version with
   an explicit `version + 1` update (after a flush, then a refresh) before each rollout publish and once per vehicle
   progress event. Step 1 passes. Run the whole backend suite.
5. **Dashboard**: `version` in `api/types.ts`; version-aware `upsertById` and a `newer(a, b)`
   for single items; `useAsync` takes an optional `merge`; list pages and the rollout page use it.
   Vitest tests first. `npm run lint`, `npm test`, `npm run build`.
6. **README**: "Next" item → done list; describe the ordering rule; fact-check every sentence.
7. **Fresh review** of the branch, fixes, push, PR, CI green.
