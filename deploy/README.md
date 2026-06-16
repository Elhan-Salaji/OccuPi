# OccuPi — deploy

Host-side bits that live outside Docker Compose: the Nginx reverse proxy
([`nginx/occupi.conf`](nginx/occupi.conf)) and the **automatic deploy** timer.

## Automatic deploy (systemd timer)

The server only ever **pulls** prebuilt images from GHCR and never builds (an
on-server Maven build once exhausted the swapless VM's RAM, see #140). This timer
automates the manual `pull` + recreate you would otherwise SSH in to run.

```
auto-deploy.sh              # the deploy logic (versioned here, runs from the repo)
occupi-autodeploy.service   # one-shot unit that runs the script
occupi-autodeploy.timer     # fires the service every 5 minutes
```

### What a run does

1. **Fast-forwards the repo** (`git pull --ff-only` on `develop`) so compose files
   and this script stay current. Diverged/blocked checkout → it warns and skips
   the update instead of forcing anything.
2. **Pulls** the latest `backend` + `frontend` images from GHCR (download only —
   running containers are not touched yet).
3. **Recreates only what changed.** A service is restarted only if its `:latest`
   image id differs from the one its container is running. Infra
   (`postgres` / `keycloak` / `influxdb`) is deliberately left alone.
4. **Health-gates each update with rollback.** After recreating a service it polls
   a liveness URL (`backend` → `/v3/api-docs`, `frontend` → `/`). If it does not
   return `200` within ~2 min, it **rolls back** to the previous image, records the
   bad image digest, and that digest is **not redeployed** until `:latest` moves on.
5. **Prunes** dangling and >14-day-old unused images to reclaim disk.

It is safe to run on every tick: when nothing changed it is a no-op. Only one run
executes at a time (flock).

> **Note:** auto-deploy means *every merge to `develop` goes live by itself*
> within ~5–10 min (CI build + next tick). That is the intended behaviour.

### Install (one-time, on the server, as root)

After this is merged to `develop` and the repo is pulled on the server:

```bash
cd /home/Elhan/Occupi
git pull --ff-only                      # get the deploy/ files

# Copy the units into systemd (cp, not symlink — most reliable for enable):
cp deploy/occupi-autodeploy.service deploy/occupi-autodeploy.timer /etc/systemd/system/

systemctl daemon-reload
systemctl enable --now occupi-autodeploy.timer

# Optional: run it once now and watch it
systemctl start occupi-autodeploy.service
journalctl -u occupi-autodeploy -f
```

The **script** (`auto-deploy.sh`) runs straight from the repo, so it stays current
via `git pull` automatically. Only the two **unit files** are copied — re-`cp` them
+ `systemctl daemon-reload` on the rare occasion they change.

### Operate

```bash
systemctl list-timers occupi-autodeploy.timer     # when does it next run?
journalctl -u occupi-autodeploy -n 100 --no-pager # recent deploy logs
systemctl start occupi-autodeploy.service         # deploy now (don't wait for the tick)

# Pause / resume automatic deploys:
systemctl stop occupi-autodeploy.timer            # pause
systemctl start occupi-autodeploy.timer           # resume
```

A failed deploy makes the **service** unit fail (visible in
`systemctl status occupi-autodeploy.service` and `journalctl`). The site stays up
because the script rolls back.

### Change the deploy logic later

`auto-deploy.sh` runs straight from the repo, so editing it via Git (push to
`develop`) takes effect on the next tick — **no server access needed**. Only
changing the schedule in `occupi-autodeploy.timer` needs a server-side re-`cp`
+ `systemctl daemon-reload`.

### Manual deploy (no timer)

The timer just automates this; you can always do it by hand:

```bash
cd /home/Elhan/Occupi/docker
docker compose -f docker-compose.yml -f docker-compose.prod.yml pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

### Uninstall

```bash
systemctl disable --now occupi-autodeploy.timer
rm -f /etc/systemd/system/occupi-autodeploy.{service,timer}
systemctl daemon-reload
```
