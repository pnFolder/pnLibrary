# Native Cross-Platform Task System Design

## Goal

Replace pnLibrary's timer-first task implementation with a small, understandable high-level API backed by the native scheduler of Bukkit/Paper/Folia, BungeeCord, or Velocity. The same contracts must be convenient from Kotlin and Java, observable at runtime, owner-scoped, and safe to cancel.

## Public model

`TaskScope` exposes one scheduling primitive:

```kotlin
interface TaskScope : AutoCloseable {
    val owner: Any
    fun schedule(spec: TaskSpec): TaskHandle
    fun find(id: TaskId): TaskHandle?
    fun findByKey(key: String): TaskHandle?
    fun query(query: TaskQuery = TaskQuery.all()): List<TaskSnapshot>
    fun cancel(id: TaskId): Boolean
    fun cancelAll()
}
```

`TaskService` creates and closes owner scopes, provides library-wide diagnostics, and can address a particular owner's tasks. A scope query can never escape its owner. A service query can cover all owners or select one owner.

The existing `global`, `async`, `entity`, `later`, `laterEntity`, `repeat`, `repeatEntity`, `repeatAsync`, and `asyncThen` functions remain as deprecated compatibility wrappers. They construct a `TaskSpec` and delegate to `schedule`.

## Task specification and builders

`TaskSpec` is immutable and contains:

- optional display `name`;
- optional owner-local unique `key`;
- `TaskConflictPolicy`, defaulting to `REJECT` when a key is present;
- `TaskExecution` (`GLOBAL`, `ASYNC`, or `ENTITY(target)`);
- non-negative `delay`, defaulting to zero;
- optional positive `interval`; absence means a one-shot task;
- zero or more execution conditions;
- zero or more cancellation conditions;
- one required action accepting `TaskContext`;
- immutable string tags.

The Java entry point is `TaskSpec.builder()` with fluent methods accepting Java types such as `Duration`, `BooleanSupplier`, and `Consumer<TaskContext>`. Kotlin receives an extension DSL that builds the same `TaskSpec`; it does not implement separate scheduling behavior.

Invalid specifications fail before native registration. Negative delays, zero or negative intervals, blank keys/tags, and missing actions produce precise `IllegalArgumentException` messages.

## Identity and duplicate names

Every registration receives a globally unique opaque `TaskId`. Display names are intentionally non-unique: any number of tasks may be named `player-hud`, and `findByName`-style queries therefore return lists.

An optional `key` provides singleton semantics inside one owner scope. Equal keys belonging to different owners do not conflict. On an active owner-local key collision:

- `REJECT` fails without native registration;
- `KEEP_EXISTING` returns the active existing handle;
- `REPLACE` cancels the existing task and registers a new task with a new ID.

Terminal tasks do not reserve their former key.

## Execution, conditions, and self-cancellation

Immediately before each invocation, the runtime evaluates cancellation conditions and then execution conditions in declaration order.

- A cancellation condition returning `true` cancels the task permanently.
- An execution condition returning `false` skips only that invocation. A repeating task checks again on its next interval; a one-shot task becomes completed because it has no future invocation.
- A thrown condition or action exception is logged without exposing it to a scheduler thread and transitions the task to `FAILED`.

The action receives `TaskContext`, containing ID, optional name, run number, scheduled time, start time, and `cancel()`. Calling `cancel()` inside the action is idempotent and prevents future invocations. It does not interrupt the currently executing callback.

A repeating task never runs two copies of its own action concurrently. If a native fixed-rate scheduler signals another invocation while the previous action is running, that invocation is counted as skipped.

## Handles, status, and registry

`TaskHandle` is the live control object:

```kotlin
interface TaskHandle : AutoCloseable {
    val id: TaskId
    val status: TaskStatus
    fun snapshot(): TaskSnapshot
    fun cancel(): Boolean
}
```

Statuses are `SCHEDULED`, `RUNNING`, `COMPLETED`, `CANCELLED`, and `FAILED`. Cancellation is idempotent; only the transition that actually cancels returns `true`.

`TaskSnapshot` is immutable diagnostic data and never exposes a native task. It includes identity, owner description, name, key, tags, execution kind, status, timestamps, next scheduled time when known, run count, skipped count, and a sanitized last-failure description.

`TaskQuery` can filter by owner, ID, exact key, name substring, statuses, execution kinds, and tags. Empty criteria match every task visible in the query domain:

- `scope.query()` returns that owner's tasks;
- `service.query(owner)` returns one owner's tasks;
- `service.query()` returns tasks across every owner.

Active tasks live in the main registry. Terminal snapshots move into a bounded history so completed tasks cannot leak forever. History capacity is configured by `TaskServiceSettings`; zero disables history. Active tasks are never evicted.

## Low-level scheduler SPI

Core owns state transitions, conditions, error handling, registry bookkeeping, conflict policies, and owner lifecycle. Platform modules own only native scheduling and cancellation:

```kotlin
interface PlatformTaskAdapter : AutoCloseable {
    fun schedule(request: PlatformTaskRequest): PlatformTaskHandle
}
```

`PlatformTaskRequest` contains execution kind, delay, optional interval, and a guarded callback supplied by core. `PlatformTaskHandle.cancel()` cancels the real native task. The SPI does not expose public builders or registry objects.

Platform behavior:

- Bukkit/Paper uses `BukkitScheduler` for global sync and async work.
- Folia uses `GlobalRegionScheduler` for global work and `EntityScheduler` for entity work. Unsupported entity targets fail before registration.
- BungeeCord uses its `TaskScheduler`.
- Velocity uses its `Scheduler` task builder.
- Entity execution on proxy platforms resolves to the platform's safe general scheduler because those platforms have no region/entity thread ownership.

Native schedulers own delays and intervals. Core must not run another scheduled timer in front of them. A small executor may exist only when a platform genuinely lacks the requested asynchronous facility; it is injectable/configurable rather than hard-coded.

## Configuration

`TaskServiceSettings` supplies bounded-history capacity, shutdown policy, and any fallback executor configuration. Defaults are safe and require no configuration. Platform-native scheduling remains the default and cannot be accidentally replaced by the fallback executor.

Settings are provided when the runtime is constructed. They are not read from a global static object or an implicit configuration file.

## Lifecycle and failures

Each plugin or subsystem obtains one identity-based `TaskScope`. Closing a handle cancels one task. Closing a scope cancels every active task owned by that exact owner and rejects future scheduling. Closing `TaskService` closes all scopes, native adapters, and owned fallback executors exactly once.

Native registration failure leaves no active registry entry and releases any reserved key. A race between cancellation and registration must either cancel before publication or immediately cancel the newly returned native handle. Platform shutdown and repeated close/cancel calls remain safe.

## Documentation and compatibility

The task guide will show equivalent Kotlin and Java examples for one-shot, delayed, repeating, entity-bound, conditional, self-cancelling, queried, and keyed tasks. Deprecated methods document their `TaskSpec` equivalent and remain binary/source compatible for the current compatibility generation.

## Verification

Tests are concentrated by responsibility rather than duplicated for every convenience wrapper:

- API contract tests cover builders, validation, Java-callable factories, duplicate names, keys, and queries.
- Core tests use a recording platform adapter to cover lifecycle, statuses, conditions, self-cancellation, conflict policies, non-overlap, failure cleanup, and bounded history.
- One focused adapter suite per platform verifies request translation, native cancellation, delay/interval mapping, and scheduler selection, including Folia global/entity behavior.
- Compatibility tests prove old methods delegate to the new primitive.
- Final verification runs all tests, API compatibility checks, and every distribution build.
