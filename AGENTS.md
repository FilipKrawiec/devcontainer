# AGENTS

This repository defines the Master Dev Container development environment on macOS using the official Dev Container specification (`@devcontainers/cli`).

## Primary Rule

Do not create or use host-side working trees for active repositories under `~/Developer`.

Project source code lives exclusively inside the persistent Docker named volume `devws_projects` mounted at `/projects` inside the Master Dev Container.

## Workspace Model

- A single Docker named volume (`devws_projects`) holds all active Git working trees under `/projects/<git-host>/<group>/<project>`.
- One persistent Docker named volume (`devws_home`) holds container state at `/home/vscode`; image-provided executables and toolchains live outside this mount.
- Opening `~/Developer` in Zed, Antigravity IDE, or VS Code launches/attaches the Master Dev Container defined in `.devcontainer/devcontainer.json`.
- Host CLI interactions use the official `@devcontainers/cli` (`devcontainer build`, `devcontainer up`, `devcontainer exec`).
- The default configuration mounts the Docker Desktop socket through Docker-outside-of-Docker so repositories can build images and run sibling services. Host SSH private keys remain unmounted.
- Ollama runs on the macOS host and is reached from containers through `http://host.docker.internal:11434`.

## Agent Expectations

- AI Agents (Antigravity CLI / `agy`, `claude`, `codex`, Zed ACP) execute natively inside the Master Dev Container where all compilers, test runners, and git tools are pre-installed.
- Treat the macOS host `~/Developer` directory as control-plane infrastructure only.
- Docker-daemon access is a deliberate default for this workspace because most active repositories build images or run local services. Keep host filesystem and credential mounts capability-safe; do not mount private SSH keys.

## Toolchain & Pre-Installed Runtimes

- Pinned polyglot image defined in [.devcontainer/Dockerfile](.devcontainer/Dockerfile).
- Pre-installed tools: Kotlin Clikt CLI (`dev`), GraalVM JDK 21, Node.js 24, Python 3.14 (uv), Go 1.25, Rust 1.86, Flutter 3.41, GitHub CLI (`gh`), `ollama` CLI, `agy`, `codex`, and `claude`.

## Human Files

- `AGENTS.md` is the primary machine-facing contract.
- `README.md` is the primary human-facing overview.
