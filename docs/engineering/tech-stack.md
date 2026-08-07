# Tech Stack

This document fixes the initial MVP implementation stack for Dudecide.

## Decision Summary

### Repository And Build

- `Gradle` multi-module for backend and backend-shared libraries
- separate `Next.js` web app in the same repository
- repository-level orchestration may be added later, but contract ownership stays with backend schemas

### Client

- `Next.js`
- `React`
- `TypeScript`
- `Tailwind CSS`
- `TanStack Query`
- `React Hook Form`

### Backend

- `Kotlin`
- `Spring Boot 3`
- `Spring MVC`
- modular monolith with `Spring Modulith` style boundaries

### Data And Infra

- `PostgreSQL`
- `pgvector`
- `Redis`
- queue-backed async worker
- `S3-compatible object storage`

### Persistence And SQL Tooling

- `jOOQ`
- `Flyway`

### Search

- PostgreSQL full-text search
- `pgvector` hybrid semantic retrieval

### AI Integration

- official server-side `OpenAI` SDK
- async AI orchestration through worker jobs

### Testing And Quality

- `JUnit 5`
- `Kotest`
- `Testcontainers`
- `Playwright`
- `ESLint`
- `Prettier`

## Why This Stack

The product needs:

- strong domain modeling for decision, vote, and evidence workflows
- robust transactional consistency
- long-lived backend maintainability
- safe async AI integration
- clean contract boundaries between frontend and backend

This stack optimizes for a durable backend while keeping the frontend web-first and fast to iterate.

## Workspace Shape

Recommended repository shape:

```text
apps/
  web/
backend/
  app/
  worker/
  modules/
    meeting/
    thread/
    decision/
    vote/
    search/
    report/
    notification/
    ai/
  shared/
contracts/
  openapi/
  events/
```

## Shared-Schema Strategy

### Decision

- do not share runtime validation code between TypeScript and Kotlin
- backend Kotlin DTOs and enums define the authoritative HTTP contract
- `OpenAPI` generated from backend code is the shared API contract
- frontend uses generated TypeScript types and API clients from OpenAPI
- async jobs and internal events use explicit JSON payload contracts

### Why

- cross-language teams should share contracts, not implementation types
- backend remains authoritative over business and transport rules
- frontend keeps strong typing without forcing backend model design

### Rules

- transport DTO changes require OpenAPI regeneration
- frontend should consume generated client types for server payloads
- backend validates AI outputs before mapping into domain objects
- event payloads are versioned and treated as contracts, not ad hoc maps

## Concrete Choices

## 1. Repository And Build Strategy

### Choice

- Kotlin backend on `Gradle` multi-module
- standalone `Next.js` web app in the same repository

### Why

- Gradle is the natural build tool for Spring and Kotlin
- backend modules stay explicit and testable
- frontend and backend still share one repository without forcing one toolchain

### Rejected Alternatives

- TypeScript-only monorepo as the main architecture
- forcing frontend builds into Gradle

## 2. Frontend Framework

### Choice

- `Next.js` App Router
- `React`
- server-rendered entry plus client interactivity where needed

### Why

- strong fit for invite-link entry flows and shareable meeting pages
- easy deployment and good support for public entry surfaces
- mature web ecosystem and strong developer velocity

### UI Layer

- `Tailwind CSS` for rapid product iteration
- repository-local UI components, extracted only if reuse pressure appears

### State And Data Fetching

- `TanStack Query` for cache and mutation handling

### Forms

- `React Hook Form` for meeting setup, vote input, and admin actions

### Rejected Alternatives

- pure `Vite` SPA
- `Remix`
- React Native first

## 3. Backend Framework

### Choice

- `Kotlin`
- `Spring Boot 3`
- `Spring MVC`

### Why

- excellent fit for transaction-heavy relational domain logic
- strong testability and operational maturity
- better long-term structure for a growing planning platform

### Why Not WebFlux For MVP

- current flows are request-response and queue-driven
- SSE works without a fully reactive stack
- reactive complexity is not justified yet

### Rejected Alternatives

- `NestJS`
- raw `Ktor`
- `Spring WebFlux` for the initial MVP

## 4. Modularity Strategy

### Choice

- modular monolith
- module boundaries enforced through package structure and tests
- `Spring Modulith` conventions where useful

### Why

- aligns with the architecture docs
- keeps meeting, thread, decision, vote, and AI boundaries explicit
- avoids microservice overhead while preserving extraction paths

## 5. Database And SQL Access

### Choice

