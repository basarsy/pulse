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

# A throwaway pod in the namespace, with the given labels, that runs redis-cli once.
redis_cli() {
  local name=$1 labels=$2; shift 2
  kubectl -n "$NS" run "$name" --rm -i --quiet --restart=Never --image="$IMAGE" --labels="$labels" \
    --command -- sh -c "timeout 5 redis-cli -h redis --no-auth-warning $* 2>&1 || echo CONNECTION_FAILED"
}

kubectl apply -f k8s/namespace.yaml
kubectl -n "$NS" create secret generic app-secrets --from-literal=REDIS_PASSWORD="$PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n "$NS" apply -f k8s/network-policies/
kubectl -n "$NS" apply -f k8s/infrastructure/redis-service.yaml -f k8s/infrastructure/redis-deployment.yaml
kubectl -n "$NS" rollout status deployment/redis --timeout=120s

echo "--- the app, with the password"
out=$(redis_cli as-app "app=pulse,component=pulse-app" -a "$PASSWORD" ping)
echo "$out"
[[ $out == *PONG* ]] || fail "the app could not reach Redis with the password"

echo "--- the app, without the password"
out=$(redis_cli as-app-no-password "app=pulse,component=pulse-app" ping)
echo "$out"
[[ $out == *NOAUTH* ]] || fail "Redis answered without the password"

echo "--- any other pod, even with the password"
out=$(redis_cli intruder "app=pulse,component=intruder" -a "$PASSWORD" ping)
echo "$out"
[[ $out != *PONG* ]] || fail "a pod that isn't the app reached Redis: the network policy did not apply"

echo "OK: only the app reaches Redis, and only with the password"
