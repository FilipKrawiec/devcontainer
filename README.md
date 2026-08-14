# Master Dev Workspace

This repository defines a macOS Dev Container workspace using the [Development Container specification](https://containers.dev/). Active repositories live in Docker volumes, while the host checkout remains control-plane configuration only.

The default configuration is designed for interactive development, AI agents, Docker image builds, local sibling services, and instant HTML artifact previews via an Nginx HTTP sidecar.

---

## Minimal Host Structure (`~/Developer`)

Your macOS host computer contains only standard configuration files:

```
~/Developer/
├── .devcontainer/
│   ├── devcontainer.json   # Official Dev Container specification
│   ├── docker-compose.yml  # Docker Compose orchestration & sidecar definitions
│   ├── Dockerfile          # Polyglot container image definition
│   └── cli/                # Kotlin Clikt CLI source code (built inside Docker)
├── AGENTS.md               # Machine-facing agent contract
├── README.md               # Documentation & quick start guide
└── .gitignore
```

---

## Getting Started

### Option A: IDE Native Dev Container (Recommended)

Open `~/Developer` in **Zed**, **Antigravity IDE**, or **VS Code**. The editor automatically detects `.devcontainer/devcontainer.json`, launches the environment via Docker Compose, and attaches directly into `/projects`.

For Codex, Claude, or Antigravity agents, open an in-container terminal as `vscode` and authenticate the CLI there. Agent configuration persists in `devws_home`; image-provided executables are outside that volume and remain available after rebuilds.

### Option B: Official Dev Container CLI (`@devcontainers/cli`)

Install the official Dev Container CLI:

```sh
npm install -g @devcontainers/cli
devcontainer build --workspace-folder ~/Developer
devcontainer up --workspace-folder ~/Developer
```

---

## Daily In-Container Workflow

Inside the container terminal (or via `devcontainer exec --workspace-folder ~/Developer <cmd>`):

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

---

## HTTP Preview Sidecar & Port Forwarding

An automated **Nginx HTTP Preview Sidecar** (`preview-sidecar`) runs alongside the workspace on port **`8383`**. It mounts `/projects` read-only and provides instantaneous browser access to generated HTML artifacts (e.g. `teach-learning.html`, reports, mockups, or docs).

- **Browse projects directory**: [`http://localhost:8383/`](http://localhost:8383/)
- **View specific artifact**: `http://localhost:8383/<git-host>/<group>/<project>/path/to/file.html`

To add or modify forwarded ports, edit `ports` in `.devcontainer/docker-compose.yml` and `forwardPorts` in `.devcontainer/devcontainer.json`:

```yaml
ports:
  - "3000:3000"   # Node.js / React / Next.js
  - "5173:5173"   # Vite Dev Server
  - "8000:8000"   # Python / FastAPI / Django
  - "8080:8080"   # Vue / Spring Boot / HTTP
  - "8081:8081"   # Alternate Web / Metro
  - "8383:80"     # HTML Preview Sidecar (Nginx)
  - "9000:9000"   # Backend Service
```

---

## Storage and Volumes

| Mount Target | Source | Purpose |
| --- | --- | --- |
| `/projects` | `devws_projects` | Active Git working trees under `/projects/<git-host>/<group>/<project>` |
| `/home/vscode` | `devws_home` | Agent authentication, shell state, and package caches |
| `/var/run/docker.sock` | Docker Desktop host socket | Docker image builds and local sibling services |

---

## Included Toolchain

The image builds the `dev` Kotlin CLI, GraalVM JDK 21, Node.js 24, Python 3.14 through uv, Go 1.25, Rust 1.86, Flutter 3.41, Gradle 9.4, GitHub CLI, and the Codex, Claude, Antigravity, and Ollama CLIs. Ollama routes to `http://host.docker.internal:11434` through `OLLAMA_HOST`.
