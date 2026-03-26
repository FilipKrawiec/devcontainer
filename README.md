# Developer Workspace

This repository bootstraps a Docker Desktop-based development setup on macOS.

## Start

On a new Mac:

```sh
git clone <this-repo> ~/Developer
cd ~/Developer
./dev bootstrap
./dev rebuild
```

This installs the host tools managed by this repo and builds the default workspace image.

To work on this bootstrap repo itself in Zed Dev Containers, open `~/Developer`.

## Workspaces

Create a volume-backed workspace stub for a GitLab repository:

```sh
./dev prepare git@gitlab.com:group/project.git
```

Open an existing workspace again:

```sh
./dev prepare group/project
```

List workspaces:

```sh
./dev list
```

Reset a workspace:

```sh
./dev reset group/project
```

## Notes

- `./dev bootstrap` installs Xcode Command Line Tools if needed, installs Homebrew if missing, ensures an SSH key exists, then installs Zed and Ollama.
- After bootstrap, add the printed SSH public key to GitLab before using `./dev prepare` with SSH remotes.
- To use Codex and Gemini in Zed, add them manually from Zed's ACP Registry UI.
- This repository includes a shared [`.devcontainer/devcontainer.json`](/Users/filip/Developer/.devcontainer/devcontainer.json) for the bootstrap repo.
- Workspace stubs live under `projects/<git-host>/<group>/<project>`, but the actual repository stays in a Docker named volume.
- The repo path inside the dev container is `/workspace/<git-host>/<group>/<project>`.
- Example: `git@gitlab.com:harborly.org/platform.git` creates [projects/gitlab.com/harborly.org/platform](/Users/filip/Developer/projects/gitlab.com/harborly.org/platform) and opens `/workspace/gitlab.com/harborly.org/platform` from the named volume.
- GitLab paths are preserved in metadata under `state/group/.../project`, while Docker-safe workspace ids use `--`, for example `group/project` -> `group--project`.
- In Zed, open the stub folder and let Zed open the dev container from its `.devcontainer/devcontainer.json`.
- The dev container can reach host Ollama at `http://host.docker.internal:11434`.
- `./dev open` and `./dev rebuild` still exist for maintenance, but the normal flow is `bootstrap`, `prepare`, `list`, `reset`.
- Ollama models are not pulled automatically.
- Suggested starting models:
  - `ollama pull qwen3-coder:30b`
  - `ollama pull gemma3:4b`
- Other common starting points:
  - `ollama pull deepseek-r1`
  - `ollama pull llama3.3`
  - `ollama pull mistral-small`
