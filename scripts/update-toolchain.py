#!/usr/bin/env python3
"""
Toolchain Version Checker & Updater for Master Dev Container.

Queries official upstream distribution APIs and repositories to detect the latest
stable releases for all toolchain components pinned in .devcontainer/Dockerfile.
Validates asset availability, and automatically updates Dockerfile, AGENTS.md,
and README.md.

Usage:
  ./scripts/update-toolchain.py          # Check & update if needed
  ./scripts/update-toolchain.py --check  # Dry run (exits 1 if updates found)
  ./scripts/update-toolchain.py --json   # Output JSON summary
"""

import argparse
import json
import re
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path

HTTP_HEADERS = {"User-Agent": "MasterDevWorkspace-ToolchainUpdater/1.0"}


def fetch_latest_node() -> str:
    req = urllib.request.Request("https://nodejs.org/dist/index.json", headers=HTTP_HEADERS)
    with urllib.request.urlopen(req, timeout=15) as resp:
        data = json.loads(resp.read().decode())
    return data[0]["version"].lstrip("v")


def fetch_latest_go() -> str:
    req = urllib.request.Request("https://go.dev/dl/?mode=json", headers=HTTP_HEADERS)
    with urllib.request.urlopen(req, timeout=15) as resp:
        data = json.loads(resp.read().decode())
    return data[0]["version"].replace("go", "")


def fetch_latest_rust() -> str:
    req = urllib.request.Request(
        "https://static.rust-lang.org/dist/channel-rust-stable.toml",
        headers=HTTP_HEADERS,
    )
    with urllib.request.urlopen(req, timeout=15) as resp:
        toml = resp.read().decode()
    m = re.search(r'\[pkg\.rust\]\s*version\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)', toml)
    if not m:
        raise RuntimeError("Failed to parse Rust stable version from channel-rust-stable.toml")
    return m.group(1)


def fetch_latest_gradle() -> str:
    req = urllib.request.Request("https://services.gradle.org/versions/all", headers=HTTP_HEADERS)
    with urllib.request.urlopen(req, timeout=15) as resp:
        data = json.loads(resp.read().decode())
    stable = [
        g["version"]
        for g in data
        if re.match(r"^[0-9]+\.[0-9]+(\.[0-9]+)?$", g.get("version", ""))
    ]
    if not stable:
        raise RuntimeError("Failed to find stable Gradle version")
    return stable[0]


def fetch_latest_graalvm() -> str:
    req = urllib.request.Request(
        "https://api.github.com/repos/graalvm/graalvm-ce-builds/releases",
        headers=HTTP_HEADERS,
    )
    with urllib.request.urlopen(req, timeout=15) as resp:
        data = json.loads(resp.read().decode())
    jdk_tags = [
        r["tag_name"].replace("jdk-", "")
        for r in data
        if re.match(r"^jdk-[0-9]+\.[0-9]+\.[0-9]+$", r.get("tag_name", ""))
    ]
    if not jdk_tags:
        raise RuntimeError("Failed to find GraalVM CE JDK release tag")
    return jdk_tags[0]


def fetch_latest_flutter() -> str:
    out = subprocess.check_output(
        ["git", "ls-remote", "--tags", "https://github.com/flutter/flutter.git"],
        text=True,
        timeout=30,
    )
    tags = []
    for line in out.splitlines():
        if "refs/tags/" in line and "^{}" not in line:
            tag = line.split("refs/tags/")[-1].strip()
            if re.match(r"^[0-9]+\.[0-9]+\.[0-9]+$", tag):
                tags.append(tag)
    if not tags:
        raise RuntimeError("Failed to find Flutter release tags")
    return sorted(tags, key=lambda s: [int(u) for u in s.split(".")])[-1]


def fetch_latest_python() -> str:
    req = urllib.request.Request(
        "https://api.github.com/repos/astral-sh/python-build-standalone/releases/latest",
        headers=HTTP_HEADERS,
    )
    with urllib.request.urlopen(req, timeout=15) as resp:
        data = json.loads(resp.read().decode())
    py_vers = set()
    for asset in data.get("assets", []):
        m = re.match(r"^cpython-([0-9]+\.[0-9]+\.[0-9]+)\+", asset.get("name", ""))
        if m:
            py_vers.add(m.group(1))
    if not py_vers:
        raise RuntimeError("Failed to find Python version from python-build-standalone assets")
    return sorted(list(py_vers), key=lambda s: [int(u) for u in s.split(".")])[-1]


