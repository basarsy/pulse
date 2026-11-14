# Command idempotency: database as the source of truth

## Why

A client that retries a command with the same idempotency key must get the original command back:
never a duplicate, never an error, never a used-up rate limit. Today the key is checked in Redis,
the command is inserted, then the key is written to Redis, with a unique constraint on
`remote_commands (issued_by, idempotency_key)` behind it. That breaks in six ways:

1. Two concurrent requests with the same key: the loser hits the constraint → **500**.
2. The Redis key is written before the DB commits; a failed commit leaves a dangling key → **404 for 24h**.
3. The rate limit is consumed before the idempotency check, so retries burn quota.
4. After 24h Redis forgets the key but the constraint does not → reusing it gives **500**.
5. The same key with a different command type silently returns the earlier command.
6. Keys longer than the 100-character column → **500**.

## Goals

- Idempotency is decided by the database only. Keys are valid **forever**, per user (decided).
- A replay (same user, same key, same vehicle, same type) returns the original command, with no
  new side effects (no audit entry, metric, dispatch or WebSocket push).
- The same key with a different vehicle **or a different type** → 409.
- Concurrent requests with the same key: exactly one command is created; every request gets it.
- Replays don't count against the rate limit.
- Keys longer than 100 characters → 400. A blank key means "no key", as today.

## Non-goals

- Dashboard changes: it already sends a fresh UUID per click and disables buttons while sending.
- The spec's "retry a FAILED command with the same key root and an attempt count".
- Changing the rate limit itself (Redis, 10/min, fixed window).

## Approaches

- **Insert, and recover if we lose** — **chosen.** Look the key up; if it isn't there, insert. If
  the constraint rejects the insert, re-read the winner and return it. Postgres aborts a
  transaction after a failed insert, so issuing is split into a create transaction and a re-read
  transaction. Standard, no extra locking, and the extra step only runs in the rare race.
- *Atomic `INSERT … ON CONFLICT DO NOTHING RETURNING id`* — rejected: bypasses the entity (creation
  timestamp and version by hand), Postgres-only.
- *Postgres advisory lock on (user, key)* — rejected: Postgres-specific, an extra round-trip on
  every keyed command.
- *Keep Redis with a reservation step* — rejected: brings back the two-store consistency problem.

## Design

- `RemoteCommandRepository.findByIssuedByIdAndIdempotencyKey(UUID userId, String key)`;
  the unused global `findByIdempotencyKey` is removed.
- `CommandService.issueCommand` is no longer one `@Transactional` method:
  1. If there's a key, look it up (read transaction). Found → match or 409, return.
  2. Rate limit (new commands only) → 429 as today.
  3. Create transaction: the existing create path (save, audit, metric, dispatch or queue,
     WebSocket push), using `saveAndFlush` so a constraint violation surfaces inside it.
  4. On `DataIntegrityViolationException` with a key: re-read by (user, key) in a new transaction,
     then match or 409 as in step 1. Without a key, rethrow.
- `IssueCommandRequest.idempotencyKey` gets `@Size(max = 100)`.
- Redis no longer stores idempotency keys. `CommandIdempotencyService` keeps only the rate limit
  and is renamed `CommandRateLimiter` *(my call: the old name would be wrong)*. Existing
  `command:idempotency:*` keys expire on their own within 24h; no migration.
- Replays keep the current 202 response.
- README: idempotency is database-backed and keys never expire; Redis does rate limiting.

## Accepted trade-off

The loser of a race has already used one rate-limit slot (step 2 runs before it can know it lost).
It only happens when two identical requests arrive at the same moment.

## Left to the builder

How the two transactions are run (e.g. `TransactionTemplate`), log wording, and test layout,
following existing patterns.
