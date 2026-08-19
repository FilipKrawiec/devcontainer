# Monorepository Structure Reference

This reference details the folder structure and design rules for Trunk-Based Development monorepos.

## Layout

```text
my-monorepo/
├── .gitignore                          # Standard ignore rules
├── AGENTS.md                           # Monorepo architecture invariants & frontmatter
├── README.md                           # Project introduction & getting started
├── justfile                            # Root recipe book (unit, verify)
│
├── components/                         # Self-contained Bounded Contexts
│   ├── .gitkeep
│   ├── auth-service/                   # Service component (created on demand)
│   │   ├── src/
│   │   ├── justfile                    # Inner-loop recipe (just unit)
│   │   └── .deploy/helm/               # Co-located Helm chart (if deployed via Helm)
│   └── portal/                         # UI / Monolith component (created on demand)
│       ├── src/
│       ├── justfile
│       └── .deploy/ansible/            # Co-located Ansible playbook (if deployed via Ansible)
│
├── deploy/                             # Deployment configurations
│   └── .gitkeep                        # Clean placeholder (manifests added on demand)
│
├── docs/                               # Project Documentation (created on demand)
│   ├── context.md                      # Domain context & ubiquitous language
│   ├── glossary.md                     # Vocabulary definitions
│   └── adr/                            # Architecture Decision Records
│
└── shared/                             # Local shared libraries (created on demand)
    └── domain-common/
```

## Rules
1. **Ultralight Initialization**: Initialize projects via `dev projects init <owner/repo>` to get the clean baseline.
2. **Co-located Component Deployment**: When a component requires deployments, place Helm charts in `components/<service>/.deploy/helm` or Ansible playbooks in `components/<name>/.deploy/ansible`.
3. **Task Runner Parity**: Each component in `components/<name>/` implements a `justfile` with a `unit` recipe. The root `justfile` aggregates verification across all components.

