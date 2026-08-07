# Decision State Machine

This document defines the authoritative decision lifecycle for Dudecide MVP.

## Scope

This state machine applies to `decision` records in the MVP domain.

It defines:

- valid decision states
- who may trigger transitions
- required preconditions
- side effects that must happen on transition

## States

- `proposed`
- `in_discussion`
- `voting`
- `confirmed`
- `on_hold`
- `needs_reconsideration`

## State Intent

### `proposed`

- a candidate decision exists
- evidence may already exist
- no active team commitment yet

Typical entry:

- manual creation by host or co-host
- AI suggestion accepted as a draft
- message or thread context promoted into a decision object

### `in_discussion`

- the topic is actively being discussed
- evidence and candidate values may still change
- there may be no vote yet

### `voting`

- an active vote is open for this decision
- decision outcome is constrained by vote rules and deadlines

### `confirmed`

- the decision is authoritative for the meeting
- downstream planning should treat it as current truth

### `on_hold`

- the decision cannot progress until another dependency is resolved
- the item remains visible, but no active resolution is expected right now

### `needs_reconsideration`

- a previously stable decision must be reopened because a condition materially changed
- old history and evidence remain part of the audit trail

## Allowed Actors

- `host`
- `co_host`
- `participant`
- `ai_system`
- `worker_system`

Rules:

- `participant` cannot directly confirm a decision
- `ai_system` and `worker_system` cannot finalize a decision
- system actors may suggest or move state only where explicitly allowed

## Transition Table

| From | To | Allowed Actor | Preconditions | Required Side Effects |
| --- | --- | --- | --- | --- |
| none | `proposed` | `host`, `co_host`, `worker_system` | decision payload and target thread exist | create decision history row |
| `proposed` | `in_discussion` | `host`, `co_host`, `worker_system` | discussion has begun or host explicitly opens discussion | append history row |
| `proposed` | `voting` | `host`, `co_host` | vote created and linked to decision | append history row, link vote |
| `proposed` | `confirmed` | `host` | confirmation evidence exists and no required vote is missing | set `confirmed_result`, `decided_at`, append history |
| `proposed` | `on_hold` | `host`, `co_host` | blocking dependency recorded | append history with reason |
| `in_discussion` | `voting` | `host`, `co_host` | vote created and opened | append history |
| `in_discussion` | `confirmed` | `host` | enough evidence exists and host explicitly confirms | set `confirmed_result`, `decided_at`, append history |
| `in_discussion` | `on_hold` | `host`, `co_host` | blocking dependency recorded | append history with reason |
| `in_discussion` | `proposed` | `host`, `co_host` | decision should be demoted to draft | append history |
| `voting` | `confirmed` | `host`, `worker_system` | vote closed or early-close satisfied and result mapped to decision payload | set `confirmed_result`, `decided_at`, append history |
| `voting` | `in_discussion` | `host`, `co_host` | vote invalidated or reopened for discussion | append history, mark vote superseded if needed |
| `voting` | `on_hold` | `host`, `co_host` | dependency prevents continued resolution | append history |
| `confirmed` | `needs_reconsideration` | `host`, `co_host`, `worker_system` | material condition changed | append history with reason |
| `confirmed` | `on_hold` | `host` | confirmed decision cannot currently execute due to dependency | append history |
| `needs_reconsideration` | `in_discussion` | `host`, `co_host` | re-opened discussion has started | append history |
| `needs_reconsideration` | `voting` | `host`, `co_host` | re-resolution requires formal vote | append history |
| `needs_reconsideration` | `confirmed` | `host` | host reconfirms without needing vote | update `confirmed_result`, `decided_at`, append history |
| `on_hold` | `in_discussion` | `host`, `co_host`, `worker_system` | blocking dependency resolved | append history |
| `on_hold` | `voting` | `host`, `co_host` | hold removed and vote opened | append history |
| `on_hold` | `confirmed` | `host` | decision can now be finalized directly | set `confirmed_result`, `decided_at`, append history |

## Forbidden Transitions

These transitions are not allowed in MVP:

- `confirmed -> proposed`
- `confirmed -> voting` directly
- `proposed -> needs_reconsideration`
- `on_hold -> proposed`
- any transition to `confirmed` by `participant`, `ai_system`, or `worker_system` without explicit host-approved rule

## Transition Semantics

### Draft Creation

When AI suggests a decision from message content:

- create state as `proposed`
- attach evidence rows
- never set `confirmed_result`

### Voting Start

Moving to `voting` requires:

- linked `vote` row
- vote status of `open` or imminent open
- scoring rule persisted in `vote.scoring_rule`

### Confirmation

Moving to `confirmed` requires:

- at least one evidence row
- final result payload
- actor with `host` authority, unless auto-confirm from vote closure is explicitly host-approved by product rule

### Reconsideration

`needs_reconsideration` is used only when a previously stable decision is no longer safe to treat as current truth.

Examples:

- selected lodging sold out
- participant count changed enough to invalidate budget
- weather makes chosen activity impossible

## Side Effects By Transition

### All State Changes

- write one `decision_history` row
- emit domain event
- publish SSE update for decision board refresh

### Enter `voting`

- ensure linked vote exists
- emit `VoteCreated` or `VoteOpened` event if not already emitted

### Enter `confirmed`

- set `decision.confirmed_result`
- set `decision.decided_at`
- emit `DecisionConfirmed`
- trigger report refresh
- trigger next-action generation if applicable

### Enter `needs_reconsideration`

- emit `DecisionReconsiderationRequested`
- mark stale downstream summaries for refresh

## Actor Rules

### Host

- may perform every transition except system-only suggestion creation

### Co-Host

- may create drafts
- may move items into discussion, voting, and hold states
- may not directly confirm unless future product policy changes

### Participant

- may contribute messages, evidence-worthy content, and votes
- may not change the decision lifecycle directly

### Worker System

- may create `proposed` decisions
- may move `proposed -> in_discussion` if policy enables auto-promotion
- may move `confirmed -> needs_reconsideration` only when a deterministic rule flags invalidation
- may complete `voting -> confirmed` only if the vote result is host-authorized by configured rule

## Validity Checks

Before writing a transition, the service must verify:

- actor has meeting role permission
- source state matches current persisted state
- linked meeting and thread are still active
- vote linkage is valid if target state is `voting` or `confirmed` through vote resolution

## Worked Examples

### Example 1: Date Decision

1. host creates `Trip date` as `proposed`
2. participants discuss candidate dates
3. host opens date matrix vote and state becomes `voting`
4. vote closes with one clear winner
5. system resolves result and host-confirm rule marks state `confirmed`

### Example 2: Lodging Dependency

1. `Lodging` begins as `proposed`
2. discussion reveals location is not final
3. co-host moves item to `on_hold`
4. once location is confirmed, lodging returns to `in_discussion`

### Example 3: Reconsideration

1. location decision is `confirmed`
2. chosen package becomes unavailable
3. worker flags invalidation and host accepts reason
4. state becomes `needs_reconsideration`
5. host reopens voting

## Implementation Notes

- implement transition rules in one service layer, not scattered across controllers
- encode allowed transitions in executable tests
- store transition reason when entering `on_hold` or `needs_reconsideration`
- never infer `confirmed` only from message language without explicit rule path
