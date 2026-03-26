#!/usr/bin/env bash

set -euo pipefail

WORKSPACE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STATE_DIR="$WORKSPACE_ROOT/state"
IMAGE_NAME="devws-polyglot"
LABEL_NS="devws"
WORKSPACES_VOLUME="devws_workspaces"
WORKSPACES_ROOT="/workspaces"

mkdir -p "$STATE_DIR"

die() {
  echo "error: $*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "missing required command: $1"
}

detect_docker_socket() {
  if [[ -n "${DOCKER_HOST:-}" && "${DOCKER_HOST}" == unix://* ]]; then
    echo "${DOCKER_HOST#unix://}"
    return
  fi

  local candidates=(
    "$HOME/.docker/run/docker.sock"
    "/var/run/docker.sock"
  )
  local path
  for path in "${candidates[@]}"; do
    if [[ -S "$path" ]]; then
      echo "$path"
      return
    fi
  done

  die "could not find a Docker socket; start Docker Desktop first"
}

normalize_gitlab_path() {
  local raw="$1"
  raw="${raw%.git}"
  if [[ "$raw" == ssh://git@* ]]; then
    raw="${raw#ssh://git@}"
    raw="${raw#*/}"
  elif [[ "$raw" == git@*:* ]]; then
    raw="${raw#*:}"
  elif [[ "$raw" == https://* ]] || [[ "$raw" == http://* ]]; then
    raw="${raw#https://}"
    raw="${raw#http://}"
    raw="${raw#*/}"
  fi
  raw="${raw#/}"
  raw="${raw%/}"
  [[ -n "$raw" ]] || die "could not derive GitLab path from input"
  echo "$raw"
}

checkout_rel_path_from_remote() {
  local raw="$1"
  local host=""
  local path=""

  raw="${raw%.git}"
  if [[ "$raw" == ssh://git@* ]]; then
    raw="${raw#ssh://git@}"
    host="${raw%%/*}"
    path="${raw#*/}"
  elif [[ "$raw" == git@*:* ]]; then
    host="${raw#git@}"
    host="${host%%:*}"
    path="${raw#*:}"
  elif [[ "$raw" == https://* ]] || [[ "$raw" == http://* ]]; then
    raw="${raw#https://}"
    raw="${raw#http://}"
    host="${raw%%/*}"
    path="${raw#*/}"
  else
    die "could not derive checkout path from input"
  fi

  path="${path#/}"
  path="${path%/}"
  [[ -n "$host" && -n "$path" ]] || die "could not derive checkout path from input"
  printf '%s/%s\n' "$host" "$path"
}

checkout_path_from_remote() {
  local rel
  rel="$(checkout_rel_path_from_remote "$1")"
  printf '%s/%s\n' "$WORKSPACES_ROOT" "$rel"
}

checkout_path_from_path() {
  local path="$1"
  printf '%s/gitlab.com/%s\n' "$WORKSPACES_ROOT" "$path"
}

checkout_path_from_ref() {
  local ref="$1"
  if [[ "$ref" == *:* ]] || [[ "$ref" == http*://* ]]; then
    checkout_path_from_remote "$ref"
  elif [[ "$ref" == */* ]]; then
    checkout_path_from_path "$ref"
  else
    local state_dir
    state_dir="$(workspace_state_dir_from_id "$ref")"
    cat "$state_dir/checkout_path.txt"
  fi
}

sanitize_component() {
  local raw="$1"
  raw="$(printf '%s' "$raw" | tr '[:upper:]' '[:lower:]')"
  raw="$(printf '%s' "$raw" | sed -E 's/[^a-z0-9._-]+/-/g; s/^-+//; s/-+$//; s/-+/-/g')"
  [[ -n "$raw" ]] || die "invalid empty workspace path component"
  echo "$raw"
}

workspace_id_from_path() {
  local path="$1"
  local out=""
  local component
  IFS='/' read -r -a parts <<< "$path"
  for component in "${parts[@]}"; do
    component="$(sanitize_component "$component")"
    if [[ -n "$out" ]]; then
      out="${out}--${component}"
    else
      out="$component"
    fi
  done
  [[ -n "$out" ]] || die "could not derive workspace id"
  echo "$out"
}

workspace_id_from_ref() {
  local ref="$1"
  if [[ "$ref" == */* ]] || [[ "$ref" == *:* ]] || [[ "$ref" == http*://* ]]; then
    workspace_id_from_path "$(normalize_gitlab_path "$ref")"
  else
    printf '%s\n' "$ref"
  fi
}

workspace_state_dir_from_path() {
  local path="$1"
  echo "$STATE_DIR/$path"
}

workspace_state_dir_from_id() {
  local id="$1"
  local found
  found="$(find "$STATE_DIR" -type f -name workspace_id.txt -print 2>/dev/null | while read -r file; do
    if [[ "$(cat "$file")" == "$id" ]]; then
      dirname "$file"
      break
    fi
  done)"
  [[ -n "$found" ]] || die "workspace metadata not found for id: $id"
  echo "$found"
}

workspace_state_dir_from_ref() {
  local ref="$1"
  if [[ "$ref" == */* ]] || [[ "$ref" == *:* ]] || [[ "$ref" == http*://* ]]; then
    workspace_state_dir_from_path "$(normalize_gitlab_path "$ref")"
  else
    workspace_state_dir_from_id "$ref"
  fi
}

ensure_docker() {
  require_cmd docker
  docker version >/dev/null 2>&1 || die "docker is not available; start Docker Desktop first"
}

ensure_git() {
  require_cmd git
}

SSH_MOUNT_ARGS=()

ssh_mount_args() {
  SSH_MOUNT_ARGS=()

  if [[ -d "$HOME/.ssh" ]]; then
    SSH_MOUNT_ARGS+=("-v" "$HOME/.ssh:/root/.ssh:ro")
  fi

  if [[ -n "${SSH_AUTH_SOCK:-}" && -S "${SSH_AUTH_SOCK}" ]]; then
    SSH_MOUNT_ARGS+=("-v" "${SSH_AUTH_SOCK}:/ssh-agent")
    SSH_MOUNT_ARGS+=("-e" "SSH_AUTH_SOCK=/ssh-agent")
  fi
}

print_attach_help() {
  local workspace_id="$1"
  local workspace_path="${2:-$1}"
  local checkout_path="${3:-/workspace}"
  cat <<EOF
workspace: $workspace_path
workspace id: $workspace_id
path in container: $checkout_path

Open in Zed:
- folder: $WORKSPACE_ROOT
- workspace path in container: $checkout_path
EOF
}
