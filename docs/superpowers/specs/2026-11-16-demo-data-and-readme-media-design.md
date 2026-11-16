# Demo data and README media

## Why

Pulse is a portfolio project. Today a visitor who runs `docker compose up` sees an empty
dashboard: they must register, create vehicles one by one, promote themselves to admin with
`psql`, sign in again, upload a software version and create a rollout before the OTA pages show
anything. Most reviewers never run it at all, and the README has no picture of the product.

## Goals

- `docker compose up` opens onto a populated fleet: sign in with published demo credentials and
  everything is there.
- One screenshot of the dashboard at the top of the README, and a short GIF of a rollout
  progressing live (the part a static image can't show).
- Media that can be re-captured with one command after a UI change.

## Non-goals

- A demo login button or any demo-only UI.
- Demo data in Kubernetes or in tests other than the seeder's own.

## Anti-goals

- Demo accounts with known passwords anywhere except an explicitly enabled local stack.
- Seed data that bypasses domain rules in ways the app itself never could (e.g. a campaign whose
  counts don't match its update rows).

## Decided with the user

- Published demo credentials in the README are fine (local stack only).

## Approaches

**How the data gets in**
- *A Java seeder in a new `demo` module, behind `pulse.demo.enabled` (picked).* Runs once at
  startup through the existing entities and repositories, idempotent, invisible unless enabled.
  Spring Modulith checks its dependencies like any other module.
- *Flyway SQL in a demo-only location:* rejected. Raw SQL duplicates entity defaults and needs
  BCrypt hashes pasted in, and migrations are the wrong tool for optional data.
- *A shell script against the REST API:* rejected. Admin roles can't be granted through the API,
  so it still needs `psql`, and it breaks whenever an endpoint changes.

**How the media gets made**
- *A Playwright script in `pulse-dashboard` (picked)* that signs in to a running stack, saves the
  screenshot, starts a rollout and records it, then turns the recording into a GIF with `ffmpeg`.
  Re-runnable with one command; Playwright is also the tool the README's "Next" names for
  dashboard end-to-end tests.
- *Manual captures:* rejected. They go stale silently after any UI change.

## Design

**Switch.** `pulse.demo.enabled: ${DEMO_ENABLED:false}` in `application.yml`. `docker-compose.yml`
sets `DEMO_ENABLED: "true"` for the app (the simulator is already enabled there). The k8s
ConfigMap does not set it.

**Seeder** (`com.pulse.connected.demo.DemoDataSeeder`, `@ConditionalOnProperty`): on application
start, if `admin@pulse.demo` doesn't exist, in one transaction create:
- `admin@pulse.demo` / `demo-admin` with `FLEET_ADMIN`, and `owner@pulse.demo` / `demo-owner` with
  `OWNER`.
- About 30 vehicles across 3 made-up models (not real brand names), with valid 17-character VINs.
  The owner owns 3 of them; the admin owns the rest.
- Software versions: the fleet runs `v1.0.0`; `v1.1.0` was rolled out to one model by a
  **completed** campaign (stages completed, one `INSTALLED` update row per targeted vehicle, the
  vehicles' current version set to match); `v1.2.0` is uploaded and ready for the visitor to roll
  out.
- It logs one line with the demo logins.

**README.** A screenshot under the title, a "Try it" block in Getting Started with the two logins
and what to click (start a rollout of `v1.2.0`), and the GIF next to the OTA capability.

**Media script** (`pulse-dashboard/scripts/capture-readme-media.mjs`, `npm run media`): needs the
compose stack running and `ffmpeg` on the PATH. Signs in as the admin, saves
`docs/media/dashboard.png`, creates and starts a rollout of `v1.2.0` through the API, records the
rollout page until the campaign finishes or pauses, and writes `docs/media/rollout.gif`
(cropped, ≤ 15 s, ≤ 3 MB).

## Verification

- `DemoDataSeederTest` (Postgres via Testcontainers): with the switch on, the expected users,
  roles, vehicles, versions and completed campaign exist, and the campaign's numbers from
  `mapToCampaignResponse` match its rows; a second run changes nothing.
- With the switch off (the default), there is no seeder bean.
- `ModularityTests` passes with the new module.
- Manual: a fresh `docker compose up`, sign in as each demo user, start the rollout and watch it.

## Left to the builder

Exact model names, VINs, vehicle counts per model, the suggested rollout stages and threshold
for the demo (the simulator fails 10% of installs, so a tiny first stage pauses easily), and GIF
timing.
