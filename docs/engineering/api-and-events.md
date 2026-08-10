# API And Events

This document defines the MVP external API surface and internal async events.

## API Style

- REST for core domain operations
- SSE for room timeline updates
- JSON request and response bodies
- idempotency support for key mutation endpoints where retries are likely

## Versioning

- initial namespace: `/api/v1`
- breaking changes should use additive migration where possible during MVP

## Auth Model

- current implementation uses header-resolved development actors backed by `LocalActorProvider`
- `X-Actor-Id` selects the caller user identity and auto-provisions the user row if needed
- `X-Actor-Name` updates or seeds the caller display name for local development
- invite token join is implemented and creates guest membership records
- meeting-scope authorization is enforced through membership role checks
- production-grade authenticated sessions remain follow-up work

## Core Resources

- meetings
- invite-links
- memberships
- threads
- messages
- decisions
- votes
- reports
- search
- notifications

## Implemented Surface

### Meetings

`POST /api/v1/meetings`

- create meeting

`GET /api/v1/meetings/{meetingId}`

- get meeting summary and progress
- returns thread-level counts so the client can render planning progress without separate summary calls
- requires meeting membership

- `PATCH /api/v1/meetings/{meetingId}` is not implemented yet

### Invites And Membership

`POST /api/v1/meetings/{meetingId}/invite-links`

- create invite link
- requires `host` or `co_host`

`POST /api/v1/invite-links/{token}/join`

- join via invite

`PATCH /api/v1/meetings/{meetingId}/members/{memberId}`

- update role or attendance state
- requires `host` or `co_host`

### Threads And Messages

`GET /api/v1/meetings/{meetingId}/threads`

- list threads with badges and activity state
- requires meeting membership

`POST /api/v1/meetings/{meetingId}/threads`

- create thread
- requires `host` or `co_host`

`GET /api/v1/threads/{threadId}/messages`

- list paginated messages
- current implementation returns chronological thread messages without pagination
- requires meeting membership

`POST /api/v1/threads/{threadId}/messages`

- post message
- writes `MessageCreated` into the outbox for downstream AI and notification workers
- requires meeting membership

`PATCH /api/v1/messages/{messageId}`

- edit message
- allowed for the original sender, `host`, or `co_host`

### Decisions

`GET /api/v1/meetings/{meetingId}/decisions`

- list decision board items
- requires meeting membership

`POST /api/v1/meetings/{meetingId}/decisions`

- create manual or AI-originated draft decision
- requires `host` or `co_host`

`GET /api/v1/decisions/{decisionId}`

- get decision detail with evidence and history
- requires meeting membership

`POST /api/v1/decisions/{decisionId}/confirm`

- confirm decision
- requires `host` or `co_host`

`POST /api/v1/decisions/{decisionId}/reconsider`

- reopen a confirmed decision with reason
- requires `host` or `co_host`

`POST /api/v1/decisions/{decisionId}/evidence`

- link a thread message as decision evidence
- requires `host` or `co_host`

### Votes

`POST /api/v1/meetings/{meetingId}/votes`

- create vote
- creates a linked decision automatically when `decisionId` is omitted
- stores deterministic scoring-rule metadata on the vote row
- requires `host` or `co_host`

`GET /api/v1/votes/{voteId}`

- get vote status and aggregated results
- returns option metadata, caller's current responses, and aggregate ranking snapshot
- requires meeting membership

`PUT /api/v1/votes/{voteId}/responses/me`

- submit or replace caller responses
- requires meeting membership

`POST /api/v1/votes/{voteId}/close`

- manually close vote
- freezes `closed_at` and persists a decision-facing vote snapshot into `decision.proposed_result`
- requires `host` or `co_host`

### Search And Reports

`GET /api/v1/meetings/{meetingId}/search?q=...`

- current implementation performs keyword search across messages, decisions, and tasks
- semantic search remains follow-up work
- requires meeting membership

`GET /api/v1/meetings/{meetingId}/tasks`

- list meeting tasks
- requires meeting membership

`POST /api/v1/meetings/{meetingId}/tasks`

- create task
- requires `host` or `co_host`

`PATCH /api/v1/tasks/{taskId}`

- update task title, status, owner, or due date
- requires `host` or `co_host`

`GET /api/v1/meetings/{meetingId}/notifications`

- list notifications for the current local actor
- requires meeting membership

