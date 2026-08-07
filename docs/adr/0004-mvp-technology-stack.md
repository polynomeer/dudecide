# ADR 0004: MVP Technology Stack

## Status

Accepted

## Date

2026-08-07

## Context

The repository now has product, architecture, data model, and API direction, but implementation cannot begin cleanly without fixing a concrete runtime and toolchain. The MVP needs one stack that supports:

- web-first product delivery
- modular backend structure
- async AI and notification jobs
- evidence-heavy relational data
- hybrid search without large infra overhead

## Decision

The MVP stack will use:

- `TypeScript` across web, API, worker, and shared packages
- `pnpm` workspaces with `Turborepo`
- `Next.js` for the web client
- `NestJS` with `Fastify` for the API server
- `PostgreSQL` with `Drizzle ORM`
- `Redis` with `BullMQ`
- `pgvector` for semantic retrieval
- `Zod` for shared runtime validation
- official `OpenAI` JavaScript SDK for AI integration
- `Vitest` and `Playwright` for testing

## Consequences

### Positive

- one language across the codebase reduces context-switch cost
- backend module boundaries map cleanly to the architecture docs
- search and vector retrieval stay inside the PostgreSQL-centered MVP infra
- async AI work remains isolated from request latency

### Negative

- NestJS adds framework structure some small teams may find heavy
- Next.js introduces framework opinions beyond a minimal SPA
- custom backend-owned auth still needs careful implementation

## Rejected Alternatives

- SPA-first `Vite` frontend
- raw `Fastify` backend without framework structure
- `Prisma` as the primary ORM
- dedicated vector or search infrastructure from day one
- native mobile first

## Follow-Up

- scaffold the workspace according to this stack
- define the initial DB schema and migrations
- write the first state-machine and scoring tests before broad feature implementation
