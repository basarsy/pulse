# Demo data and README media — plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans.

**Spec:** `docs/superpowers/specs/2026-11-16-demo-data-and-readme-media-design.md`

## Global Constraints
- Demo accounts: `admin@pulse.demo` / `demo-admin` (FLEET_ADMIN), `owner@pulse.demo` / `demo-owner` (OWNER).
- Switch: `pulse.demo.enabled: ${DEMO_ENABLED:false}`; only `docker-compose.yml` turns it on.
- Made-up model names only: `Pulse Aurora` (12), `Pulse Vela` (10), `Pulse Terra` (8). VINs are 17 chars: `PLS` + model letter + `DEMO` + 9 digits.
- Versions: `v1.0.0` (fleet baseline), `v1.1.0` (completed rollout to Pulse Vela, stages 50/100), `v1.2.0` (ready).
- The seeder writes through entities and repositories only; the completed campaign's rows must make `mapToCampaignResponse` report total 10, installed 10, failed 0.
- Seeded data must not leak into other tests' database.

## Review Focus
- Seeder runs twice (restart): second run is a no-op.
- Switch absent/false: no seeder bean.
- Stage index of each seeded update row matches how the service splits a model's fleet by VIN.
- Modulith: `demo` depends on auth, vehicle, ota; nothing depends on `demo`.
- Media script fails with a clear message when the stack or ffmpeg is missing.

### Task 1: Seeder (TDD)
- `DemoDataSeederTest` (root package, default integration context): runs `DemoDataSeeder.seed()` twice inside a transaction that is rolled back at the end; asserts users/roles, 30 vehicles by model, owner owns 3 Vela vehicles, 3 versions, one COMPLETED campaign whose response is 10/10/0, Vela vehicles on `v1.1.0` and the rest on `v1.0.0`, second call adds nothing. Also asserts the default context has no `DemoDataSeeder` bean.
- Implement `com.pulse.connected.demo.DemoDataSeeder` (`ApplicationRunner`, `@ConditionalOnProperty`), `package-info.java`, the yml switch and compose env.
- Commit `feat(demo): seed a demo fleet when DEMO_ENABLED is set`.

### Task 2: Media script
- `pulse-dashboard/scripts/capture-readme-media.mjs` + `npm run media` (devDependency `playwright`). Writes `docs/media/dashboard.png` and `docs/media/rollout.gif`.
- Run it against a fresh `docker compose up --build`; commit the script and the media.

### Task 3: README, review, PR
- Screenshot under the title; "Try it" in Getting Started; GIF by the OTA capability; layout lists `docs/media/`.
- Parse Mermaid blocks if touched; full `./mvnw verify`; fresh review; PR.
