# Network policies + Redis password — plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans.

**Spec:** `docs/superpowers/specs/2026-11-16-k8s-network-policies-redis-auth-design.md`

## Global Constraints
- Ingress rules only; pods matched by the existing `app: pulse` + `component: <name>` labels.
- The password comes from `app-secrets` (`REDIS_PASSWORD`); never hard-coded in a manifest.
- With `REDIS_PASSWORD` unset, everything behaves exactly as today (compose, tests, local profile).

## Review Focus
- A connection Pulse needs that no rule allows (dashboard → app, Prometheus → app, Kafka → itself).
- Redis probes or tooling that would now fail without the password.
- The app with an empty password property: must mean "no password", not an empty AUTH.
- The CI test must fail if a policy is missing (an unlabelled pod reaching Redis fails the job).

### Task 1: Redis password in the app (TDD)
- Integration test in `LiveUpdatesAcrossInstancesTest` (it can start extra instances): a Redis
  container started with `--requirepass`; an instance with the right password issues a command, the
  rate-limit key exists in Redis and nothing is skipped; with a wrong password the command is still
  accepted and the skip is counted.
- `spring.data.redis.password: ${REDIS_PASSWORD:}`.

### Task 2: Manifests
- `k8s/network-policies/` (default deny + one allow per component), added to the kustomization.
- Redis: `--requirepass` from `app-secrets`; `REDIS_PASSWORD` in `secrets.env.example`.

### Task 3: Proof in CI
- `k8s/tests/network-policies.sh` and a CI job with kind: apply namespace, policies, secret, Redis;
  check app-labelled pod PING with password (PONG), without password (NOAUTH), unlabelled pod
  (connection fails).

### Task 4: README, review, PR
- Kubernetes section (policies, enforcement caveat, port-forward for Grafana/Prometheus), "Next"
  item removed, roadmap line. Fresh review, PR.
