# Database Schema

This document maps the MVP relational model to the first Flyway migration.

## Source Files

- migration: [`backend/app/src/main/resources/db/migration/V1__initial_schema.sql`](../../backend/app/src/main/resources/db/migration/V1__initial_schema.sql)
- model reference: [`data-model-erd.md`](./data-model-erd.md)

## Scope

The initial schema covers:

- core identity and meeting entities
- topic threads and messages
- decisions, evidence, and history
- votes, options, and responses
- tasks, reports, and notifications
- infrastructure support through an outbox table

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

## Follow-Up Migrations

Likely next migrations:

1. search projection tables for keyword and semantic retrieval
2. attachment normalization if file handling becomes richer
3. settlement domain tables after MVP validation
4. audit refinements for AI-generated artifacts
5. chat import tables and imported message origin (`V3__chat_import.sql`, see [`chat-import-design.md`](./chat-import-design.md))
