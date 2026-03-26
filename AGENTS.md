# AGENTS

This repository is a bootstrap workspace for Docker Desktop-based development on macOS.

## Primary Rule

Do not create or use host-side working trees for active repositories under `~/Developer`.

Project source code is expected to live in Docker named volumes. Host-side folders under `~/Developer/projects` are only lightweight devcontainer stubs for Zed.

## Workspace Model

- One Docker named volume holds the Git working tree at `/workspace`.
- One lightweight host-side stub directory per repository under `projects/<git-host>/<group>/<project>` contains `.devcontainer/devcontainer.json` for Zed.
- Project services such as Postgres or Redis run as sibling containers.
- Ollama runs on the macOS host and is reached from containers through `http://host.docker.internal:11434`.
- A shared devcontainer definition lives in this repository and is the baseline for bootstrap-repo editing.

## Agent Expectations

- Prefer editing files inside the repository workspace container, not on the macOS host.
- Treat the host `~/Developer` directory as control-plane infrastructure only, except for lightweight stub folders in `~/Developer/projects`.
- Prefer the single user-facing CLI at `bin/dev`; treat other scripts in `bin/` as internal helpers unless a lower-level entrypoint is needed.
- Do not broaden mounts beyond the repo volume unless explicitly requested.
- Do not move repository contents from Docker volumes onto the host as a convenience shortcut.

## Host Tooling Policy

- Host-local apps and base package-management setup may be bootstrapped from this repository when they are part of the intended workstation setup.
- `bin/bootstrap-macos-base` is the primary fresh-machine entrypoint after cloning this repository.
- `bin/bootstrap-macos-base` should ensure the machine has an SSH key ready for GitLab SSH remotes.
- The current host bootstrap installs Zed via Homebrew.
- Do not assume Zed registry agents are installed or configured by automation in this repository; install them manually through the ACP Registry UI.

## Toolchain Policy

- The default workspace image is a pinned polyglot image.
- Update tool versions only by editing the pinned versions in [templates/workspace/Dockerfile](/Users/filip/Developer/templates/workspace/Dockerfile).
- When changing versions, use official upstream release documentation and record the change in Git.
- Do not switch pinned versions to floating `latest` installers.

## Reproducibility Policy

- This repository should remain sufficient to rebuild the workspace model on a new machine.
- Runtime state under `state/` is local and disposable.
- Docker volumes are persistent workspace state unless the user explicitly asks to reset them.

## Human Files

- `AGENTS.md` is the primary machine-facing contract.
- `README.md` is the primary human-facing overview.
