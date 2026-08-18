---
name: improve
description: Use when capturing workflow friction, debugging pain points, or agent mistakes in any project to generate sanitized, actionable improvements or report issues upstream to FilipKrawiec/skills.
allowed-tools: Bash(dev:*,gh:*,git:*) Read Edit
---

# Retrospective Learning & Friction Reporting

The **improve** skill captures runtime friction, tool failures, missing rules, developer corrections, and `devcli` capability gaps in downstream consumer projects, transforming them into sanitized, actionable improvements and upstream issues on `FilipKrawiec/skills` or `FilipKrawiec/devcontainer`.

## Operational Workflow

1. **Trigger & Context Extraction**:
   - **Stage 7 Retrospective**: Automatically invoke during delivery orchestration (`07 Improve`) to reflect on retries, test failures, ambiguous guidance, or instances where `devcli` lacked capability and required a fallback to `gh`.
   - **CLI Gap Auditing**: Explicitly audit if any raw `gh` commands were executed during delivery due to missing `dev forge` or `dev issuetracker` subcommands/flags. If a fallback occurred, queue a `cli-capability-gap` improvement for `devcli`.
   - **On-Demand Friction**: Invoke via `/improve` or `/learn` whenever an unexpected hurdle, tool exception, or manual prompt correction occurs.

2. **Structure 5-Point Root Cause Diagnostic**:
   - Extract the 5 core diagnostic fields:
     - **Target Skill / Tool**: Name of the affected skill, persona, or CLI tool (`devcli`).
     - **Failure Symptom / User Correction**: Concrete failure, retry loop, error message, manual prompt correction, or `gh` fallback command used.
     - **Root Cause Analysis**: Why the skill, rule, or CLI tool was insufficient.
     - **Proposed Remediation / Diff**: Suggested guideline, prompt tweak, verification rule, or `devcli` subcommand/flag extension.
     - **Sanitized Reproduction**: Minimal, reproducible trace or example.

3. **Automated Privacy Scrubbing**:
   - Redact all sensitive identifiers: API keys, tokens, internal URLs, company names, and local machine file paths.
   - Verify zero proprietary code or secrets remain in the diagnostic payload.

4. **Interactive Preview & Upstream Dispatch**:
   - Render the formatted diagnostic payload to the user in an interactive preview modal.
   - Upon explicit user confirmation, submit the issue upstream:
     - For skills/rules:
       ```bash
       gh issue create -R FilipKrawiec/skills --title "friction(<skill>): <summary>" --body-file <payload> --label "type:feature,friction"
       ```
       Add the created issue to Project 4 (`Agentic Workflow`) in phase **`01 Define`**.
     - For `devcli` capability gaps (`dev forge` / `dev issuetracker`):
       ```bash
       gh issue create -R FilipKrawiec/devcontainer --title "feat(cli): add <missing command/flag> to devcli" --body-file <payload> --label "type:feature"
       ```

5. **Offline & Fallback Logging**:
   - If GitHub CLI is unauthenticated or network is unavailable, write the structured report to `.agents/friction/friction-<timestamp>.md` and provide manual export instructions.

---

## Context Pointers

- Read [friction-taxonomy.md](references/friction-taxonomy.md) when classifying friction categories, applying privacy scrubbing rules, or structuring issue templates.
