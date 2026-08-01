# Master Dev Workspace

This repository defines a macOS Dev Container workspace using the [Development Container specification](https://containers.dev/). Active repositories live in Docker volumes, while the host checkout remains control-plane configuration only.

The default configuration is designed for interactive development, AI agents, Docker image builds, and local sibling services. It does not mount host SSH private keys.

## Getting started

### IDE workflow (recommended)

Open `~/Developer` in VS Code, Zed, or Antigravity IDE and select the normal Dev Container flow. The default configuration is [.devcontainer/devcontainer.json](.devcontainer/devcontainer.json) and opens `/projects`.

For Codex or another agent CLI, open an in-container terminal as `vscode`, authenticate the CLI there, then work under `/projects`. Agent configuration persists in `devws_home`; image-provided executables are outside that volume and therefore remain available after rebuilds.

### CLI workflow

The official Dev Container CLI requires Node.js on the host. Use this only if that prerequisite is acceptable:

```sh
npm install -g @devcontainers/cli
devcontainer build --workspace-folder ~/Developer
devcontainer up --workspace-folder ~/Developer
```

After configuration changes, use your IDE's **Rebuild Container** action or rebuild with the CLI. Rebuilding preserves `devws_projects` and `devws_home`; do not remove either volume unless you have a backup.

The configuration uses the official Docker-outside-of-Docker feature. Its Docker CLI connects to Docker Desktop through the mounted host socket, so `docker build` and `docker compose` work inside the Dev Container without running a second daemon. Ollama remains independently available at `http://host.docker.internal:11434`.

Use your local SSH agent or an HTTPS credential helper for Git authentication. Do not bind-mount `~/.ssh` into this shared workspace.

## Daily in-container workflow

```sh
# Clone a repository, or fetch it when it already exists.
dev projects get group/project
dev projects get git@gitlab.com:group/project.git

# List repositories with branch and upstream staleness.
dev projects list

# Verify the runtime, including Docker access.
dev doctor

# Permanently remove one verified Git worktree.
dev projects reset github.com/user/project --yes
```

`reset` accepts only a relative path beneath `/projects`, refuses non-Git directories, and requires `--yes`.

## Storage and ports

| Mount target | Source | Purpose |
| --- | --- | --- |
| `/projects` | `devws_projects` | Active Git working trees under `/projects/<git-host>/<group>/<project>` |
| `/home/vscode` | `devws_home` | Agent authentication, shell state, and package caches |
| `/var/run/docker.sock` | Docker Desktop host socket | Docker image builds and local sibling services |

Ports 3000, 5173, 8000, 8080, 8081, and 9000 are IDE-forwarded on demand. They are not published on host interfaces by the default profile, so parallel workspaces do not reserve or expose them.

## Included toolchain

The image builds the `dev` Kotlin CLI, GraalVM JDK 21, Node.js 24, Python 3.14 through uv, Go 1.25, Rust 1.86, Flutter 3.41, Gradle 9.4, GitHub CLI, and the Codex, Claude, Antigravity, and Ollama CLIs. Ollama routes to `http://host.docker.internal:11434` through `OLLAMA_HOST`.

Run a fresh container rebuild after changing the Dockerfile, then verify the tools as `vscode` before relying on the image for agent work.
