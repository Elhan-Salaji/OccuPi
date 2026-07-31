#!/usr/bin/env bash
#
# OccuPi — automatic deploy (build from source on new commits, health-gated).
#
# Invoked periodically by occupi-autodeploy.timer. Each run:
#   1. fetches origin and compares commits — no new commit on the branch means
#      the run is a cheap no-op,
#   2. fast-forwards the repo (compose files, sources and this script itself),
#   3. builds backend and frontend from source, ONE AT A TIME (the single-core
#      host must never run two builds at once; a swap file is required — an
#      unswapped build killed this VM once, see #140 and the root README),
#   4. recreates the two services and health-checks them; on failure it ROLLS
#      BACK to the last good commit (reset + rebuild) and records the bad
#      commit so it is not retried until the branch moves on,
#   5. prunes old images to reclaim disk.
#
# Infra (postgres / keycloak / influxdb / grafana) is intentionally NOT touched
# here — pinned versions, updated manually. See the root README.
#
# Runs as root via systemd. Logs go to journald:  journalctl -u occupi-autodeploy
#
# The stack lives in docker/server (single compose file + .env).

set -euo pipefail

# The whole body is wrapped in a brace group so bash reads the entire script into
# memory before executing it. The `git pull` below may rewrite this very file
# mid-run; reading it up front prevents that from corrupting execution.
{

# ── Configuration (env-overridable for non-standard hosts) ───────────────────
REPO_DIR="${REPO_DIR:-/home/Elhan/Occupi}"
BRANCH="${BRANCH:-develop}"
SERVICES=(backend frontend)          # infra is updated manually on purpose
STATE_DIR="/var/lib/occupi-autodeploy"
LOCKFILE="/run/occupi-autodeploy.lock"
HEALTH_RETRIES=40                    # 40 x 3s = up to 120s for a service to come up
HEALTH_DELAY=3
PRUNE_OLDER_THAN="336h"              # drop unused images older than 14 days

COMPOSE_FILE="$REPO_DIR/docker/server/compose.yml"

log() { echo "[$(date -Is)] $*"; }

# Compose helper — project dir is docker/server so its .env is picked up.
dc() {
  docker compose --project-directory "$REPO_DIR/docker/server" \
                 -f "$COMPOSE_FILE" "$@"
}

# Liveness URL per service, checked from the host (docker/server publishes
# these on 127.0.0.1). backend: /v3/api-docs is permitAll in the prod
# SecurityConfig -> 200 when up. frontend: nginx serves the SPA index -> 200.
health_url() {
  case "$1" in
    backend)  echo "http://127.0.0.1:8080/v3/api-docs" ;;
    frontend) echo "http://127.0.0.1:3000/" ;;
    *)        echo "" ;;
  esac
}

# Poll the URL until it returns HTTP 200, or fail after the retry budget.
wait_healthy() {
  local url="$1" code i
  [ -n "$url" ] || return 0
  for ((i = 1; i <= HEALTH_RETRIES; i++)); do
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$url" 2>/dev/null || echo 000)
    [ "$code" = "200" ] && return 0
    sleep "$HEALTH_DELAY"
  done
  return 1
}

# Serial build + recreate + health-gate for every service. Returns non-zero on
# the first failure, leaving the loop early.
build_and_apply() {
  local svc
  for svc in "${SERVICES[@]}"; do
    log "$svc: building"
    dc build "$svc" || { log "ERROR: $svc build failed"; return 1; }
  done
  for svc in "${SERVICES[@]}"; do
    log "$svc: recreating"
    dc up -d "$svc" || { log "ERROR: $svc up failed"; return 1; }
    if wait_healthy "$(health_url "$svc")"; then
      log "$svc: healthy"
    else
      log "ERROR: $svc did not come up healthy"
      return 1
    fi
  done
  return 0
}

# ── Single-instance lock (skip if a previous run is still going) ─────────────
exec 9>"$LOCKFILE"
if ! flock -n 9; then
  log "another auto-deploy run is in progress — exiting"
  exit 0
fi

mkdir -p "$STATE_DIR"
LAST_GOOD_FILE="$STATE_DIR/last-good-commit"
BAD_FILE="$STATE_DIR/bad-commit"

# ── 1. What does origin have? ────────────────────────────────────────────────
log "fetching origin/$BRANCH in $REPO_DIR"
if ! git -C "$REPO_DIR" fetch --quiet --prune origin "$BRANCH"; then
  log "WARN: git fetch failed — nothing to compare, exiting"
  exit 0
fi

remote=$(git -C "$REPO_DIR" rev-parse "origin/$BRANCH")
head=$(git -C "$REPO_DIR" rev-parse HEAD)

if [ -f "$BAD_FILE" ] && [ "$(cat "$BAD_FILE")" = "$remote" ]; then
  log "origin/$BRANCH ($remote) is known-bad — waiting for a new commit"
  exit 0
fi

# Nothing new AND the services are running -> cheap no-op. When a service is
# down despite an unchanged commit (first adoption, crashed container), fall
# through and rebuild the current checkout.
if [ "$remote" = "$head" ]; then
  running=$(dc ps --status running --format '{{.Service}}' 2>/dev/null || true)
  if echo "$running" | grep -q "backend" && echo "$running" | grep -q "frontend"; then
    log "nothing to deploy — already at $(git -C "$REPO_DIR" rev-parse --short HEAD)"
    exit 0
  fi
  log "commit unchanged but services not running — rebuilding current checkout"
else
  # ── 2. Fast-forward the repo ───────────────────────────────────────────────
  if git -C "$REPO_DIR" merge-base --is-ancestor HEAD "origin/$BRANCH"; then
    git -C "$REPO_DIR" pull --ff-only --quiet origin "$BRANCH"
    log "repo fast-forwarded to $(git -C "$REPO_DIR" rev-parse --short HEAD)"
  else
    log "WARN: local HEAD diverged from origin/$BRANCH — skipping this run"
    exit 0
  fi
fi

# ── 3. Build serially, recreate, health-gate ─────────────────────────────────
deployed=$(git -C "$REPO_DIR" rev-parse HEAD)

if build_and_apply; then
  echo "$deployed" > "$LAST_GOOD_FILE"
  rm -f "$BAD_FILE"
  log "deploy of $(git -C "$REPO_DIR" rev-parse --short HEAD) finished OK"
else
  echo "$deployed" > "$BAD_FILE"
  last_good=$(cat "$LAST_GOOD_FILE" 2>/dev/null || echo "")
  if [ -n "$last_good" ] && [ "$last_good" != "$deployed" ]; then
    log "rolling back to last good commit ${last_good:0:8} (rebuild takes a few minutes)"
    # The server checkout is deploy-only — reset is safe, and the bad-commit
    # marker keeps the next tick from re-deploying the bad commit until the
    # branch moves past it.
    git -C "$REPO_DIR" reset --hard "$last_good"
    if build_and_apply; then
      log "rollback to ${last_good:0:8} healthy"
    else
      log "CRITICAL: rollback build also failed — manual intervention needed"
    fi
  else
    log "CRITICAL: no last good commit to roll back to — manual intervention needed"
  fi
  docker image prune -f >/dev/null 2>&1 || true
  log "auto-deploy finished WITH ERRORS"
  exit 1
fi

# ── 4. Reclaim disk (source builds leave dangling layers behind) ─────────────
docker image prune -f >/dev/null 2>&1 || true
docker image prune -af --filter "until=$PRUNE_OLDER_THAN" >/dev/null 2>&1 || true

exit 0

}
