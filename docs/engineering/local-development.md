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

## Current Runtime Notes

- API realtime uses in-memory Spring `SseEmitter` subscriptions
- notifications are stored in PostgreSQL and exposed through the API, but external delivery is not wired yet
- the current actor model first checks an HTTP session, then development headers, then falls back to a local host identity
- `POST /api/v1/auth/guest-sessions` creates a guest-backed local session for browser-style flows
- invite-link joins also bind the joined user into the current HTTP session
- use `X-Actor-Id: <user-uuid>` and optional `X-Actor-Name: <display-name>` to simulate different callers locally
- meeting membership is required for read access and `host` or `co_host` is required for protected mutations

## Seed Data

For a reusable sample meeting and open vote scenario, use:

- [`dev-seed-data.md`](./dev-seed-data.md)
- [`backend/app/src/main/resources/dev-seed/sample_meeting_vote.sql`](../../backend/app/src/main/resources/dev-seed/sample_meeting_vote.sql)
