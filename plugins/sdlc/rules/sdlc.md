# Antigravity Native SDLC Rules

Enhance the provider-neutral `deliver` workflow with Antigravity native IDE harness capabilities (interactive UI artifacts, subagent persona delegation via `invoke_subagent`, autonomous self-correction loops, and interactive merge approval).

---

## 1. Operating Modes & Direct Execution Efficiency

To prevent ceremonial overhead and duplicated steps, distinguish execution mode based on the user's intent:

- **Direct Execution Mode (Default)**:
  - When the user asks for a code change, bugfix, refactoring, or feature directly in chat, execute it directly in place using focused TDD and proportionate verification.
  - Do NOT trigger GitHub issue/tracker creation, worktree creation, subagent persona dispatch cascades, or PR ceremonies.
  - Do NOT generate speculative `implementation_plan.md` artifacts or pause execution on Proceed buttons for direct requests.
- **Orchestrated Delivery Mode (`deliver` skill)**:
  - Apply the multi-stage persona delegation, worktree isolation, and GitHub tracker synchronization below ONLY when the user explicitly invokes `/deliver` or when coordinating complex multi-slice, multi-repository epics.

---

## 2. Project Configuration & GitHub Prerequisites (Orchestrated Mode)

1. Verify GitHub CLI authentication via `gh auth status` (scopes: `repo`, `read:org`, `project`).
2. Inspect target repository root for `.agy/config.json` overrides:
   ```json
   {
     "orchestration": {
       "enforce_hexagonal": true,
       "enforce_ddd": true,
       "require_security_audit": true,
       "max_autonomous_retries": 3,
       "github_projects_enabled": true
     }
   }
   ```
   Apply safe defaults if unconfigured (`enforce_hexagonal: true`, `enforce_ddd: true`, `require_security_audit: true`, `max_autonomous_retries: 3`, `github_projects_enabled: true`).

---

## 3. Interactive Artifact & Planning Protocol

- **Adaptive Planning Gate**: Generate technical `implementation_plan.md` with `RequestFeedback: true` and `UserFacing: true` ONLY for multi-slice architectural initiatives or when the user explicitly requests a plan first. Antigravity renders the native IDE **Proceed** button. Wait for user approval before dispatching slices.
- **Direct Requests Exempt**: Never block direct user change requests with planning artifacts or Proceed gates.
- **Progress Tracking**: Record execution and review evidence continuously in `walkthrough.md` (`UserFacing: true`).

---

## 4. Subagent Persona Delegation & Verification Deduplication

When executing in Orchestrated Mode:

1. **Selective Persona Dispatch**: Dispatch only personas relevant to the modified path boundaries rather than all personas unconditionally:
   - `developer`: Dispatched for code implementation inside a Git worktree.
   - `quality-engineer`: Dispatched when deterministic verification or assertion depth requires independent audit.
   - `solution-architect`: Dispatched ONLY when domain pure logic, layer boundaries, or public ports are altered.
   - `security-auditor`: Dispatched ONLY when auth, crypto, dependencies, or external input handling are touched.
   - `orchestration-reviewer`: Dispatched for final multi-slice synthesis.

2. **Verification Deduplication Invariant**:
   - Full repository verification must run at most ONCE per slice outcome.
   - Never run full verification suites before starting code changes unless reproducing a defect baseline.
   - If the `developer` or `quality-engineer` persona has already executed the deterministic verification gate with exit code 0 on the active commit SHA, reuse that evidence. The orchestrator and subsequent reviewers must NOT re-execute identical full verification suites.

---

## 5. Autonomous Self-Correction Loop

When a verifier or reviewer persona rejects a slice (`VERIFICATION_FAILED`, `CORRECT_EXECUTE`, `CORRECT_PLAN`, or `SECURITY_VULNERABILITY_FOUND`):
1. Increment slice `attempt_count`.
2. If `attempt_count <= max_autonomous_retries` (default 3), re-dispatch the `developer` subagent with the exact failure payload. Do not prompt the user during intermediate retries.
3. Update `walkthrough.md` with retry diagnostics.
4. If retries are exhausted, stop and escalate failure logs to the Product Owner.

---

## 6. Interactive PR Review & Merge Guardrail

1. Create Pull Request (`dev forge pr create` or `gh pr create`) linking `Closes #<id>`.
2. Submit persona PR review comments (`dev forge pr review <pr-number>` or `gh pr review`) with `walkthrough.md` evidence.
3. Present native IDE `ReviewRequest` with `RequestFeedback: true` linking PR and Issue.
4. **Merge Guardrail**: Executors must NEVER merge, approve, or force-push protected default branches (`main`). Present the interactive merge prompt for user authorization (`dev forge pr merge <pr-number> --strategy squash --delete-branch`). Upon merge, clean up local worktrees (`git worktree remove`) and prune remote tracking branches (`git remote prune origin`).