`GET /api/v1/meetings/{meetingId}/reports/latest`

- get latest materialized report
- requires meeting membership

`POST /api/v1/meetings/{meetingId}/reports/refresh`

- trigger report regeneration
- requires `host` or `co_host`

### Realtime

`GET /api/v1/meetings/{meetingId}/events`

- SSE stream for room events
- current implementation is in-memory per application instance
- reconnect replay and broker-backed fan-out remain follow-up work
- requires meeting membership

## Sample Shapes

### Create Meeting Request

```json
{
  "title": "2026 Summer Rafting",
  "meetingType": "trip",
  "expectedHeadcount": 9,
  "originRegion": "Daegu",
  "budget": {
    "min": 150000,
    "max": 200000
  }
}
```

### Decision Detail Response

```json
{
  "id": "decision_123",
  "meetingId": "meeting_123",
  "threadId": "thread_123",
  "decisionType": "date",
  "title": "Trip date",
  "status": "confirmed",
  "confirmedResult": {
    "startDate": "2026-07-18",
    "endDate": "2026-07-19"
  },
  "evidence": [
    {
      "id": "evidence_123",
      "messageId": "message_123",
      "evidenceType": "vote_result",
      "quoteSnapshot": "8 of 9 participants can attend on 7/18",
      "confidence": 0.98
    }
  ],
  "history": [
    {
      "action": "confirmed",
      "reason": "No hard conflicts remained",
      "createdAt": "2026-08-07T10:00:00Z"
    }
  ]
}
```

### Vote Submission Request

```json
{
  "responses": [
    {
      "optionId": "opt_0718",
      "responseValue": "available"
    },
    {
      "optionId": "opt_0719",
      "responseValue": "maybe"
    }
  ]
}
```

## Domain Events

Use an outbox-backed event stream. These events are internal contracts between the API transaction boundary and async workers.

Current implementation detail:

- mutations write to `outbox_event`
- selected mutations also publish immediate in-memory SSE updates
- durable outbox relay workers are not implemented yet

### Core Events

- `MeetingCreated`
- `InviteLinkCreated`
- `MemberJoinedMeeting`
- `ThreadCreated`
- `MessageCreated`
- `MessageEdited`
- `VoteCreated`
- `VoteResponseUpserted`
- `VoteClosed`
- `DecisionDrafted`
- `DecisionConfirmed`
- `DecisionReconsiderationRequested`
- `DecisionEvidenceLinked`
- `SummaryGenerated`
- `ReportRefreshed`
- `NotificationRequested`
- `TaskCreated`
- `TaskUpdated`

## Event Flows

### Message Flow

```mermaid
sequenceDiagram
    participant Client
    participant API
    participant DB
    participant Outbox
    participant Worker

    Client->>API: POST /threads/{id}/messages
    API->>DB: insert message
    API->>DB: insert outbox(MessageCreated)
    API-->>Client: 201 Created
    Outbox->>Worker: MessageCreated
    Worker->>DB: classify message
    Worker->>DB: update summary or draft decision
```

### Decision Confirmation Flow

```mermaid
sequenceDiagram
    participant Host
    participant API
    participant DB
    participant Worker
    participant SSE

    Host->>API: POST /decisions/{id}/confirm
    API->>DB: confirm decision + history + outbox
    API->>SSE: push decision.updated
    API-->>Host: 200 OK
    Worker->>DB: refresh report
    Worker->>DB: create notifications
    Worker->>SSE: push report.updated
```

## SSE Event Types

- `meeting.updated`
- `thread.updated`
- `message.created`
- `message.updated`
- `vote.updated`
- `decision.updated`
- `report.updated`
- `notification.created`

## Idempotency Guidance

Planned idempotency coverage:

- create meeting
- create vote
- confirm decision
- refresh report

Current implementation:

- no request idempotency key enforcement yet
- duplicate protection relies only on resource-level constraints and service state checks

This reduces duplicate writes during mobile retry conditions.

## Error Semantics

- `400` invalid request shape or rule violation
- `401` unauthenticated
- `403` authenticated but lacking meeting role permission
- `404` resource not found or not visible
- `409` state conflict such as confirming an already superseded decision
- `422` semantically invalid transition

## Open API Follow-Up

Once implementation begins, convert this draft into a machine-readable OpenAPI spec and keep examples aligned with the scoring and state-machine rules.
