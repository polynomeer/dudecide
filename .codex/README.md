# Codex Assets

This folder stores reusable repository-local assets for Codex tasks.

## Structure

- [`prompts/feature.md`](./prompts/feature.md): feature implementation kickoff template
- [`prompts/bugfix.md`](./prompts/bugfix.md): bug investigation and fix template
- [`prompts/review.md`](./prompts/review.md): review-oriented validation template

## Usage

- copy the closest template into a new task prompt and fill in repository-specific details
- pair prompt templates with roles in [`.agents/`](../.agents/README.md) when parallelizing work
- update these templates when the repository gains code, build tools, or operational checks
