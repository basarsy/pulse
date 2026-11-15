# Dashboard Lint Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `npm run lint` works on a fresh clone and passes with zero warnings, and CI runs it on every push.

**Architecture:** ESLint 9 with its flat config in `pulse-dashboard/eslint.config.js`. A probe run on the current code found
**0 errors and 5 warnings**, all `react-refresh/only-export-components`. They come from files that export a
component plus something else: `useAuth`, `useLive`/`useTopic`, `useNotifications` and `campaignProgress`.
The fix moves those exports out of the component files. The CI lint step is added last, so every commit stays green.

**Tech Stack:** React 18, TypeScript 5, Vite 5, Vitest 1, ESLint 9, typescript-eslint 8.

**Spec:** `docs/superpowers/specs/2026-11-15-dashboard-lint-design.md`

## Global Constraints

- Dev dependencies, at these ranges: `eslint@^9.39.0`, `@eslint/js@^9.39.0`, `typescript-eslint@^8.72.0`, `eslint-plugin-react-hooks@^5.2.0`, `eslint-plugin-react-refresh@^0.4.26`, `globals@^15.15.0`.
- Use react-hooks **v5**. v6 and later add React Compiler rules, which are outside the spec's "standard recommended" set.
- The lint script is exactly `eslint . --max-warnings 0`.
- Fix findings in the code. An inline `eslint-disable` is allowed only with a comment giving the reason, and none are expected.
- No formatting-only changes, and no Prettier.
- All commands run from `pulse-dashboard/`. CI uses Node 20.

## Review Focus

1. **The lockfile matches `package.json`.** CI runs `npm ci`, which fails if they're out of sync. Task 1 verifies with `npm ci` locally.
2. **Context defaults are unchanged.** `LiveContext`'s default `{ connected: false, subscribe: () => () => {} }` must move over exactly as it is, because components rendered outside the provider rely on it. Verified by `npm test` plus a `tsc` build.
3. **No new import cycles.** Today `NotificationContext.tsx` imports `Toast`, and `Toast` imports the `Notification` type back. Moving the type into `context/notifications.ts` removes that cycle. `LiveContext.tsx` must import `useAuth` from `./auth`. Verified by `npm run build`.
4. **`campaignProgress` behaves the same** at every edge: 0 targeted with status COMPLETED gives 100, 0 targeted otherwise gives 0, and the result is rounded. Pinned by tests in Task 2.
5. **`dist/` and `node_modules` aren't linted.** `dist` is in `ignores`, and ESLint skips `node_modules` by default. Verified by the lint output naming only `src/` and `vite.config.ts`.

---

### Task 1: Install ESLint and its config

**Files:**
- Create: `pulse-dashboard/eslint.config.js`
- Modify: `pulse-dashboard/package.json` (the `lint` script and dev dependencies), `pulse-dashboard/package-lock.json`

- [ ] **Step 1: Install**

Run: `npm i -D eslint@^9.39.0 @eslint/js@^9.39.0 typescript-eslint@^8.72.0 eslint-plugin-react-hooks@^5.2.0 eslint-plugin-react-refresh@^0.4.26 globals@^15.15.0`

- [ ] **Step 2: Write `eslint.config.js`** with `tseslint.config(...)` containing:
  - `{ ignores: ['dist'] }`;
  - for `files: ['**/*.{ts,tsx}']`:
    - `extends: [js.configs.recommended, ...tseslint.configs.recommended]`;
    - `languageOptions: { ecmaVersion: 2020, globals: globals.browser }`;
    - plugins `react-hooks` and `react-refresh`;
    - rules `...reactHooks.configs.recommended.rules` and `'react-refresh/only-export-components': ['warn', { allowConstantExport: true }]`.

  `package.json` already has `"type": "module"`, so a `.js` ESM config loads as is.

- [ ] **Step 3: Set the script** `"lint": "eslint . --max-warnings 0"`.

- [ ] **Step 4: Run lint. Expected: exactly the 5 known warnings**

Run: `npm run lint`
Expected: exit 1, `0 errors, 5 warnings`, all `react-refresh/only-export-components`:
- `src/context/AuthContext.tsx` (`useAuth`)
- `src/context/LiveContext.tsx` (`useLive` and `useTopic`)
- `src/context/NotificationContext.tsx` (`useNotifications`)
- `src/pages/RolloutListPage.tsx` (`campaignProgress`)

Anything else is new information. Stop and report it.

- [ ] **Step 5: Verify the lockfile and the existing checks**

Run: `npm ci && npm test && npm run build`
Expected: all succeed.

- [ ] **Step 6: Commit**

```bash
git add pulse-dashboard/eslint.config.js pulse-dashboard/package.json pulse-dashboard/package-lock.json
git commit -m "build(dashboard): install ESLint 9 with typescript-eslint and React hooks rules"
```

### Task 2: Move `campaignProgress` into `utils/format.ts`, with tests

