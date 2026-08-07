# Codex Workflows

This document defines how to use advanced Codex capabilities in this repository.

## Core Principles

- treat one completed work unit as one commit
- keep durable context in the repository, not only in task history
- use subagents only when the task can be decomposed into clear, non-overlapping outputs

## Recommended Subagent Split

### Research

Use [`.agents/researcher.md`](../../.agents/researcher.md) when a task needs broad context gathering first.

Typical outputs:

- impacted files and docs
- assumptions and risks
- suggested implementation boundary

### Implementation

Use [`.agents/implementer.md`](../../.agents/implementer.md) when the task is ready for a scoped change.

Typical outputs:

- code or docs change set
- local verification result
- known follow-up risks

### Review

Use [`.agents/reviewer.md`](../../.agents/reviewer.md) after implementation or in parallel when a separate validation pass is valuable.

Typical outputs:

- defects or regression risks
- missing tests or docs
- release-readiness concerns

## Task Kickoff Templates

Reusable templates live in [`.codex/prompts/`](../../.codex/prompts/).

- `feature.md`: new capability work
- `bugfix.md`: defect investigation and repair
- `review.md`: code review or QA-oriented work

## Handoff Rules

- each subagent should receive one explicit goal
- each subagent should report in a compact, structured format
- the main agent is responsible for integrating, editing, verifying, and committing
- durable findings should be written back to `docs/` when they matter for future tasks

## Commit Discipline

- use Conventional Commits from [`CONTRIBUTING.md`](../../CONTRIBUTING.md)
- create a commit after each finished work unit
- avoid batching research-only doc cleanup with unrelated implementation changes

## When To Add An ADR

Create an ADR in `docs/adr/` when a Codex task changes:

- project structure
- framework or infrastructure direction
- long-lived workflow expectations
- core domain boundaries
