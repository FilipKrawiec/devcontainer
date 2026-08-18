# Intent Capture and Tracker Mapping

This reference documents the intent capture schema, CLI commands, and Markdown templates used during Stage 1 (**DEFINE**) of delivery orchestration.

## Intent Capture Schema

When capturing an idea, collect the following structured information:

| Field | Description | Example |
| :--- | :--- | :--- |
| **Title** | Concise summary of intent | `Add /define command to plugins` |
| **Type** | Tracker classification label | `feature` \| `story` \| `task` \| `bug` |
| **Business Outcomes** | High-level goals and benefits | Enable starting delivery orchestration directly from raw ideas |
| **In-Scope** | Boundaries of work included in this slice | Skill definition, reference docs, validator updates, unit tests |
| **Non-Goals** | Excluded or deferred capabilities | Automated multi-project board sync scripts |
| **Constraints** | Technical or architectural requirements | Provider-neutral format, lowercase kebab-case naming |
| **Decision Owner** | Owner with trade-off authority | Product Owner / User |

## Tracker Item Payload Template

```markdown
## Business Outcomes
<What value or capability does this change provide?>

## In-Scope
- <Boundary item 1>
- <Boundary item 2>

## Non-Goals
- <Excluded item 1>
- <Excluded item 2>

## Constraints & Assumptions
- <Constraint 1>

## Decision Owner
- <Name / Role>
```

## CLI Invocation Examples

### Create Work Item via Dev CLI (Primary)
```bash
dev issuetracker create \
  --title "Add /define command for intent capture" \
  --type feature \
  --body-file .github/DEFINE_TEMPLATE.md \
  --json
```

### Fallback GitHub CLI Invocation
```bash
ISSUE_URL=$(gh issue create \
  --title "Add /define command for intent capture" \
  --label "type:feature" \
  --body-file .github/DEFINE_TEMPLATE.md)
gh project item-add <project-number> --owner <owner> --url "$ISSUE_URL"
```