TOOL_FETCHERS = {
    "NODE_VERSION": ("Node.js", fetch_latest_node),
    "PYTHON_VERSION": ("Python", fetch_latest_python),
    "GO_VERSION": ("Go", fetch_latest_go),
    "GRADLE_VERSION": ("Gradle", fetch_latest_gradle),
    "RUST_VERSION": ("Rust", fetch_latest_rust),
    "FLUTTER_VERSION": ("Flutter", fetch_latest_flutter),
    "GRAALVM_VERSION": ("GraalVM JDK", fetch_latest_graalvm),
}


def read_dockerfile_versions(dockerfile_path: Path) -> dict[str, str]:
    content = dockerfile_path.read_text(encoding="utf-8")
    versions = {}
    for var in TOOL_FETCHERS:
        m = re.search(rf"^ARG\s+{var}=([^\s]+)", content, re.MULTILINE)
        if m:
            versions[var] = m.group(1)
        else:
            raise ValueError(f"Could not find ARG {var} in {dockerfile_path}")
    return versions


def verify_asset_urls(latest: dict[str, str]) -> list[tuple[str, bool, str]]:
    checks = [
        (
            f"Node.js x64 (v{latest['NODE_VERSION']})",
            f"https://nodejs.org/download/release/v{latest['NODE_VERSION']}/node-v{latest['NODE_VERSION']}-linux-x64.tar.xz",
        ),
        (
            f"Node.js arm64 (v{latest['NODE_VERSION']})",
            f"https://nodejs.org/download/release/v{latest['NODE_VERSION']}/node-v{latest['NODE_VERSION']}-linux-arm64.tar.xz",
        ),
        (
            f"Go amd64 ({latest['GO_VERSION']})",
            f"https://go.dev/dl/go{latest['GO_VERSION']}.linux-amd64.tar.gz",
        ),
        (
            f"Go arm64 ({latest['GO_VERSION']})",
            f"https://go.dev/dl/go{latest['GO_VERSION']}.linux-arm64.tar.gz",
        ),
        (
            f"Gradle ({latest['GRADLE_VERSION']})",
            f"https://services.gradle.org/distributions/gradle-{latest['GRADLE_VERSION']}-bin.zip",
        ),
        (
            f"GraalVM x64 ({latest['GRAALVM_VERSION']})",
            f"https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-{latest['GRAALVM_VERSION']}/graalvm-community-jdk-{latest['GRAALVM_VERSION']}_linux-x64_bin.tar.gz",
        ),
        (
            f"GraalVM arm64 ({latest['GRAALVM_VERSION']})",
            f"https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-{latest['GRAALVM_VERSION']}/graalvm-community-jdk-{latest['GRAALVM_VERSION']}_linux-aarch64_bin.tar.gz",
        ),
    ]

    results = []
    for label, url in checks:
        req = urllib.request.Request(url, headers=HTTP_HEADERS, method="HEAD")
        try:
            with urllib.request.urlopen(req, timeout=15) as resp:
                results.append((label, resp.status == 200, url))
        except Exception as exc:
            results.append((label, False, f"{url} ({exc})"))
    return results


def update_dockerfile(dockerfile_path: Path, latest: dict[str, str]):
    content = dockerfile_path.read_text(encoding="utf-8")
    for var, ver in latest.items():
        content = re.sub(rf"^(ARG\s+{var}=).*$", rf"\g<1>{ver}", content, flags=re.MULTILINE)
    dockerfile_path.write_text(content, encoding="utf-8")


def short_ver(ver: str, segments: int = 2) -> str:
    parts = ver.split(".")
    return ".".join(parts[:segments])


