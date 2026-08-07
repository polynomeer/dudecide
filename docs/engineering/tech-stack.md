# Tech Stack

This document fixes the initial MVP implementation stack for Dudecide.

## Decision Summary

### Monorepo And Language

- `TypeScript`
- `pnpm` workspace
- `Turborepo`

### Client

- `Next.js`
- `React`
- `TypeScript`
- `Tailwind CSS`
- `TanStack Query`
- `React Hook Form`
- `Zod`

### Backend

- `NestJS`
- `Fastify` adapter
- `TypeScript`
- `Zod` for boundary validation

### Data And Infra

- `PostgreSQL`
- `pgvector`
- `Redis`
- `BullMQ`
- `S3-compatible object storage`

### Persistence And SQL Tooling

- `Drizzle ORM`
- `drizzle-kit`

### Search

- PostgreSQL full-text search
- `pgvector` hybrid semantic retrieval

### AI Integration

- official `OpenAI` JavaScript SDK
- async AI orchestration through worker jobs

### Testing And Quality

- `Vitest`
- `Playwright`
- `ESLint`
- `Prettier`

## Why This Stack

The product needs:

- one language across frontend, backend, worker, and AI integration
- strong modular backend structure
- fast UI iteration on web
- first-class async job support
- a low-friction path for hybrid semantic search

This stack optimizes for shipping a real MVP without introducing infrastructure that is harder than the product itself.

## Workspace Shape

Recommended repository shape:

```text
apps/
  web/
  api/
  worker/
packages/
  config/
  ui/
  domain/
  schemas/
  prompts/
```

## Concrete Choices

## 1. Package Manager And Monorepo

### Choice

- `pnpm` for package management
- `Turborepo` for task orchestration and caching

### Why

- efficient workspace dependency handling
- fast installs and predictable lockfile behavior
- simple separation of `web`, `api`, and `worker`

### Rejected Alternatives

- `npm workspaces`: workable, but less efficient for multi-app iteration
- `Nx`: stronger enterprise tooling than needed for this MVP stage

## 2. Frontend Framework

### Choice

- `Next.js` App Router
- `React`
- server-rendered entry plus client interactivity where needed

### Why

- good fit for invite-link entry flows and shareable meeting pages
- easy deployment and SEO-friendly landing or invite pages if needed later
- mature ecosystem for auth, routing, and web app ergonomics

### UI Layer

- `Tailwind CSS` for rapid product iteration
- shared UI package under `packages/ui`
- no heavy component framework at the start

### State And Data Fetching

- `TanStack Query` for client cache and mutation handling
- local UI state kept in React components or lightweight stores only when truly needed

### Forms

- `React Hook Form` plus `Zod` for meeting setup, vote input, and admin actions

### Rejected Alternatives

- pure `Vite` SPA: lighter, but worse fit for invite entry and future shareable routes
- `Remix`: solid data model, but the team advantage versus Next.js is smaller here
- React Native first: too early before the core behavior is validated

## 3. Backend Framework

### Choice

- `NestJS` on `Fastify`

### Why

- module system fits the planned modular monolith
- clean dependency injection for domain modules and worker coordination
- built-in support for structured controllers, providers, and testing
- Fastify keeps the HTTP layer leaner than Express

### Rejected Alternatives

- raw `Fastify`: faster to start small, but easier to drift into ad hoc architecture
- `Express`: weaker default structure for the size this app will reach quickly
- `tRPC` as the primary backend contract: useful in fullstack TS apps, but less ideal for SSE, worker boundaries, and explicit external API contracts

## 4. Database And ORM

### Choice

- `PostgreSQL`
- `Drizzle ORM`
- `drizzle-kit` migrations

### Why PostgreSQL

- strong relational model for meetings, memberships, decisions, votes, and evidence
- good support for JSON fields where structured payloads vary
- works well with full-text search and `pgvector`

### Why Drizzle

