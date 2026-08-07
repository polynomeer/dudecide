# AGENTS.md

This repository is prepared for Codex-driven development. Follow these rules before making changes.

## Source of Truth

- Read [`README.md`](./README.md) and [`docs/README.md`](./docs/README.md) first.
- Use `docs/product/` for product context, `docs/engineering/` for implementation context, and `docs/adr/` for decisions.
- If important context only exists in conversation, promote it into repository docs during the same task when practical.

## Working Agreement

- Prefer small, reversible changes.
- Keep each task scoped to a single work unit when possible.
- After each completed work unit, create a commit instead of batching unrelated changes together.
- Do not rewrite or revert user changes unless explicitly requested.
- When using subagents, assign one clear objective per subagent and merge results back into repository docs or code.

## Commit Policy

- Use Conventional Commits as described in [`CONTRIBUTING.md`](./CONTRIBUTING.md).
- Default format: `<type>(<scope>): <summary>`
- Examples:
  - `docs(repo): add codex-friendly repository documentation`
  - `feat(auth): add magic link sign-in flow`
  - `fix(api): handle empty decision payload`

## Documentation Expectations

- Update docs when structure, behavior, or operating assumptions change.
- Add an ADR in `docs/adr/` for decisions that are hard to reverse.
- Keep docs concise and specific to this repository.

## Advanced Codex Usage

- Check [`.agents/README.md`](./.agents/README.md) before spawning subagents.
- Reuse task starters under [`.codex/prompts/`](./.codex/prompts/) for feature, bugfix, and review work.
- Summarize durable findings from subagents in `docs/` rather than leaving them only in task history.
