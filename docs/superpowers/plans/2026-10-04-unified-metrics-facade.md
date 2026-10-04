# Unified Metrics Facade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a single, convenient metrics facade that configures bStats and FastStats without exposing provider configuration classes to plugin authors.

**Architecture:** Keep the existing provider adapters and advanced collection-based API internally. Add a DSL on `PluginBuilder` that builds those configurations, expose shared chart registration through `context.metrics`, and expose FastStats-only error tracking as an optional capability. Preserve optional-provider behavior and never let telemetry failures prevent plugin startup.

**Tech Stack:** Kotlin API, Java provider adapters, JUnit/Kotlin tests, Gradle multi-module build.

**Spec:** `docs/metrics-facade-design.md`

## Global Constraints

- bStats and FastStats remain independently optional.
- Shared metrics are registered once and fanned out to enabled providers.
- FastStats-only access must be nullable/safe when FastStats is disabled.
- Uncaught FastStats errors are automatic; handled errors support explicit capture.
- Sensitive values must remain sanitized; no raw credentials or player data are sent.
- Existing advanced provider configuration remains source-compatible.

## Review Focus

- No providers enabled: builder must reject an empty configuration clearly.
- Only one provider enabled: the other SDK must not be required at runtime.
- Both providers enabled: each shared chart is registered exactly once per provider.
- FastStats disabled: optional access returns null rather than throwing.
- Handled error capture: error is reported without duplicating automatic uncaught handling.

### Task 1: Add the provider DSL

**Files:**
- Modify: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/plugin/PluginBuilder.kt`
- Test: `modules/api/src/test/kotlin/ru/privatenull/pnlibrary/api/plugin/PluginBuilderMetricsTest.kt`

**Interfaces:**
- Produces `PluginBuilder.metrics(configure: Consumer<MetricsBuilder>): PluginBuilder`.
- Produces `MetricsBuilder.bStats(projectId: Int)` and `MetricsBuilder.fastStats(token: String)`.
- Existing collection-based `metrics(...)` methods remain available.

- [ ] Write tests for bStats-only, FastStats-only, both providers, and empty configuration.
- [ ] Run the focused API tests and verify the new tests fail before implementation.
- [ ] Implement the DSL as a thin adapter that creates existing `MetricsProviderConfiguration` values and delegates to the current metrics implementation.
- [ ] Run focused tests and verify all pass.
- [ ] Commit: `feat: add provider-neutral metrics dsl`.

### Task 2: Expose one runtime metrics facade

**Files:**
- Modify: the existing `ModuleContext`/metrics service implementation where `context.metrics` is assembled.
- Test: existing metrics service tests plus a focused facade test.

**Interfaces:**
- Shared chart methods remain available from `context.metrics`.
- Add `fastStatsOrNull(): FastStatsFacade?` without making FastStats a mandatory dependency.

- [ ] Add tests proving shared chart fan-out and nullable FastStats access.
- [ ] Implement the facade by delegating to existing provider adapters; do not duplicate chart logic.
- [ ] Verify provider absence does not throw during module creation.
- [ ] Commit: `feat: expose unified runtime metrics facade`.

### Task 3: Wire automatic and explicit FastStats error tracking

**Files:**
- Modify: existing `ErrorPipeline` and FastStats reporter adapters.
- Test: existing error pipeline tests and new automatic/handled capture tests.

**Interfaces:**
- `context.errors.capture(error, operation?, attributes?)` remains the explicit path.
- FastStats context-aware tracker is installed when FastStats is enabled.

- [ ] Test automatic uncaught reporting through the provider adapter.
- [ ] Test explicit handled capture and sanitization/deduplication.
- [ ] Implement the adapter hook using the typed FastStats integration, not reflection.
- [ ] Ensure provider failures are swallowed after local diagnostic recording.
- [ ] Commit: `feat: wire faststats error tracking`.

### Task 4: Document and verify the public workflow

**Files:**
- Modify: `docs/metrics-providers.md`.
- Test/verification: full Gradle test suite and platform shadow builds.

- [ ] Document minimal, single-provider, dual-provider, chart, and error examples.
- [ ] Run `./gradlew.bat test --no-daemon --max-workers=1`.
- [ ] Run Bukkit, BungeeCord, and Velocity shadow builds.
- [ ] Check API compatibility output and working-tree diff.
- [ ] Commit: `docs: document unified metrics facade`.
