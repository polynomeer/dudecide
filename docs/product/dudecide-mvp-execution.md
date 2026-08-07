# Dudecide MVP Execution Brief

Source: condensed from [`dudecide_planning_document_v1.pdf`](./dudecide_planning_document_v1.pdf) into an implementation-oriented brief for Codex tasks.

## Objective

Build an MVP that proves Dudecide can help groups decide date and location faster than a generic group chat while preserving trust through evidence-linked summaries.

## Release Hypothesis

If users can:

- join a meeting quickly
- participate in structured date and location decisions with minimal effort
- see current status immediately
- verify summaries through linked evidence

then organizers will prefer Dudecide over running the same process entirely inside chat.

## MVP Deliverables

### Vertical Slice 1: Meeting Setup

- create meeting
- define title, type, expected headcount, origin region, and budget band
- generate invite link

Acceptance:

- organizer can create a room in one flow
- invitees can enter the room with minimal friction

### Vertical Slice 2: Topic Discussion

- create default threads for date, location, lodging, transport, and free chat
- persist messages by thread
- support thread status metadata

Acceptance:

- new message belongs to a visible topic
- users can separate casual conversation from decision discussion

### Vertical Slice 3: Date Decision

- create candidate dates
- collect `available`, `maybe`, `unavailable`
- compute ranked recommendation
- show vote progress and deadline state

Acceptance:

- ranking prefers zero-conflict dates
- organizer can confirm a final date
- decision board reflects the result

### Vertical Slice 4: Location Package Decision

- collect constraints
- create up to 3 location-plus-activity packages
- collect `good`, `okay`, `dislike`
- compute recommendation emphasizing low opposition

Acceptance:

- users compare full packages instead of many fragmented choices
- decision result includes cost range and rationale summary

### Vertical Slice 5: Decision Board And Evidence

- create decision records with lifecycle states
- attach evidence messages
- link from decision detail to source thread context

Acceptance:

- every confirmed decision has at least one evidence record
- users can inspect why a decision was made

### Vertical Slice 6: AI Assistance

- classify topic and intent for messages
- generate thread summaries
- suggest draft decisions for approval

Acceptance:

- AI never finalizes without human approval
- summaries cite supporting message evidence

### Vertical Slice 7: Search And Report

- keyword and semantic retrieval
- consolidated meeting report
- unresolved items and next actions

Acceptance:

- users can ask why an option failed or who owns a task
- report summarizes status without reading the full thread history

## Suggested Implementation Order

1. meeting and membership domain
2. topic threads and message persistence
3. decision model and status transitions
4. date voting workflow
5. location package workflow
6. evidence linking
7. AI summarization and suggestion pipeline
8. search
9. report generation

## Domain Rules

### Decision Statuses

- `proposed`
- `in_discussion`
- `voting`
- `confirmed`
- `on_hold`
- `needs_reconsideration`

### Early Close Rule

Close a vote early when unresolved participants cannot change the winning recommendation under the active scoring model.

### Reconsideration Rule

If a confirmed condition changes, move the decision to `needs_reconsideration` and keep previous evidence and resolution history.

### Candidate Compression Rule

Location flow should not expose an unbounded list. Compress to at most 3 comparable packages before evaluation.

## AI Pipeline Requirements

### Message Classification

- topic: date, location, lodging, transport, settlement, free_chat
- intent: proposal, support, opposition, condition, confirmation, task, question, chatter

### Structured Extraction

- dates
- place names
- budget ranges
- people
- constraints
- action items

### Decision Suggestion

Detect phrases equivalent to "let's finalize this" and generate a draft decision with:

- target decision type
- proposed result
- evidence messages
- confidence score

### Evidence Object

Required fields:

- decision_id
- message_id
- thread_id
- evidence_type
- quote_snapshot
- confidence

## Data Model Priorities

### Must Exist For MVP

- Meeting
- Member
- TopicThread
- Message
- Decision
- DecisionEvidence
- Vote
- VoteOption
- VoteResponse
- Summary
- Report
- Notification

### Can Wait

- Expense
- Settlement
- DepositPolicy
- DepositTransaction

## API / Service Boundaries

### Core APIs

- meeting management
- membership and invite handling
- thread and message operations
- decision lifecycle operations
- vote creation and response submission
- search query handling
- report retrieval

### Async Jobs

- message classification
- summary generation
- decision suggestion
- notification dispatch
- report refresh

## Recommended Architecture Direction

For MVP, prefer a modular monolith with asynchronous workers over early microservice decomposition.

Suggested components:

- client app
- API server
- realtime update layer
- async worker
- PostgreSQL
- Redis
- vector search store
- object storage for attachments and exported reports

## Product Analytics Events

- `meeting_created`
- `invite_opened`
- `message_posted`
- `vote_submitted`
- `decision_suggested`
- `decision_confirmed`
- `evidence_opened`
- `report_viewed`
- `search_executed`

## Codex Task Backlog

### P0 Foundation

- define domain schema for meeting, thread, message, decision, and vote
- design decision status state machine
- document scoring logic for date recommendations
- document scoring logic for low-opposition location ranking

### P0 Product Surface

- outline home, discussion, decisions, search, and report screens
- define minimum create-meeting flow
- define evidence-link detail experience

### P1 Intelligence

- define prompt and extraction schema for message classification
- define summary output contract
- define decision suggestion approval flow

### P1 Validation

- create test scenarios for date and location decision flows
- create sample datasets for evidence-linked summaries
- create user interview scripts for organizer and participant feedback

## Immediate Next Docs To Create

- technical architecture spec under `docs/engineering/`
- ADR for client platform choice
- ADR for AI extraction and evidence model
- backlog or milestone breakdown tied to actual implementation tasks
