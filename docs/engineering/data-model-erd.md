# Data Model And ERD

This document defines the MVP data model and its near-term extensions.

## Modeling Principles

- decisions are first-class entities, not derived UI artifacts
- evidence links must survive summary regeneration
- votes are generic enough to support both date matrices and package evaluations
- reports are materialized outputs, not the sole source of truth

## Core ERD

```mermaid
erDiagram
    USER ||--o{ MEMBERSHIP : joins
    MEETING ||--o{ MEMBERSHIP : has
    MEETING ||--o{ INVITE_LINK : issues
    MEETING ||--o{ TOPIC_THREAD : contains
    TOPIC_THREAD ||--o{ MESSAGE : contains
    TOPIC_THREAD ||--o{ SUMMARY : has
    MEETING ||--o{ DECISION : tracks
    DECISION ||--o{ DECISION_EVIDENCE : supported_by
    MESSAGE ||--o{ DECISION_EVIDENCE : cited_in
    TOPIC_THREAD ||--o{ DECISION_EVIDENCE : anchored_in
    DECISION ||--o{ DECISION_HISTORY : changes
    DECISION ||--o{ VOTE : resolved_by
    VOTE ||--o{ VOTE_OPTION : offers
    VOTE ||--o{ VOTE_RESPONSE : collects
    VOTE_OPTION ||--o{ VOTE_RESPONSE : selected_for
    USER ||--o{ VOTE_RESPONSE : submits
    USER ||--o{ MESSAGE : sends
    USER ||--o{ TASK : owns
    MEETING ||--o{ TASK : contains
    MEETING ||--o{ REPORT : publishes
    MEETING ||--o{ NOTIFICATION : emits

    USER {
      uuid id PK
      string display_name
      string avatar_url
      string status
      timestamptz created_at
    }

    MEETING {
      uuid id PK
      uuid owner_user_id FK
      string title
      string meeting_type
      string status
      string origin_region
      int expected_headcount
      numeric budget_min
      numeric budget_max
      timestamptz starts_at
      timestamptz ends_at
      timestamptz created_at
    }

    MEMBERSHIP {
      uuid id PK
      uuid meeting_id FK
      uuid user_id FK
      string role
      string attendance_status
      timestamptz joined_at
    }

    INVITE_LINK {
      uuid id PK
      uuid meeting_id FK
      string token
      string status
      timestamptz expires_at
      timestamptz created_at
    }

    TOPIC_THREAD {
      uuid id PK
      uuid meeting_id FK
      string category
      string title
      string status
      int sort_order
      timestamptz last_activity_at
      timestamptz created_at
    }

    MESSAGE {
      uuid id PK
      uuid thread_id FK
      uuid sender_user_id FK
      string body
      jsonb attachments
      jsonb ai_labels
      timestamptz created_at
      timestamptz edited_at
    }

    SUMMARY {
      uuid id PK
      uuid thread_id FK
      string summary_type
      text content
      jsonb source_message_ids
      decimal confidence
      timestamptz created_at
    }

    DECISION {
      uuid id PK
      uuid meeting_id FK
      uuid thread_id FK
      uuid decided_by_user_id FK
      string decision_type
      string title
      string status
      jsonb proposed_result
      jsonb confirmed_result
      timestamptz decided_at
      timestamptz created_at
    }

    DECISION_EVIDENCE {
      uuid id PK
      uuid decision_id FK
      uuid message_id FK
      uuid thread_id FK
      string evidence_type
      string quote_snapshot
      decimal confidence
      timestamptz created_at
    }

    DECISION_HISTORY {
      uuid id PK
      uuid decision_id FK
      uuid actor_user_id FK
      string action
      jsonb before_state
      jsonb after_state
      string reason
      timestamptz created_at
    }

    VOTE {
      uuid id PK
      uuid meeting_id FK
      uuid decision_id FK
      uuid thread_id FK
      string vote_type
      string status
      jsonb scoring_rule
      timestamptz opens_at
      timestamptz deadline_at
      timestamptz closed_at
    }

    VOTE_OPTION {
      uuid id PK
      uuid vote_id FK
      string option_type
      string label
      jsonb payload
      int sort_order
    }

    VOTE_RESPONSE {
      uuid id PK
      uuid vote_id FK
      uuid option_id FK
      uuid user_id FK
      string response_value
      jsonb response_payload
      timestamptz created_at
    }

    TASK {
      uuid id PK
      uuid meeting_id FK
      uuid owner_user_id FK
      uuid source_message_id FK
      string title
      string status
      timestamptz due_at
      timestamptz created_at
    }

    REPORT {
      uuid id PK
      uuid meeting_id FK
      int version
      jsonb snapshot
      timestamptz generated_at
    }

    NOTIFICATION {
      uuid id PK
      uuid meeting_id FK
      uuid recipient_user_id FK
      string type
      jsonb payload
      timestamptz sent_at
      timestamptz created_at
    }
```

