# Review Task Template

Goal: review a change for correctness, regressions, and missing validation.

## Required Context

- read `AGENTS.md` and the relevant docs
- inspect the diff or changed files
- understand intended behavior before judging the implementation

## Execution Pattern

1. Focus findings on bugs, regressions, and missing tests or docs.
2. Use a reviewer subagent if the main task also requires implementation work.
3. Cite exact files and lines when available.
4. Keep the summary secondary to the findings.

## Deliverable

- ordered findings
- open questions
- residual risks if no blocking issues are found
