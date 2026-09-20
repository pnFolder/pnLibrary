# Smart Update Orchestrator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build one graph-aware updater that selects, verifies, stages, announces, and confirms a mutually compatible pnLibrary-and-plugin update plan.

**Architecture:** Public immutable models live in `modules/api`; manifest parsing, catalogue caching, solving, verification, and transactions live in `modules/features/update`; `modules/core` owns orchestration and persistence; platform runtimes supply notification and restart integration. Existing per-product registrations become graph inputs instead of starting independent updater threads.

**Tech Stack:** Kotlin/JVM 8, Java 8 public ABI, Gradle Kotlin DSL, Gson, Kyori Adventure, JUnit Jupiter

**Spec:** `docs/superpowers/specs/2026-09-20-smart-update-orchestrator-design.md`

## Global Constraints

- Keep `PnLibraryApi.VERSION == 1`; refresh additive API baselines only.
- Never download or stage a partial compatibility plan.
- Tests never access public networks.
- Require HTTPS, bounded responses, safe paths, and SHA-256 before staging.
- Server policy can restrict component policy; components cannot relax it.
- Bukkit gets administrator messages; BungeeCord and Velocity remain console-only.
- Never hot-reload plugins. Apply staged files only through restart.

## Review Focus

- An API migration without a compatible release for every installed dependent returns `BLOCKED` and stages nothing (Task 4).
- Stale, foreign, expired, or replayed player tokens perform no action (Task 8).
- Syntactically valid but structurally invalid cached manifests are quarantined and refetched (Task 3).
- Interrupted publication restores the complete old component set (Task 5).
- Component metadata cannot enable an external host or restart disabled by server policy (Task 6).

---

### Task 1: Public component and plan API

**Files:**
- Modify: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/updates/UpdateModel.kt`
- Modify: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/updates/UpdateService.kt`
- Modify: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/plugin/PluginRegistry.kt`
- Modify: `modules/api/src/test/kotlin/ru/privatenull/pnlibrary/api/updates/UpdateModelTest.kt`
- Create: `modules/api/src/test/java/ru/privatenull/pnlibrary/api/updates/JavaUpdateApiTest.java`
- Modify: `modules/api/api/pnlibrary-api.api`

**Interfaces:** Produces `ComponentDescriptor`, managed/external dependency models, artifact descriptors, structured blockers, immutable plan snapshots, and asynchronous check/stage/confirm/history operations. Existing request and registration calls remain compatibility views.

- [ ] **Step 1: Write failing validation tests.** Assert inverted API ranges, duplicate dependencies, unsafe artifact names, HTTP automatic artifacts, invalid SHA-256, and defensive-copy behavior. The wished-for Java call is:

```java
ComponentDescriptor descriptor = ComponentDescriptor.builder("economy", "3.4.0")
    .pnLibraryApi(1, 2)
    .managedDependency("permissions", "2.1.0", "pnFolder", "Permissions")
    .build();
