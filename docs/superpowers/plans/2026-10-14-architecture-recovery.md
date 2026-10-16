# pnLibrary Architecture Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Reduce pnLibrary from a tightly coupled runtime monolith into a set of explicit API, feature, core, platform, and distribution boundaries without a risky rewrite.

**Architecture:** Stabilize behavior first, then introduce typed contracts and dependency rules, then migrate features one at a time. The existing public API remains source-compatible during the migration through deprecated adapters; removal happens only after a documented migration window.

**Tech Stack:** Kotlin/JVM 8, Java, Gradle Kotlin DSL, JUnit, Bukkit/Bungee/Velocity adapters, binary-compatibility-validator.

**Spec:** This plan is based on the audit of `pnLibrary` at `main` on 2026-10-14; the audit findings are the specification for this recovery.

## Global Constraints

- Preserve Java 8 compatibility for all modules except the existing Velocity target.
- Keep Bukkit, BungeeCord, and Velocity distributions buildable after every phase.
- Do not change published public API signatures without a deprecated compatibility path.
- No diagnostic, log, test fixture, or documentation example may contain a real credential.
- Every phase ends with `./gradlew test`, API validation, and a focused architecture review.

## Review Focus

- A feature must be usable without pulling every other feature into `core`; enforce this with dependency tests.
- Update state transitions must have one vocabulary and reject invalid transitions; test all transitions.
- Diagnostic collection must remain bounded and privacy-safe when a platform call fails; test partial failure and sensitive mode.
- Runtime-created executors must close deterministically; test shutdown and rejected work.
- Each distribution must contain exactly its declared platform features; test artifact contents.

## Target module layout

```text
modules/common
modules/api-core              # small public runtime/lifecycle contracts
modules/runtime-spi           # platform-to-core contracts only
modules/features/*-api        # feature contracts
modules/features/*-runtime    # feature implementations
modules/core-runtime          # composition and lifecycle
platforms/*/api
platforms/*/runtime
distribution                  # manifest-driven assembly
```

The current modules may be migrated incrementally; do not rename all modules in one commit.

### Task 1: Freeze the baseline and add architecture checks

**Files:**
- Create: `docs/architecture/DEPENDENCY-RULES.md`
- Create: `build-logic` or an existing test location for dependency checks
- Modify: `settings.gradle.kts`
- Test: architecture tests covering registered projects and forbidden edges

**Interfaces:**
- Produces a machine-checkable list of allowed dependency directions.

- [ ] Record the current project graph and identify `modules/remote-check/bukkit` as either an explicitly included project or an explicitly excluded legacy directory.
- [ ] Add a test that fails when a source directory containing `build.gradle.kts` is not registered in `settings.gradle.kts`.
- [ ] Add rules forbidding platform code from depending on `modules:internal:*` except through the approved adapter boundary.
- [ ] Add rules forbidding feature runtime modules from depending on platform runtime modules.
- [ ] Run `./gradlew test` and commit the baseline checks.

### Task 2: Remove ambiguity from update states

**Files:**
- Modify: `modules/api/src/main/kotlin/.../api/updates/UpdateService.kt`
- Modify: `modules/core/src/main/kotlin/.../core/updates/RegisteredUpdate.kt`
- Modify: `modules/core/src/main/kotlin/.../core/updates/UpdateOrchestrator.kt`
- Modify: `platforms/*/runtime/**/Update*.kt`
- Test: existing update API, orchestrator, renderer, lifecycle, and persistence tests

**Interfaces:**
- Produces one canonical `UpdateState` vocabulary: `CURRENT`, `AVAILABLE`, `CHECKING`, `DOWNLOADING`, `STAGED`, `BLOCKED`, `INCOMPATIBLE`, `FAILED`, `FROZEN`, `ROLLED_BACK`.
- Deprecated aliases may translate old names, but internal code must use canonical values only.

- [ ] Write transition tests for check, download, stage, rollback, freeze, failure, and restart recovery.
- [ ] Replace duplicate states and update all consumers to canonical names.
- [ ] Add an invariant test that every published state has a legal predecessor/successor set.
- [ ] Run focused update tests, then the full suite; commit as one behavior-preserving change.

### Task 3: Establish lifecycle and executor ownership

**Files:**
- Create: `modules/runtime-spi/src/main/kotlin/.../RuntimeExecutors.kt`
- Modify: `modules/core/**/UpdateServiceImpl.kt`
- Modify: `modules/features/currency-runtime/**/JdbcCurrencyStorage.kt`
- Modify: `modules/features/minecraft-localization/**/DefaultMinecraftLocalization.kt`
- Modify: runtime shutdown/composition files
- Test: executor lifecycle and shutdown tests

**Interfaces:**
- `RuntimeExecutors` exposes named `io`, `cpu`, and `scheduler` executors and implements `AutoCloseable`.

- [ ] Write tests proving tasks are accepted before close and rejected after close.
- [ ] Inject runtime executors into update, currency, localization, and download services.
- [ ] Remove service-owned pools unless a feature explicitly documents an independent pool requirement.
- [ ] Ensure `PnLibrary.close()` closes services and executors exactly once.
- [ ] Run concurrency-focused tests and full build.

### Task 4: Split the public API by capability

**Files:**
- Create: `modules/features/*-api` contracts where missing
- Modify: `modules/api/**/PnLibrary*.kt` and service interfaces
- Modify: plugin builder/context APIs
- Test: API compatibility and capability-isolation tests

**Interfaces:**
- Introduce capability interfaces such as `PluginContext`, `MetricsContext`, `UpdateContext`, and `CurrencyContext`.
- Keep existing `PnLibrary` accessors as deprecated forwarding adapters during migration.

