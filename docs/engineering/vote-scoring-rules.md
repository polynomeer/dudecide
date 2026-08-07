# Vote Scoring Rules

This document defines the MVP scoring rules for date and location decisions.

## Scope

The MVP has two primary scoring models:

- date availability matrix scoring
- location package low-opposition scoring

The goal is not only to find the most liked option, but to find the option most likely to unblock the group quickly.

## Core Principles

- hard conflicts matter more than enthusiasm
- for group planning, low opposition often beats high peak preference
- unresolved votes should not prevent early close if the outcome cannot change
- scoring rules must be deterministic and stored by version

## Vocabulary

### Hard Conflict

For date voting, a `hard conflict` is one `unavailable` response.

### Soft Support

For date voting, a `maybe` response counts as weaker support than `available`.

### Opposition

For location voting, a `dislike` response counts as stronger negative signal than lack of enthusiasm.

## Date Matrix Scoring

### Allowed Responses

- `available`
- `maybe`
- `unavailable`

### Default Weights

- `available = 1.0`
- `maybe = 0.5`
- `unavailable = -2.0`

These weights are intentionally conflict-sensitive. One hard conflict should hurt more than one extra soft positive helps.

### Score Formula

For each candidate date:

```text
raw_score = (available_count * 1.0)
          + (maybe_count * 0.5)
          + (unavailable_count * -2.0)
```

### Ranking Rule

Dates are ranked by:

1. fewer `unavailable`
2. higher `raw_score`
3. higher `available_count`
4. higher `maybe_count`
5. earlier calendar date

This means a no-conflict date outranks a date with more enthusiasm but one hard blocker.

### Worked Example

Assume 5 participants.

#### Date A

- available: 4
- maybe: 1
- unavailable: 0

```text
raw_score = 4.0 + 0.5 + 0 = 4.5
```

#### Date B

- available: 5
- maybe: 0
- unavailable: 1

```text
raw_score = 5.0 + 0 - 2.0 = 3.0
```

Result:

- Date A ranks above Date B because zero hard conflicts outrank one blocker.

## Date Early-Close Rule

### Purpose

Close early when unresolved voters cannot change the winning date.

### Input

- current counts for each date
- number of unresolved participants

### Best-Case Swing Check

For each non-leading date, compute the maximum possible future score if every unresolved participant gave that date the best possible response and the leading date the worst possible response.

Assumptions for conservative early close:

- unresolved participant may still vote on every date
- best case for challenger = all unresolved become `available`
- worst case for leader = all unresolved become `unavailable`

### Early-Close Condition

The leader may close early only if every challenger still loses after the conservative swing simulation using the ranking rule order.

### Example

Two dates remain.

#### Leader: Date A

- available: 7
- maybe: 1
- unavailable: 0
- unresolved participants: 1

Current:

```text
Date A raw_score = 7 + 0.5 = 7.5
```

#### Challenger: Date B

- available: 5
- maybe: 2
- unavailable: 1

Current:

```text
Date B raw_score = 5 + 1.0 - 2.0 = 4.0
```

Best conservative swing:

- Date A gets one `unavailable`: `7.5 - 2.0 = 5.5`
- Date B gets one `available`: `4.0 + 1.0 = 5.0`

Date B still loses, so early close is allowed.

## Location Package Scoring

### Allowed Responses

- `good`
- `okay`
- `dislike`

### Default Weights

- `good = 1.0`
- `okay = 0.2`
- `dislike = -1.5`

These weights intentionally punish opposition more than they reward weak acceptance.

### Raw Score Formula

```text
raw_score = (good_count * 1.0)
          + (okay_count * 0.2)
          + (dislike_count * -1.5)
```

### Ranking Rule

Packages are ranked by:

1. fewer `dislike`
2. higher `raw_score`
3. higher `good_count`
4. lower expected max budget
5. shorter travel time

This supports the product rule that the least resisted option is often better for groups than the most loved but polarizing one.

### Worked Example

Assume 8 participants.

#### Package A

- good: 4
- okay: 3
- dislike: 1
- max budget: 220000
- travel: 120 min

```text
raw_score = 4 + 0.6 - 1.5 = 3.1
```

#### Package B

- good: 5
- okay: 0
- dislike: 3
- max budget: 180000
- travel: 90 min

```text
raw_score = 5 + 0 - 4.5 = 0.5
```

Result:

- Package A wins because low opposition outweighs B's stronger top-line enthusiasm.

## Location Early-Close Rule

Use the same conservative swing logic as date voting.

Leader may close early only if every challenger still loses after:

- challenger receives all unresolved votes as `good`
- leader receives all unresolved votes as `dislike`

Then apply the package ranking rule.

## Tie-Break Rules

### Date Tie

If top dates remain tied after the full ranking sequence:

1. prefer the earlier date
2. if still tied because same date range object differs only by metadata, host decides

### Location Tie

If top packages remain tied after ranking:

1. prefer lower `dislike`
2. prefer lower maximum estimated cost
3. prefer shorter travel time
4. host decides

## Candidate Compression Rule

Location package voting should start only after candidate compression.

Rules:

- maximum 3 packages in MVP
- compression should remove near-duplicates
- package payload must include budget and travel assumptions for tie-breaking

## Vote Closure Output

When a vote closes, the backend should persist:

- score version identifier
- per-option counts
- computed raw score
- winner id
- early-close or deadline-close reason
- tiebreak reason if used

## Recommended Stored `scoring_rule` Payload

### Date Matrix

```json
{
  "version": "date-v1",
  "weights": {
    "available": 1.0,
    "maybe": 0.5,
    "unavailable": -2.0
  },
  "rankingOrder": [
    "fewest_unavailable",
    "highest_raw_score",
    "highest_available",
    "highest_maybe",
    "earliest_date"
  ]
}
```

### Package Preference

```json
{
  "version": "package-v1",
  "weights": {
    "good": 1.0,
    "okay": 0.2,
    "dislike": -1.5
  },
  "rankingOrder": [
    "fewest_dislike",
    "highest_raw_score",
    "highest_good",
    "lowest_max_budget",
    "shortest_travel_time"
  ]
}
```

## Implementation Notes

- scoring logic should live in dedicated domain services
- use exact decimal arithmetic in backend code where practical
- write executable examples matching this document
- do not bury weights in frontend code
- any future weight change must create a new scoring version
