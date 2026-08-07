# ADR 0001: Codex Operations Structure

## Status

Accepted

## Date

2026-08-07

## Context

The repository is being prepared for sustained Codex-driven work, including reusable prompts, subagent task splits, and consistent commit boundaries. Without repository-local guidance, operational knowledge stays in conversation history and becomes inconsistent across tasks.

## Decision

The repository will store Codex operating assets in three places:

- `AGENTS.md` for repository-wide rules
- `.agents/` for reusable subagent role definitions
- `.codex/prompts/` for repeatable task kickoff templates

Long-lived process guidance will be documented under `docs/engineering/`, and work-unit commits will follow Conventional Commits.

## Consequences

- future Codex tasks can start from repository context instead of rebuilding instructions from scratch
- subagent usage becomes more predictable and easier to review
- process changes should now update repository docs instead of remaining only in chat history