assertEquals("economy", descriptor.getId().getValue());
```

- [ ] **Step 2: Run `./gradlew :modules:api:test` and confirm RED** because descriptor builders do not exist.
- [ ] **Step 3: Implement validated immutable builders.** Use existing `ComponentId`, `SemanticVersion`, and `ApiVersionRange`. Add `DOWNLOADING` and retain deprecated compatibility state constants.
- [ ] **Step 4: Add these Java-friendly service methods and make tests GREEN:**

```kotlin
fun checkNow(): CompletionStage<UpdatePlanSnapshot>
fun currentPlan(): Optional<UpdatePlanSnapshot>
fun stage(planId: UUID): CompletionStage<UpdatePlanSnapshot>
fun confirm(planId: UUID, token: String): CompletionStage<UpdatePlanSnapshot>
fun history(): List<UpdatePlanSnapshot>
```

- [ ] **Step 5: Run `./gradlew :modules:api:test :modules:api:apiDump :modules:api:apiCheck`; confirm API generation is still `1` and commit `feat(updates): define graph update API`.**

### Task 2: Strict embedded and release descriptors

**Files:**
- Create: `modules/features/update/src/main/kotlin/ru/privatenull/pnlibrary/update/ComponentDescriptorCodec.kt`
- Create: `modules/features/update/src/main/kotlin/ru/privatenull/pnlibrary/update/EmbeddedDescriptorReader.kt`
- Create: `modules/features/update/src/test/kotlin/ru/privatenull/pnlibrary/update/ComponentDescriptorCodecTest.kt`
- Create: `modules/features/update/src/test/kotlin/ru/privatenull/pnlibrary/update/EmbeddedDescriptorReaderTest.kt`

**Interfaces:** `decodeRelease(ByteArray): ComponentRelease`, `encodeInstalled(ComponentDescriptor): ByteArray`, and `read(Path): ComponentDescriptor`; schema is exactly `1`.

- [ ] **Step 1: Write failing literal-fixture tests.** Cover two Java artifacts, schema `2`, duplicate dependency IDs, `../plugin.jar`, missing required fields, negative sizes, and malformed hashes.
- [ ] **Step 2: Run `./gradlew :modules:features:update:test --tests '*ComponentDescriptor*'`; confirm RED.**
- [ ] **Step 3: Implement explicit Gson field parsing.** Reject shape/type errors with a stable `ManifestException(reason, field)` rather than direct Gson model binding.
- [ ] **Step 4: Implement bounded JAR reading.** Read exactly `META-INF/pnlibrary/component.json`, reject duplicates and entries over 256 KiB, and never extract the archive.
- [ ] **Step 5: Run `./gradlew :modules:features:update:test`; confirm GREEN and commit `feat(updates): parse component manifests`.**

### Task 3: Trusted release catalogue and persistent cache

**Files:**
- Create: `modules/features/update/src/main/kotlin/ru/privatenull/pnlibrary/update/TrustedHttpClient.kt`
- Create: `modules/features/update/src/main/kotlin/ru/privatenull/pnlibrary/update/ReleaseCatalogueClient.kt`
- Create: `modules/features/update/src/main/kotlin/ru/privatenull/pnlibrary/update/ReleaseCatalogueStore.kt`
- Create: `modules/features/update/src/test/kotlin/ru/privatenull/pnlibrary/update/ReleaseCatalogueClientTest.kt`

**Interfaces:** `releases(source, channel): List<ComponentRelease>` returns verified newest-first candidates. Concurrent requests for one source share a future. Cache entries store release identity, fetch time, digest, and bytes.

- [ ] **Step 1: Write failing fake-transport tests.** Assert concurrent coalescing, fresh cache reuse, stale verified fallback, corrupt-cache quarantine/refetch, response limits, and redirect rejection to an untrusted host.
- [ ] **Step 2: Run the catalogue test and confirm RED.**
- [ ] **Step 3: Implement bounded HTTPS transport.** Validate every redirect, stream in 8 KiB chunks, apply independent JSON/artifact limits and timeouts, and allow only built-in plus configured hosts.
- [ ] **Step 4: Implement atomic verified storage and GitHub discovery.** Parse at most 30 releases, fetch their `pn-update.json`, filter channel, parse with Task 2, and sort semantic versions descending.
- [ ] **Step 5: Run feature tests and commit `feat(updates): add verified release catalogues`.**

### Task 4: Deterministic complete-graph solver

**Files:**
- Modify: `modules/features/update/src/main/kotlin/ru/privatenull/pnlibrary/update/UpdateResolver.kt`
- Modify: `modules/features/update/src/test/kotlin/ru/privatenull/pnlibrary/update/UpdateResolverTest.kt`

**Interfaces:** Pure `resolve(installed, releases, platform, java, policy): ResolutionResult`; no network, filesystem, or mutable global state.

- [ ] **Step 1: Add failing graph tests.** Required primary fixture:

```text
installed: pnlibrary(api1), A1(api1), B1(api1)
available: pnlibrary2(api2), A2(api2), B2(api2)
expected changes: pnlibrary2, A2, B2
```

Also assert missing `B2` blocks all changes, already compatible installed versions remain, managed missing dependencies may be introduced by policy, external manual dependencies report their page, cycles require a complete assignment, and ties minimize changes deterministically.

- [ ] **Step 2: Run resolver tests and confirm RED.**
- [ ] **Step 3: Implement backtracking constraint propagation.** Order variables by smallest domain then ID; candidates by minimal-change preference then version descending; apply API, dependency, Java, platform, channel, and freeze constraints after each assignment; memoize failed sorted assignments.
- [ ] **Step 4: Return all deduplicated blockers sorted by component and reason.** Never return a partial ready plan.
- [ ] **Step 5: Run feature tests and commit `feat(updates): solve complete compatibility graphs`.**

### Task 5: Verified multi-artifact transaction and recovery

**Files:**
- Modify: `modules/features/update/src/main/kotlin/ru/privatenull/pnlibrary/update/ArtifactDownloader.kt`
- Modify: `modules/features/update/src/main/kotlin/ru/privatenull/pnlibrary/update/ArtifactVerifier.kt`
- Modify: `modules/features/update/src/main/kotlin/ru/privatenull/pnlibrary/update/UpdateTransaction.kt`
- Modify: `modules/features/update/src/test/kotlin/ru/privatenull/pnlibrary/update/UpdateTransactionTest.kt`

**Interfaces:** `stage(plan, transport): StagedTransaction` and `recoverAll(): List<RecoveryResult>`. Verification covers size, SHA-256, JAR readability, embedded descriptor, component/version, platform, Java, and API.

- [ ] **Step 1: Write failing tests for a three-component plan, second-download failure, descriptor mismatch, failure after each journal state, restart recovery, rollback, repeated recovery, and path escape.**
- [ ] **Step 2: Run transaction tests and confirm RED.**
- [ ] **Step 3: Implement journal states `CREATED`, `DOWNLOADING`, `VERIFIED`, `PUBLISHING`, `STAGED`, `ROLLING_BACK`, `ROLLED_BACK`, `FAILED` with atomic forced writes.**
- [ ] **Step 4: Re-run Task 4 resolution from verified downloaded descriptors and require exact plan equality before publication.**
- [ ] **Step 5: Run feature tests and commit `feat(updates): stage atomic update plans`.**

### Task 6: Configuration, durable state, and single orchestrator

**Files:**
- Create: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/updates/UpdateConfiguration.kt`
- Create: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/updates/UpdateStateStore.kt`
- Create: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/updates/UpdateOrchestrator.kt`
- Replace: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/updates/UpdateServiceImpl.kt`
- Modify: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/updates/MandatoryUpdateService.kt`
- Create: `modules/core/src/test/kotlin/ru/privatenull/pnlibrary/core/updates/UpdateConfigurationTest.kt`
- Create: `modules/core/src/test/kotlin/ru/privatenull/pnlibrary/core/updates/UpdateOrchestratorTest.kt`

