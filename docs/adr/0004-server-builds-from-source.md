# 0004 — Der Server baut Backend und Frontend aus Quellen

**Status:** Akzeptiert (#314/#315, Juli 2026) — kehrt die Entscheidung aus #140
bewusst um; `images.yml` (GHCR-Publishing) ist damit ersatzlos entfernt.

**Kontext.** Vite brennt die `VITE_*`-URLs zur Build-Zeit ins Frontend-Bundle.
Das in CI gebaute GHCR-Image war dadurch fest an occupi.mi.hdm-stuttgart.de
gebunden — ein „vollständig über die `.env` konfigurierbarer" Server war mit
Registry-Images für das Frontend nicht zu haben. Zur Geschichte: #140 hatte
Builds auf dem Server verboten, nachdem ein Maven-Build die swaplose 1-Core-VM
per OOM abgeschossen hatte — daher kam GHCR überhaupt.

**Entscheidung.** `docker/server/compose.yml` baut Backend und Frontend mit
`build:`; die `VITE_*`-Werte entstehen aus `PUBLIC_HOST` in der Server-`.env`.
`deploy/auto-deploy.sh` triggert auf neue Commits statt auf Registry-Digests,
baut **seriell** und rollt bei rotem Healthcheck per Reset auf den letzten guten
Commit zurück. Die #140-Lehre wird direkt adressiert: eine Swap-Datei ist
dokumentierte Voraussetzung, Builds laufen nie parallel.

Verworfene Alternative: GHCR behalten + Laufzeit-Konfiguration im Frontend
(`env.js` beim Containerstart). Sauber und mit sekundenschnellem Rollback, aber
sie hätte ein zusätzliches Frontend-Feature vorausgesetzt und die
Registry-Abhängigkeit behalten; die Team-Entscheidung fiel auf Quellen-Builds.

**Konsequenzen.** Jeder Host ist mit einer ausgefüllten `.env` sofort
deploybar; GHCR und `images.yml` entfallen (weniger Infrastruktur, kein
Digest-Drift). Preis: Deploys dauern Minuten statt Sekunden, Rollback ist ein
Rebuild, und die VM braucht ihren Swap — steht als Kasten in
`deploy/README.md` und als Schritt im Migrations-Runbook.
