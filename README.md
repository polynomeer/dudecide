# Dudecide

`Dudecide` repository baseline for product, engineering, and agent-friendly documentation.

## Documentation Map

- [`AGENTS.md`](./AGENTS.md): rules for Codex and other coding agents working in this repository
- [`CLAUDE.md`](./CLAUDE.md): Claude Code entry point; imports `AGENTS.md` and adds commands and verification steps
- [`.claude/`](./.claude/): Claude Code settings, subagents, and slash commands
- [`CONTRIBUTING.md`](./CONTRIBUTING.md): contribution workflow and commit convention
- [`.agents/README.md`](./.agents/README.md): subagent role definitions for parallel Codex work
- [`.codex/README.md`](./.codex/README.md): reusable Codex prompt assets and task templates
- [`docs/README.md`](./docs/README.md): documentation index
- [`docs/product/`](./docs/product/): product context and planning materials
- [`docs/engineering/`](./docs/engineering/): implementation notes, setup, and operational docs
- [`docs/adr/`](./docs/adr/): architecture decision records

## Working Principles

- Keep long-lived context in `docs/` instead of chat history.
- Record implementation decisions in `docs/adr/` when they affect future work.
- Use `.agents/` when splitting work across subagents.
- Reuse `.codex/prompts/` templates for repeatable task kickoff.
- Commit by work unit using Conventional Commits.

## Current Product Source

- [`docs/product/dudecide_planning_document_v1.pdf`](./docs/product/dudecide_planning_document_v1.pdf)