**Interfaces:** One orchestrator owns all registrations, one scheduler, catalogue refresh, current plan, staging, history, and close lifecycle. `MandatoryUpdateService` becomes a self-registration adapter and starts no thread.

- [ ] **Step 1: Write failing configuration tests.** Cover conservative defaults, migration of `channel`/`auto-download`, malformed fallback, complete disable, and component attempts to override external-host/restart policy.
- [ ] **Step 2: Write failing orchestrator tests.** Cover one graph check, 30-minute schedule, immediate changed-plan announcement, six-hour identical throttle, stale plan rejection, optional automatic staging, 100-entry/10 MiB history limits, and shutdown.
- [ ] **Step 3: Run both test classes and confirm RED.**
- [ ] **Step 4: Implement exact spec YAML mapping, atomic `state.json`/history, and backup migration to `updates.yml.pre-orchestrator.bak`.**
- [ ] **Step 5: Implement serialized/coalesced orchestration on one owned executor.** Registration changes increment plan revision and trigger a debounced check.
- [ ] **Step 6: Run core tests and commit `feat(updates): orchestrate graph updates`.**

### Task 7: Plugin-context metadata and dependency gate

**Files:**
- Modify: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/plugin/PluginRegistryImpl.kt`
- Modify: `modules/core/src/test/kotlin/ru/privatenull/pnlibrary/core/plugin/PluginRegistryImplTest.kt`
- Modify: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/runtime/PnLibraryRuntimeHost.kt`