**Files:**
- Create: `pulse-dashboard/src/utils/format.test.ts`
- Modify: `pulse-dashboard/src/utils/format.ts`, `pulse-dashboard/src/pages/RolloutListPage.tsx:11-14`, `pulse-dashboard/src/pages/RolloutDetailPage.tsx:10`

**Interfaces:**
- Produces: `export function campaignProgress(c: Campaign): number` in `utils/format.ts`. The body moves unchanged.

- [ ] **Step 1: Write the failing tests** in `format.test.ts`, building a `Campaign` with a small factory (`{ ...base, ...overrides }` cast to `Campaign`):

```ts
describe('campaignProgress', () => {
  it('is the share of targeted vehicles that finished, installed or failed', () => {
    expect(campaignProgress(campaign({ totalVehiclesTargeted: 8, vehiclesInstalled: 2, vehiclesFailed: 1 }))).toBe(38);
  });
  it('is 100 for a completed campaign that targeted no vehicles', () => {
    expect(campaignProgress(campaign({ totalVehiclesTargeted: 0, status: 'COMPLETED' }))).toBe(100);
  });
  it('is 0 for a running campaign that has targeted no vehicles yet', () => {
    expect(campaignProgress(campaign({ totalVehiclesTargeted: 0, status: 'IN_PROGRESS' }))).toBe(0);
  });
});
```

Use status values that actually exist in `CampaignStatus` in `src/api/types.ts`.

- [ ] **Step 2: Run them. Expected: FAIL** (`campaignProgress` isn't exported from `./format`).

Run: `npx vitest run src/utils/format.test.ts`

- [ ] **Step 3: Move the function** into `format.ts`, delete it from `RolloutListPage.tsx`, and import it from `../utils/format` in both pages.

- [ ] **Step 4: Verify**

Run: `npx vitest run src/utils/format.test.ts && npm run lint`
Expected: the tests pass, and lint shows 4 warnings with no `RolloutListPage` entry.

- [ ] **Step 5: Commit**

```bash
git add pulse-dashboard/src/utils pulse-dashboard/src/pages/RolloutListPage.tsx pulse-dashboard/src/pages/RolloutDetailPage.tsx
git commit -m "refactor(dashboard): move campaignProgress into utils/format with tests"
```

### Task 3: Split context hooks from providers, and lint in CI

**Files:**
- Create: `pulse-dashboard/src/context/auth.ts`, `pulse-dashboard/src/context/live.ts`, `pulse-dashboard/src/context/notifications.ts`
- Modify: the three `src/context/*Context.tsx` files, every importer listed below, and `.github/workflows/ci.yml`

**Interfaces:** each `.ts` file holds the context object, its value type and its hooks. Each `*Context.tsx` keeps only its Provider component.
- `context/auth.ts`: `AuthContextType`, `AuthContext`, `useAuth()`
- `context/live.ts`: `Listener`, `LiveContextType`, `LiveContext` (keep the default value exactly as it is), `useLive()`, `useTopic<T>(destination: string | null, onMessage: (body: T) => void)`. `useTopic` moves unchanged.
- `context/notifications.ts`: `Notification`, `NotificationContextType`, `NotificationContext`, `useNotifications()`

Use these filenames rather than `authContext.ts` and so on: on Windows the file system ignores case, so `./AuthContext` could resolve to either file.

- [ ] **Step 1: Create the three `.ts` files and slim the providers.** `LiveContext.tsx` imports `useAuth` from `./auth`.

- [ ] **Step 2: Repoint the importers** to the new modules for hooks and types. Keep the provider imports in `App.tsx`. The importers are:
  - `App.tsx` (`useAuth`)
  - `components/Header.tsx`
  - `components/LiveNotifications.tsx`
  - `components/NotificationBell.tsx`
  - `components/Sidebar.tsx`
  - `components/Toast.tsx` (`import type { Notification }`)
  - `pages/DashboardPage.tsx`, `LoginPage.tsx`, `RegisterPage.tsx`, `RolloutDetailPage.tsx`, `RolloutListPage.tsx`, `VehicleDetailPage.tsx` and `VehicleListPage.tsx`

  Confirm with `grep -rn "useAuth\|useLive\|useTopic\|useNotifications\|Notification }" src`.

- [ ] **Step 3: Verify that lint is now clean**

Run: `npm run lint && npm test && npm run build`
Expected: lint exits 0 with no output, and the tests and the build pass.

- [ ] **Step 4: Add a CI step** in `.github/workflows/ci.yml`, in the dashboard job, between "Install dependencies" and "Unit tests":

```yaml
      - name: Lint
        run: npm run lint
        working-directory: pulse-dashboard
```

  Rename the job to `Dashboard (lint, test, build)`.

- [ ] **Step 5: Commit**

```bash
git add pulse-dashboard/src .github/workflows/ci.yml
git commit -m "refactor(dashboard): keep context files component-only; lint in CI"
```