- [ ] Inventory each public `PnLibrary` member and assign it to one capability.
- [ ] Write compile/API tests showing a plugin can depend on lifecycle, logging, and scheduling without depending on update or currency runtime.
- [ ] Move contracts into feature API modules without moving implementations yet.
- [ ] Update examples and documentation to use capability contexts.
- [ ] Run binary compatibility validation and publish a migration note.

### Task 5: Make `core` a composition root instead of a feature container

**Files:**
- Modify: `modules/core/src/main/kotlin/.../core/runtime/PnLibraryImpl.kt`
- Create: focused installers for logging, diagnostics, metrics, updates, commands, and features
- Modify: `modules/core/build.gradle.kts`
- Test: composition and optional-feature tests

**Interfaces:**
- Each installer receives explicit contracts and returns a closeable service registration.
- `PnLibraryImpl` becomes an assembly object with no feature-specific business logic.

- [ ] Extract one installer at a time, starting with metrics and diagnostics.
- [ ] Add a test that core can compose with a fake feature implementation.
- [ ] Remove direct concrete feature dependencies from core where an SPI exists.
- [ ] Keep shutdown ordering explicit and tested.
- [ ] Measure and review the dependency graph after each extraction.

### Task 6: Replace untyped diagnostics with typed sections

**Files:**
- Create: typed diagnostic DTOs under `modules/api` or a dedicated diagnostics API module
- Create: `DiagnosticReportAssembler`, redactor, and codec classes
- Split: `platforms/bukkit/runtime/**/BukkitDiagnosticsCollector.kt`
- Create equivalent section collectors for Bungee and Velocity
- Test: section collectors, redaction, partial failures, and size limits

**Interfaces:**
- `DiagnosticReport` contains typed sections; serialization to `Map`/JSON occurs only in the codec boundary.

- [ ] Define typed models for server, performance, plugins, players, worlds, dependencies, warnings, and privacy metadata.
- [ ] Split Bukkit’s 1100-line collector into section collectors with one responsibility each.
- [ ] Add per-section timeout/cost limits and failure metadata instead of swallowing exceptions.
- [ ] Add tests for `includeSensitive=false`, platform-call failure, Folia restrictions, and bounded history.
- [ ] Migrate upload/encryption code to consume the typed report.

### Task 7: Narrow exception handling and failure reporting

**Files:**
- Modify production occurrences of `catch (Throwable)` and broad `catch (Exception)` identified by `rg`
- Create: shared failure classification/reporting utility if needed
- Test: failure classification tests for update, localization, currency, upload, and platform adapters

- [ ] Replace each broad catch with the narrowest expected exception type.
- [ ] Re-throw cancellation and fatal JVM errors where appropriate.
- [ ] Ensure every intentionally degraded operation emits structured diagnostics with operation and cause.
- [ ] Add static checks preventing new `catch (Throwable)` in production code.

### Task 8: Clean platform and internal dependency boundaries

**Files:**
- Modify: `platforms/*/runtime/build.gradle.kts`
- Modify: `modules/internal/faststats-*`
- Modify: `modules/runtime-spi/**`
- Modify: distribution build scripts
- Test: platform dependency and runtime artifact tests

- [ ] Replace direct Gradle task-file dependencies with a declared internal adapter artifact or SPI.
- [ ] Ensure platform modules depend on platform adapters and SPI, not arbitrary internal implementations.
- [ ] Keep third-party implementation classes shaded or isolated behind internal packages.
- [ ] Add artifact tests for Bukkit, Bungee, and Velocity contents and duplicate classes.

### Task 9: Make distribution manifest-driven and clean repository structure

**Files:**
- Create: feature/platform manifest model
- Modify: `distribution/build.gradle.kts`
- Modify: `settings.gradle.kts`
- Remove or register: `modules/remote-check/bukkit`
- Modify: release and publishing documentation

- [ ] Define feature IDs and supported platforms once.
- [ ] Generate platform assemblies from the manifest rather than repeated dependency lists.
- [ ] Add a test that every declared feature appears in the intended distribution and no unsupported feature appears.
- [ ] Resolve `remote-check` explicitly: integrate it, archive it, or remove it in a separate reviewed commit.

### Task 10: Deprecation cleanup and final hardening

**Files:**
- Modify: deprecated API adapters and migration docs
- Modify: `README.md`, `HELP-README.md`, architecture docs, release checklist
- Test: full suite, API validation, artifact smoke tests

- [ ] Search consumers and examples for deprecated access paths.
- [ ] Remove adapters only after the documented compatibility window.
- [ ] Add CI checks for architecture rules, unregistered modules, broad catches, forbidden public `Any?`, and artifact contents.
- [ ] Run `./gradlew clean test`, API validation, all smoke projects, and distribution tasks.
- [ ] Publish a release note describing the new boundaries and migration path.

## Suggested commit sequence

1. `test: add dependency and project registration architecture checks`
2. `refactor: unify update state machine`
3. `refactor: centralize runtime executor ownership`
4. `refactor: introduce capability contexts`
5. `refactor: split core composition root`
6. `refactor: type diagnostic report sections`
7. `refactor: narrow production exception handling`
8. `refactor: isolate platform and internal dependencies`
9. `refactor: make distribution manifest-driven`
10. `docs: finalize architecture migration guide`

## Definition of done

- No unregistered Gradle modules containing source remain.
- One canonical update state machine exists.
- `core` does not own feature business logic.
- Platform collectors are split into focused, typed components.
- Runtime executor ownership and shutdown are explicit.
- Public API is capability-oriented and compatibility adapters are documented.
- All three platform distributions build and pass smoke tests.
- Architecture checks run in CI and prevent the old dependency problems from returning.