**Interfaces:** Registration merges embedded, explicit, then native metadata; conflicts fail. Required dependencies validate before commands, services, tasks, events, or placeholders become visible.

- [ ] **Step 1: Write failing tests for embedded metadata, builder fallback, metadata conflict, legacy unmanaged registration, missing dependency, incompatible installed version, and resource cleanup after rejection.**
- [ ] **Step 2: Run `PluginRegistryImplTest` and confirm RED.**
- [ ] **Step 3: Implement merge and dependency validation.** Return one diagnostic listing every missing/incompatible dependency and its download page.
- [ ] **Step 4: Register pnLibrary itself as `pnlibrary` with build version, API `1`, platform artifact, and GitHub source before plugins register.**
- [ ] **Step 5: Run core tests and commit `feat(updates): validate plugin component contexts`.**

### Task 8: Bukkit administrator messages and confirmations

**Files:**
- Create: `platforms/bukkit/runtime/src/main/kotlin/ru/privatenull/pnlibrary/bukkit/updates/BukkitUpdateNotifier.kt`
- Create: `platforms/bukkit/runtime/src/main/kotlin/ru/privatenull/pnlibrary/bukkit/updates/UpdateConfirmationTokens.kt`
- Modify: `platforms/bukkit/runtime/src/main/kotlin/ru/privatenull/pnlibrary/bukkit/BukkitLifecycleListener.kt`
- Modify: `platforms/bukkit/runtime/src/main/kotlin/ru/privatenull/pnlibrary/bukkit/commands/BukkitControlCommand.kt`
- Modify: `platforms/bukkit/runtime/src/main/resources/plugin.yml`
- Create: `platforms/bukkit/runtime/src/test/kotlin/ru/privatenull/pnlibrary/bukkit/updates/BukkitUpdateNotifierTest.kt`
- Create: `platforms/bukkit/runtime/src/test/kotlin/ru/privatenull/pnlibrary/bukkit/updates/UpdateConfirmationTokensTest.kt`

**Interfaces:** Permissions are `notify`, `view`, `download`, `install`, and `restart`. Tokens contain 256 random bits and bind UUID, plan UUID/revision, action, expiry, and one use.

- [ ] **Step 1: Write failing token tests for valid use, foreign player, wrong action, changed revision, expiry, replay, and cleanup.**
- [ ] **Step 2: Write failing notifier tests for OP policy, permission filtering, hidden buttons, URL/path absence, one-tick join message, online threshold, second confirmation, player-count recheck, and staged-plan-only restart.**
- [ ] **Step 3: Run Bukkit update tests and confirm RED.**
- [ ] **Step 4: Implement Adventure `Подробнее`, `Скачать`, and `Перезапустить` actions through nonce-bearing pnLibrary commands.** Never embed a download URL or path in click events.
- [ ] **Step 5: Declare permissions as OP by default while still respecting configuration gates; dispatch configured restart as console only after final confirmation.**
- [ ] **Step 6: Run Bukkit tests and commit `feat(bukkit): add update administrator flow`.**

### Task 9: Proxy console-only lifecycle

