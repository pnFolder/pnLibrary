# pnUpdate Transaction Implementation Plan

**Goal:** Safely verify, stage, activate, commit, and roll back an atomic multi-component update.

**Architecture:** Transaction behavior lives in `pnlibrary-feature-update`. A verifier accepts an artifact only after bounded-size, SHA-256, file-name, and embedded metadata checks. A durable JSON journal records every transition. Activation first backs up the complete current component set, then replaces all targets; recovery restores the complete set whenever activation or the following health check does not commit.

**Constraints:** JVM 8 compatible APIs; no live network in tests; paths remain under caller-provided transaction roots; atomic file replacement where supported; idempotent recovery after process interruption.

## Task 1: Artifact verification

- Test exact hash, size, identity, metadata and path constraints.
- Implement streaming bounded verification of staged JARs.

## Task 2: Durable journal and staging

- Test journal transitions and restart loading.
- Implement immutable records with atomic JSON persistence.

## Task 3: Activation and rollback

- Test successful commit and injected failures after each transition.
- Back up and replace the whole plan, then restore the whole previous set on any incomplete activation or failed health check.

## Task 4: Integration verification

- Run feature tests, full tests, ABI checks and distribution build.
- Inspect packaged update implementation and commit the completed transaction engine.
