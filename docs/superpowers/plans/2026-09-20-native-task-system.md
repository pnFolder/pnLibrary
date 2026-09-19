# Native Cross-Platform Task System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the hard-coded pnLibrary timer pool with one Java/Kotlin-friendly task specification, an observable owner-scoped registry, and native scheduling adapters for Bukkit/Paper/Folia, BungeeCord, and Velocity.

**Architecture:** Public code builds an immutable `TaskSpec` and submits it through an owner-bound `TaskScope`. Core owns validation, IDs, conflict handling, conditions, state, history, and lifecycle; a narrow runtime SPI translates delay, repetition, execution target, and cancellation to each platform's native scheduler.

**Tech Stack:** Kotlin/JVM, Java 8 public API and Bukkit/Bungee runtimes, Java 17 Velocity runtime, Gradle, JUnit 5, Bukkit/Paper/Folia scheduler APIs, BungeeCord `TaskScheduler`, Velocity `Scheduler`.

**Spec:** `docs/superpowers/specs/2026-09-20-native-task-system-design.md`

## Global Constraints

- Preserve the old `TaskScope` scheduling methods as deprecated source/binary-compatible wrappers for the current compatibility generation.
- `TaskSpec.builder()` and all factories must be directly callable from Java without Kotlin function types or default-argument helpers.
- Display names may repeat; `TaskId` is globally unique; optional keys are unique only within the exact owner identity.
- Native platform schedulers own all delays and intervals; core must not place another scheduled timer in front of them.
- A scope query cannot escape its owner; service-level queries may inspect every owner.
- Active tasks are never evicted; terminal history is bounded by settings.
- Preserve all pre-existing uncommitted user changes and stage only task-system files owned by this plan.

## Review Focus

- A `REPLACE` collision racing native registration must leave one active keyed task and cancel every losing native handle; Task 3 adds the race-oriented recording-adapter test.
- Self-cancellation before the native handle is attached must cancel that handle immediately after attachment; Task 3 tests synchronous callback-on-registration.
- A slow repeating callback must not overlap with itself when the platform fires twice; Task 3 tests concurrent invocations and skipped-count accounting.
- A one-shot task whose execution condition is false must become terminal instead of staying active forever; Task 3 tests the terminal snapshot.
- Folia entity scheduling with an unsupported recipient must fail before native registration and without a registry leak; Task 4 tests target validation and cleanup.

---

## File Structure

- `modules/api/.../tasks/TaskIdentity.kt`: `TaskId`, statuses, conflict policy, and execution description.
- `modules/api/.../tasks/TaskSpec.kt`: immutable specification, Java builder, Kotlin DSL builder, action, and context contracts.
- `modules/api/.../tasks/TaskQuery.kt`: immutable query, Java builder, and diagnostic snapshot.
- `modules/api/.../tasks/TaskService.kt`: small scheduling/service interfaces plus deprecated compatibility methods.
- `modules/runtime-spi/.../tasks/PlatformTaskAdapter.kt`: platform scheduling request and cancellation handle only.
- `modules/core/.../tasks/TaskServiceImpl.kt`: scope ownership and public service coordination.
- `modules/core/.../tasks/ManagedTask.kt`: one task's atomic lifecycle, guarded invocation, conditions, and native-handle attachment.
- `modules/core/.../tasks/TaskRegistry.kt`: IDs, owner/key indexes, queries, conflict policy, and bounded terminal history.
- one `*TaskAdapter.kt` per platform runtime: native API translation.
- platform `*PlatformAdapter.kt` files: expose their task adapter to core without moving task policy into platform code.

### Task 1: Public task model and Java/Kotlin builders

