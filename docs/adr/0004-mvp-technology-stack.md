# ADR 0004: MVP Technology Stack

## Status

Accepted

## Date

2026-08-07

## Context

The repository now has product, architecture, data model, and API direction, but implementation cannot begin cleanly without fixing a concrete runtime and toolchain. The MVP needs one stack that supports:

- web-first product delivery
- strong backend domain modeling
- async AI and notification jobs
- evidence-heavy relational data
- hybrid search without large infra overhead
- stable contracts between a TypeScript frontend and Kotlin backend

## Decision

The MVP stack will use:

- `Next.js` and `TypeScript` for the web client
- `Kotlin + Spring Boot 3` for the API server
- `Spring MVC` plus SSE for the realtime HTTP layer
- `PostgreSQL` with `jOOQ`
- `Flyway` for schema migrations
- `Redis` plus a queue-backed Kotlin worker
- `pgvector` for semantic retrieval
- backend-owned `OpenAPI` as the HTTP contract source of truth
- generated TypeScript API types and clients on the frontend
- server-side AI integration only
- `JUnit 5`, `Kotest`, `Testcontainers`, and `Playwright` for testing

## Consequences

### Positive

- backend domain logic gets stronger transactional and modeling support
- SQL-heavy search, evidence, and scoring flows remain explicit
- frontend-backend contracts are shared through generated schemas rather than shared runtime code
- async AI work remains isolated from request latency

### Negative

- frontend and backend no longer share one application language
- build and tooling split between Node and Gradle
- generated-client discipline becomes mandatory to avoid contract drift

## Rejected Alternatives

- TypeScript end-to-end stack
- `NestJS` backend
- ORM-first persistence with hidden SQL
- dedicated vector or search infrastructure from day one
- native mobile first

## Follow-Up

- scaffold the repository with `Next.js` plus Gradle multi-module backend layout
- define the initial DB schema and migrations
- generate the first OpenAPI contract and frontend client types
- write the first state-machine and scoring tests before broad feature implementation
