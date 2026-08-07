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
