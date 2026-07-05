# Contributing

So arbeiten wir in diesem Repo. Die Regeln sind kurz, weil sie konsequent gelten —
der Commit-Check in der CI erzwingt einen Teil davon automatisch.

## Workflow

1. **Issue zuerst.** Jede Änderung beginnt mit einem Issue (Objective / Tasks /
   Acceptance criteria). Kein Branch ohne Issue-Nummer.
2. **Branch von develop.** Name: `typ/kurze-beschreibung-#issue`, z. B.
   `feature/sensor-registry-#310` oder `fix/admin-panel-responsive-#305`.
3. **Kleine Commits, einzeln gepusht.** Format `typ: beschreibung #issue`
   (erlaubte Typen prüft `.github/scripts/check_commits.py`). Erst committen,
   dann pushen, dann der nächste Commit — keine lokal angesammelten Stapel.
4. **PR gegen develop** am Ende, Review, Merge-Commit (kein Squash, kein Rebase —
   die Einzel-Commits sind Teil der Historie).

## Branching-Regeln

- **Branches entstehen NUR von develop. Merges gehen NUR nach develop zurück.**
  Keine Branches von Feature-Branches, keine Merges zwischen Feature-Branches.
- Baut ein Thema auf einem noch offenen PR auf: warten, bis der gemerged ist,
  und dann frisch von develop abzweigen. Gestackte PR-Ketten vermeiden wir —
  sie haben uns einen History-Rewrite gekostet.
- Falls eine Kette doch einmal unvermeidbar ist: streng von unten nach oben
  mergen und **jeden Basis-Branch sofort nach seinem Merge löschen** — GitHub
  retargetet den nächsten PR nur dann automatisch auf develop.
- `main` bekommt ausschließlich Release-Merges von develop, mit SemVer-Tag
  (`vX.Y.Z`) und geschnittenem CHANGELOG-Abschnitt.

## Sonstiges

- CHANGELOG (`Keep a Changelog`) pro PR mitpflegen: Nutzersichtbares unter
  `[Unreleased]` in der passenden Kategorie.
- Architektur-Entscheidungen als ADR nach `docs/adr/`; Teil-spezifische
  Logbücher liegen in `backend/docs/` und `raspberry/docs/`.
- Secrets bleiben in git-ignorierten `.env`-Dateien (`docker/server/.env`,
  `raspberry/docker/.env`); `docker/local/.env` ist bewusst committet und
  enthält keine.
