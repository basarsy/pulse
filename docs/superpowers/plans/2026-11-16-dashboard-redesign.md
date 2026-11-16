# Dashboard redesign — plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans.

**Spec:** `docs/superpowers/specs/2026-11-16-dashboard-redesign-design.md`

## Global Constraints
- Same routes, data, API calls, live subscriptions and actions on every page; only markup and styles change.
- Raw colours only in `src/styles/tokens.css`. Fonts: IBM Plex Sans / IBM Plex Mono (Google Fonts).
- Icons: `lucide-react` (exact version pinned). No emoji anywhere in `src/`.
- No `gradient`, `backdrop-filter`, `glass` or glow shadows; inline `style={{}}` only for dynamic values (widths).
- Every interactive element has a visible `:focus-visible` ring; icon-only buttons have `aria-label`.
- Status = dot + text via one `Status` component; tones `ok | bad | warn | info | neutral`.

## Review Focus
- An action that was visible before is missing after (cancel command, register vehicle, rollout start/pause/resume/abort, new version, mark all read, logout).
- Contrast of muted text on raised surfaces, and of text on amber.
- Long values (emails, VINs, release notes) overflowing table cells at 1024px.
- Owner view (no admin pages, fewer columns) and empty states.
- The README media script still finds what it needs after the markup changes.

### Task 1: Foundation
- `src/styles/tokens.css`, `src/styles/base.css` (reset, typography, forms, tables, focus, reduced motion); `index.css` removed; fonts in `index.html`.
- Components with CSS Modules: `Button`, `Status`, `Panel`, `Stat`, `ProgressBar`, `StageTrack`, `EmptyState`, `PageHeader`.
- `utils/format.ts`: colour maps become tone maps; tests updated.
- Commit `feat(dashboard): design tokens, type and core components`.

### Task 2: Shell
- `Layout`, `Sidebar`, `Header`, `NotificationBell`, `Toast`, toast stack in `NotificationContext`.
- Commit `feat(dashboard): app shell in the new design`.

### Task 3: Pages
- Login, Register, Dashboard, Vehicles, Vehicle detail, Rollouts, Rollout detail. Remove `Badge`, `StatCard`.
- Commit `feat(dashboard): every page in the new design`.

### Task 4: Verify, media, README
- Lint, test, build; greps for emoji/gradient/blur/inline styles; contrast table; screenshots of every page as admin and owner on a demo stack; keyboard pass.
- Update `capture-readme-media.mjs` selectors; re-capture media; update README alt text if needed.
- Fresh review, PR.
