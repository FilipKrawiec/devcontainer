# Tracker and Forge Pipeline Integration Reference

This reference documents the integration of `dev issuetracker`, `dev forge`, and fallback `gh` commands into the `deliver` workflow for backlog tracking, delivery phases, pull requests, reviews, and merges.

---

## 1. Toolchain Hierarchy & Fallback Policy

1. **Primary Toolchain (`devcli`)**:
   - Work item creation, delivery phases, and comments: `dev issuetracker`.
   - Branching, PRs, reviews, and merges: `dev forge`.
2. **Fallback Toolchain (`gh`)**:
   - If a specific capability or flag is unsupported in `devcli` (e.g. specialized board filtering or repository cloning), fall back to `gh`.
3. **Continuous CLI Improvement Directive**:
   - Whenever a missing capability forces an agent to fallback to `gh`, the agent MUST note the gap and flag it during Stage 7 (**IMPROVE**) as a `cli-capability-gap` to file an upstream issue on `FilipKrawiec/devcontainer`.

---

## 2. Delivery Phase Command Mapping

### Stage 1: DEFINE (Backlog Item Creation)
- **Phase**: `01 Define` / `Backlog`
- **Primary Command**:
  ```bash
  dev issuetracker create --title "<type>(<scope>): <summary>" --body "<payload>" --type <feature|story|task|bug> --json
  ```
- **Fallback (`gh`)**:
  ```bash
  ISSUE_URL=$(gh issue create --title "<title>" --body "<body>" --label "type:<type>")
  ITEM_ID=$(gh project item-add <project-number> --owner <owner> --url "$ISSUE_URL" --format json --jq .id)
  gh project item-edit --id "$ITEM_ID" --project-id <project-id> --field-id <field-id> --single-select-option-id <define-id>
  ```

### Stage 2: SPECIFY / GRILL (Specification Refinement)
- **Phase**: `02 Spec`
- **Primary Commands**:
  ```bash
  # 1. Fetch item details
  dev issuetracker get <id> --json

  # 2. Advance phase to Spec
  dev issuetracker set-phase <id> --phase 02-spec

  # 3. Post refinement notes
  dev issuetracker comment <id> --body "### Specification Refinement Summary\n<notes>"
  ```
- **Fallback (`gh`)**:
  ```bash
  gh project item-edit --id <item-id> --project-id <project-id> --field-id <status-field-id> --single-select-option-id <spec-id>
  gh issue comment <issue-number> --body "### Specification Refinement Summary\n<notes>"
  ```

### Stage 3: PLAN (Technical Planning & DAG Construction)
- **Phase**: `03 Plan`
- **Primary Command**:
  ```bash
  dev issuetracker set-phase <id> --phase 03-plan
  ```
- **Fallback (`gh`)**:
  ```bash
  gh project item-edit --id <item-id> --project-id <project-id> --field-id <status-field-id> --single-select-option-id <plan-id>
  ```

### Stage 4: DISPATCH / EXECUTE (Branching & Worktree Execution)
- **Phase**: `04 Execute` / `In Progress`
- **Primary Commands**:
  ```bash
  # 1. Advance phase to Execute
  dev issuetracker set-phase <id> --phase 04-execute

  # 2. Create isolated task branch
  dev forge branch create <branch-name> --base main
  ```
- **Fallback (`gh` / `git`)**:
  ```bash
  gh project item-edit --id <item-id> --project-id <project-id> --field-id <status-field-id> --single-select-option-id <execute-id>
  git checkout -b <branch-name> origin/main
  ```

### Stage 5: COLLECT / VERIFY (Deterministic Gate Validation)
- **Phase**: `04 Execute` (retained during test and verification loops)
- **Command**:
  ```bash
  just verify # or configured project verifier in AGENTS.md
  ```

### Stage 6: REVIEW (Pull Request & Multi-Persona Audits)
- **Phase**: `05 Review`
- **Primary Commands**:
  ```bash
  # 1. Advance phase to Review
  dev issuetracker set-phase <id> --phase 05-review

  # 2. Open Pull Request
  dev forge pr create --title "<type>(<scope>): <summary>" --body "Closes #<id>\n\n<summary>" --head <branch-name> --base main --json

  # 3. Submit formal review verdicts
  dev forge pr review <pr-number> --verdict approve --body "### Quality Engineer Review\n- TDD Assertion Strength: PASSED"
  ```
- **Fallback (`gh`)**:
  ```bash
  gh pr create --title "<title>" --body "Closes #<id>\n\n<summary>" --head <branch> --base main
  gh pr review <pr-number> --comment --body "### Quality Engineer Review\n- TDD: PASSED"
  ```

### Stage 7: SHIP / RETURN (Merge Authorization, Improve & Done)
- **Phase**: `06 Ship` -> `07 Improve` -> `Done`
- **Primary Commands**:
  ```bash
  # 1. Set status to Ship
  dev issuetracker set-phase <id> --phase 06-ship

  # 2. Merge upon user authorization
  dev forge pr merge <pr-number> --strategy squash --delete-branch

  # 3. Set status to Improve for retrospective learning
  dev issuetracker set-phase <id> --phase 07-improve

  # 4. Audit fallback CLI usage: If raw gh was used due to missing devcli features, trigger improve to file issue
  # gh issue create -R FilipKrawiec/devcontainer --title "feat(cli): add <capability> to devcli" ...
  ```
- **Fallback (`gh`)**:
  ```bash
  gh project item-edit --id <item-id> --project-id <project-id> --field-id <status-field-id> --single-select-option-id <ship-id>
  gh pr merge <pr-number> --squash --delete-branch
  gh project item-archive <project-number> --owner <owner> --id <item-id>
  ```

---

## 3. Standard Labeling Schema

All items managed by the sdlc plugin use a unified type classification:
- **Work Item Types**: `type:feature`, `type:story`, `type:task`, `type:bug` (mapped in `dev issuetracker` as `feature`, `story`, `task`, `bug`)
- **Orchestration Marker**: `orchestrated`
