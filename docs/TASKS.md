# Tasks

pnLibrary exposes one cross-platform scheduling primitive: `TaskScope.schedule(TaskSpec)`. Delays and intervals are owned by the native Bukkit/Paper/Folia, BungeeCord, or Velocity scheduler; pnLibrary adds owner lifecycle, conditions, status, querying, and consistent cancellation.

## Kotlin

```kotlin
val handle = context.tasks.schedule {
    name = "player-hud"                  // display names may repeat
    key = "player-hud:${player.uniqueId}" // optional owner-local singleton key
    execution = TaskExecution.entity(player)
    delay = Duration.ofSeconds(1)
    interval = Duration.ofSeconds(1)
    tags = setOf("hud", "player")

    condition { player.isOnline }
    cancelWhen { !plugin.isEnabled }

    run { task ->
        updateHud(player)
        if (hudFinished(player)) task.cancel()
    }
}
```

Without `interval`, the task runs once. A false `condition` skips the current repetition; a true `cancelWhen` permanently cancels it. `GLOBAL`, `ASYNC`, and `entity(target)` select the native execution context.

## Java

```java
TaskHandle handle = tasks.schedule(
    TaskSpec.builder()
        .name("cache-refresh")
        .key("cache-refresh")
        .execution(TaskExecution.async())
        .delay(Duration.ofSeconds(5))
        .interval(Duration.ofMinutes(1))
        .condition(() -> plugin.isEnabled())
        .action(task -> refreshCache())
        .build()
);
```

Every builder method uses ordinary Java types. No Kotlin function type or generated default-argument method is required.

## Finding and cancelling tasks

```kotlin
val mine = context.tasks.query() // only this owner
val runningHud = context.tasks.query(
    TaskQuery.builder()
        .nameContains("hud")
        .tag("player")
        .status(TaskStatus.SCHEDULED, TaskStatus.RUNNING)
        .build()
)

context.tasks.find(handle.id)?.cancel()
library.tasks.query() // library-wide diagnostic view
```

`TaskId` is globally unique. `name` is only a display label and may repeat. An optional `key` is unique within one owner; `REJECT`, `KEEP_EXISTING`, and `REPLACE` define collision behavior.

Queries return immutable `TaskSnapshot` values rather than native scheduler objects. Completed, cancelled, and failed tasks live in bounded diagnostic history; active tasks are never evicted.

The history limit is configured at bootstrap with `PnLibraryConfig(taskHistoryCapacity = 256)`. Set it to zero to disable terminal history; active tasks remain queryable regardless of the limit.

## Lifecycle

Closing a handle cancels one task. Closing a `TaskScope` cancels every active task for that exact owner and rejects new work. Closing pnLibrary closes all scopes and the native task adapters. Cancellation is idempotent and does not interrupt an action already running.

The older `global`, `later`, `repeat`, `entity`, and `asyncThen` methods remain compatibility wrappers, but new integrations should use `schedule`.
