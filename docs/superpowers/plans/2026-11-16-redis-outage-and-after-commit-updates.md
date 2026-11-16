# Redis outage + after-commit live updates — plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans.

**Spec:** `docs/superpowers/specs/2026-11-16-redis-outage-and-after-commit-updates-design.md`

## Global Constraints
- No new dependencies (Resilience4j is already on the classpath).
- No JSON changes (the contract tests must pass untouched).

## Review Focus
- A breaker that is open must skip Redis (`CallNotPermittedException` → fallback), not throw.
- Only Redis failures fail open; any other exception from the limiter still propagates.
- `publish` called from inside an `afterCommit` callback, or from a read-only transaction, still sends.
- Several publishes in one transaction leave in publish order.
- Rollback after `publish` sends nothing, locally or to Redis.

### Task 1: Live updates after commit (TDD)
- Tests in `LiveUpdatesTest`: `insideTransaction_SendsOnlyAfterCommit_InOrder`,
  `insideTransaction_RollbackSendsNothing`, `outsideTransaction_SendsImmediately`.
- Implement in `LiveUpdates.publish` via `TransactionSynchronizationManager`: one synchronization
  per transaction (bound as a resource) holding the queue; `afterCommit` sends; `afterCompletion`
  unbinds.
- Integration: `LiveUpdatesAfterCommitTest` (real STOMP client): publish inside a
  `TransactionTemplate` that throws → no frame within 1s; one that commits → frame arrives.
- Commit `fix(websocket): send live updates only after the transaction commits`.

### Task 2: Rate limit fails open (TDD)
- Unit `CommandRateLimiterTest`: Redis throws `RedisConnectionFailureException` → `true`, counter 1;
  `IllegalStateException` still propagates.
- Implement: `@CircuitBreaker(name = "redisRateLimit", fallbackMethod = "skipWhenRedisUnavailable")`
  with fallbacks for `DataAccessException` and `CallNotPermittedException`; breaker config in
  `application.yml` (window 5, open 30s).
- Integration `CommandWithoutRedisTest`: app with a closed Redis port issues a command → 202;
  10 more commands complete in well under 10 × 2s.
- Commit `fix(command): keep accepting commands while Redis is down`.

### Task 3: README and review
- Update decision 5, the diagram step, the capabilities rate-limit line, and "Next".
- Full `./mvnw verify`, fresh reviewer, PR.
