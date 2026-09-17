# pnUpdate Resolver Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Evolve the existing updater into an API-aware, dependency-aware resolver with durable temporary freezes and structured results while preserving existing plugin registration calls.

**Architecture:** Public immutable update models stay in `pnlibrary-api`; pure resolution and freeze policy live in a new `pnlibrary-feature-update` module. The current network/downloader bridge consumes resolver plans later, so domain behavior is tested without GitHub or server APIs.

**Tech Stack:** Kotlin/JVM 8, Gradle, JUnit Jupiter, Gson only in implementation

**Spec:** `docs/superpowers/specs/2026-09-17-pnlibrary-architecture-and-pnupdate-design.md`

## Global Constraints

- Preserve binary compatibility throughout API generation 4.
- Add `DEV`; channels are maximum accepted risk levels.
- Select the newest allowed compatible release, not merely latest.
- A blocked newer API generation must not block updates on the installed generation.
- Frozen components remain at their installed version and may block only plans that require changing them.
- Resolution is deterministic and returns structured blocking reasons.

---

### Task 1: Public update domain

**Files:**
- Modify: `pnlibrary-api/src/main/kotlin/ru/privatenull/pnlibrary/api/updates/UpdateService.kt`
- Create: `pnlibrary-api/src/main/kotlin/ru/privatenull/pnlibrary/api/updates/UpdateModel.kt`
- Create: `pnlibrary-api/src/test/kotlin/ru/privatenull/pnlibrary/api/updates/UpdateModelTest.kt`

**Interfaces:**
- Adds `DEV`, canonical update states, `ComponentId`, `ComponentDependency`, `ComponentRelease`, `InstalledComponent`, `UpdatePlan`, and sealed `BlockedReason`.
- Retains old enum constants and builder methods as deprecated compatibility bridges.

- [ ] Test channel acceptance, identifiers, ranges, and dependency validation; confirm RED.
- [ ] Implement immutable validated models and compatibility aliases; confirm GREEN.
- [ ] Refresh API dump and commit `feat: define pnUpdate compatibility model`.

### Task 2: Resolver module

**Files:**
- Modify: `settings.gradle.kts` and root Dokka wiring.
- Create: `pnlibrary-feature-update/build.gradle.kts`.
- Create: `pnlibrary-feature-update/src/main/kotlin/ru/privatenull/pnlibrary/update/UpdateResolver.kt`.
- Create: `pnlibrary-feature-update/src/test/kotlin/ru/privatenull/pnlibrary/update/UpdateResolverTest.kt`.

**Interfaces:**
- Produces: `UpdateResolver.resolve(installed, releases, channels, frozen): ResolutionResult`.
- Produces: `ResolutionResult.Ready(plan)` or `ResolutionResult.Blocked(reasons, fallbackPlan)`.

- [ ] Test newest-compatible fallback, cross-API migration, one-plugin API block with old-generation fallback, pre-compatible plugin reuse, dependency closure, per-component channel override, freeze blocking, and deterministic output; confirm RED.
- [ ] Implement library-candidate-first deterministic search and plugin/dependency selection; confirm GREEN.
- [ ] Add module to distribution dependency graph without replacing the legacy downloader yet.
- [ ] Run module and full tests; commit `feat: add API-aware update resolver`.

### Task 3: Durable freeze policy

**Files:**
- Create: `FreezeDuration.kt`, `FreezeStore.kt`, and tests in `pnlibrary-feature-update`.

**Interfaces:**
- Accepts only 1 minute through 30 days.
- Persists absolute expiry instants per component and automatically removes expired entries.

- [ ] Test boundary durations, invalid permanent/31-day values, restart persistence, component isolation, and expiry; confirm RED.
- [ ] Implement strict duration parsing plus atomic JSON persistence; confirm GREEN.
- [ ] Commit `feat: add durable pnUpdate freezes`.

### Task 4: Legacy registration bridge

**Files:**
- Modify: `PluginUpdateRequest` builder and `PluginRegistryImpl` metadata wiring.
- Modify: `UpdateServiceImpl` to create resolver-compatible installed/release identities while retaining current background checks.

**Interfaces:**
- Plugins can declare GitHub repository, exact artifact, API range, and component dependencies.
- Old regex/Java artifact declarations continue to work during migration.

- [ ] Add builder and registry tests first.
- [ ] Implement compatibility mapping and metadata exposure.
- [ ] Run ABI checks and full verification; commit `refactor: bridge plugin updates to pnUpdate resolver`.