- `PostgreSQL`
- `jOOQ`
- `Flyway`

### Why PostgreSQL

- strong relational model for meetings, memberships, decisions, votes, and evidence
- native path to full-text search and `pgvector`
- strong transactional guarantees

### Why jOOQ

- SQL-first and type-safe without hiding the database
- strong fit for scoring queries, search projections, and evidence-heavy joins
- good control over advanced SQL and indexing

### Why Flyway

- predictable SQL migration workflow
- easy schema review in pull requests

### Rejected Alternatives

- `Spring Data JPA`
- `Exposed`

## 6. Search Strategy

### Choice

- PostgreSQL full-text search for keyword retrieval
- `pgvector` for semantic retrieval
- hybrid ranking in SQL and application code

### Why

- avoids a separate search cluster in MVP
- keeps searchable projections close to the source-of-truth database
- enough for message, summary, and decision retrieval at MVP scale

### Rejected Alternatives

- dedicated vector database from day one
- Elasticsearch/OpenSearch

## 7. Queue And Worker

### Choice

- Redis-backed queue
- dedicated Kotlin worker process

### Why

- good fit for AI classification, summaries, notifications, and report refresh
- preserves one backend language for business-side async logic
- supports retries and dead-letter handling

### Implementation Direction

- start with a simple Redis-backed queue library or Spring-compatible job runner
- keep job payloads explicit and versioned
- use PostgreSQL outbox plus relay where delivery guarantees matter

### Rejected Alternatives

- database-only polling jobs
- heavyweight workflow engines
- separate TypeScript worker

## 8. Realtime Transport

### Choice

- Server-Sent Events for meeting room updates

### Why

- sufficient for one-way updates like messages, votes, and decision changes
- simpler auth and reconnect semantics for MVP

### Upgrade Trigger

Move to WebSocket only if:

- typing or presence becomes essential
- client-server bidirectional signaling grows materially

## 9. Auth And Session Approach

### Choice

- session-based auth owned by the backend
- invite-token join flow for guests
- social login can be added later after base flows are stable

### Why

- keeps invite and meeting-role logic inside the core domain
- avoids early dependency on a third-party auth product

### Near-Term Implementation Suggestion

- signed HTTP-only cookie sessions for MVP
- email magic link or social login after core room flows stabilize

## 10. Object Storage

### Choice

- S3-compatible storage

### Why

- portable across local, staging, and production
- works with MinIO locally and cloud object stores later

## 11. AI SDK And Model Integration

### Choice

- official provider SDK from server-side runtime only
- model access only from backend or worker

### Why

- keeps prompts, audit logs, and evidence generation server-side
- avoids leaking provider credentials or prompt logic into the client

### Usage Split

- smaller structured calls for classification and extraction
- larger reasoning calls for summaries and report generation

## 12. Contract And Validation Strategy

### Choice

- backend-owned DTO validation in Kotlin
- OpenAPI-generated client types on the frontend
- explicit event payload contracts for async boundaries

### Why

- shared schema should mean stable contracts, not shared source code
- backend stays authoritative over business and transport rules
- frontend gains type safety without dictating backend model design

## 13. Testing Stack

### Choice

- `JUnit 5` and `Kotest` for backend tests
- `Testcontainers` for PostgreSQL and Redis-backed tests
- `Playwright` for end-to-end scenario tests

### Why

- strong JVM testing ecosystem for transaction-heavy behavior
- realistic infrastructure testing without fragile local assumptions
- browser automation for invite, voting, and decision flows

## 14. Local Development Baseline

### Choice

- `Docker Compose` for PostgreSQL, Redis, and local object storage
- backend and frontend run with native Gradle and Node tooling

### Why

- keep app hot-reload practical
- containerize only the infra pieces that benefit from standardization

## Service Boundary Mapping

### `apps/web`

- meeting entry
- discussion UI
- decision board UI
- vote UX
- report UX

### `backend/app`

- REST API
- SSE endpoint
- auth/session handling
- core domain orchestration

### `backend/worker`

- message classification
- summary generation
- decision suggestions
- notification jobs
- report refresh jobs

## Things We Are Explicitly Not Choosing Yet

- native mobile stack
- external auth provider
- dedicated search cluster
- dedicated workflow orchestration platform
- microservices split

## Trigger To Revisit

Revisit this stack if:

- mobile retention clearly exceeds mobile web needs
- semantic search load outgrows PostgreSQL plus `pgvector`
- realtime collaboration requires strong two-way signaling
- auth funnel conversion is blocked by custom session UX
