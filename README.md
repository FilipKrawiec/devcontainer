# Master Dev Workspace

This repository defines an isolated, Docker Desktop-based development environment on macOS using the official **Dev Container specification (`@devcontainers/cli`)**.

All your repositories and user settings live in high-performance Docker named volumes (`devws_projects` mounted at `/projects` and `devws_home` mounted at `/home/vscode`) inside a unified **Master Dev Container**. Your macOS host remains 100% clean with zero host build tools, zero host language runtimes, and zero custom host scripts.

---

## Minimal Host Structure (`~/Developer`)

Your macOS host computer contains only standard configuration files:

```
~/Developer/
├── .devcontainer/
│   ├── devcontainer.json   # Official Dev Container specification
│   ├── docker-compose.yml  # Docker Compose service definition & port bindings
│   ├── Dockerfile          # Polyglot container image definition
│   └── cli/                # Kotlin Clikt CLI source code (built inside Docker)
├── AGENTS.md               # Machine-facing agent contract
├── README.md               # Documentation & quick start guide
└── .gitignore
```

---

## Getting Started

### Option A: IDE Native Dev Container (Recommended)

Open `~/Developer` in **Zed**, **Antigravity IDE**, or **VS Code**. The editor automatically detects `.devcontainer/devcontainer.json`, launches via Docker Compose, and attaches directly into `/projects`.

### Option B: Official Dev Container CLI (`@devcontainers/cli`)

Install the official Dev Container CLI:

```sh
npm install -g @devcontainers/cli
```

Build and launch the workspace container:

```sh
devcontainer build --workspace-folder ~/Developer
devcontainer up --workspace-folder ~/Developer
```

---

## Daily In-Container Workflow

Inside the container terminal (or via `devcontainer exec --workspace-folder ~/Developer <cmd>`):

- **Clone a repository into `/projects`**:
  ```sh
  dev clone git@gitlab.com:group/project.git
  ```
- **List active repositories**:
  ```sh
  dev list
  ```
- **Fetch latest git remotes**:
  ```sh
  dev fetch              # Fetch all active repositories in /projects
  dev fetch --prune      # Fetch all and prune deleted branches (-p)
  dev fetch --tags       # Fetch all tags from remote (-t)
  dev fetch group/project # Fetch a specific repository
  ```
- **Remove a repository**:
  ```sh
  dev reset group/project
  ```

---

## Container Volumes & Mounting Architecture

| Mount Target | Source | Type | Description |
| :--- | :--- | :--- | :--- |
| `/projects` | `devws_projects` | Docker Volume | High-performance storage for all git repository working trees |
| `/home/vscode` | `devws_home` | Docker Volume | Persistent storage for user history, package caches (`.cargo`, `.gradle`, `.npm`), and tool settings |
| `/home/vscode/.ssh` | `${localEnv:HOME}/.ssh` | Read-only Bind | Subpath overlay providing container access to host SSH keys (`id_ed25519`, `id_rsa`) |
| `/var/run/docker.sock` | `/var/run/docker.sock` | Bind | Docker socket for running sibling service containers (Postgres, Redis) |

---

## Port Forwarding Configuration

Container ports are bound and forwarded as configured in [.devcontainer/docker-compose.yml](file:///Users/filip/Developer/.devcontainer/docker-compose.yml) and [.devcontainer/devcontainer.json](file:///Users/filip/Developer/.devcontainer/devcontainer.json).

> [!TIP]
> **Fast Port Updates Without Rebuilding Image**:
> When using Docker Compose, port bindings (`ports: - "3000:3000"`) are handled by Docker container creation rather than image compilation. Editing `ports` in `docker-compose.yml` allows Docker Compose to recreate the container in ~1 second reusing the cached image, with **zero image rebuild required**.

To add or modify forwarded ports, edit `ports` in `docker-compose.yml` and `forwardPorts` in `devcontainer.json`:

```yaml
ports:
  - "3000:3000"   # Node.js / React / Next.js
  - "5173:5173"   # Vite Dev Server
  - "8000:8000"   # Python / FastAPI / Django
  - "8080:8080"   # Vue / Spring Boot / HTTP
  - "8081:8081"   # Alternate Web / Metro
  - "9000:9000"   # Backend Service
```

---

## Pre-Installed Toolchain

The Master Dev Container (`devws-polyglot:latest`) comes pre-installed with:

- **Kotlin Clikt CLI (`dev`)**: Native Kotlin Clikt 4.4.0 CLI compiled from `.devcontainer/cli` into `/usr/local/bin/dev`.
- **GraalVM JDK 21**: GraalVM Community Edition (`JAVA_HOME=/opt/graalvm`, `native-image` enabled).
- **Node.js v24.x**: Node.js 24.14.0 + `npm` + `pnpm`.
- **Python 3.14**: Managed via `uv`.
- **GitHub CLI (`gh`)**: Official GitHub package.
- **AI Agent CLIs**: `agy` (Google Antigravity CLI), `codex` CLI, and `claude` (Claude Code CLI).
- **Go 1.25**, **Rust 1.86**, **Flutter 3.41**, **Gradle 9.4**.
- **Ollama Host Route**: Bound to `http://host.docker.internal:11434/v1` for local coder model inference.
