# Dashboard lint

## Why

`pulse-dashboard/package.json` has a `lint` script, but ESLint was never installed, so
`npm run lint` fails on a fresh clone. CI runs only the dashboard's tests and build, so frontend
mistakes such as stale values in React hooks, unused code or `any` are never caught. The backend is
already checked on every push. Pulse is a portfolio project, so the frontend should be held to the
same bar.

## Goals

- `npm run lint` works on a fresh `npm ci` and passes with **zero warnings**.
- CI runs lint on every push, before the dashboard's unit tests.
- Problems the linter finds in existing code get fixed. A real bug, such as a missing hook
  dependency that shows stale data, gets its own commit with a test that fails first.

## Non-goals

- Prettier, or any formatting-only changes.
- Rule sets beyond the standard recommended ones.

## Approaches

- **ESLint 9 with its flat config, typescript-eslint, react-hooks and react-refresh:** **chosen.**
  This matches Vite's current React template, so reviewers recognise it, and it's actively supported.
- *ESLint 8 with `.eslintrc`:* rejected. It's end-of-life, which looks dated in a portfolio.
- *Biome:* rejected. It's further from what React reviewers expect, and its hooks rules are less
  complete.

## Design

- Dev dependencies: `eslint`, `@eslint/js`, `typescript-eslint`, `eslint-plugin-react-hooks` and
  `eslint-plugin-react-refresh`, at versions that work with the existing TypeScript 5.2+.
- `eslint.config.js`:
  - ignores `dist`;
  - applies `js.configs.recommended`, the typescript-eslint recommended config, the react-hooks
    recommended rules and the react-refresh `only-export-components` warning to `**/*.{ts,tsx}`;
  - sets browser globals.
- The script becomes `eslint . --max-warnings 0`. The `--ext` flag was removed in ESLint 9.
- In `.github/workflows/ci.yml`, a **Lint** step comes before **Unit tests**, and the job is renamed
  "Dashboard (lint, test, build)".

## Left to the builder

- Exact package versions.
- How to fix each lint finding: change the code rather than disable the rule. An inline disable is
  allowed only with a comment giving the reason.