**Files:**
- Create: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/tasks/TaskIdentity.kt`
- Create: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/tasks/TaskSpec.kt`
- Create: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/tasks/TaskQuery.kt`
- Create: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/tasks/TaskServiceSettings.kt`
- Modify: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/tasks/TaskService.kt`
- Create: `modules/api/src/test/kotlin/ru/privatenull/pnlibrary/api/tasks/TaskSpecTest.kt`
- Modify: `modules/api/api/api.api`

**Interfaces:**
- Consumes: Java `Duration`, `Instant`, `BooleanSupplier`, `Consumer`, and `Runnable`-compatible SAM contracts.
- Produces: `TaskId`, `TaskStatus`, `TaskConflictPolicy`, `TaskExecution`, `TaskAction`, `TaskContext`, `TaskSpec`, `TaskQuery`, `TaskSnapshot`, `TaskServiceSettings`, expanded `TaskHandle`, `TaskScope.schedule/find/query/cancel`, and `TaskService.query/find/cancel`.

- [ ] **Step 1: Write failing builder and identity tests**

Add tests proving two identical names produce valid specs, blank keys/tags fail, negative delay fails, non-positive interval fails, a missing action fails, Java-style factories return global/async/entity executions, `TaskServiceSettings` rejects a negative history capacity, and `TaskId` equality depends on its opaque value. Include a reflection assertion that `TaskSpec` exposes a static `builder()` method.

```kotlin
val first = TaskSpec.builder().name("player-hud").action { }.build()
val second = TaskSpec.builder().name("player-hud").action { }.build()
assertEquals("player-hud", first.name)
assertEquals("player-hud", second.name)
assertThrows<IllegalArgumentException> {
    TaskSpec.builder().key(" ").action { }.build()
}
assertNotNull(TaskSpec::class.java.getMethod("builder"))
```

- [ ] **Step 2: Run the API test and confirm the missing model failure**

Run: `./gradlew :modules:api:test --tests '*TaskSpecTest' --no-daemon`

Expected: compilation fails because `TaskSpec`, execution factories, and query contracts do not exist.

- [ ] **Step 3: Implement immutable public contracts**

Use a Java-friendly execution value rather than exposing Kotlin-only object types:

```kotlin
class TaskExecution private constructor(val kind: Kind, val target: Any?) {
    enum class Kind { GLOBAL, ASYNC, ENTITY }
    companion object {
        @JvmStatic fun global() = TaskExecution(Kind.GLOBAL, null)
        @JvmStatic fun async() = TaskExecution(Kind.ASYNC, null)
        @JvmStatic fun entity(target: Any) = TaskExecution(Kind.ENTITY, target)
    }
}

fun interface TaskAction { fun run(context: TaskContext) }
```

Implement `TaskSpec.Builder` with fluent setters, singular and collection tag/condition methods, `BooleanSupplier` conditions, `TaskAction`, and validation in `build()`. Add `TaskSpecBuilder` plus `TaskScope.schedule { ... }` as Kotlin-only convenience over the same immutable spec. Define `TaskQuery.Builder` filters without embedding owner mutation into scope-level APIs.

Keep old methods concrete on `TaskScope` and delegate them through `schedule(TaskSpec)`. Mark each old method with `@Deprecated` and a `ReplaceWith` expression. Preserve `asyncThen` semantics by composing an async spec whose completion is scheduled through the same scope as global work.

- [ ] **Step 4: Run API tests and generate the ABI dump**

Run: `./gradlew :modules:api:test :modules:api:apiDump --no-daemon`

Expected: tests pass and `modules/api/api/api.api` contains the new Java-visible contracts while retaining old method signatures.

- [ ] **Step 5: Commit the public model**

```bash
git add modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/tasks modules/api/src/test/kotlin/ru/privatenull/pnlibrary/api/tasks modules/api/api/api.api
git commit -m "feat(tasks): add unified task specification"
```

### Task 2: Runtime scheduling SPI

**Files:**
- Create: `modules/runtime-spi/src/main/kotlin/ru/privatenull/pnlibrary/spi/tasks/PlatformTaskAdapter.kt`
- Modify: `modules/runtime-spi/src/main/kotlin/ru/privatenull/pnlibrary/spi/platform/PlatformAdapter.kt`
- Create: `modules/runtime-spi/src/test/kotlin/ru/privatenull/pnlibrary/spi/tasks/PlatformTaskAdapterTest.kt`

**Interfaces:**
- Consumes: `TaskExecution.Kind`, `Duration`, execution target, and a core-provided guarded `Runnable`.
- Produces: `PlatformTaskRequest(execution, delay, interval, callback)`, `PlatformTaskHandle.cancel(): Boolean`, and `PlatformAdapter.taskAdapter`.

- [ ] **Step 1: Write a failing SPI contract test**

Test that `PlatformTaskRequest` rejects an entity execution without a target and accepts zero delay with no interval. Test the unsupported default adapter fails with a precise platform message and performs no callback.

```kotlin
assertThrows<IllegalArgumentException> {
    PlatformTaskRequest(TaskExecution.Kind.ENTITY, null, Duration.ZERO, null, Runnable {})
}
```

- [ ] **Step 2: Run the SPI test and confirm missing types**

Run: `./gradlew :modules:runtime-spi:test --tests '*PlatformTaskAdapterTest' --no-daemon`

Expected: compilation fails because the task SPI is absent.

- [ ] **Step 3: Implement the narrow adapter contract**

```kotlin
interface PlatformTaskAdapter : AutoCloseable {
    fun schedule(request: PlatformTaskRequest): PlatformTaskHandle
    override fun close() {}
}

