# Observability Clean Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the split diagnostics/activity implementation with one readable observability subsystem while preserving source compatibility through thin adapters.

**Architecture:** `ObservabilityRuntime` is the only composition point. Focused journal, attachment, status, and report classes own persistence and reporting separately. Existing diagnostics and activity APIs delegate to the new runtime and contain no business logic.

**Tech Stack:** Kotlin, Java 8-compatible API, Gson, JUnit 5, Gradle multi-project build.

**Spec:** `docs/superpowers/specs/2026-10-03-observability-clean-architecture-design.md`

## Global Constraints

- Preserve existing plugin source and binary compatibility where default interface methods allow it.
- Do not add user-configurable observability policy.
- Keep unrelated `BukkitControlCommand.kt` and crash-dump changes untouched.
- Preserve Java 8 compatibility for published APIs.
- All persisted files remain under `plugins/pnLibrary/observability/`.
- One diagnostic incident creates one observation.

## Review Focus

- A missing or unreadable attachment must produce a useful error without corrupting the journal.
- A malformed JSONL line must not prevent valid later observations from loading.
- Clearing an expired observation must remove its attachment and manifest entry.
- Compatibility calls through `diagnostics` and `activity` must reach the same runtime once.
- Report size or encryption failure must preserve existing local-report guarantees.

---

### Task 1: Public observability model and DSL

**Files:**
- Create: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/observability/ObservabilityService.kt`
- Create: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/observability/Observation.kt`
- Create: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/observability/ObservationQuery.kt`
- Create: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/observability/ObservationScope.kt`
- Modify: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/runtime/PnLibrary.kt`
- Test: `modules/api/src/test/kotlin/ru/privatenull/pnlibrary/api/observability/ObservabilityDslTest.kt`

**Interfaces:**
- Produces: `ObservabilityService.capture`, `failure`, `status`, `recent`, and report-facing snapshot types.
- Consumes: Java `Path` and existing diagnostic severity concepts only where compatibility requires them.

- [ ] Write tests proving `capture { files(path); data(...) }` and `failure(error) { ... }` need no event ID or MIME type.
- [ ] Run `./gradlew.bat :modules:api:test --no-daemon` and verify the new tests fail before implementation.
- [ ] Implement the focused public model and DSL with readable builders and immutable results.
- [ ] Run API tests and verify they pass.
- [ ] Commit the public API change.

### Task 2: Journal and attachment persistence

**Files:**
- Create: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/observability/ObservationJournal.kt`
- Create: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/observability/AttachmentStore.kt`
- Create: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/observability/ObservationRetention.kt`
- Test: `modules/core/src/test/kotlin/ru/privatenull/pnlibrary/core/observability/ObservationPersistenceTest.kt`

**Interfaces:**
- Consumes: immutable `Observation` and `Path` inputs from Task 1.
- Produces: journal snapshots, attachment metadata, attachment byte sources, and retention cleanup results.

- [ ] Write failing tests for reload, malformed JSONL, inferred file type, executable rejection, expiry, and orphan deletion.
- [ ] Run the focused tests and confirm failure.
- [ ] Implement the journal and attachment store with small named methods and no reporting knowledge.
- [ ] Run focused and core tests.
- [ ] Commit persistence changes.

### Task 3: Status registry and unified runtime

**Files:**
- Create: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/observability/ComponentStatusRegistry.kt`
- Create: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/observability/ObservabilityRuntime.kt`
- Test: `modules/core/src/test/kotlin/ru/privatenull/pnlibrary/core/observability/ObservabilityRuntimeTest.kt`

**Interfaces:**
- Consumes: API DSL and persistence interfaces from Tasks 1–2.
- Produces: the concrete `ObservabilityService` and one normalized observation per call.

- [ ] Write failing tests for capture, failure, status replacement, thread safety, and exactly-once diagnostic recording.
- [ ] Run focused tests and confirm failure.
- [ ] Implement runtime orchestration and status storage.
- [ ] Run focused and core tests.
- [ ] Commit runtime changes.

### Task 4: Compatibility adapters and composition root

**Files:**
- Modify: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/runtime/PnLibraryImpl.kt`
- Modify: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/runtime/PnLibraryBootstrap.kt`
- Modify: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/diagnostics/DiagnosticsRegistry.kt`
- Replace: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/diagnostics/UnifiedObservabilityService.kt`
- Test: `modules/core/src/test/kotlin/ru/privatenull/pnlibrary/core/observability/CompatibilityAdapterTest.kt`

**Interfaces:**
- Consumes: `ObservabilityRuntime` from Task 3.
- Produces: `PnLibrary.observability` and thin deprecated `diagnostics`/`activity` adapters.

- [ ] Write failing tests that all three entry points share one event and one lifecycle.
- [ ] Run focused tests and confirm failure.
- [ ] Replace callback wiring with direct runtime delegation and deprecate compatibility properties.
- [ ] Run bootstrap and core tests.
- [ ] Commit compatibility migration.

### Task 5: Binary report assembly

**Files:**
- Create: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/observability/BinaryReportBuilder.kt`
- Create: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/observability/ReportStore.kt`
- Modify: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/diagnostics/ReportGenerator.kt`
- Test: `modules/core/src/test/kotlin/ru/privatenull/pnlibrary/core/observability/BinaryReportBuilderTest.kt`

**Interfaces:**
- Consumes: snapshots from journal, attachments, statuses, platform collector, encryption, and uploader.
- Produces: one stored `DiagnosticReport` without activity-specific callbacks.

- [ ] Write failing archive-content, size-limit, encryption-failure, and upload-failure tests.
- [ ] Run focused tests and confirm failure.
- [ ] Move archive assembly into `BinaryReportBuilder`; reduce `ReportGenerator` to orchestration or remove it if empty.
- [ ] Run report and core tests.
- [ ] Commit report cleanup.

### Task 6: Remove obsolete implementation and document the result

**Files:**
- Remove after migration: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/activity/ActivityJournalService.kt`
- Remove after migration: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/activity/ActivityService.kt` implementation logic, retaining only compatibility declarations if required.
- Update: `docs/observability.md`
- Update: `docs/superpowers/specs/2026-10-03-observability-clean-architecture-design.md` only if implementation uncovered a clarified invariant.

**Interfaces:**
- Consumes: completed Tasks 1–5.
- Produces: a repository with one implementation path and current usage documentation.

- [ ] Use `rg` to prove production logic no longer references the obsolete activity implementation or callback names.
- [ ] Update documentation with the final package map and Kotlin/Java examples.
- [ ] Run `./gradlew.bat :modules:api:test :modules:core:test :platforms:bukkit:runtime:build --no-daemon`.
- [ ] Build the distributable Bukkit JAR and copy it to the established test-server plugin path.
- [ ] Inspect `git status` and preserve unrelated user files.
- [ ] Commit and push the final cleanup.