## Entity Notes

### User

- one identity across many meetings
- guest flows can later map temporary invite visitors into full users

### Meeting

- top-level aggregate for product experience
- stores coarse planning metadata only
- decision progress should be derived, not manually edited

### Membership

- role lives at meeting scope
- supports `host`, `co_host`, `participant`, `guest`

### TopicThread

- default categories for MVP: `date`, `location`, `lodging`, `transport`, `free_chat`
- status can reflect `active`, `blocked`, `waiting_on_dependency`, `closed`

### Message

- raw discussion source of truth
- `ai_labels` stores latest inferred topic and intent tags for fast UI display
- richer extraction artifacts should live in dedicated tables or search indexes, not only here

### Decision

- one record per discrete planning outcome or pending item
- `proposed_result` is editable draft state
- `confirmed_result` is authoritative once confirmed

### DecisionEvidence

- critical trust object
- quote snapshot preserves historical evidence even if message rendering later changes
- message_id remains canonical backlink target

### Vote

- generic vote container
- `vote_type` examples: `date_matrix`, `package_preference`, `binary_confirm`
- `scoring_rule` stores algorithm version and weights

### VoteOption

- payload differs by vote type
- date options store date metadata
- package options store region, activity, cost, and pros/cons

### VoteResponse

- for date matrix, one user creates one response per date option
- for package preference, one user creates one response per package option
- `response_value` examples: `available`, `maybe`, `unavailable`, `good`, `okay`, `dislike`

### Summary

- thread-level summary snapshots
- not authoritative over decisions
- can be regenerated safely as long as source links remain stable

### Report

- meeting-level materialized output
- stores generated snapshot for fast reads and sharing

## State Enums

### Meeting Status

- `draft`
- `active`
- `finalizing`
- `completed`
- `archived`

### Decision Status

- `proposed`
- `in_discussion`
- `voting`
- `confirmed`
- `on_hold`
- `needs_reconsideration`

### Vote Status

- `draft`
- `open`
- `closed`
- `superseded`

## Example Vote Payloads

### Date Option Payload

```json
{
  "date": "2026-07-18",
  "is_weekend": true,
  "label": "Sat 7/18"
}
```

### Location Package Payload

```json
{
  "region": "Sancheong",
  "activity": "rafting",
  "travel_minutes": 120,
  "budget_min": 180000,
  "budget_max": 220000,
  "pros": ["strong activity fit", "valley atmosphere"],
  "cons": ["driving burden"]
}
```

## Search Projection

The semantic search layer should maintain a denormalized document per searchable unit:

- message document
- summary document
- decision document
- report section document

Each document should keep:

- meeting_id
- thread_id if present
- source entity id
- source entity type
- plain text
- tags
- embedding vector

## Future Extensions

Likely next entities after MVP:

- AttendanceInterval
- Expense
- ExpenseParticipant
- SettlementPlan
- DepositPolicy
- ReservationReference
