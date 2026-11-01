# Database Schema

This document maps the MVP relational model to the Flyway migrations.

## Source Files

- migration: [`backend/app/src/main/resources/db/migration/V1__initial_schema.sql`](../../backend/app/src/main/resources/db/migration/V1__initial_schema.sql)
- migration: [`V2__idempotency_records.sql`](../../backend/app/src/main/resources/db/migration/V2__idempotency_records.sql)
- migration: [`V3__chat_import.sql`](../../backend/app/src/main/resources/db/migration/V3__chat_import.sql)
- migration: [`V4__chat_import_claims.sql`](../../backend/app/src/main/resources/db/migration/V4__chat_import_claims.sql)
- migration: [`V5__notification_read_state.sql`](../../backend/app/src/main/resources/db/migration/V5__notification_read_state.sql)
- migration: [`V6__decision_suggestions.sql`](../../backend/app/src/main/resources/db/migration/V6__decision_suggestions.sql)
- model reference: [`data-model-erd.md`](./data-model-erd.md)

## Scope

The initial schema covers:

- core identity and meeting entities
- topic threads and messages
- decisions, evidence, and history
- votes, options, and responses
- tasks, reports, and notifications
- infrastructure support through an outbox table
- idempotency records (V2)
- chat imports, imported speakers, and imported message origin (V3)
- analysis claim time and attempt count on chat imports (V4)
- notification read time (V5)
- live decision suggestions and their analysis runs (V6)

It does not yet include:

- search projection tables
- settlement or deposit domain tables
- attachment metadata tables separate from message payload JSON

## Naming Rules

- SQL tables use snake_case
- `app_user` is used instead of `user` to avoid reserved-word collisions
- `thread_summary`, `task_item`, and `meeting_report` are used to keep names specific at SQL level

## Enum Strategy

The migration uses PostgreSQL enums for stable workflow states:

- meeting status
- membership role
- attendance status
- thread category and status
- decision type and status
- evidence type
- vote type, status, and response value
- notification type
- outbox status
- message origin, chat import source, and chat import status (V3)

This is intentional because jOOQ works well with explicit DB enums, and these states are central to business logic.

## Design Notes

### Meeting Ownership

- `meeting.owner_user_id` identifies the organizer who created the room
- memberships remain separate so ownership and participation are not conflated

### Decision Persistence

- `decision.proposed_result` stores draft state
- `decision.confirmed_result` stores authoritative confirmed state
- `decision_history` records lifecycle changes

### Evidence Trust Model

- `decision_evidence.quote_snapshot` preserves historical context even if message rendering changes later
- `(decision_id, message_id, evidence_type)` is unique to avoid duplicate evidence rows

### Chat Import (V3)

Design: [`chat-import-design.md`](./chat-import-design.md).

- `chat_import` records one upload: source, range, counts, consent version, staged analysis `result`, and `purge_at` for unconverted guest imports
- `import_speaker` holds exported display names and pseudonyms per import thread; `membership_id` links a speaker to a member once mapped or claimed
- imported chat lives in `message` with `origin = 'imported'`, `sender_user_id` null, and `import_id`, `import_speaker_id`, `content_hash` set; `message_sender_origin_ck` enforces exactly one sender kind
- `topic_thread.read_only` marks import threads that cannot receive native messages
- `message_thread_content_hash_uk` (partial unique index) deduplicates re-imports per thread
- `chat_import.claimed_at` and `attempts` (V4) let the maintenance worker requeue or fail analyses left behind by a crashed process; `(uploaded_by_user_id, created_at)` backs the daily import limit
- `decision_suggestion` (V6) stores pending, accepted, or dismissed AI suggestions per thread with evidence as message ids and roles; `decision_suggestion_run` records every live analysis for the per-meeting daily cap; `topic_thread.suggestions_analyzed_at` is the last activity a thread was analyzed through
- `notification.read_at` (V5) is set once per notification by the recipient; `(recipient_user_id, meeting_id, read_at)` backs the unread lookup
- deleting a `chat_import` cascades to its messages and, through `decision_evidence.message_id`, to evidence built on them; `task_item.source_message_id` is set to null

### Vote Modeling

- votes are generic containers
- `vote_option.payload` stores type-specific metadata for date or package voting
- `vote_response_value` is constrained at DB level to approved values

### Outbox

- `outbox_event` is included in V1 instead of adding it later
- this keeps async AI, notifications, and report refresh aligned with the architecture docs

## Constraints Worth Noting

- meeting budget range must be valid
- meeting start/end times must be ordered
- membership is unique per `(meeting_id, user_id)`
- invite tokens are unique
- report versions are unique per meeting
- vote response is unique per `(vote_id, option_id, user_id)`
- chat import range start must not be after range end
- imported message content hash is unique per thread

## Follow-Up Migrations

Likely next migrations:

1. search projection tables for keyword and semantic retrieval
2. attachment normalization if file handling becomes richer
3. settlement domain tables after MVP validation
4. audit refinements for AI-generated artifacts
5. `import_ready` notification type for chat import completion (deferred from V3, see below)

## Tooling Notes

- jOOQ code is generated from the migration files through `DDLDatabase`, which replays them on H2. Statements H2 cannot run, such as `ALTER TYPE ... ADD VALUE`, fail code generation. Adding an enum value needs either a jOOQ ignore-comment block (and a manual generated-code check) or a type swap the H2 replay can parse; decide when the first enum extension is needed.
- `AppServicesIntegrationTest` keeps a hand-written H2 schema (enums as `VARCHAR`, partial indexes as plain unique constraints). Update it with every migration.
- Gradle needs JDK 21 (`jvmToolchain(21)`); with a newer default JDK, set `JAVA_HOME=$(/usr/libexec/java_home -v 21)`.
