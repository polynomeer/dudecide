# Live Decision Suggestions

Status: implemented (2026-11-01).

Topic conversations get an AI card ("결정 후보를 찾았어요") when recent messages look like a decision. A host turns the card into a decision draft with its evidence attached, or dismisses it. This is the "Decision Suggestion Detection" stage of [`ai-pipeline.md`](./ai-pipeline.md) applied to live messages.

## Flow

1. Posting a message updates `topic_thread.last_activity_at` (existing behavior).
2. `DecisionSuggestionWorker` polls every minute for threads that are quiet and have enough new messages since their last analysis, then claims one by setting `topic_thread.suggestions_analyzed_at`.
3. It loads the latest window of messages in that thread, pseudonymizes speakers (`Participant A`, `Participant B`, …), and runs the same `ChatAnalysisPipeline` as chat import (prefilter, segment, map, reduce, status rules).
4. Decision items with status `likely_decided` or `contested` become `decision_suggestion` rows (`pending`), unless the thread already has a decision or a suggestion with the same type and title.
5. New suggestions publish `thread.updated` on the meeting stream and notify hosts and co-hosts (`decision_suggested`).
6. A host or co-host accepts (creates a `proposed` or `in_discussion` decision in the same thread, with the evidence) or dismisses the suggestion.

## Cost Policy

Live analysis calls the configured LLM provider (`dudecide.ai.provider`, `fake` by default), so it is rate-limited by design:

| Setting (`dudecide.suggestions.*`) | Default | Effect |
| --- | --- | --- |
| `enabled` | `true` | turns the worker off entirely |
| `quiet-minutes` | `3` | wait until the conversation pauses, so one burst of chat is one analysis |
| `min-new-messages` | `4` | skip threads with only a few new messages |
| `window-messages` | `60` | analyze at most this many recent messages |
| `daily-per-meeting` | `20` | cap analyses per meeting per rolling 24 hours |
| `max-pending-per-thread` | `3` | stop analyzing a thread while this many suggestions wait for a host |

One analysis is one map call per episode in the window (usually one or two) plus one reduce call when subjects were found.

## Privacy

- Speaker names never go to the provider; only pseudonyms and message text in the window.
- Suggestions only store message ids and roles. Quotes are snapshotted into `decision_evidence` when a host accepts, using the speaker's member name.
- Read-only import threads are skipped; their content was already analyzed at import time.

## Deferred

- Suggesting a better topic for a message ("교통 토픽으로 옮길까요?") needs a per-message classification call and is left for later.
