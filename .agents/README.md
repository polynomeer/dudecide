# Subagents

This folder defines repository-specific subagent roles for Codex multi-agent work.

## When To Use

- break a large task into independent research, implementation, and review streams
- parallelize context gathering before editing code
- enforce a predictable handoff format between task participants

## Available Roles

- [`researcher.md`](./researcher.md): gathers product, API, and codebase context
- [`implementer.md`](./implementer.md): makes scoped changes and verifies behavior
- [`reviewer.md`](./reviewer.md): performs code-review style validation and risk checks

## Operating Rules

- Spawn subagents only when work can be partitioned cleanly.
- Give each subagent one explicit output and a bounded file or problem scope.
- Treat subagent output as input to the main task, not as the final source of truth.
- Promote durable findings into `docs/` or code comments when they matter beyond one task.
