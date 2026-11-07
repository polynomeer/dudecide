# Dudecide Codex PRD

Source: derived from [`dudecide_planning_document_v1.pdf`](./dudecide_planning_document_v1.pdf) and rewritten for repository-driven Codex work.

## Product Summary

Dudecide is a casual meeting operations app for friend groups, family gatherings, and hobby communities. It does not replace chat. It reduces decision fatigue around scheduling, location selection, and consensus tracking by structuring noisy group conversations into topics, decisions, evidence, and reports.

## Problem Statement

Users currently plan trips, dinners, camping, and club meetups inside general-purpose group chats. Important decision context gets buried inside jokes, photos, repeated questions, and fragmented polls. This creates:

- slow date and location decisions
- repeated questions from late participants
- loss of rationale behind decisions
- operational burden on one organizer

## Product Positioning

- Not a replacement messenger
- Not a generic poll app
- Not a settlement-first finance app
- A casual decision support layer for group planning

One-line positioning:

`A lightweight meeting operations app that turns long group chat discussions into structured topics, decisions, evidence, and actionable summaries.`

## Target Users

### Primary

- friend groups planning 1-night or longer trips
- social organizers managing dinners or outings
- hobby group operators running recurring events
- family planners coordinating multi-person schedules

### Early Adopter Profile

- already feels pain from KakaoTalk-style planning chaos
- willing to install or open a separate coordination surface if it saves time
- usually has one informal organizer carrying the planning burden

## Jobs To Be Done

- decide a date faster than in a normal group chat
- narrow location options without running many separate polls
- see what is already decided without rereading the full conversation
- understand why a decision was made through linked evidence
- reduce organizer workload with structured prompts, summaries, and nudges

## Product Principles

- Chat stays casual. Outcomes become structured.
- The product should reduce the number of votes, not multiply them.
- Current status must be visible immediately on entry.
- AI can propose and summarize, but humans confirm decisions.
- Every summary that matters should link back to message evidence.

## MVP Goal

Deliver a first experience where users say:

`It is easier and faster to decide date and location here than in our normal group chat.`

## MVP Scope

### P0

- meeting room creation
- invite link sharing
- topic-based discussion threads
- date availability matrix with recommendation scoring
- location and activity package comparison with at most 3 options
- decision board with statuses
- evidence links from decisions back to source messages

### P1

- AI thread summaries
- natural-language search across discussion and decisions
- report view summarizing decided, pending, and blocked items

### Excluded From MVP

- direct payment or wallet features
- live booking integrations
- voice or video chat
- enterprise-grade workspace and permission systems
- KakaoTalk crawling or unofficial API sync (user-initiated chat export and paste import is proposed in [`chat-import-planning.md`](./chat-import-planning.md))

## Core User Flows

### Flow 1: Create Meeting

1. Organizer selects meeting type.
2. Organizer enters title, expected group size, departure region, and rough budget.
3. System creates a meeting room and invite link.

### Flow 2: Decide Date

1. Participants submit `available`, `maybe`, or `unavailable` for candidate dates.
2. System ranks dates by hard conflicts, availability score, and uncertainty.
3. Organizer confirms the recommended date.
4. Decision board updates with evidence and participation summary.

### Flow 3: Decide Location

1. Participants enter preferences and constraints.
2. System compresses candidates into up to 3 location-plus-activity packages.
3. Participants evaluate each option with `good`, `okay`, or `dislike`.
4. System recommends the option with low resistance and acceptable fit.
5. Organizer confirms the final choice.

### Flow 4: Track Decisions

1. AI detects candidate decisions from thread language.
2. System creates a draft decision with evidence.
3. Organizer or operator accepts, edits, or keeps it in discussion.
4. Decision board and report update.

## Information Architecture

### Meeting Room

- Home
- Discussion
- Decisions
- Search
- Report

### Home

- progress indicator
- confirmed decisions
- pending decisions
- next required action

### Discussion

- date topic
- location topic
- lodging topic
- transport topic
- free chat

### Decisions

- proposed
- in discussion
- voting
- confirmed
- on hold
- needs reconsideration

### Search

- keyword search
- natural-language search
- person-based search
- decision-focused search

### Report

- current status
- confirmed items
- pending items
- unresolved blockers
- major rationale trail

## Functional Requirements

### Date Decision

- support availability matrix responses
- compute recommendation scores with conflict-sensitive weighting
- surface current participation completion
- allow early close when remaining votes cannot change outcome

### Location Decision

- collect constraints before opening comparison
- cap recommendation set to 3 packages
- support low-resistance scoring, not only single-choice winner logic
- preserve explicit pros, cons, and expected cost range per option

### Topic-Based Discussion

- allow a free-chat area to preserve casual interaction
- promote decision-relevant messages into structured topics
- recommend topic assignment from message content

### Decision Board

- show status for every active decision
- preserve rationale through evidence objects
- separate confirmed, active, blocked, and deferred decisions
- require human confirmation before finalizing AI-suggested decisions

### Search

- return messages, decisions, tasks, and candidates together
- support queries like why a candidate failed or who owns a task

### Report

- summarize confirmed and unconfirmed planning state
- link every major summary item to the detailed evidence trail

## Roles And Permissions

### Host

- confirm decisions
- close votes
- organize candidates
- publish reports

### Co-Host

- add candidates
- create votes
- approve draft decisions

### Participant

- comment
- vote
- suggest candidates
- inspect evidence

### Guest

- limited read access
- RSVP access through invite flow

## Decision Change Policy

- confirmed items should not be silently changed
- any change reopens the item as `needs reconsideration`
- the system must preserve reason, evidence, and change history

## AI Responsibilities

### Should Do

- classify messages by topic and intent
- extract candidates, conditions, objections, and tasks
- draft summaries and next actions
- identify decision-candidate language
- link summaries to message evidence

### Must Not Do

- auto-confirm decisions without a human approver
- erase minority objections
- generate unsupported summaries without evidence
- delete candidates arbitrarily

## Data Model Summary

Core entities:

- User
- Group
- Meeting
- Member
- TopicThread
- Message
- MessageAnchor
- Decision
- DecisionEvidence
- Vote
- VoteOption
- VoteResponse
- Task
- Summary
- Report
- Notification

Future entities:

- AttendanceInterval
- Expense
- ExpenseAllocationRule
- Settlement
- DepositPolicy
- DepositTransaction

## Success Metrics

- first decision completion rate after meeting creation
- time to date decision
- time to location decision
- participant voting rate
- decision board view rate
- evidence click-through rate
- 30-day reuse rate
- organizer satisfaction

## Key Risks

- separate-app adoption friction
- product tone becoming too much like a work tool
- inaccurate AI summaries lowering trust
- vote fatigue returning through poor interaction design
- organizer burden remaining too high
- financial or regulatory risk if money features arrive too early

## Open Product Questions

- How much of the MVP should be web-first to reduce install friction?
- What threshold defines "remaining votes cannot change the result" for early close?
- Should free chat live inside the same meeting room from day one or be deferred?
- What evidence UI creates trust without overwhelming non-technical users?