fun interface PlatformTaskHandle {
    fun cancel(): Boolean
}
```

Add `val taskAdapter: PlatformTaskAdapter` to `PlatformAdapter` with an unsupported default so existing test adapters compile until Task 3 explicitly injects a recording adapter. Do not remove `executeGlobal` or `executeReply`; diagnostics/events still consume those dispatch primitives.

- [ ] **Step 4: Run SPI and compilation checks**

Run: `./gradlew :modules:runtime-spi:test :modules:core:compileTestKotlin --no-daemon`

Expected: SPI tests pass and existing core test doubles continue compiling.

- [ ] **Step 5: Commit the SPI**

```bash
git add modules/runtime-spi/src/main/kotlin/ru/privatenull/pnlibrary/spi modules/runtime-spi/src/test/kotlin/ru/privatenull/pnlibrary/spi
git commit -m "feat(tasks): define platform scheduler SPI"
```

### Task 3: Managed task lifecycle, registry, queries, and compatibility

**Files:**
- Create: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/tasks/ManagedTask.kt`
- Create: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/tasks/TaskRegistry.kt`
- Rewrite: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/tasks/TaskServiceImpl.kt`
- Modify: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/runtime/PnLibraryImpl.kt`
- Rewrite: `modules/core/src/test/kotlin/ru/privatenull/pnlibrary/core/TaskServiceImplTest.kt`
- Modify: `modules/core/src/test/kotlin/ru/privatenull/pnlibrary/core/testing/TestTaskService.kt`

**Interfaces:**
- Consumes: `TaskSpec`, public query/snapshot types, `PlatformTaskAdapter.schedule`, and the existing owner-aware error logger.
- Produces: atomic live handles, owner-scoped and global queries, bounded history, conflict policies, guarded callbacks, and compatibility behavior for existing callers.

- [ ] **Step 1: Write recording-adapter lifecycle tests**

Create a deterministic `RecordingTaskAdapter` that stores requests and can invoke callbacks manually or during `schedule`. Cover one-shot completion, repeat rescheduling state, action failure, false one-shot condition, false repeating condition, `cancelWhen`, action self-cancellation, cancellation before native-handle attachment, duplicate display names, and owner-isolated queries.

```kotlin
val handle = scope.schedule(TaskSpec.builder()
    .name("self-stop")
    .interval(Duration.ofSeconds(1))
    .action { it.cancel() }
    .build())