**Files:**
- Modify: `platforms/bungee/runtime/src/main/kotlin/ru/privatenull/pnlibrary/bungee/PnLibraryBungeePlugin.kt`
- Modify: `platforms/velocity/runtime/src/main/kotlin/ru/privatenull/pnlibrary/velocity/PnLibraryVelocityPlugin.kt`
- Create: `platforms/bungee/runtime/src/test/kotlin/ru/privatenull/pnlibrary/bungee/UpdateRuntimeTest.kt`
- Create: `platforms/velocity/runtime/src/test/kotlin/ru/privatenull/pnlibrary/velocity/UpdateRuntimeTest.kt`

**Interfaces:** Proxies expose console check/plan/stage commands, start and close the orchestrator, and register no update player listeners.

- [ ] **Step 1: Write failing lifecycle tests for ready/blocked/staged console output, console-only mutation, shutdown, and absence of player notification listeners.**
- [ ] **Step 2: Run proxy tests and confirm RED.**
- [ ] **Step 3: Wire shared core formatting and commands without login handlers or clickable messages.**
- [ ] **Step 4: Run proxy tests and commit `feat(proxies): expose console update orchestration`.**

### Task 10: Reproducible component build metadata

**Files:**
- Create: `tools/component-metadata/build.gradle.kts`
- Create: `tools/component-metadata/src/main/kotlin/ru/privatenull/pnlibrary/gradle/PnComponentPlugin.kt`
- Create: `tools/component-metadata/src/main/kotlin/ru/privatenull/pnlibrary/gradle/PnComponentExtension.kt`
- Create: `tools/component-metadata/src/test/kotlin/ru/privatenull/pnlibrary/gradle/PnComponentPluginTest.kt`
- Modify: `settings.gradle.kts`
- Modify: `README.md`
- Create: `docs/updates.md`

**Interfaces:** Gradle plugin ID `ru.privatenull.pnlibrary.component` generates the embedded descriptor, `pn-update.json`, and checksum inputs from one typed extension with reproducible output.

- [ ] **Step 1: Write failing Gradle TestKit tests.** Build a temporary plugin, configure `pnComponent`, and assert exact matching embedded/release data, deterministic bytes, missing ID/version failure, and duplicate dependency rejection.
- [ ] **Step 2: Run `./gradlew :tools:component-metadata:test` and confirm RED.**
- [ ] **Step 3: Implement typed `Property`/`ListProperty` inputs, sorted output, UTF-8, no timestamps, `processResources` wiring, and `assemble` release artifacts.**
- [ ] **Step 4: Document Kotlin/Java registration, dependencies, policy, permissions, commands, Bukkit confirmations, proxy behavior, publishing, recovery, and migration.**
- [ ] **Step 5: Run tooling tests and commit `feat: generate pnLibrary component metadata`.**

### Task 11: Remove duplicate paths and verify the release

**Files:**
- Remove obsolete independent-loop code under `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/updates/`
- Modify: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/logging/PlatformLoggingService.kt`
- Modify affected API baselines under `modules/*/api/` and `platforms/*/api/`

**Interfaces:** Exactly one production scheduler, resolver, downloader, transaction engine, state store, and announcement throttle remain reachable.

- [ ] **Step 1: Run `rg -n "CHECK_INTERVAL_MS|startProduct|Thread\.sleep|downloadNow|UpdateResolver\(" modules platforms` and remove every obsolete per-registration production path.**
- [ ] **Step 2: Run focused suites:**

```text
./gradlew :modules:api:test :modules:features:update:test :modules:core:test :platforms:bukkit:runtime:test :platforms:bungee:runtime:test :platforms:velocity:runtime:test
```

- [ ] **Step 3: Run `./gradlew apiCheck`; confirm API generation remains `1`.**
- [ ] **Step 4: Run `./gradlew clean test apiCheck :distribution:build --warning-mode all --no-daemon`; require `BUILD SUCCESSFUL` and zero failed tests.**
- [ ] **Step 5: Run `git diff --check`, inspect distributions/configuration, request whole-branch review, fix every Critical/Important finding, repeat Step 4, and commit `refactor(updates): complete smart update orchestration`.**
