# README: design decisions, and nothing but the truth

## Why

Pulse is a portfolio project. The README says *what* Pulse does but not *why* it is built that
way. The engineering behind PRs #2–#13 (outbox, database idempotency, rollout locking, ShedLock,
Redis fan-out, the ordering fix, the DTO contract tests) is only visible as feature bullets or
roadmap ticks. The user also asked that the README be up to date and contain nothing untrue.

## Goals

- A **Design decisions** section, right after Architecture. Each decision has four parts:
  problem → decision → alternative considered → the test that proves it (linked).
- One diagram of a command's full path across pods (REST → DB + outbox → Kafka → vehicle →
  result → Redis → every pod → browser).
- Every claim in the README checked against the code, config and manifests: versions, limits,
  defaults, endpoints, topics, commands, file paths. Wrong claims are fixed; claims that can't be
  verified are removed.
- The finished roadmap phases 0–9 are folded into a `<details>` block; "Next" stays visible.

## Non-goals

- No code changes. If the audit finds a bug, it is reported, not fixed here.
- No screenshot or demo data (that's the next item on the list).

## Anti-goals

- Marketing language, or claims stronger than the tests show ("production-ready", "exactly once"
  where it isn't).

## Approaches

1. **Section in the README, with the roadmap collapsed (picked).** Reviewers read the README,
   and collapsing the roadmap makes room without deleting anything.
2. A separate `docs/architecture.md` linked from the README. Rejected: few reviewers click
   through.
3. One decision record per file (`docs/decisions/`). Rejected: too much ceremony for six
   decisions.

## Left to the builder

Wording, the exact decisions chosen (5–7), and the order of the sections after Design decisions.
