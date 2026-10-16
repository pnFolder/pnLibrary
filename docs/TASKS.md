# Tasks

Tasks are obtained only through `ModuleContext.tasks`. Register your native plugin
once, then create its logical module:

```kotlin
val pluginRegistration = library.plugins.register(plugin)
val context = pluginRegistration.registerModule("main")
val tasks = context.tasks
```

Closing the module cancels its tasks; closing the plugin registration closes all its
modules. There is no public `PnLibrary.tasks` or owner-based scope factory.

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

context.tasks.get(handle.id)?.cancel()
context.tasks.query() // this module only
```

`TaskId` is globally unique. `name` is only a display label and may repeat. An optional `key` is unique within one owner; `REJECT`, `KEEP_EXISTING`, and `REPLACE` define collision behavior.

Queries return immutable `TaskSnapshot` values rather than native scheduler objects. Completed, cancelled, and failed tasks live in bounded diagnostic history; active tasks are never evicted.

The history limit is configured at bootstrap with `PnLibraryConfig(taskHistoryCapacity = 256)`. Set it to zero to disable terminal history; active tasks remain queryable regardless of the limit.

## Lifecycle

Closing a handle cancels one task. Closing a `TaskScope` cancels every active task for that exact owner and rejects new work. Closing pnLibrary closes all registered modules and native task adapters. Cancellation is idempotent and does not interrupt an action already running.

The older `global`, `later`, `repeat`, `entity`, and `asyncThen` methods were removed. Integrations
must use `schedule` with an explicit `TaskSpec`.
