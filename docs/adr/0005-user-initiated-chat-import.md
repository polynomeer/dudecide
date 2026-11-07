# ADR 0005: User-Initiated Chat Import

## Status

Accepted

## Date

2026-10-18

## Context

Asking a whole group to move its planning conversation into Dudecide is the largest adoption risk. Most groups keep talking in KakaoTalk. Importing an existing conversation lets one organizer get value alone and pulls the rest of the group in through a shared report.

KakaoTalk offers no general API to read group chat messages. The only compliant inputs are files and text the user exports or copies themselves. Imported chats contain personal data of people who did not upload them, so the input path and data handling must be fixed before implementation.

Detailed plan: [`../product/chat-import-planning.md`](../product/chat-import-planning.md). Design: [`../engineering/chat-import-design.md`](../engineering/chat-import-design.md).

## Decision

- Accept chat content only through user-initiated paths: messenger export files, pasted text, and later official bot APIs and OS share sheets.
- Never crawl, use unofficial clients, scrape through accessibility services, or post into chat rooms without a user action.
- Parse raw input in the browser. The raw file is never uploaded; only the selected range, normalized and masked, reaches the server.
- Mask phone numbers, bank account numbers, card numbers, resident registration numbers, and emails before upload.
- Send speakers to the LLM as pseudonyms only.
- Store imported messages in the existing `message` table with an `imported` origin so evidence, search, and reports reuse one model.
- Imported content lands as `proposed` decisions with evidence; AI never confirms (ADR 0003).
- Unconverted guest imports are purged after 7 days; the uploader can delete an import and all evidence derived from it at any time.
- Imported content is not used for model training.

## Consequences

### Positive

- single-player value and a share-back acquisition loop without platform risk
- a clear privacy story: raw chat stays on the device
- no parallel message model for evidence, search, and reports

### Negative

- parsers live in TypeScript and must track messenger format changes
- the server cannot reparse raw input to fix a parser bug; users must re-import
- `message.sender_user_id` becomes nullable, which touches existing message code
- deleting an import can remove evidence from confirmed decisions; this must be explained in the UI

## Follow-Up

- legal review under the Personal Information Protection Act before public launch
- privacy policy update disclosing LLM processing and retention
