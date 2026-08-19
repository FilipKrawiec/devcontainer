---
name: init-project
description: Use when initializing or scaffolding a new Trunk-Based Development monorepository with justfile, AGENTS.md rules, and clean components/deploy layout.
disable-model-invocation: true
allowed-tools: Read Edit Bash
---

# Init Project

Use this skill to initialize a new monorepository for Trunk-Based Development. It uses the `dev` CLI to generate a clean, ultralight baseline without heavy boilerplate.

## Steps

1. Run `dev projects init <owner/repo>` (or `dev init <owner/repo>`) to scaffold the project structure:
   ```bash
   dev projects init <owner/repo> -d "Project description"
   ```
2. Verify the generated baseline files:
   - `components/.gitkeep`: Directory anchor for application components and bounded contexts.
   - `deploy/.gitkeep`: Directory anchor for deployment infrastructure (added on-demand per component).
   - `justfile`: Universal task runner with `default`, `unit`, and `verify` recipes.
   - `AGENTS.md`: Frontmatter and rules for AI coding assistants.
   - `README.md`: Human-facing project overview and quick start.
   - `.gitignore`: Standard baseline ignore patterns.
3. Define application components on-demand inside `components/<name>/`:
   - Services and libraries define their own `justfile` and inner-loop targets (`just unit`).
4. Verify the scaffolded environment by running `just verify`.

## Context Pointers

- Read [monorepo-structure.md](references/monorepo-structure.md) when setting up component boundaries, shared libraries (`shared/`), or deployment manifests.

