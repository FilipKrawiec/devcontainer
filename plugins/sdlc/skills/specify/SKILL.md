---
name: specify
description: Use when refining, grilling, and detailing a Backlog GitHub Issue before delivery execution.
disable-model-invocation: true
allowed-tools: Skill Bash(dev:*,gh:*) Read
---

# Backlog Item Specification & Grilling

The **specify** skill takes an existing item on the **Backlog** and conducts interactive grilling sessions (`grill-with-context`) to refine its scope, clarify edge cases, validate acceptance criteria against repository context, and update the tracker item.

> [!IMPORTANT]
> **Execution Boundary Directive**: The `specify` skill updates tracker specifications (`dev issuetracker set-phase`, `dev issuetracker comment`). It supersedes host implementation planning mode. When `specify` or `/specify` is invoked, do NOT draft `implementation_plan.md`, generate technical code DAGs, or modify source code files.

## Operational Steps

1. **Select Target Backlog Item**:
   - Fetch target item details:
     ```bash
     dev issuetracker get <id> --json
     ```
   - Fallback if `dev` CLI is unavailable: `gh issue view <id>`.
   - Parse existing business outcomes, scope, non-goals, and constraints.

2. **Conduct Interactive Grilling Session**:
   - Read `grill-with-context` to challenge the specification against repository code, Central Knowledge items, and Project Knowledge overrides.
   - Ask progressive, one-at-a-time decision questions to resolve:
     - Architectural trade-offs and layer boundaries (Hexagonal / DDD).
     - Concrete acceptance criteria and edge cases.
     - Testing requirements (TDD contracts).

3. **Update Tracker Specification**:
   - Update the project board item phase to **`02 Spec`**:
     ```bash
     dev issuetracker set-phase <id> --phase 02-spec
     ```
   - Post summary comment detailing resolved decisions and acceptance criteria:
     ```bash
     dev issuetracker comment <id> --body "### Specification Refinement Summary\n<notes>"
     ```
   - Fallback if `dev` CLI is unavailable: use `gh project item-edit`, `gh issue edit`, and `gh issue comment`.

4. **Completion Boundary Guardrail**:
   - The `specify` skill MUST finish by updating the tracker item and its project board phase to **`02 Spec`** (or setting status to **Blocked** if unresolved blockers remain).
   - It MUST NOT proceed to drafting technical `implementation_plan.md` or executing code changes. `deliver` owns stage 3+ execution when the item is ready.

---

## Context Pointers

- Read `grill-with-context` when conducting source-backed specification challenges.
- Read [github-pipeline-integration.md](../../references/github-pipeline-integration.md) when executing tracker updates or configuring CLI options.
