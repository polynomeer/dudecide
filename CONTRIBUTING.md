# Contributing

## Workflow

1. Review [`README.md`](./README.md), [`AGENTS.md`](./AGENTS.md), and relevant files under [`docs/`](./docs/README.md).
2. Keep changes focused on one work unit.
3. Update documentation when behavior or decisions change.
4. Commit immediately after a finished work unit.

## Commit Convention

This repository uses Conventional Commits.

Format:

```text
<type>(<scope>): <summary>
```

Scope is recommended but optional when it adds no value.

Common types:

- `feat`: new functionality
- `fix`: bug fix
- `docs`: documentation only
- `refactor`: internal code change without behavior change
- `test`: tests added or updated
- `chore`: maintenance, tooling, or repository housekeeping
- `build`: build system or dependency changes
- `ci`: CI workflow changes

Examples:

- `feat(decision): add candidate ranking endpoint`
- `fix(ui): prevent duplicate vote submission`
- `docs(repo): define documentation structure`
- `chore(tooling): add lint script`

## Commit Message Style

- Write commit messages in English, regardless of the language used in task discussion.
- Summary: imperative mood (`add`, `fix`, `align`), lowercase start, no trailing period, 72 characters or fewer including the type prefix.
- Scope: lowercase area name such as `web`, `api`, `dev`, `repo`, `docs`, `decision`, `vote`.
- Body (optional): separated by a blank line, wrapped at 72 characters, explaining what and why rather than how.
- Breaking changes: add `!` after the type or scope (`feat(api)!: ...`) and a `BREAKING CHANGE:` footer.
- Footers such as `Co-Authored-By:` go at the end of the message.

## Commit Granularity

- One commit per completed work unit
- Do not mix unrelated changes in one commit
- If a task includes follow-up cleanup, commit the main change first and cleanup separately when meaningful

## Documentation Structure

- `docs/product/`: PRD, planning docs, user flows, business context
- `docs/engineering/`: local setup, implementation notes, runbooks, integration details
- `docs/adr/`: architecture decision records

Add new documents to the correct folder and update [`docs/README.md`](./docs/README.md) when introducing a new category or key reference.
