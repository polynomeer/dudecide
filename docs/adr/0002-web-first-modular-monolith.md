# ADR 0002: Web-First Modular Monolith

## Status

Accepted

## Date

2026-08-07

## Context

Dudecide needs to validate a consumer planning workflow quickly. The main product risk is whether users prefer structured meeting decisions over generic group chat, not whether the system can scale to high concurrent traffic on day one.

We need an architecture that:

- ships quickly
- preserves clean domain boundaries
- supports realtime updates
- integrates async AI processing
- avoids premature distributed-system overhead

## Decision

The MVP will use:

- a web-first client
- a modular monolith backend
- SSE for realtime updates
- PostgreSQL as the transactional source of truth
- Redis for ephemeral coordination and queue support
- async workers for AI, notification, and report refresh

## Consequences

### Positive

- fast implementation and iteration speed
- simpler consistency for decision and evidence writes
- easier local development and test setup
- clear path to later extraction of AI and search workloads

### Negative

- some module boundaries will be process-local rather than network-enforced
- SSE may need replacement if bidirectional realtime features expand
- a later client platform expansion will require rethinking shared UI architecture

## Follow-Up

- document the initial service skeleton and package boundaries
- add an ADR later if the product moves from web-first to native-first