def update_docs(agents_path: Path, readme_path: Path, latest: dict[str, str]):
    graal_major = latest["GRAALVM_VERSION"].split(".")[0]
    node_major = latest["NODE_VERSION"].split(".")[0]
    py_minor = short_ver(latest["PYTHON_VERSION"], 2)
    go_minor = short_ver(latest["GO_VERSION"], 2)
    rust_minor = short_ver(latest["RUST_VERSION"], 2)
    flutter_minor = short_ver(latest["FLUTTER_VERSION"], 2)
    gradle_minor = short_ver(latest["GRADLE_VERSION"], 2)

    # Update AGENTS.md
    if agents_path.exists():
        content = agents_path.read_text(encoding="utf-8")
        agents_pattern = (
            r"- Pre-installed tools: Kotlin Clikt CLI \(`dev`\), GraalVM JDK \d+, "
            r"Node\.js \d+, Python \d+\.\d+ \(uv\), Go \d+\.\d+, Rust \d+\.\d+, "
            r"Flutter \d+\.\d+, GitHub CLI \(`gh`\), `ollama` CLI, `agy`, `codex`, and `claude`\."
        )
        agents_replacement = (
            f"- Pre-installed tools: Kotlin Clikt CLI (`dev`), GraalVM JDK {graal_major}, "
            f"Node.js {node_major}, Python {py_minor} (uv), Go {go_minor}, Rust {rust_minor}, "
            f"Flutter {flutter_minor}, GitHub CLI (`gh`), `ollama` CLI, `agy`, `codex`, and `claude`."
        )
        content = re.sub(agents_pattern, agents_replacement, content)
        agents_path.write_text(content, encoding="utf-8")

    # Update README.md
    if readme_path.exists():
        content = readme_path.read_text(encoding="utf-8")
        readme_pattern = (
            r"The image builds the `dev` Kotlin CLI, GraalVM JDK \d+, Node\.js \d+, "
            r"Python \d+\.\d+ through uv, Go \d+\.\d+, Rust \d+\.\d+, Flutter \d+\.\d+, "
            r"Gradle \d+\.\d+, GitHub CLI, and the Codex, Claude, Antigravity, and Ollama CLIs\."
        )
        readme_replacement = (
            f"The image builds the `dev` Kotlin CLI, GraalVM JDK {graal_major}, Node.js {node_major}, "
            f"Python {py_minor} through uv, Go {go_minor}, Rust {rust_minor}, Flutter {flutter_minor}, "
            f"Gradle {gradle_minor}, GitHub CLI, and the Codex, Claude, Antigravity, and Ollama CLIs."
        )
        content = re.sub(readme_pattern, readme_replacement, content)
        readme_path.write_text(content, encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description="Check and update toolchain versions.")
    parser.add_argument("--check", action="store_true", help="Check only, do not write changes")
    parser.add_argument("--json", action="store_true", help="Output results in JSON format")
    parser.add_argument("--no-verify", action="store_true", help="Skip download URL verification")
    parser.add_argument("--apply", action="store_true", help="Apply updates to files")
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parent.parent
    dockerfile_path = repo_root / ".devcontainer" / "Dockerfile"
    agents_path = repo_root / "AGENTS.md"
    readme_path = repo_root / "README.md"

    if not dockerfile_path.exists():
        print(f"Error: {dockerfile_path} not found.", file=sys.stderr)
        sys.exit(2)

    current_versions = read_dockerfile_versions(dockerfile_path)

    latest_versions = {}
    errors = {}
    for var, (label, fetcher) in TOOL_FETCHERS.items():
        try:
            latest_versions[var] = fetcher()
        except Exception as exc:
            errors[var] = str(exc)

    if errors:
        print("Encountered errors fetching latest versions:", file=sys.stderr)
        for var, err in errors.items():
            print(f"  {var}: {err}", file=sys.stderr)
        sys.exit(1)

    has_updates = any(current_versions[var] != latest_versions[var] for var in TOOL_FETCHERS)

    if not args.no_verify and has_updates:
        asset_checks = verify_asset_urls(latest_versions)
        failed_checks = [c for c in asset_checks if not c[1]]
        if failed_checks:
            print("Error: One or more download URLs failed verification:", file=sys.stderr)
            for label, ok, detail in failed_checks:
                print(f"  [FAIL] {label}: {detail}", file=sys.stderr)
            sys.exit(1)

    if args.json:
        payload = {
            "has_updates": has_updates,
            "tools": {
                var: {
                    "name": TOOL_FETCHERS[var][0],
                    "current": current_versions[var],
                    "latest": latest_versions[var],
                    "status": "outdated" if current_versions[var] != latest_versions[var] else "up-to-date",
                }
                for var in TOOL_FETCHERS
            },
        }
        print(json.dumps(payload, indent=2))
        return

    # Print summary table
    print("\nMaster Dev Container Toolchain Status:")
    print("-" * 65)
    print(f"{'Tool':<15} {'Variable':<18} {'Current':<12} {'Latest':<12} {'Status'}")
    print("-" * 65)
    for var, (label, _) in TOOL_FETCHERS.items():
        cur = current_versions[var]
        lat = latest_versions[var]
        status = "UPDATE" if cur != lat else "OK"
        print(f"{label:<15} {var:<18} {cur:<12} {lat:<12} {status}")
    print("-" * 65)

    if not has_updates:
        print("✓ All tools are currently up to date!\n")
        return

    if args.check:
        print("! Updates are available. Run without --check or with --apply to update.\n")
        sys.exit(1)

    # Apply updates by default unless --check was specified
    update_dockerfile(dockerfile_path, latest_versions)
    update_docs(agents_path, readme_path, latest_versions)
    print("✓ Successfully updated .devcontainer/Dockerfile, AGENTS.md, and README.md!\n")


if __name__ == "__main__":
    main()
