# MVP Delivery Plan

This document turns the product and architecture direction into an execution sequence.

## Delivery Principles

- ship vertical slices, not disconnected infrastructure
- keep work scoped so each milestone can be demoed with realistic planning scenarios
- validate date and location decision speed before expanding side features

## Milestone Plan

### Milestone 0: Foundations

Target:

- repository and technical baseline
- domain modeling
- initial service skeleton

Deliverables:

- architecture docs
- ERD
- API draft
- basic backend project bootstrap

Exit criteria:

- team can start implementation without unresolved core modeling gaps

### Milestone 1: Meeting And Thread Core

Target:

- create meeting
- invite join flow
- default topic threads
- message persistence

Deliverables:

- meeting APIs
- membership roles
- thread and message APIs
- basic SSE room updates

Exit criteria:

- a user can create a room and hold topic-based conversation

### Milestone 2: Date Decision Flow

Target:

- date options
- matrix response input
- ranking logic
- decision board integration

Deliverables:

- date vote type
- scoring algorithm implementation
- vote deadlines and early-close rules
- confirmed date decision detail view

Exit criteria:

- end-to-end date decision can be completed inside the product

### Milestone 3: Location Package Decision

Target:

- collect constraints
- compare compressed packages
- low-opposition recommendation logic

Deliverables:

- package option schema
- preference vote type
- recommendation explanation payload

Exit criteria:

- users can choose among at most 3 packages with clear rationale

### Milestone 4: Evidence And Decision Trust

Target:

- evidence linking
- decision history
- reconsideration flow

Deliverables:

- decision detail API
- evidence persistence
- source message deep-link behavior

Exit criteria:

- every confirmed decision can be audited through evidence links

### Milestone 5: AI Summary And Suggestion

Target:

- topic and intent classification
- thread summary
- decision suggestion drafts

Deliverables:

- async worker jobs
- prompt and schema contracts
- moderator or host review flow

Exit criteria:

- AI assists structure without bypassing human control

### Milestone 6: Search And Report

Target:

- keyword plus semantic search
- meeting report snapshot

Deliverables:

- search API
- report materialization job
- unresolved-item summary

Exit criteria:

- users can retrieve decisions and rationale without rereading full history

## Suggested Workstreams

### Backend Domain

- entities
- migrations
- state transitions
- API implementation

### Client Product Surface

- meeting setup flow
- thread UI
- decision board UI
- vote UI
- evidence detail UI

### AI And Search

- classifiers
- summary generation
- vector indexing
- hybrid retrieval

### QA And Product Validation

- scenario fixtures
- acceptance checks
- organizer usability testing

## Testing Strategy By Phase

### Foundations

- schema review
- state-machine examples
- API contract review

### Core Interaction

- integration tests for meeting, thread, and message flows
- permission tests for host, co-host, participant, guest

### Decision Flows

- scenario tests for date ranking and early close
- scenario tests for location low-opposition scoring
- regression tests for reconsideration flow

### AI Features

- offline evaluation set
- golden examples for summary and decision suggestion outputs
- fallback behavior tests when AI jobs fail

## Demo Scenarios

Use the same seeded scenarios across milestones:

- 9-person summer trip
- office dinner location choice
- camping meetup with transport coordination

This keeps validation comparable across iterations.

## Risks During Delivery

- overbuilding the chat surface before validating decisions
- shipping AI summaries without evidence fidelity
- designing search before stable decision and summary schemas exist
- letting future settlement requirements distort MVP scope

## Recommended Next Implementation Tickets

1. choose concrete runtime and framework stack
2. define initial PostgreSQL schema and migrations
3. specify decision state machine in executable tests
4. design vote scoring algorithms with worked examples
5. draft OpenAPI contract from `api-and-events.md`
