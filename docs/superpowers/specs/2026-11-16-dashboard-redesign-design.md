# Dashboard redesign: "instrument cluster"

## Why

The README now opens with a screenshot of the dashboard, and the user says it "looks a lot like
it's made with AI". The causes are concrete:
- the default navy background with blue→purple gradients;
- blurred "glass" panels with heavy shadows and a neon glow;
- emoji used as icons (about 20 places);
- four identical stat cards with no hierarchy;
- Inter at default weights, and all-caps badges.

The user wants it modern and visibly designed by a person, and left the look to me. They picked
direction A, "instrument cluster", over a light editorial look and a neutral Linear-style dark.

## Goals

- One deliberate visual language across every page: login, register, dashboard, vehicle list,
  vehicle detail, rollout list, rollout detail, plus the layout, header, notifications and toasts.
- It reads as an operations tool for vehicles:
  - calm graphite surfaces;
  - one amber accent, like a car's warning lights;
  - monospace for VINs, numbers and times;
  - real icons;
  - hierarchy carried by type, not boxes.
- Accessibility built in from the start:
  - text contrast of at least 4.5:1;
  - a visible focus ring on everything interactive;
  - icon-only buttons have labels;
  - status is never shown by colour alone;
  - `prefers-reduced-motion` is respected.
- The README screenshot and GIF are re-captured with `npm run media` and still show what their
  alt text says.

## Non-goals

- No change to behaviour, routes, the API, live-update logic or data fetching. A page shows the
  same data and offers the same actions as today, laid out and styled differently.
- No light theme, and no mobile layout beyond "nothing breaks" down to 1024px wide.
- No new pages and no charting library.

## Anti-goals

- Swapping one template look for another: gradients, glass, glow, emoji, decorative blobs.
- Hiding information or actions that are visible today.
- Colours or text that fail contrast.

## Approaches

**Visual direction** (the user picked A):
- A "instrument cluster": warm graphite, an amber accent, monospace data, thin rules.
- B light editorial: off-white, ink and a serif.
- C neutral Linear-style dark.

**How styles are organised** (my call):
- *Design tokens in `src/styles/tokens.css`, shared base styles in `src/styles/base.css`, and a
  CSS Module next to each component and page (picked).* Vite supports CSS Modules natively, so
  there is no new dependency, styles stay next to the component, and inline `style={{…}}` goes
  away almost entirely: about 130 occurrences today.
- *Tailwind:* rejected. It adds a build dependency and a second styling vocabulary for a
  2,000-line app, and every component would be rewritten into class strings.
- *CSS-in-JS:* rejected. It adds a runtime and gives nothing over CSS Modules here.

## Design

**Tokens** (`tokens.css`, the only place raw colours appear):

| Token | Value | Use |
|---|---|---|
| `--bg` | `#121110` | page background |
| `--surface` | `#1a1917` | panels, sidebar |
| `--surface-raised` | `#23211e` | hover, inputs, table header |
| `--rule` | `#2f2c28` | 1px borders and dividers |
| `--text` | `#ece8e1` | primary text |
| `--text-muted` | `#a39d93` | secondary text (about 7:1 on `--bg`) |
| `--accent` | `#f0a83a` | primary actions, focus ring, the active nav item, progress |
| `--on-accent` | `#1a1206` | text on amber |
| `--ok` | `#6cc58a` | online, installed, completed |
| `--warn` | `#e5c547` | paused, sleeping, timed out (a yellower hue than the accent, so a warning never looks like progress or focus) |
| `--neutral` | `#8f897f` | pending, draft, cancelled |
| `--bad` | `#ef6b5e` | failed, offline, danger buttons |
| `--info` | `#7fb0e0` | in progress, sent |

Plus a spacing scale (4/8/12/16/24/32/48), radii (4 and 8px; no large rounding), and two motion
durations (120ms and 200ms) that collapse to 0 under reduced motion.

**Type:**
- IBM Plex Sans (400/500/600) for the interface. It's humanist and engineered, which fits the
  domain and isn't Inter.
- IBM Plex Mono (400/500) for VINs, version labels, numbers, percentages and timestamps, always
  with tabular figures.
- Loaded from Google Fonts like the current Inter.
- Base 15px, line-height 1.5. Page titles 22px/600; section labels 12px, uppercase, letter-spaced
  and muted, used sparingly as "cluster" labels.