- SQL-first and transparent schema definitions
- easier control over migrations and database shape than heavier ORM abstractions
- good fit when the domain contains many stateful relational rules

### Rejected Alternatives

- `Prisma`: productive, but less SQL-transparent and less attractive for advanced indexing and constraint-heavy domain modeling
- `TypeORM`: weaker fit than Drizzle for modern migration discipline

## 5. Search Strategy

### Choice

- PostgreSQL full-text search for keyword retrieval
- `pgvector` for semantic retrieval
- hybrid ranking in application code or SQL views

### Why

- avoids operating a separate search cluster in MVP
- keeps source-of-truth data close to searchable projections
- enough for message, summary, and decision retrieval at MVP scale

### Rejected Alternatives

- dedicated vector database from day one: unnecessary operational overhead
- Elasticsearch/OpenSearch: too much infrastructure before product fit is proven

## 6. Queue And Worker

### Choice

- `BullMQ`
- `Redis`
- dedicated `worker` app in the same workspace

### Why

- good fit for AI classification, summaries, notifications, and report refresh
- straightforward retry and dead-letter handling
- simple local development story

### Rejected Alternatives

- database-only polling jobs: too brittle for AI-heavy async work
- heavyweight workflow engines: too much platform cost this early

## 7. Realtime Transport

### Choice

- Server-Sent Events for meeting room updates

### Why

- sufficient for one-way updates like messages, votes, and decision changes
- simpler auth and reconnect semantics for MVP

### Upgrade Trigger

Move to WebSocket only if:

- typing or presence becomes essential
- client-server bidirectional signaling grows materially

## 8. Auth And Session Approach

### Choice

- session-based auth owned by the backend
- invite-token join flow for guests
- social login can be added later after base flows are stable

### Why

- keeps invite and meeting-role logic inside the core domain
- avoids early dependency on a third-party auth product before the user funnel is stable

### Near-Term Implementation Suggestion

- email magic link or simple social login later
- signed HTTP-only cookie sessions for MVP

## 9. Object Storage

### Choice

- S3-compatible storage

### Why

- keeps attachment and export handling portable across local, staging, and production
- works with MinIO locally and cloud object stores later

## 10. AI SDK And Model Integration

### Choice

- official `OpenAI` JavaScript SDK
- model access only from backend or worker

### Why

- keeps prompts, audit logs, and evidence generation server-side
- avoids leaking provider credentials or prompt logic into the client

### Usage Split

- smaller structured calls for classification and extraction
- larger reasoning calls for summaries and report generation

## 11. Validation And Shared Schemas

### Choice

- `Zod` in shared packages

### Why

- one runtime validation language across web, API, and worker
- useful for API DTO validation, form validation, and AI output schema checking

## 12. Testing Stack

### Choice

- `Vitest` for unit and module tests
- `Playwright` for end-to-end scenario tests

### Why

- fast TS-native unit test workflow
- strong browser automation for invite, voting, and decision flows

## 13. Local Development Baseline

### Choice

- `Docker Compose` for PostgreSQL, Redis, and local object storage
- app processes run with native Node tooling

### Why

- keep app hot-reload fast
- containerize only the infra pieces that benefit from standardization

## Service Boundary Mapping

### `apps/web`

- meeting entry
- discussion UI
- decision board UI
- vote UX
- report UX

### `apps/api`

- REST API
- SSE endpoint
- auth/session handling
- core domain orchestration

### `apps/worker`

- message classification
- summary generation
- decision suggestions
- notification jobs
- report refresh jobs

## Initial Library Baseline

Recommended first install set:

- web: `next react react-dom @tanstack/react-query react-hook-form zod tailwindcss`
- api: `@nestjs/core @nestjs/common @nestjs/platform-fastify @nestjs/config zod drizzle-orm pg ioredis bullmq`
- worker: `bullmq ioredis zod openai`
- shared: `typescript vitest eslint prettier`

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
