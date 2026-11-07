# CLAUDE.md

Claude Code entry point for this repository. Shared agent rules live in `AGENTS.md` and apply to Claude Code as-is:

@AGENTS.md

## Claude Code Rules

- Respond to the user in Korean. Code, identifiers, commit messages, and repository docs stay in English.
- Commit after every completed work unit, without waiting to be asked. Follow [`CONTRIBUTING.md`](./CONTRIBUTING.md) (Conventional Commits, English message style).
- Stage only the files you changed for that work unit (`git add <paths>`). Never use `git add -A` / `git add .`; the working tree often holds unrelated user changes.
- Do not push, amend published commits, or rewrite history unless the user asks.
- Use `.claude/agents/` (researcher, implementer, reviewer) for subagent work; they mirror the roles in `.agents/`.
- Use `/feature`, `/bugfix`, `/review-change` slash commands for task kickoff; they reuse `.codex/prompts/`.

## Repository Layout

- `apps/web/`: Next.js 15 App Router + React 19 + TypeScript, Tailwind, TanStack Query, React Hook Form. Unit tests with Vitest, e2e with Playwright.
- `backend/app/`: Kotlin 2.1 + Spring Boot 3.5 (Spring MVC) API, jOOQ + Flyway, SSE realtime. Layers: `api/` (controllers, `dto/`), `domain/` (services, workers), `config/`.
- `backend/shared/`: pure domain logic (decision state machine, vote scoring) with unit tests.
- `backend/worker/`: Redis-backed async worker process.
- `contracts/openapi/`: HTTP contract (backend DTOs are authoritative).
- `backend/app/src/main/resources/db/migration/`: Flyway SQL; also the jOOQ codegen source.
- `docs/`: product, engineering, ADRs. Update when behavior or assumptions change.

## Commands

Backend (no Gradle wrapper; use the env vars below because Gradle needs writable temp dirs):

```bash
GRADLE_USER_HOME=/private/tmp/dudecide-gradle-home GRADLE_OPTS='-Djava.io.tmpdir=/private/tmp/dudecide-gradle-tmp' gradle --no-daemon :backend:shared:test :backend:app:test
```

Other useful Gradle tasks: `:backend:app:compileKotlin`, `:backend:app:generateJooq`, `:backend:app:flywayMigrate`, `:backend:app:bootRun`. Backend tests use H2 and do not need Docker.

Frontend (run inside `apps/web`):

```bash
npm run typecheck
npm run test
npm run lint
npm run test:e2e
```

Full local stack: `./scripts/play_stack_local.sh [--skip-infra] [--with-worker]` (needs Docker for PostgreSQL, Redis, MinIO). Details: [`docs/engineering/local-development.md`](./docs/engineering/local-development.md).

## Verification Expectations

- Frontend change: `npm run typecheck` and `npm run test` in `apps/web`.
- Backend change: the Gradle test command above for the touched module.
- API contract change: update Kotlin DTOs, `contracts/openapi/dudecide-openapi-v1.yaml`, and the web client in `apps/web/lib/api/` together.
- Schema change: add a new Flyway migration (never edit an applied one) and update `docs/engineering/database-schema.md`.

## Key Docs

- Architecture: [`docs/engineering/architecture-overview.md`](./docs/engineering/architecture-overview.md)
- API and events: [`docs/engineering/api-and-events.md`](./docs/engineering/api-and-events.md)
- Decision state machine: [`docs/engineering/decision-state-machine.md`](./docs/engineering/decision-state-machine.md)
- Vote scoring: [`docs/engineering/vote-scoring-rules.md`](./docs/engineering/vote-scoring-rules.md)
- Claude Code workflow: [`docs/engineering/claude-code-workflows.md`](./docs/engineering/claude-code-workflows.md)
