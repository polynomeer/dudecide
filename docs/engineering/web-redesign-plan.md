# Web Redesign Plan

Status: In progress (2026-10-21).

Apply the approved web design to `apps/web`. The design lives in the "웹 시안" page of the design canvas (private Claude artifact `3sVk5XAhY2RjnrAjcdoPKa`, boards W-1 to W-6 plus dark variants). The mobile boards on the "화면 흐름" page are the reference for screens the web set does not cover.

## Scope

- Visual and layout changes only. Data fetching, mutations, routing, and API contracts stay as they are.
- Light and dark themes from the same token set.
- Every existing route keeps working; screens without a dedicated web board get the shared primitives so the app looks consistent.
- Out of scope: new backend endpoints, new features that the API does not support yet (listed under Data Gaps).

## Design Tokens

| Token | Light | Dark | Use |
| --- | --- | --- | --- |
| `--bg` | `#F3F4F1` | `#0E0F12` | page background |
| `--surface` | `#FFFFFF` | `#1A1C21` | cards, panels |
| `--surface-2` | `#F3F4F1` | `#23262D` | inset boxes (evidence, inputs) |
| `--ink` | `#15171C` | `#F2F3F0` | primary text, primary button |
| `--muted` | `#5B606B` | `#A2A7B1` | secondary text |
| `--line` | `#E1E3DE` | `#2C2F37` | borders, dividers |
| `--accent` | `#2D46F5` | `#3550F0` | next-action card, primary accent fill |
| `--accent-text` | `#2235C9` | `#B3BEFF` | accent text, links |
| `--accent-soft` | `#E6E9FF` | `#252C55` | voting / needs-review chips |
| `--warn-soft` / `--warn-ink` | `#FFF1D6` / `#8A5A00` | `#3A2F14` / `#F5C451` | in discussion, open question |
| `--risk-soft` / `--risk-ink` | `#FFF6F1` / `#7A2A08` | `#2A1C15` / `#FFB38A` | objection, reconsider |

- Fonts: Gothic A1 (display, 900), IBM Plex Sans KR (body), IBM Plex Mono (labels, numbers) via `next/font/google`.
- Radii: 12px controls, 14–20px cards, 999px chips. Touch targets at least 44px.
- Status chips: confirmed = ink fill, voting/needs review = accent soft, in discussion = warn soft, reconsider = risk soft, on hold = dashed outline.
- Theme: follows `prefers-color-scheme`; a header toggle stores an explicit choice in `localStorage` and sets `data-theme` on `<html>`.
- Logo: check speech bubble symbol (`components/brand/logo.tsx`), shared by header, favicon, and report.

## Board To Route Mapping

| Board | Route | Main components |
| --- | --- | --- |
| W-1 Landing | `/` | `app/page.tsx`, `marketing-hero.tsx`, `site-header.tsx` |
| W-2 Meeting dashboard | `/meetings/[meetingId]` | new `meeting-shell.tsx` (top bar + sidebar) in `meetings/[meetingId]/layout.tsx`, `meeting-overview.tsx` |
| W-3 Decision detail, 3 panes | `/meetings/[meetingId]/decisions` | `decision-board.tsx` |
| W-4 Shared report | `/meetings/[meetingId]/reports/latest`, `/invite/[token]` preview | `report-workspace.tsx`, `import/invite-preview.tsx` |
| W-5 Chat import | `/import`, `/meetings/[meetingId]/import` | `import/import-wizard.tsx` |
| W-6 Import review | `/meetings/[meetingId]/import/[importId]` | `import/import-review.tsx` |
| Mobile reference | threads, votes, tasks, notifications, search, start, new meeting, invite claim | existing components restyled with shared primitives |

## Work Units

Each unit is one commit (Conventional Commits, `web` scope) and must pass `npm run typecheck` and `npm run test` in `apps/web`.

1. **Tokens and primitives**: `globals.css` tokens (light/dark), Tailwind color mapping, fonts in `app/layout.tsx`, theme toggle, logo, shared classes (`panel`, `chip-*`, `btn-*`, `field`).
2. **Landing (W-1)**: header, hero with before/after example, how it works, features, privacy band, CTA, footer.
3. **Meeting shell (W-2 frame)**: top bar (meeting title, search entry, notifications, theme toggle) and sidebar (home, threads, decisions, report, import, members) wrapping every meeting route.
4. **Dashboard (W-2 content)**: next action card, progress, decision board columns by status, recent activity built from available data.
5. **Decision detail (W-3)**: list / detail / source thread panes, evidence focus highlighting, confirm and reconsider actions, history.
6. **Report (W-4)**: summary hero, confirmed / remaining / blocked sections, tasks table, member vs. invite preview states.
7. **Import (W-5, W-6)**: wizard steps on one wide page, review grouped by category with the apply summary panel.
8. **Remaining screens**: threads, votes, tasks, notifications, search, start, new meeting, invite join, error and empty states.
9. **Verification and docs**: update unit and E2E selectors where copy changed, run Playwright smoke tests when the stack is available, update `frontend-implementation-plan.md`.

## Data Gaps

Shown only when the API provides the data; otherwise the element is omitted, not faked.

- D-day countdown and trip dates: `MeetingDetailResponse` has no event date.
- Recent activity feed: no activity endpoint; build from decision history and notifications where possible.
- Member avatars: `memberCount` only, no member list in the meeting response.
- Support/objection counts on a decision: derive from evidence types when present.
- Natural-language search hint in the top bar routes to the existing keyword search.

## Risks

- `apps/web` had 33 uncommitted files when this plan was written. They must be committed, stashed, or discarded by the user before unit 1 so redesign commits do not mix with unrelated changes.
- Copy changes can break text-based test selectors; fix them in the same unit that changes the copy.
