# Local Development

This document defines the local backend development flow for Dudecide.

## Prerequisites

- Java 21
- Node.js 23 or later
- Docker Desktop with `docker compose`
- Gradle 8.14.x or compatible wrapper once added

## Local Infra

The repository includes [`docker-compose.yml`](../../docker-compose.yml) with:

- PostgreSQL + `pgvector`
- Redis
- MinIO

Start infra:

```bash
docker compose up -d
```

Stop infra:

```bash
docker compose down
```

## Default Local Credentials

### PostgreSQL

- host: `localhost`
- port: `5432`
- database: `dudecide`
- user: `dudecide`
- password: `dudecide`

### Redis

- host: `localhost`
- port: `6379`

### MinIO

- API: [http://localhost:9000](http://localhost:9000)
- console: [http://localhost:9001](http://localhost:9001)
- user: `dudecide`
- password: `dudecide123`

## Gradle Commands

In this environment, Gradle may require writable temp directories.

Recommended invocation:

```bash
GRADLE_USER_HOME=/private/tmp/dudecide-gradle-home \
GRADLE_OPTS='-Djava.io.tmpdir=/private/tmp/dudecide-gradle-tmp' \
/opt/homebrew/bin/gradle --no-daemon <task>
```

## Flyway

Run migrations:

```bash
GRADLE_USER_HOME=/private/tmp/dudecide-gradle-home \
GRADLE_OPTS='-Djava.io.tmpdir=/private/tmp/dudecide-gradle-tmp' \
/opt/homebrew/bin/gradle --no-daemon :backend:app:flywayMigrate
```

It targets `jdbc:postgresql://localhost:5432/dudecide` (user and password `dudecide`) by default. Point it elsewhere with Flyway's own properties, for example `-Pflyway.url=jdbc:postgresql://localhost:55433/dudecide`; other `flyway.*` property names are rejected by the plugin. Create `/private/tmp/dudecide-gradle-tmp` first if it was cleared.

## jOOQ

Generate jOOQ classes from Flyway SQL without requiring a running database:

```bash
GRADLE_USER_HOME=/private/tmp/dudecide-gradle-home \
GRADLE_OPTS='-Djava.io.tmpdir=/private/tmp/dudecide-gradle-tmp' \
/opt/homebrew/bin/gradle --no-daemon :backend:app:generateJooq
```

This uses the checked-in migration SQL as the source for code generation.

## Verification

Useful first checks:

```bash
GRADLE_USER_HOME=/private/tmp/dudecide-gradle-home \
GRADLE_OPTS='-Djava.io.tmpdir=/private/tmp/dudecide-gradle-tmp' \
/opt/homebrew/bin/gradle --no-daemon :backend:shared:test :backend:app:compileKotlin
```

Current verified command in this repository:

```bash
GRADLE_USER_HOME=/private/tmp/dudecide-gradle-home \
GRADLE_OPTS='-Djava.io.tmpdir=/private/tmp/dudecide-gradle-tmp' \
/opt/homebrew/bin/gradle --no-daemon :backend:app:test
```

Known local caveat as of August 10, 2026:

- if Docker Desktop is not running, `docker compose up -d` will fail before PostgreSQL, Redis, and MinIO start
- backend tests do not require Docker because integration coverage currently uses H2

## Frontend Commands

Bootstrap the web app:

```bash
cd apps/web
npm install
npm run dev
```

Run frontend checks:

```bash
cd apps/web
npm run typecheck
npm run test
npm run lint
npm run test:e2e
```

Playwright browser binaries are required for `npm run test:e2e`. It starts (or reuses) a dev server on port 3000; if another app already holds that port, set `PLAYWRIGHT_PORT`, for example `PLAYWRIGHT_PORT=3017 npm run test:e2e`.

Optional API base URL:

```bash
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
```

Default local CORS allowlist:

- `http://localhost:3000`
- `http://127.0.0.1:3000`

Override if needed:

```bash
APP_CORS_ALLOWED_ORIGINS=http://localhost:3000,http://127.0.0.1:3000
```

## Unified Local Stack

Start frontend and backend together from the repository root:

```bash
./scripts/play_stack_local.sh
```

Useful options:

```bash
./scripts/play_stack_local.sh --skip-infra
./scripts/play_stack_local.sh --with-worker
```

Behavior:

- runs `docker compose up -d` unless `--skip-infra` is used
- starts `:backend:app:bootRun`
- starts `apps/web` dev server with `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080`
- stops all started child processes when you press `Ctrl+C`

## Current Runtime Notes

- API realtime uses in-memory Spring `SseEmitter` subscriptions
- notifications are stored in PostgreSQL and exposed through the API, but external delivery is not wired yet
- the current actor model first checks an HTTP session, then development headers, then falls back to a local host identity
- `POST /api/v1/auth/guest-sessions` creates a guest-backed local session for browser-style flows
- invite-link joins also bind the joined user into the current HTTP session
- requests without a session get `401` by default
- for curl or seed-data work only, start the backend with `DUDECIDE_AUTH_DEV_ACTORS_ENABLED=true`: `X-Actor-Id: <user-uuid>` and optional `X-Actor-Name: <display-name>` then simulate callers, and requests with neither a session nor a header act as the shared `Local Host` user. Never set this outside local development; anyone could act as anyone
- meeting membership is required for read access and `host` or `co_host` is required for protected mutations

## Seed Data

For a reusable sample meeting and open vote scenario, use:

- [`dev-seed-data.md`](./dev-seed-data.md)
- [`backend/app/src/main/resources/dev-seed/sample_meeting_vote.sql`](../../backend/app/src/main/resources/dev-seed/sample_meeting_vote.sql)
