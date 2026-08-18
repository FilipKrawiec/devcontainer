# Friction Taxonomy & Upstream Reporting Reference

This reference details friction classification categories, data sanitization protocols, and upstream issue templates for the `improve` skill.

---

## 1. Friction Classification Categories

| Category | Definition & Trigger Scenario | Target Remediation |
| :--- | :--- | :--- |
| `cli-capability-gap` | `devcli` (`dev issuetracker` or `dev forge`) lacked a needed subcommand, flag, or query capability, requiring an agent fallback to raw `gh`. | Extend `devcli` in `FilipKrawiec/devcontainer` with the missing command/options to eliminate raw CLI fallbacks. |
| `skill-guidance` | Skill instructions were vague, misleading, missing a required step, or lacked clear context pointers. | Clarify `SKILL.md` steps, add concrete guardrails, or split subtopics into `references/`. |
| `tooling-error` | Tool invocation syntax failed, parameter schemas were violated, or CLI wrapper failed unexpectedly. | Improve tool documentation, add retry/resilience instructions, or refine parameter typing. |
| `workflow-friction` | Delivery phase handoff, worktree isolation, branching, or project board synchronization encountered friction. | Adjust lifecycle stage definitions in `deliver` or `github-pipeline-integration.md`. |
| `testing-gap` | TDD assertions, mock boundaries, or verification gates failed to catch a regression or were over-restrictive. | Refine test templates, enhance assertion guidelines, or adjust verification scripts. |
| `boundary-violation` | Agent breached persona boundaries (e.g. solution architect generating micro-tasks) or overengineered output. | Strengthen negative constraints and anti-overengineering guardrails in `agent-personas.md`. |

---

## 2. Privacy Scrubbing Checklist

Before presenting any friction diagnostic to the user or transmitting upstream, verify and redact:

- [ ] **API Keys & Credentials**: Redact GitHub tokens (`gho_*`, `ghp_*`), OpenAI/Anthropic API keys (`sk-*`), AWS/GCP secrets.
- [ ] **Internal Domains & URLs**: Replace internal hostnames (e.g., `*.corp.internal`, `*.corp.company.com`) with `example.com` or placeholder tags (`<internal-host>`).
- [ ] **Proprietary Identifiers & Company Names**: Anonymize organization names, client names, project names, and internal product codenames.
- [ ] **Local Machine Paths**: Strip absolute workstation paths (e.g., `/Users/username/...`, `/home/vscode/...`) and replace with relative project paths (e.g., `plugins/...`, `src/...`).
- [ ] **Customer & PII Data**: Ensure zero customer emails, usernames, database records, or production logs are present.

---

## 3. Sanitized Issue Template

When submitting an upstream friction report, use the following structured body:

```markdown
## Friction Report

### 1. Target Skill / Tool
- **Component**: `plugins/common/sdlc/skills/<skill-name>` | `devcli (<forge|issuetracker>)`
- **Category**: `cli-capability-gap` | `skill-guidance` | `tooling-error` | `workflow-friction` | `testing-gap` | `boundary-violation`

### 2. Failure Symptom / User Correction
- **Observed Behavior**: <Describe what failed, where the agent got stuck, or what raw fallback was needed>
- **Human Correction**: <Describe the manual prompt or correction given by the user>

### 3. Root Cause Analysis
- **Underlying Gap**: <Explain why the skill instruction, rule, or CLI capability was insufficient>

### 4. Proposed Remediation / Diff
- **Suggested Change**:
\`\`\`diff
- <existing rule, instruction, or missing CLI capability>
+ <proposed improved rule, instruction, or devcli feature>
\`\`\`

### 5. Sanitized Reproduction
- **Minimal Reproduction Trace**:
\`\`\`text
<Anonymized error snippet, command trace, or prompt sequence>
\`\`\`
```

---

## 4. Upstream CLI & Board Integration

```bash
# Option A: Create skills friction issue on FilipKrawiec/skills
ISSUE_URL=$(gh issue create -R FilipKrawiec/skills \
  --title "friction(<skill>): <concise summary>" \
  --body "<sanitized-issue-template>" \
  --label "type:feature,friction")

# Option B: Create CLI feature request on FilipKrawiec/devcontainer for missing devcli capability
DEVCLI_ISSUE_URL=$(gh issue create -R FilipKrawiec/devcontainer \
  --title "feat(cli): add <missing command/flag> to devcli" \
  --body "<sanitized-issue-template>" \
  --label "type:feature")
```
