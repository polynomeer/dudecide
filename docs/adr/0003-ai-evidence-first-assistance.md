# ADR 0003: AI Evidence-First Assistance

## Status

Accepted

## Date

2026-08-07

## Context

Dudecide depends on AI to reduce planning friction through classification, summaries, and decision suggestions. The product will lose trust quickly if AI states unsupported conclusions or appears to make decisions on behalf of the group.

We need a consistent operating rule for how AI participates in the product.

## Decision

AI will be used as an evidence-first assistant with the following rules:

- AI may classify, extract, summarize, and suggest
- AI may not finalize decisions without human confirmation
- user-visible summaries and suggestions that affect decision making must include evidence links
- disagreement and uncertainty must be preserved rather than flattened into false consensus
- AI processing should be asynchronous and auditable

## Consequences

### Positive

- stronger trust model for decision summaries
- easier debugging when users dispute AI outputs
- clearer product boundary between assistance and authority

### Negative

- some flows will remain slower than full automation
- evidence storage and audit metadata increase system complexity
- prompt and evaluation design become product-critical infrastructure

## Follow-Up

- define prompt and schema versions in code
- build an evaluation dataset for classifier and summary quality
- ensure decision detail UI exposes evidence clearly
