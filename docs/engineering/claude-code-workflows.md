# Claude Code Workflows

This document defines how Claude Code operates in this repository. It complements [`codex-workflows.md`](./codex-workflows.md); both tools share the same rules in [`AGENTS.md`](../../AGENTS.md).

## Entry Point

- [`CLAUDE.md`](../../CLAUDE.md) is loaded automatically and imports `AGENTS.md`.
- It adds Claude-specific rules, repository layout, commands, and verification expectations.

## Operating Rules

- Respond to users in Korean; keep code, commit messages, and repository docs in English.
- Commit after every completed work unit using the message style in [`CONTRIBUTING.md`](../../CONTRIBUTING.md).
- Stage explicit paths only, so unrelated working-tree changes are never swept into a commit.

## Configuration Files

- [`.claude/settings.json`](../../.claude/settings.json): shared permissions (common git, npm, Gradle commands allowed; force push, hard reset, and `.env` reads denied).
- `.claude/settings.local.json`: personal overrides, git-ignored.
- [`.claude/agents/`](../../.claude/agents/): `researcher`, `implementer`, `reviewer` subagents. Each delegates to its role definition in [`.agents/`](../../.agents/README.md), so role changes are made in one place.
- [`.claude/commands/`](../../.claude/commands/): `/feature`, `/bugfix`, `/review-change` slash commands. Each delegates to the matching template in [`.codex/prompts/`](../../.codex/README.md).

## Keeping Tools In Sync

- Change shared rules in `AGENTS.md`, role definitions in `.agents/`, and task templates in `.codex/prompts/`.
- Change `CLAUDE.md` or `.claude/` only for Claude-specific behavior.
