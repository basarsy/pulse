# Kubernetes: network policies for every component, and a Redis password

## Why

The README lists it under "Next": Redis has no password, and nothing in `k8s/` restricts traffic
inside the cluster. Any pod in the cluster can reach Redis, Kafka (also without authentication) and
Postgres (which has a password). A reviewer reading `k8s/` sees that straight away.

## Goals

- A default rule that denies all incoming traffic in the `pulse` namespace, plus one allow rule
  per component for exactly the connections Pulse uses:

  | Destination | Allowed from | Port |
  |---|---|---|
  | `pulse-app` | the ingress-nginx namespace, `dashboard` (its nginx proxies `/api`, `/ws-pulse`), `prometheus` (scrape) | 8080 |
  | `dashboard` | the ingress-nginx namespace | 80 |
  | `postgres` | `pulse-app` | 5432 |
  | `redis` | `pulse-app` | 6379 |
  | `kafka` | `pulse-app`; `kafka` itself (it reaches its own broker and controller through the `kafka` Service) | 9092, 9093 |
  | `prometheus` | `grafana` | 9090 |
  | `grafana` | nothing in the cluster (open it with `kubectl port-forward`) | — |

- Redis requires a password (`REDIS_PASSWORD` in `app-secrets`). The app sends it through
  `spring.data.redis.password`, which stays empty, meaning no password, everywhere else.
- Proof in CI, in a real cluster: a kind cluster, whose default network plugin enforces
  NetworkPolicies, gets the namespace, the policies, Redis and the secret. Then:
  - a pod labelled `component: pulse-app` can `PING` Redis with the password;
  - an unlabelled pod can't connect at all;
  - without the password, Redis answers `NOAUTH`.
- An integration test: the app works against a password-protected Redis (the rate limit really runs,
  nothing skipped), and with a wrong password it fails open as designed in #15.

## Non-goals

- Egress rules. Denying outgoing traffic means explicitly allowing DNS and every external call, for
  little gain once incoming traffic is locked down.
- Kafka authentication (SASL) and Redis ACL users: much larger changes; the network rules confine
  both to the app.
- A password for the local Docker Compose stack, which only listens on the developer's machine.

## Approaches

- *Every component behind default-deny-ingress (picked by the user)* over *Redis only*, which
  would have left Kafka just as exposed.
- *Ingress-only policies (my call)* over *ingress + egress*: see non-goals.
- *`requirepass` (my call)* over Redis ACL users: there is one client.
- *Prove the policies in a real kind cluster in CI (my call)* over *render-only*: a policy that
  renders but never takes effect is the failure that matters.

## Constraints

- Policies only take effect where the cluster's network plugin enforces them. kind and k3s do by
  default; managed clouds once network policy support is switched on (GKE, EKS VPC CNI); Docker
  Desktop's Kubernetes does not. The README says so.
- The ingress controller is assumed to run in the `ingress-nginx` namespace (its standard install),
  matched by the `kubernetes.io/metadata.name` label every namespace carries.

## Left to the builder

File layout under `k8s/`, the exact test commands, and timeouts.
