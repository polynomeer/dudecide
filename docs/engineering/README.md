# Engineering Docs

Use this folder for implementation-facing documentation.

## Add Here

- local setup
- API contracts
- Codex workflows and task orchestration
- data model notes
- deployment or operations runbooks
- testing strategy

When code is added to the repository, create focused docs here instead of overloading the root README.

## Current Files

- [`codex-workflows.md`](./codex-workflows.md): repository conventions for subagents, prompt reuse, and work-unit commits
- [`tech-stack.md`](./tech-stack.md): concrete implementation stack choices for the MVP
- [`architecture-overview.md`](./architecture-overview.md): target MVP architecture and subsystem boundaries
- [`frontend-implementation-plan.md`](./frontend-implementation-plan.md): frontend scope, route design, and phased delivery order
- [`local-development.md`](./local-development.md): local infra and backend development commands
- [`dev-seed-data.md`](./dev-seed-data.md): reusable local sample meeting and vote dataset
- [`database-schema.md`](./database-schema.md): initial Flyway schema and relational design notes
- [`data-model-erd.md`](./data-model-erd.md): ERD and storage modeling notes
- [`decision-state-machine.md`](./decision-state-machine.md): authoritative decision lifecycle and transition rules
- [`vote-scoring-rules.md`](./vote-scoring-rules.md): date and location vote scoring formulas and early-close rules
- [`api-and-events.md`](./api-and-events.md): API surface and async event contracts
- [`ai-pipeline.md`](./ai-pipeline.md): AI extraction, summary, and decision suggestion flow
- [`mvp-delivery-plan.md`](./mvp-delivery-plan.md): implementation phases, milestones, and testing focus
