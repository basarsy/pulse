#!/usr/bin/env bash
# Proves, in a real cluster, that the network policies and the Redis password take effect.
# Needs a cluster whose network plugin enforces NetworkPolicies (kind ≥ 0.24 does) and kubectl
# pointing at it. Deploys only what the checks need: the namespace, the policies, the secret and
# Redis. Run from the repository root: k8s/tests/network-policies.sh
set -euo pipefail

NS=pulse
PASSWORD=test-redis-password
IMAGE=redis:7-alpine

fail() { echo "FAIL: $*" >&2; exit 1; }

# A throwaway pod with the given labels that runs redis-cli once against the redis Service in the
# pulse namespace. A blocked connection is dropped silently, so redis-cli hangs until `timeout`.
redis_cli() {
  local ns=$1 name=$2 labels=$3; shift 3
  kubectl -n "$ns" run "$name" --rm -i --quiet --restart=Never --image="$IMAGE" --labels="$labels" \
    --command -- sh -c "timeout 5 redis-cli -h redis.$NS.svc.cluster.local --no-auth-warning $* 2>&1 || echo CONNECTION_FAILED"
}

kubectl apply -f k8s/namespace.yaml
kubectl -n "$NS" create secret generic app-secrets --from-literal=REDIS_PASSWORD="$PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n "$NS" apply -f k8s/network-policies/
kubectl -n "$NS" apply -f k8s/infrastructure/redis-service.yaml -f k8s/infrastructure/redis-deployment.yaml
kubectl -n "$NS" rollout status deployment/redis --timeout=120s

echo "--- the app, with the password"
# Redis is ready (its probe answered PING), but a new endpoint and a new pod's policy rules can take
# a few seconds to reach the node's network rules. So this check, which must succeed, is retried.
# The checks that must fail are never retried: a retry can't hide a hole in the policies.
for attempt in 1 2 3 4 5; do
  out=$(redis_cli "$NS" "as-app-$attempt" "app=pulse,component=pulse-app" -a "$PASSWORD" ping)
  echo "$out"
  [[ $out == *PONG* ]] && break
  echo "(attempt $attempt: not yet, retrying)"
  sleep 3
done
[[ $out == *PONG* ]] || fail "the app could not reach Redis with the password"

echo "--- the app, without the password"
out=$(redis_cli "$NS" as-app-no-password "app=pulse,component=pulse-app" ping)
echo "$out"
[[ $out == *NOAUTH* ]] || fail "Redis answered without the password"

blocked() { [[ $1 == *CONNECTION_FAILED* && $1 != *PONG* && $1 != *resolve* ]]; }

echo "--- another pod in the namespace, even with the password"
out=$(redis_cli "$NS" intruder "app=pulse,component=intruder" -a "$PASSWORD" ping)
echo "$out"
blocked "$out" || fail "a pod that isn't the app reached Redis, or the check failed for another reason"

echo "--- a pod in another namespace with the app's labels"
kubectl create namespace pulse-outsider --dry-run=client -o yaml | kubectl apply -f -
out=$(redis_cli pulse-outsider lookalike "app=pulse,component=pulse-app" -a "$PASSWORD" ping)
echo "$out"
blocked "$out" || fail "a pod outside the namespace reached Redis by copying the app's labels"

echo "OK: only the app's pods in the pulse namespace reach Redis, and only with the password"
