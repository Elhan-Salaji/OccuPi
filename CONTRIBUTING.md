# Contributing

How we work in this repo. The rules are short because they apply without
exception — the commit check in CI enforces part of them automatically.

## Workflow

1. **Issue first.** Every change starts with an issue (objective / tasks /
   acceptance criteria). No branch without an issue number.
2. **Branch from develop.** Name: `type/short-description-#issue`, e.g.
   `feature/sensor-registry-#310` or `fix/admin-panel-responsive-#305`.
3. **Small commits, pushed one at a time.** Format `type: description #issue`
   (`.github/scripts/check_commits.py` checks the allowed types). Commit, push,
   then the next commit — no piling up local commits and pushing them in a batch.
4. **PR against develop** at the end, review, merge commit (no squash, no
   rebase — the individual commits are part of the history).

## Branching rules

- **Branches come from develop only. Merges go back to develop only.**
  No branches off feature branches, no merges between feature branches.
- When a topic builds on a still-open PR: wait until it is merged, then branch
  fresh from develop. We avoid stacked PR chains — one cost us a history rewrite.
- If a chain is ever unavoidable: merge strictly bottom-up and **delete each
  base branch right after its merge** — GitHub only retargets the next PR to
  develop automatically when the base branch is gone.
- `main` receives release merges from develop only, with a SemVer tag
  (`vX.Y.Z`) and a cut CHANGELOG section.

## Everything else

- Maintain the CHANGELOG (`Keep a Changelog`) with each PR: user-visible
  changes go under `[Unreleased]` in the matching category.
- Architecture decisions go to `docs/adr/` as ADRs; the part-specific decision
  logs live in `backend/docs/` and `raspberry/docs/`.
- Secrets stay in git-ignored `.env` files (`docker/server/.env`,
  `raspberry/docker/.env`); `docker/local/.env` is committed on purpose and
  contains none.
