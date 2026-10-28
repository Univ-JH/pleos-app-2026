"""Skip Android work only for verified documentation-only diffs."""

import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess


def is_documentation(path: str) -> bool:
    file = PurePosixPath(path)
    if path == "README.md":
        return True
    if file.parts[0] != "docs":
        return False
    return file.suffix == ".md" or (
        path.startswith("docs/images/")
        and file.suffix.lower() in {".png", ".jpg", ".jpeg", ".webp", ".gif", ".svg"}
    )


def valid_sha(value: object) -> bool:
    return isinstance(value, str) and bool(re.fullmatch(r"[0-9a-f]{40}", value)) and value != "0" * 40


def git(repo: Path, *args: str) -> bytes:
    return subprocess.run(
        ["git", *args], cwd=repo, check=True, capture_output=True, timeout=60,
    ).stdout


def should_build(event_name: str, event: dict, head: str, repo: Path) -> tuple[bool, str]:
    if event_name == "pull_request":
        base = event.get("pull_request", {}).get("base", {}).get("sha")
    elif event_name == "push":
        base = event.get("before")
    else:
        return True, "Manual or unknown event: run all Android checks."

    if not valid_sha(base) or not valid_sha(head):
        return True, "Missing comparison commit: run all Android checks."

    try:
        if git(repo, "rev-parse", "HEAD").decode().strip() != head:
            return True, "Checkout does not match the event: run all Android checks."
        try:
            git(repo, "cat-file", "-e", f"{base}^{{commit}}")
        except subprocess.CalledProcessError:
            # Fetch only the needed base, not the repository's entire history.
            git(repo, "fetch", "--no-tags", "--depth=1", "origin", base)
        # For PRs, HEAD is GitHub's tested merge commit. Include deletions and
        # both sides of renames so moving code into docs cannot bypass checks.
        changed = git(repo, "diff", "--no-ext-diff", "--no-renames", "--name-only", "-z", base, head, "--")
        paths = [path.decode("utf-8", errors="surrogateescape") for path in changed.split(b"\0") if path]
    except (OSError, subprocess.SubprocessError):
        return True, "Cannot verify changed files: run all Android checks."

    if paths and all(is_documentation(path) for path in paths):
        return False, f"Documentation-only change ({len(paths)} files): Android checks not needed."
    return True, "Code, configuration, unknown files, or empty diff: run all Android checks."


def main() -> None:
    try:
        event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
        required, reason = should_build(
            os.environ.get("GITHUB_EVENT_NAME", ""), event,
            os.environ.get("GITHUB_SHA", ""), Path.cwd(),
        )
    except (OSError, KeyError, ValueError, AttributeError, TypeError):
        required, reason = True, "Cannot read event metadata: run all Android checks."
    print(reason)
    with open(os.environ["GITHUB_OUTPUT"], "a") as output:
        output.write(f"build_required={str(required).lower()}\n")
    with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as summary:
        summary.write(f"### Android CI scope\n\n{reason}\n")


if __name__ == "__main__":
    main()