adapter.fire(handle.id)
assertEquals(TaskStatus.CANCELLED, handle.status)
assertTrue(adapter.handle(handle.id).cancelled)
```

- [ ] **Step 2: Add conflict, concurrency, and history tests**

Test `REJECT`, `KEEP_EXISTING`, and `REPLACE` for one owner; the same key across two owners; simultaneous `REPLACE` attempts; two concurrent native callbacks for a slow repeating action; failure releasing a key; zero history capacity; bounded eviction of oldest terminal snapshots; and close/cancel idempotence.

- [ ] **Step 3: Run focused tests and confirm the old implementation fails**

Run: `./gradlew :modules:core:test --tests '*TaskServiceImplTest' --no-daemon`

Expected: compilation or assertions fail because the old service owns a four-thread scheduled pool and has no registry/state model.

- [ ] **Step 4: Implement `ManagedTask` atomic lifecycle**

Use atomic status plus an invocation lock. Attach the native handle after successful registration, immediately cancelling it if cancellation won the race. Evaluate cancel conditions before run conditions. Increment run/skipped counters exactly once per platform signal. Sanitize failure snapshots to exception type plus message and pass the original throwable only to the logger.

```kotlin
fun invoke() {
    if (!running.compareAndSet(false, true)) { skipped.incrementAndGet(); return }
    try { /* conditions, status transition, action */ }
    catch (error: Throwable) { fail(error) }
    finally { running.set(false); finishOrReschedule() }
}
```

- [ ] **Step 5: Implement identity-based registry and owner scopes**

Use identity wrappers for owner indexes, a global ID map, and an owner/key map guarded by one registry lock for atomic conflict handling. Generate opaque IDs with `UUID.randomUUID().toString()`. Move terminal snapshots to an insertion-ordered bounded history and remove active/key indexes in one operation.

`TaskServiceImpl` must register core state first, call the adapter outside the registry lock, then publish/attach the native handle or roll back fully on failure. Scope queries inject their owner identity regardless of query content. Service close cancels scopes before closing the platform adapter.

- [ ] **Step 6: Update runtime wiring and test utilities**

Construct `TaskServiceImpl(platform.taskAdapter, settings, errorLogger)` in `PnLibraryImpl`. Expand `TestTaskService` to record specs and return deterministic handles so action, menu, plugin-registry, diagnostic, and event tests compile without using a real scheduler.

- [ ] **Step 7: Run core tests**

Run: `./gradlew :modules:core:test --no-daemon`

Expected: all core tests pass; no thread named `pnLibrary-tasks` is created by task tests.

- [ ] **Step 8: Commit core scheduling**

```bash
git add modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/tasks modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/runtime/PnLibraryImpl.kt modules/core/src/test/kotlin/ru/privatenull/pnlibrary/core
git commit -m "feat(tasks): manage observable task lifecycle"
```

### Task 4: Bukkit, Paper, and Folia native adapter

**Files:**
- Create: `platforms/bukkit/runtime/src/main/kotlin/ru/privatenull/pnlibrary/bukkit/tasks/BukkitTaskAdapter.kt`
- Modify: `platforms/bukkit/runtime/src/main/kotlin/ru/privatenull/pnlibrary/bukkit/BukkitPlatformAdapter.kt`
- Create: `platforms/bukkit/runtime/src/test/kotlin/ru/privatenull/pnlibrary/bukkit/tasks/BukkitTaskAdapterTest.kt`

**Interfaces:**
- Consumes: `PlatformTaskRequest` and Bukkit/Paper/Folia native scheduler APIs already available to the runtime module.
- Produces: `BukkitTaskAdapter` selecting legacy Bukkit or reflective Folia scheduling and returning cancellation backed by the native task.

- [ ] **Step 1: Write gateway-based adapter tests**

Define an internal `BukkitSchedulerGateway` seam and recording fake. Test sync/async one-shot and repeating mapping, millisecond-to-tick ceiling with zero allowed for immediate work, native cancellation result, Folia global selection, Folia entity selection, and rejection of an unsupported Folia entity target before gateway registration.

```kotlin
assertEquals(1L, durationToTicksCeil(Duration.ofMillis(1)))
assertEquals(20L, durationToTicksCeil(Duration.ofSeconds(1)))
```

- [ ] **Step 2: Run the Bukkit adapter test and confirm it fails**

Run: `./gradlew :platforms:bukkit:runtime:test --tests '*BukkitTaskAdapterTest' --no-daemon`

Expected: compilation fails because the adapter and gateway are absent.

- [ ] **Step 3: Implement native and Folia gateways**

Detect Folia once during adapter construction. Legacy Bukkit maps global/entity to sync scheduler methods and async to async scheduler methods. Folia maps global to `GlobalRegionScheduler`, entity to the recipient's `EntityScheduler`, and async to Bukkit's asynchronous scheduler. Convert both delay and interval with ceiling and enforce a minimum repeating period of one tick.

Return a `PlatformTaskHandle` whose first cancel delegates to the native cancellation object and whose later calls return `false`. Adapter close cancels only tasks it registered and is idempotent.

- [ ] **Step 4: Expose the adapter and run Bukkit tests**

Initialize `override val taskAdapter` alongside the existing command/audience adapters. Keep `executeGlobal` and `executeReply` for non-task consumers.

Run: `./gradlew :platforms:bukkit:runtime:test --no-daemon`

Expected: all Bukkit runtime tests pass, including Folia selection and unsupported-target cleanup.

- [ ] **Step 5: Commit Bukkit scheduling**

```bash
git add platforms/bukkit/runtime/src/main/kotlin/ru/privatenull/pnlibrary/bukkit platforms/bukkit/runtime/src/test/kotlin/ru/privatenull/pnlibrary/bukkit/tasks
git commit -m "feat(tasks): schedule natively on Bukkit and Folia"
```

### Task 5: BungeeCord and Velocity native adapters

**Files:**
- Create: `platforms/bungee/runtime/src/main/kotlin/ru/privatenull/pnlibrary/bungee/tasks/BungeeTaskAdapter.kt`
- Modify: `platforms/bungee/runtime/src/main/kotlin/ru/privatenull/pnlibrary/bungee/BungeePlatformAdapter.kt`
- Create: `platforms/bungee/runtime/src/test/kotlin/ru/privatenull/pnlibrary/bungee/tasks/BungeeTaskAdapterTest.kt`
- Create: `platforms/velocity/runtime/src/main/kotlin/ru/privatenull/pnlibrary/velocity/tasks/VelocityTaskAdapter.kt`
- Modify: `platforms/velocity/runtime/src/main/kotlin/ru/privatenull/pnlibrary/velocity/VelocityPlatformAdapter.kt`
- Create: `platforms/velocity/runtime/src/test/kotlin/ru/privatenull/pnlibrary/velocity/tasks/VelocityTaskAdapterTest.kt`

**Interfaces:**
- Consumes: the same `PlatformTaskRequest` contract from Task 2.
- Produces: native delay/repeat/cancellation mapping for both proxy platforms; entity execution intentionally maps to their safe general scheduler.

- [ ] **Step 1: Write BungeeCord mapping tests**

Through a recording gateway, assert immediate, delayed, and repeating requests call the correct `TaskScheduler` overload; global, async, and entity kinds all use the proxy-safe scheduler; cancellation delegates once; close cancels remaining registrations.

- [ ] **Step 2: Write Velocity mapping tests**

Through a recording task-builder gateway, assert `.delay(Duration)` and `.repeat(Duration)` are applied only when present, all execution kinds schedule safely, callback identity is preserved, and cancellation delegates once.

- [ ] **Step 3: Run both adapter suites and confirm missing adapters**

Run: `./gradlew :platforms:bungee:runtime:test --tests '*BungeeTaskAdapterTest' :platforms:velocity:runtime:test --tests '*VelocityTaskAdapterTest' --no-daemon`

Expected: compilation fails because both adapters are absent.

- [ ] **Step 4: Implement and wire BungeeCord adapter**

Use the plugin instance as native scheduler owner. Map interval absence to `schedule(plugin, callback, delay, MILLISECONDS)` and presence to the repeating overload. Track handles for adapter shutdown without adding another executor.

- [ ] **Step 5: Implement and wire Velocity adapter**

Use `server.scheduler.buildTask(plugin, callback)`, conditionally apply native delay/repeat, schedule, and wrap the returned `ScheduledTask.cancel()`. Track handles for adapter shutdown without adding another executor.

- [ ] **Step 6: Run proxy runtime tests**

Run: `./gradlew :platforms:bungee:runtime:test :platforms:velocity:runtime:test --no-daemon`

Expected: all proxy runtime tests pass.

- [ ] **Step 7: Commit proxy scheduling**

```bash
git add platforms/bungee/runtime/src/main/kotlin/ru/privatenull/pnlibrary/bungee platforms/bungee/runtime/src/test/kotlin/ru/privatenull/pnlibrary/bungee/tasks platforms/velocity/runtime/src/main/kotlin/ru/privatenull/pnlibrary/velocity platforms/velocity/runtime/src/test/kotlin/ru/privatenull/pnlibrary/velocity/tasks
git commit -m "feat(tasks): schedule natively on proxy platforms"
```

### Task 6: Documentation, compatibility evidence, and release verification

**Files:**
- Create: `docs/TASKS.md`
- Modify: `README.md`
- Modify: `HELP-README.md`
- Modify: `CHANGELOG.md`
- Modify: `modules/api/api/api.api` only if final KDoc/API corrections change the dump

**Interfaces:**
- Consumes: the finished task API and platform behavior from Tasks 1-5.
- Produces: Kotlin/Java usage guidance and complete verification evidence.

- [ ] **Step 1: Write focused Kotlin and Java documentation**

Document immediate, delayed, repeating, async, entity-bound, conditional, cancel-when, self-cancelling, duplicate-name, keyed conflict, owner query, global query, and shutdown examples. State explicitly that names can repeat and that native schedulers own timing.

- [ ] **Step 2: Replace stale timer-pool descriptions**

Update README, HELP-README, and changelog sections that currently describe `TaskServiceImpl` as the timer owner. Link `docs/TASKS.md` from the main README.

- [ ] **Step 3: Run API compatibility checks**

Run: `./gradlew :modules:api:apiCheck :platforms:bukkit:api:apiCheck :platforms:bungee:api:apiCheck :platforms:velocity:api:apiCheck --no-daemon`

Expected: all API checks pass and old task signatures remain present.

- [ ] **Step 4: Run the complete clean verification**

Run: `./gradlew clean test apiCheck :distribution:build --warning-mode all --no-daemon`

Expected: every test and API check passes; Bukkit, BungeeCord, and Velocity distribution artifacts and metadata are produced.

- [ ] **Step 5: Perform whole-branch review**

Inspect `git diff` from the pre-plan implementation commit through `HEAD` for leaked native types, owner-scope escapes, unbounded collections, hard-coded task pools, missing cancellation, Java-inaccessible methods, and accidental inclusion of the user's pre-existing changes. Correct any finding and repeat the focused/full command affected by it.

- [ ] **Step 6: Commit documentation and final corrections**

```bash
git add docs/TASKS.md README.md HELP-README.md CHANGELOG.md modules/api/api/api.api
git commit -m "docs: explain native task scheduling"
```

- [ ] **Step 7: Push and verify the remote branch**

Run:

```bash
git push origin feat/pnlibrary-architecture-pnupdate
git rev-parse HEAD
git ls-remote origin refs/heads/feat/pnlibrary-architecture-pnupdate
```

Expected: local and remote commit IDs are identical; only the user's pre-existing changes remain unstaged.
