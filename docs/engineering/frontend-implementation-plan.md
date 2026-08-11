# Frontend Implementation Plan

This document defines the remaining frontend scope for Dudecide MVP and fixes the implementation order before web development begins.

## Current State

- backend API, domain rules, persistence, and local session scaffolding exist
- initial `apps/web` `Next.js` scaffold now exists
- landing, session start, invite join, create meeting, and meeting overview routes are scaffolded
- thread detail now renders meeting-scoped thread context, paginated message history, and message posting
- decision board now supports decision creation, selected decision detail, and confirm/reconsider actions
- shared styling, query provider wiring, and a minimal backend API client exist
- no evidence attachment UI, vote UI, search surface, report surface, SSE refresh, or E2E test suite exists yet

Conclusion:

- Dudecide is not frontend-complete
- the next major workstream is implementing the actual product flows on top of the scaffold

## Target Frontend Stack

- `Next.js` App Router
- `React`
- `TypeScript`
- `Tailwind CSS`
- `TanStack Query`
- `React Hook Form`

## Delivery Goal

Ship a usable web MVP where an organizer can create a meeting, invite people, run threaded discussion and voting, confirm decisions, and review reports without using raw API tools.

## Frontend Work Breakdown

### 1. Application Foundation

- create `apps/web`
- configure `Next.js`, `TypeScript`, `Tailwind`, lint, and formatting
- define app layout, design tokens, typography, spacing, and color system
- add API client layer for backend endpoints
- add query provider, form provider conventions, and error boundary strategy

### 2. Entry And Session Flows

- landing page
- guest session start flow using `POST /api/v1/auth/guest-sessions`
- current actor bootstrap using `GET /api/v1/auth/me`
- sign-out flow using `DELETE /api/v1/auth/session`
- invite-link entry and join flow using `POST /api/v1/invite-links/{token}/join`
- redirect rules for unauthenticated, expired, or invalid invite states

### 3. Meeting Setup

- create-meeting page and form
- meeting metadata update form
- invite-link creation UI
- host-only management controls

### 4. Meeting Workspace

- meeting overview page
- thread summary cards with counts and activity status
- open votes, decisions, tasks, and report summary panels
- top-level workspace navigation

### 5. Threads And Messages

- thread list view
- thread detail page
- message list rendering
- message composer
- message edit flow
- paginated message history using `before` and `limit`
- realtime message updates through SSE

Current status:

- thread detail, message list, message composer, and basic pagination are implemented
- message edit and SSE-driven refresh are not implemented yet

### 6. Decisions

- decision board list
- manual decision creation form
- confirm decision flow
- reconsideration flow
- evidence attachment UI
- decision history rendering

Current status:

- decision list, decision detail, evidence list, history rendering, decision creation, and confirm/reconsider actions are implemented
- evidence attachment action UI is not implemented yet

### 7. Votes

- vote creation UI
- vote detail page
- per-vote-type option rendering
- response submission flow
- close-vote action
- aggregate result visualization

### 8. Tasks, Notifications, Search, And Reports

- task list/create/update UI
- notification panel
- keyword search UI
- report detail page
- report refresh action

### 9. Realtime And Refresh Strategy

- SSE connection lifecycle management
- targeted cache invalidation by event type
- reconnect behavior
- loading and stale-state UX

### 10. Role-Aware UX

- hide or disable host-only actions for non-hosts
- show membership-required and permission-denied states
- distinguish `401`, `403`, `404`, and `409` responses in UI

### 11. Testing And Quality

- component tests for core interactive widgets
- route-level integration tests with mocked API responses
- Playwright end-to-end tests for core product flows

## Required Screens

### P0 Screens

- landing page
- guest session start page
- invite-link join page
- create meeting page
- meeting overview page
- thread detail page
- decision board page
- vote detail page

### P1 Screens

- search page
- report page
- notification drawer or page
- task management surface

