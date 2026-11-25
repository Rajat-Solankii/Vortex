"""Extract commit history and app version metadata for release notifications."""

import os
import re
import subprocess
from pathlib import Path

VERSION_CONFIG_PATH = Path("iosApp/Configuration/Version.xcconfig")

IGNORED_COMMIT_PATTERNS = (
    r"^ci:",
    r"^workflow",
    r"\[skip ci\]",
    r"^merge pull request",
    r"^merge branch",
    r"dependabot",
)


def read_xcconfig_version(path: Path = VERSION_CONFIG_PATH) -> tuple[str, str]:
    """Return (marketing_version, build_number) from Version.xcconfig."""
    version_name = ""
    version_code = ""
    if not path.exists():
        return version_name, version_code

    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = (part.strip() for part in line.split("=", 1))
        if key == "MARKETING_VERSION":
            version_name = value
        elif key == "CURRENT_PROJECT_VERSION":
            version_code = value
    return version_name, version_code


def collect_commits(prev_sha: str, curr_sha: str) -> list[str]:
    """Return non-noisy commit subject lines between prev_sha and curr_sha."""
    zero_sha = "0" * 40
    if prev_sha and prev_sha != zero_sha:
        cmd = ["git", "log", "--no-merges", "--pretty=format:%s", f"{prev_sha}..{curr_sha}"]
    else:
        cmd = ["git", "log", "--no-merges", "-n", "10", "--pretty=format:%s"]

    try:
        output = subprocess.check_output(cmd, stderr=subprocess.DEVNULL).decode("utf-8", errors="ignore")
        raw_commits = [line.strip() for line in output.splitlines() if line.strip()]
    except Exception:
        return []

    filtered = [
        commit
        for commit in raw_commits
        if not any(re.search(pattern, commit, re.IGNORECASE) for pattern in IGNORED_COMMIT_PATTERNS)
    ]
    return filtered or raw_commits


def write_github_output(name: str, value: str, output_file: str | None = None) -> None:
    """Append a multiline output variable to GITHUB_OUTPUT when available."""
    target = output_file or os.environ.get("GITHUB_OUTPUT")
    if not target:
        return
    delimiter = "__VORTEX_EOF__"
    while delimiter in value:
        delimiter += "_X"
    with open(target, "a", encoding="utf-8") as fh:
        fh.write(f"{name}<<{delimiter}\n{value}\n{delimiter}\n")


def main() -> None:
    version_name, version_code = read_xcconfig_version()
    prev_sha = os.environ.get("PREV_SHA", "").strip()
    curr_sha = os.environ.get("CURR_SHA", "HEAD").strip() or "HEAD"

    commits = collect_commits(prev_sha, curr_sha)
    changelog_text = "\n".join(commits) if commits else "New release build"

    write_github_output("version_name", version_name)
    write_github_output("version_code", version_code)
    write_github_output("changelog_text", changelog_text)
    print(f"Version: {version_name} ({version_code}), commits in summary: {len(commits)}")


if __name__ == "__main__":
    main()
