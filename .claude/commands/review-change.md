---
description: Review a change using the repository review template
argument-hint: [commit range, branch, or paths; defaults to working tree diff]
---

Review target: $ARGUMENTS

Follow `.codex/prompts/review.md` for this task. If no target is given, review `git diff HEAD`. Do not edit files. Report findings to the user in Korean, ordered by severity, citing files and lines.
