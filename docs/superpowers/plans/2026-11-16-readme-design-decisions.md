# README Design Decisions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** README explains why Pulse is built as it is, and every claim in it is true.
**Spec:** `docs/superpowers/specs/2026-11-16-readme-design-decisions-design.md`

## Global Constraints

- Only `README.md` changes (plus this spec and plan). No code changes.
- Every number, version, path, endpoint, topic and test name in the README must be traceable to a
  file in the repo. Test links are relative links to the test file.

## Review Focus

- Badge versions vs `pom.xml`, `pulse-dashboard/package.json`, `docker-compose.yml`, `k8s/`.
- Limits and defaults vs `application.yml` and `@Value` defaults (offline 60s, queue expiry 15 min,
  dispatch timeout 60s, 10 commands/min, 5/25/100 stages, 10% threshold).
- Getting Started commands actually work as written (paths, profiles, env vars, ports).
- Delivery wording: "exactly once" only where a test shows it; the outbox is at-least-once.
- Mermaid renders on GitHub (no unsupported syntax).

### Task 1: Audit every existing claim

- [ ] Go section by section; for each claim find the file that proves it. Fix or remove what's
      wrong, and keep a list of corrections for the PR description.
- [ ] Commit `docs(readme): correct claims that no longer match the code`.

### Task 2: Design decisions section and diagram

- [ ] Add `## 🧭 Design decisions` after Architecture: the request-path diagram, then 5–7
      decisions in the problem → decision → alternative → proof format.
- [ ] Commit `docs(readme): explain the design decisions and link the tests that prove them`.

### Task 3: Collapse finished roadmap phases

- [ ] Wrap phases 0–9 in `<details><summary>`; keep "Next" visible. Add an entry for the
      records/contract-test work (PR #13) to the phase list, since that's missing.
- [ ] Check the rendered Markdown with `gh` preview or a Markdown render, then commit
      `docs(readme): fold finished roadmap phases`.

### Task 4: Review and PR

- [ ] A fresh reviewer re-checks every claim against the code; fix what it finds.
- [ ] Push, open the PR.
