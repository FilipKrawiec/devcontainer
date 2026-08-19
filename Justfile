# Justfile for Master Dev Workspace
# https://github.com/casey/just

# Default recipe: list available commands
default:
    @just --list

# -----------------------------------------------------------------------------
# Toolchain Maintenance
# -----------------------------------------------------------------------------

# Check upstream releases and automatically update Dockerfile, AGENTS.md, and README.md
update-tools:
    ./scripts/update-toolchain.py

# Dry-run check for upstream toolchain updates (exits 1 if updates found)
check-tools:
    ./scripts/update-toolchain.py --check

# Output toolchain versions and update status in JSON format
status-tools:
    ./scripts/update-toolchain.py --json

# -----------------------------------------------------------------------------
# Host Setup & Dev Container Lifecycle
# -----------------------------------------------------------------------------

# Install the official Dev Container CLI (@devcontainers/cli) on the host
install-devcontainer:
    npm install -g @devcontainers/cli

# Build the Master Dev Container image locally
build:
    devcontainer build --workspace-folder .

# Start the Master Dev Container environment and sidecars
up:
    devcontainer up --workspace-folder .

# Run an interactive or one-off command inside the Master Dev Container
exec +cmd:
    devcontainer exec --workspace-folder . {{cmd}}

# Stop all Dev Container services and sidecars
down:
    docker compose -f .devcontainer/docker-compose.yml down

# Rebuild and restart the Dev Container environment
rebuild:
    devcontainer build --workspace-folder . --no-cache
    devcontainer up --workspace-folder .

# -----------------------------------------------------------------------------
# Kotlin Clikt CLI (`dev`)
# -----------------------------------------------------------------------------

# Build the Kotlin dev-cli distribution
build-cli:
    cd .devcontainer/cli && gradle installDist

# Run unit tests for dev-cli
test-cli:
    cd .devcontainer/cli && gradle test

# -----------------------------------------------------------------------------
# Sidecar & Previews
# -----------------------------------------------------------------------------

# Check status of the Nginx HTML preview sidecar
preview-status:
    @docker ps -f name=master_dev_preview_sidecar

# View logs from the Nginx HTML preview sidecar
preview-logs:
    docker logs master_dev_preview_sidecar -f

# -----------------------------------------------------------------------------
# SDLC Plugin & Multi-Harness Management
# -----------------------------------------------------------------------------

# Install SDLC plugin into local Antigravity IDE configuration
install-plugins:
    #!/usr/bin/env bash
    set -euo pipefail
    target_dir="${AGY_IDE_PLUGIN_DIR:-"$HOME/.gemini/config/plugins"}"
    mkdir -p "${target_dir}"
    rm -rf "${target_dir}/filipkrawiec-agy-sdlc"
    for dir in plugins/*; do
      [ -d "$dir" ] || continue
      pkg="filipkrawiec-$(basename "$dir")"
      rm -rf "${target_dir}/${pkg}"
      cp -r "$dir" "${target_dir}/${pkg}"
    done
    echo "Installed SDLC plugin into ${target_dir}"

# Symlink SDLC and core plugins into local Antigravity IDE configuration (dev mode)
link-plugins:
    #!/usr/bin/env bash
    set -euo pipefail
    repo_root="$(pwd -P)"
    target_dir="${AGY_IDE_PLUGIN_DIR:-"$HOME/.gemini/config/plugins"}"
    mkdir -p "${target_dir}"
    rm -rf "${target_dir}/filipkrawiec-agy-sdlc"
    for dir in plugins/*; do
      [ -d "$dir" ] || continue
      pkg="filipkrawiec-$(basename "$dir")"
      rm -rf "${target_dir}/${pkg}"
      ln -s "${repo_root}/${dir}" "${target_dir}/${pkg}"
    done
    skills_root="/projects/github.com/FilipKrawiec/skills"
    if [ -d "${skills_root}/plugins/common" ]; then
      for dir in "${skills_root}/plugins/common/"*; do
        [ -d "$dir" ] || continue
        pkg="filipkrawiec-$(basename "$dir")"
        rm -rf "${target_dir}/${pkg}"
        ln -s "$dir" "${target_dir}/${pkg}"
      done
    fi
    if [ -d "${skills_root}/plugins/agy/core" ]; then
      rm -rf "${target_dir}/filipkrawiec-agy-core"
      ln -s "${skills_root}/plugins/agy/core" "${target_dir}/filipkrawiec-agy-core"
    fi
    echo "Linked workspace plugins into ${target_dir}"

# Refresh local plugin installations across harnesses (Antigravity, Codex, Claude)
refresh-plugins: install-plugins
    #!/usr/bin/env bash
    if command -v codex >/dev/null 2>&1; then
      for dir in plugins/*; do
        [ -d "$dir" ] || continue
        pkg="filipkrawiec-$(basename "$dir")"
        codex plugin remove "${pkg}@filipkrawiec" >/dev/null 2>&1 || true
        codex plugin add "${pkg}@filipkrawiec" >/dev/null 2>&1 || true
      done
    fi
    if command -v claude >/dev/null 2>&1; then
      for dir in plugins/*; do
        [ -d "$dir" ] || continue
        pkg="filipkrawiec-$(basename "$dir")"
        claude plugin remove "${pkg}@filipkrawiec" >/dev/null 2>&1 || true
        claude plugin add "${pkg}@filipkrawiec" >/dev/null 2>&1 || true
        claude plugin update "${pkg}@filipkrawiec" >/dev/null 2>&1 || true
      done
    fi
    echo "Refreshed plugin installations across all available agent harnesses."