**Icons:** `lucide-react`, 16–18px, 1.5px stroke. Each replaces one emoji. Decorative icons get
`aria-hidden`; icon-only buttons get an `aria-label`.

**Status:** a small dot plus a sentence-case label ("Online", "Installing"). Shared by all pages
through one `Status` component, replacing the all-caps `Badge` pills. The colour comes from the
tokens above, and the label is always there, so colour is never the only signal.

**Components** (in `src/components/`, each with a CSS Module):
- `Button`: primary (amber), secondary (outline), danger, ghost; sizes sm and md; loading state.
- `Status`
- `Panel`: a surface with an optional header row; flat, 1px rule, no blur or shadow.
- `Stat`: label above a mono value
- `ProgressBar`: a solid accent fill, no gradient, with the percentage as text.
- `StageTrack`: rollout stages as a horizontal timeline. Each node shows the stage %, a status
  icon and a connecting line; the current stage is highlighted.
- `DataTable` styles: a shared table class with sticky header, mono numeric columns and row hover.
- `EmptyState`: one line of copy in the product's voice and an optional action.

**Pages** (same data and actions as today):
- **Layout:** a slim sidebar (icon + label, active item marked by an amber bar), and a header
  with live status (dot + "Live" / "Reconnecting"), notifications and the user menu.
- **Dashboard:**
  - A summary strip with one line of key figures (vehicles, online, average battery, active
    rollouts) as `Stat`s divided by rules.
  - "Active rollouts": each rollout with its `StageTrack` and `ProgressBar`. This is new
    *presentation* of data the page already fetches.
  - "Live activity" as a log: time (mono), the existing message as the event, and the notification's existing type as a status dot with a visually hidden text label. The notification model and how notifications are built don't change.
  - Quick actions.
- **Vehicles:**
  - A table instead of cards: model and year, VIN (mono), status, battery (a small bar plus %),
    software (mono), last seen, owner.
  - Rows link to the detail page, and the "Register vehicle" action stays.
- **Vehicle detail:**
  - A header with model, VIN, status and readouts (battery, odometer, software, last seen, location).
  - The existing tabs (overview, commands, telemetry, updates), restyled as underline tabs with logs and tables.
  - Remote commands stay in the Commands tab, as buttons with an icon *and* their text label (an icon alone can't say "Check status" unambiguously, and today's labels must stay visible).
- **Rollouts:** a table of campaigns and the existing forms (new version, new rollout), restyled.
- **Rollout detail:**
  - A header with version, target, status and actions.
  - `StageTrack` and `ProgressBar`, plus figures for installed, failed, targeted and failure rate.
  - A per-vehicle table.
- **Login / register:** a centred form on the graphite background, with the wordmark and one line
  about what Pulse is.
- **Toasts and notification bell:** flat surfaces, a status dot, and a close button labelled for
  screen readers.

**Copy voice:** short, plain, specific. For example:
- "No vehicles yet. Register one to see it here."
- "Waiting for the first heartbeat"
- "3 of 12 installed"

Sentence case throughout. Enum values go through the existing `label()` helper.

**README media:** `capture-readme-media.mjs` finds elements by role and accessible name, not by
CSS class. Selectors that depend on the old UI (`.glass-panel`, "Live updates connected",
`×` buttons) move to stable roles and names, or `data-testid` where no role fits. The screenshot
and GIF are re-captured on a fresh stack. The README alt text is updated if what's shown changes.

## Verification

- Dashboard lint (zero warnings), the Vitest suite and the production build pass.
- A screenshot of every page in the demo stack, both as the admin and as the owner (vehicle
  detail, an empty state, a paused rollout if one occurs), reviewed by eye against this
  document. Checks: no emoji, no gradient or blur left (`grep` for `gradient`, `backdrop-filter`,
  `glass`, and emoji code points in `src/`), and no inline `style={{` except for dynamic values
  such as a bar's width.
- Contrast of every text/background token pair is calculated and recorded in the PR.
- Keyboard pass: tab through each page, and the focus is always visible.

## Calls made after the design check

- Remote commands keep their text labels and their place in the Commands tab.
- `--warn` moved from `#e8b04a` to `#e5c547`, and `--neutral` was added, so accent, warning and neutral states can be told apart.
- Live activity is derived from the existing notifications; the notification layer is untouched.

## Left to the builder

Exact spacing per page, icon choices, wording of individual empty states, and the stage-track
details, all within the tokens and rules above.
