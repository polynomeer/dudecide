# Dev Seed Data

This document defines the local seed-data workflow for Dudecide backend development.

## Purpose

Use the seed SQL when you need a predictable meeting, linked decision, and open vote without manually calling every API first.

The checked-in seed focuses on one reusable scenario:

- one host user
- one participant
- one active meeting
- default topic threads
- one linked date decision
- one open date vote with two options
- existing responses from both sample users

## Seed File

- [`backend/app/src/main/resources/dev-seed/sample_meeting_vote.sql`](../../backend/app/src/main/resources/dev-seed/sample_meeting_vote.sql)

## Apply Flow

1. Start local infra.
2. Run Flyway migrations.
3. Apply the seed SQL with `psql`.

Example:

```bash
docker compose up -d postgres

GRADLE_USER_HOME=/private/tmp/dudecide-gradle-home \
GRADLE_OPTS='-Djava.io.tmpdir=/private/tmp/dudecide-gradle-tmp' \
/opt/homebrew/bin/gradle --no-daemon :backend:app:flywayMigrate

psql postgresql://dudecide:dudecide@localhost:5432/dudecide \
  -f backend/app/src/main/resources/dev-seed/sample_meeting_vote.sql
```

## Seeded IDs

- meeting: `10000000-0000-0000-0000-000000000001`
- decision: `30000000-0000-0000-0000-000000000001`
- vote: `40000000-0000-0000-0000-000000000001`

## Operating Notes

- The SQL is idempotent for the seeded primary keys.
- The seed is for local development only and should not be used in production-like environments.
- When a new persistent demo scenario becomes important, add a new focused seed file instead of overloading this one.