## Information Architecture

Recommended route shape:

```text
/
/start
/invite/[token]
/meetings/new
/meetings/[meetingId]
/meetings/[meetingId]/threads/[threadId]
/meetings/[meetingId]/decisions
/votes/[voteId]
/meetings/[meetingId]/search
/meetings/[meetingId]/reports/latest
```

## API Integration Map

### Auth

- `POST /api/v1/auth/guest-sessions`
- `GET /api/v1/auth/me`
- `DELETE /api/v1/auth/session`

### Meetings

- `POST /api/v1/meetings`
- `GET /api/v1/meetings/{meetingId}`
- `PATCH /api/v1/meetings/{meetingId}`
- `POST /api/v1/meetings/{meetingId}/invite-links`
- `PATCH /api/v1/meetings/{meetingId}/members/{memberId}`

### Threads

- `GET /api/v1/meetings/{meetingId}/threads`
- `POST /api/v1/meetings/{meetingId}/threads`
- `GET /api/v1/threads/{threadId}/messages`
- `POST /api/v1/threads/{threadId}/messages`
- `PATCH /api/v1/messages/{messageId}`

### Decisions

- `GET /api/v1/meetings/{meetingId}/decisions`
- `GET /api/v1/decisions/{decisionId}`
- `POST /api/v1/meetings/{meetingId}/decisions`
- `POST /api/v1/decisions/{decisionId}/confirm`
- `POST /api/v1/decisions/{decisionId}/reconsider`
- `POST /api/v1/decisions/{decisionId}/evidence`

### Votes

- `POST /api/v1/meetings/{meetingId}/votes`
- `GET /api/v1/votes/{voteId}`
- `PUT /api/v1/votes/{voteId}/responses/me`
- `POST /api/v1/votes/{voteId}/close`

### Supporting Views

- `GET /api/v1/meetings/{meetingId}/tasks`
- `POST /api/v1/meetings/{meetingId}/tasks`
- `PATCH /api/v1/tasks/{taskId}`
- `GET /api/v1/meetings/{meetingId}/notifications`
- `GET /api/v1/meetings/{meetingId}/search`
- `GET /api/v1/meetings/{meetingId}/reports/latest`
- `POST /api/v1/meetings/{meetingId}/reports/refresh`
- `GET /api/v1/meetings/{meetingId}/events`

## Recommended Implementation Order

### Phase 1: Web App Bootstrap

- scaffold `apps/web`
- wire session bootstrap and API client
- add global layout and design system primitives

Current status:

- done

### Phase 2: Entry And Meeting Creation

- landing, start, invite, and create-meeting flows
- current session and redirect handling

### Phase 3: Meeting Workspace

- meeting overview
- thread list and thread detail
- message composer and pagination

### Phase 4: Decision And Vote Flows

- decision board
- vote creation and participation
- decision confirmation and evidence flow

### Phase 5: Supporting Surfaces

- tasks
- notifications
- search
- report page

### Phase 6: Realtime, Testing, And Polish

- SSE cache updates
- error-state refinement
- Playwright flows

## Definition Of Frontend MVP Done

Frontend MVP is done when:

- a new user can start a guest session in the browser
- an organizer can create a meeting and generate an invite link
- an invitee can join from the invite link and land inside the workspace
- users can read and post messages by thread
- users can participate in votes and see results
- organizers can confirm decisions and inspect evidence
- users can access search, notifications, tasks, and the latest report
- the main flows work on desktop and mobile widths
- core flows have automated browser coverage

## Known Backend Dependencies Still Affecting Frontend

- semantic search is not implemented yet, so search UI should be labeled as keyword search for now
- SSE replay is not implemented, so reconnect behavior should prefer refetch after reconnect
- notification delivery is stored-state only, so frontend should treat notifications as in-app data, not pushed external alerts
- production-grade signed sessions are still follow-up work, so MVP frontend should target the current local session model first
