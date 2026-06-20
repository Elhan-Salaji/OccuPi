import os
import re
import subprocess
import sys

VALID_TYPES = {
    "add", "update", "build", "fix", "feat", "chore", "test",
    "docs", "refactor", "style", "remove", "revert", "release", "init",
}

COMMIT_PATTERN = re.compile(
    r"^(" + "|".join(VALID_TYPES) + r"): .+ #\d+$",
    re.IGNORECASE,
)


def is_valid(message: str) -> bool:
    msg = message.strip()
    if not msg:
        return True
    if re.match(r"^Merge ", msg, re.IGNORECASE):
        return True
    if re.match(r'^Revert "', msg, re.IGNORECASE):
        return True
    if re.match(r"^(initial commit|init(ial)? repo)", msg, re.IGNORECASE):
        return True
    return bool(COMMIT_PATTERN.match(msg))


def get_commit_messages() -> list[str]:
    sha = os.environ.get("COMMIT_SHA", "HEAD")
    result = subprocess.run(
        ["git", "log", "--format=%s", "-1", sha],
        capture_output=True, text=True, check=False,
    )
    return [line for line in result.stdout.strip().splitlines() if line.strip()]


def main() -> None:
    messages = get_commit_messages()

    if not messages:
        print("No commits to validate.")
        sys.exit(0)

    invalid = [m for m in messages if not is_valid(m)]

    if invalid:
        print("Halte dich bitte an das ADR für Git Workflow")
        print()
        print("Ungültige Commit-Messages:")
        for m in invalid:
            print(f"  ✗ {m}")
        print()
        print("Erlaubtes Format:  <typ>: <beschreibung> #<issue-nummer>")
        print("Beispiele:")
        print("  add: Login endpoint #42")
        print("  fix: NPE in OccupancyService #17")
        print("  update: README with setup instructions #3")
        sys.exit(1)

    print(f"✓ {len(messages)} Commit-Message(s) entsprechen dem ADR.")


if __name__ == "__main__":
    main()
