#!/usr/bin/env bash
#
# OccuPi — automatic deploy (poll & apply the latest GHCR images, health-gated).
#
# Invoked periodically by occupi-autodeploy.timer. Each run:
#   1. fast-forwards the repo (compose files + this script itself),
#   2. pulls the latest backend/frontend images from GHCR,
#   3. recreates ONLY the services whose image actually changed,
#   4. health-checks each; on failure it ROLLS BACK to the previous image and
#      records the bad digest so it is not redeployed until :latest moves on,
#   5. prunes old images to reclaim disk.
#
# Infra (postgres / keycloak / influxdb) is intentionally NOT touched here —
# those are pinned versions and updated manually (a DB major upgrade can need
# migration steps). See deploy/README.md.
#
# Runs as root via systemd. Logs go to journald:  journalctl -u occupi-autodeploy
#
# The server only ever PULLS prebuilt images and never builds (see #140), so this
# always uses the prod overlay explicitly — never the local override.

set -euo pipefail

# The whole body is wrapped in a brace group so bash reads the entire script into
# memory before executing it. The `git pull` below may rewrite this very file
# mid-run; reading it up front prevents that from corrupting execution.
{

# ── Configuration ────────────────────────────────────────────────────────────
REPO_DIR="/home/Elhan/Occupi"
BRANCH="develop"
REGISTRY_OWNER="ghcr.io/elhan-salaji"
SERVICES=(backend frontend)          # infra is updated manually on purpose
STATE_DIR="/var/lib/occupi-autodeploy"
LOCKFILE="/run/occupi-autodeploy.lock"
HEALTH_RETRIES=40                    # 40 x 3s = up to 120s for a service to come up
HEALTH_DELAY=3
PRUNE_OLDER_THAN="336h"              # drop unused images older than 14 days

COMPOSE_DIR="$REPO_DIR/docker"

log() { echo "[$(date -Is)] $*"; }

# Compose helper — ALWAYS base + prod overlay, never the local override.
dc() {
  docker compose -f "$COMPOSE_DIR/docker-compose.yml" \
                 -f "$COMPOSE_DIR/docker-compose.prod.yml" "$@"
}

img_ref()       { echo "$REGISTRY_OWNER/occupi-$1:latest"; }
running_image() { docker inspect --format '{{.Image}}' "occupi-$1" 2>/dev/null || echo "none"; }
tag_image_id()  { docker image inspect --format '{{.Id}}' "$(img_ref "$1")" 2>/dev/null || echo "none"; }
tag_digest()    { docker image inspect --format '{{if .RepoDigests}}{{index .RepoDigests 0}}{{end}}' "$(img_ref "$1")" 2>/dev/null || echo ""; }

# Liveness URL per service, checked from the host (the prod overlay publishes
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

# ── Single-instance lock (skip if a previous run is still going) ─────────────
exec 9>"$LOCKFILE"
if ! flock -n 9; then
  log "another auto-deploy run is in progress — exiting"
  exit 0
fi

mkdir -p "$STATE_DIR"

# ── 1. Sync the repo (compose files + this script) ───────────────────────────
log "fetching origin/$BRANCH in $REPO_DIR"
if git -C "$REPO_DIR" fetch --quiet --prune origin "$BRANCH"; then
  if git -C "$REPO_DIR" merge-base --is-ancestor HEAD "origin/$BRANCH"; then
    if git -C "$REPO_DIR" pull --ff-only --quiet origin "$BRANCH"; then
      log "repo fast-forwarded to $(git -C "$REPO_DIR" rev-parse --short HEAD)"
    else
      log "WARN: git pull failed — continuing with existing checkout"
    fi
  else
    log "WARN: local HEAD diverged from origin/$BRANCH — skipping repo update"
  fi
else
  log "WARN: git fetch failed — continuing with existing checkout"
fi

# ── 2. Pull the latest images (download only; running containers untouched) ───
log "pulling images: ${SERVICES[*]}"
dc pull "${SERVICES[@]}" || log "WARN: docker pull failed — using locally cached images"

# ── 3. Apply changed services, health-gated with rollback ────────────────────
failed=0
changed_any=0

for svc in "${SERVICES[@]}"; do
  before=$(running_image "$svc")
  after=$(tag_image_id "$svc")

  if [ "$before" = "$after" ] && [ "$before" != "none" ]; then
    log "$svc: up to date"
    continue
  fi

  digest=$(tag_digest "$svc")
  bad_marker="$STATE_DIR/$svc.bad"
  if [ -n "$digest" ] && [ -f "$bad_marker" ] && [ "$(cat "$bad_marker")" = "$digest" ]; then
    log "$svc: current :latest digest is known-bad — skipping until it changes"
    continue
  fi

  changed_any=1
  log "$svc: updating ($before -> $after)"

  ok=1
  dc up -d "$svc" || ok=0
  if [ "$ok" = "1" ] && wait_healthy "$(health_url "$svc")"; then
    log "$svc: healthy after update"
    rm -f "$bad_marker"
  else
    log "ERROR: $svc did not come up healthy"
    failed=1
    if [ -n "$digest" ]; then echo "$digest" > "$bad_marker"; fi
    if [ "$before" != "none" ]; then
      log "$svc: rolling back to previous image $before"
      docker tag "$before" "$(img_ref "$svc")" || true
      dc up -d "$svc" || true
      if wait_healthy "$(health_url "$svc")"; then
        log "$svc: healthy again after rollback"
      else
        log "CRITICAL: $svc still unhealthy after rollback — manual intervention needed"
      fi
    else
      log "CRITICAL: $svc has no previous image to roll back to — manual intervention needed"
    fi
  fi
done

# ── 4. Reclaim disk ──────────────────────────────────────────────────────────
docker image prune -f >/dev/null 2>&1 || true
docker image prune -af --filter "until=$PRUNE_OLDER_THAN" >/dev/null 2>&1 || true

if [ "$changed_any" = "0" ]; then
  log "nothing to deploy — already current"
fi

if [ "$failed" != "0" ]; then
  log "auto-deploy finished WITH ERRORS"
  exit 1
fi
log "auto-deploy finished OK"
exit 0

}
