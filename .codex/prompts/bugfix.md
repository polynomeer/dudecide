# Bugfix Task Template

Goal: identify the root cause of one bug and fix it as a single work unit.

## Required Context

- read `README.md`, `AGENTS.md`, and relevant docs under `docs/`
- capture the failing behavior, expected behavior, and reproducible conditions
- inspect nearby code paths before editing

## Execution Pattern

1. Reproduce or reason concretely about the failure mode.
2. Use a researcher subagent if more codebase mapping is needed.
3. Implement the minimal safe fix.
4. Add or update verification that protects against regression.
5. Update docs if the bug exposed a missing operating assumption.
6. Commit using Conventional Commits.

## Deliverable

- root cause
- files changed
- verification result
- residual risk
