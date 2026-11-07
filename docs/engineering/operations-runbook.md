# Operations Runbook

How to run Dudecide in production. Hosting is not chosen yet, so this covers what any host needs.

## Required Configuration

| Variable | Service | Value |
| --- | --- | --- |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | API | PostgreSQL connection |
| `APP_CORS_ALLOWED_ORIGINS` | API | the web origin, for example `https://app.example.com` |
| `DUDECIDE_SESSION_COOKIE_SECURE` | API | `true` |
| `DUDECIDE_FORWARD_HEADERS` | API | `native` behind a trusted proxy; otherwise every client shares one rate limit bucket |
| `DUDECIDE_AI_PROVIDER` | API | `fake` by default; a real provider also needs its API key and spends credits |
| `NEXT_PUBLIC_API_BASE_URL` | web image build arg | the API origin; compiled into the bundle |

Never set `DUDECIDE_AUTH_DEV_ACTORS_ENABLED` in production. The API logs a warning at startup if it is on; alert on that line.

Serve web and API under the same site (`app.example.com`, `api.example.com`), because the session cookie is `SameSite=Lax`. Run one API instance until rate limits move to a shared store: limits count per instance.

## Health And Metrics

The API image serves these on port 8081, which must not be routed publicly. The web image serves `/` on port 3000.

| Endpoint | Use |
| --- | --- |
| `/actuator/health/liveness` | restart the container when it fails; does not depend on the database |
| `/actuator/health/readiness` | stop routing traffic when it fails; includes the database |
| `/actuator/prometheus` | metrics, including `http_server_requests_seconds` by route and status, and `dudecide_import_*` |

A stalled database fails requests within 30 seconds (JDBC socket timeout), and new connections give up after 5 seconds. Shutdown is graceful for up to 20 seconds. Long-lived realtime connections close at that limit and browsers reconnect.

Suggested alerts: readiness failing, 5xx rate above 1% for 5 minutes, any `Request failed` log line spike, and `decision_suggestion_run.failure_reason` rows when a real AI provider is on.

## Logs

The API image writes structured JSON (ECS) to stdout, so the host only needs to collect stdout. Every request has an id, in the `requestId` field and the `X-Request-Id` response header. The web shows it as "오류 코드" on server failures, so a user report leads straight to the log line.

Server failures log the route template, never the raw path, because paths carry invite tokens. Message bodies and request bodies are not logged. Keep it that way: chat content is other people's words.

External error tracking (Sentry or similar) is not set up. It needs a vendor decision, and its data handling must appear in the privacy policy.

## Backups

Prefer the managed database's automated backups with point-in-time recovery. Otherwise run this on a schedule, with `pg_dump` matching the server's major version (17):

```bash
DATABASE_URL=postgresql://user:pass@host:5432/dudecide ./scripts/db_backup.sh /path/to/backups
```

- Backups hold conversation text and session rows. Store them encrypted, with access limited to operators.
- `RETENTION_DAYS` defaults to 14. A deleted chat import stays in backups until it rotates out, so the privacy policy must state this period.
- Keep a copy outside the database's region or account.

## Restore

Check a backup restores at least monthly and before risky migrations. This loads it into a new scratch database and drops it afterwards:

```bash
ADMIN_DATABASE_URL=postgresql://user:pass@host:5432/postgres ./scripts/db_restore_check.sh /path/to/backup.dump
```

To recover for real:

1. Stop the API so nothing writes during the restore.
2. Restore into a new database: `pg_restore --no-owner --exit-on-error --dbname=<new-url> backup.dump`. Do not restore over the damaged one; keep it for investigation.
3. Point `DB_URL` at the new database and start the API. Flyway applies any newer migrations, and readiness turns healthy once the database is reachable.
4. Sessions in the backup still work. Anything written after the backup is lost.

Flyway migrations only move forward. To undo a bad migration, restore the backup taken before the deployment and redeploy the previous image.
