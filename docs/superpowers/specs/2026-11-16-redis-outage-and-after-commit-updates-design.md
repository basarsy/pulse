# Commands survive a Redis outage; live updates wait for the commit

## Why

The README audit (PR #14) found two real gaps, now listed under "Next":

1. `CommandRateLimiter` calls Redis on the request thread with no fallback. With Redis down,
   every new command waits out the 2s Redis timeout and then fails with a `500`.
2. `LiveUpdates.publish` sends to browsers immediately, inside the caller's transaction. If that
   transaction rolls back (or its commit fails), browsers have already seen a state that never
   existed.

## Goals

- With Redis unreachable, issuing a command still works. The rate limit is skipped (fail open),
  each skip is counted (`pulse.commands.ratelimit.skipped`) and logged at most once a minute.
- A circuit breaker on the rate-limit call, so that during an outage commands stop paying the
  Redis timeout after a few failures.
- Live updates published inside a transaction are sent only after it commits, in the order they
  were published, and never if it rolls back. Outside a transaction they are sent immediately.

## Non-goals

- Sequence numbers that let the dashboard drop stale updates (listed under "Next").
- Rate limiting in a fallback store (in-memory per pod). Skipping is enough for an outage.

## Approaches

**Rate limit when Redis is down**
- *Fail open with a Resilience4j circuit breaker (picked).* Availability of remote commands
  matters more than enforcing 10/min during an outage. Resilience4j is already used for the
  Kafka producers.
- *Fail closed (`503`)*: rejected. Commands would be unavailable for the whole outage.
- *try/catch without a breaker*: rejected. Lettuce waits out the 2s command timeout on every call
  while Redis is down, so each command would take 2s.

**Live updates after commit**
- *Defer inside `LiveUpdates.publish` with a transaction synchronization (picked).* One change
  covers every caller (commands and OTA). One synchronization per transaction keeps a queue, so
  updates leave in publish order.
- *`@TransactionalEventListener(AFTER_COMMIT)` per update type*: rejected. Every call site
  changes, and calls outside a transaction need `fallbackExecution`.

## Trade-off accepted

Today the campaign row lock also orders the pushes. After this change, two progress events for
one campaign that commit a few milliseconds apart on different threads can reach a browser in
either order. Command results are keyed by vehicle, so one command's results are handled on one
thread one after another and keep their order. The README states this precisely.

## Verification

- Unit: `LiveUpdates` defers inside a synchronization, sends in order on commit, drops on rollback,
  sends immediately with no transaction.
- Unit: the limiter allows and counts when Redis throws.
- Integration: an app instance whose Redis port is closed accepts a command with `202`, and the
  breaker opens so later commands don't wait for the Redis timeout.
- Integration: an update published in a transaction that rolls back never reaches a STOMP client.
- README: "Next" loses the two items and gains the sequence-number follow-up; decision 5 and the
  diagram describe the new behaviour.
