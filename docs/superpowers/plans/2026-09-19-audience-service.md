# Cross-platform Audience Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose one audience service for identity, permissions, messages, action bars, sounds, players, console, native senders, and composite audiences on Bukkit, BungeeCord, and Velocity.

**Architecture:** Public contracts extend the existing action audience model. Core owns lookup lifecycle and composition; runtime SPI defines native resolution; each platform implements only native wrapping and delivery. Command adapters reuse the same `AudienceSender` wrappers.

**Tech Stack:** Kotlin/JVM, Adventure API, existing ComponentService, JUnit 5, Gradle 9, Bukkit/BungeeCord/Velocity APIs.

**Spec:** `docs/superpowers/specs/2026-09-19-audience-service-design.md`

## Global Constraints

- Preserve existing `LibraryAudience` and `LibraryPlayer` source behavior.
- Keep native platform classes out of public API and core.
- Audience methods accept parsed Adventure components; no duplicate string parsing.
- Unknown/offline receivers return null; dynamic groups remain safe no-ops.
- Do not stage pre-existing API-version or Velocity edits.

## Review Focus

- Empty and duplicate composites must have deterministic delivery and sound results.
- A receiver exception must not prevent later composite receivers from receiving output.
- Console/player identity and permissions must not be inferred incorrectly.
- Offline player lookup and post-close lookup must not return stale wrappers.
- Command adapters must not retain a second message-conversion path.

---

### Task 1: Public audience contracts

**Files:**
- Create: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/audiences/AudienceService.kt`
- Modify: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/actions/core/ActionAudience.kt`
- Modify: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/commands/CommandSender.kt`
- Modify: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/runtime/PnLibrary.kt`
- Test: `modules/api/src/test/kotlin/ru/privatenull/pnlibrary/api/audiences/AudienceContractsTest.kt`

**Interfaces:**
- Produces `AudienceSender`, `AudienceService`, player identity defaults, and `PnLibrary.audiences`.

- [ ] Write failing tests for player defaults, sender flags, and facade exposure.
- [ ] Run focused API tests and verify missing-contract failure.
- [ ] Implement contracts while preserving existing action interfaces.
- [ ] Run API tests, `apiDump`, and `apiCheck` successfully.
- [ ] Commit `feat(api): add cross-platform audience service`.

### Task 2: SPI and core composition

**Files:**
- Create: `modules/runtime-spi/src/main/kotlin/ru/privatenull/pnlibrary/spi/audiences/PlatformAudienceAdapter.kt`
- Create: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/audiences/AudienceServiceImpl.kt`
- Create: `modules/core/src/test/kotlin/ru/privatenull/pnlibrary/core/audiences/AudienceServiceImplTest.kt`
- Modify: `modules/runtime-spi/src/main/kotlin/ru/privatenull/pnlibrary/spi/platform/PlatformAdapter.kt`
- Modify: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/runtime/PnLibraryImpl.kt`

**Interfaces:**
- Consumes Task 1 contracts; produces lifecycle-aware lookup and dynamic/composite audiences.

- [ ] Write failing tests for console/player/native lookup, dynamic online membership, order, identity deduplication, receiver exception isolation, sound aggregation, and close.
- [ ] Verify focused tests fail for missing implementation.
- [ ] Implement SPI and core service with fatal-error rethrowing.
- [ ] Run core and SPI tests successfully.
- [ ] Commit `feat(core): implement audience service`.

### Task 3: Bukkit audience adapter

**Files:**
- Create: `platforms/bukkit/runtime/src/main/kotlin/ru/privatenull/pnlibrary/bukkit/BukkitAudienceAdapter.kt`
- Modify: `platforms/bukkit/runtime/src/main/kotlin/ru/privatenull/pnlibrary/bukkit/BukkitLibraryPlayer.kt`
- Modify: `platforms/bukkit/runtime/src/main/kotlin/ru/privatenull/pnlibrary/bukkit/BukkitPlatformAdapter.kt`
- Test: `platforms/bukkit/runtime/src/test/kotlin/ru/privatenull/pnlibrary/bukkit/BukkitAudienceAdapterTest.kt`

**Interfaces:**
- Implements SPI resolution for Bukkit console, command sender, UUID player, and online snapshots using `BukkitAudienceService` delivery.

- [ ] Write failing adapter tests with native Bukkit proxies.
- [ ] Verify RED, implement the adapter and shared sender wrapper, then verify GREEN.
- [ ] Run the Bukkit runtime suite.
- [ ] Commit `feat(bukkit): bridge shared audiences`.

### Task 4: Proxy audience adapters

**Files:**
- Create: `platforms/bungee/runtime/src/main/kotlin/ru/privatenull/pnlibrary/bungee/BungeeAudienceAdapter.kt`
- Create: `platforms/velocity/runtime/src/main/kotlin/ru/privatenull/pnlibrary/velocity/VelocityAudienceAdapter.kt`
- Modify: platform player wrappers and platform adapters.
- Test: corresponding `BungeeAudienceAdapterTest.kt` and `VelocityAudienceAdapterTest.kt`.

**Interfaces:**
- Implements the same SPI using proxy APIs and existing component conversion rules.

- [ ] Write failing tests for player, console, unknown sender, permissions, message, and action-bar delivery.
- [ ] Implement Bungee adapter and verify focused tests.
- [ ] Implement Velocity adapter and verify focused tests.
- [ ] Run both proxy runtime suites.
- [ ] Commit `feat(proxy): bridge shared audiences`.

### Task 5: Reuse audiences in commands

**Files:**
- Modify: three platform command adapters and their tests.
- Modify: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/commands/CommandSender.kt` only if compatibility glue is required.

**Interfaces:**
- Consumes platform audience adapters; removes separate identity, permission, and component-delivery implementations from commands.

- [ ] Write failing command-adapter tests proving the dispatched sender is the shared wrapper and unsupported native objects do not dispatch.
- [ ] Replace command-specific wrappers/converters with audience adapter lookup.
- [ ] Run core and all platform command tests.
- [ ] Commit `refactor: reuse audience service in commands`.

### Task 6: Documentation and release verification

**Files:**
- Create: `docs/AUDIENCES.md`
- Modify: `README.md`
- Modify: API dump.

**Interfaces:**
- Documents Kotlin and Java lookup, composition, ComponentService parsing, limitations, and lifecycle.

- [ ] Document the final API with platform-neutral examples.
- [ ] Run `clean test apiCheck :distribution:build --warning-mode all --no-daemon`, temporarily isolating and restoring only the known user API-version edit.
- [ ] Review the complete branch, fix Critical/Important findings with RED→GREEN tests, and rerun the full suite.
- [ ] Commit documentation/review fixes, push `feat/pnlibrary-architecture-pnupdate`, and verify local/remote HEAD equality.
