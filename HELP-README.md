# pnLibrary architecture

## The 30-second model

pnLibrary is one shared runtime plugin installed in each server or proxy process.
pnMarket, pnClans, and other consumers do not embed it. They compile against the
API and obtain the running `PnLibrary` instance.

```text
consumer plugins
      │
      ▼
pnlibrary-api          public contracts
      │
      ▼
pnlibrary-runtime-spi  private platform boundary
      │
pnlibrary-core         platform-independent behavior
  ┌───┼────┐
  ▼   ▼    ▼
Bukkit Bungee Velocity
```

Remember this rule:

- `api` defines what consumers can use;
- `core` implements shared behavior;
- platform modules connect that behavior to native server APIs;
- `distribution` packages production JARs.

## Modules

| Module | Responsibility |
|---|---|
| `pnlibrary-api` | Public runtime, services, diagnostics, tasks, logging, metrics, updates, and configuration contracts |
| `pnlibrary-bukkit-api` | Public Bukkit-only server environment, Minecraft version, menu contracts, and builders |
| `pnlibrary-runtime-spi` | Private bridges used only by core and platform runtimes |
| `pnlibrary-core` | Common runtime implementations with no Bukkit, Bungee, or Velocity imports |
| `pnlibrary-bukkit` | Private Bukkit/Paper/Purpur/Leaf/Folia adapter, commands, menu implementation, and bStats |
| `pnlibrary-bungee` | BungeeCord/Waterfall adapter, commands, scheduler, and bStats |
| `pnlibrary-velocity` | Velocity adapter, commands, scheduler, SLF4J, and bStats |
| `pnlibrary-bstats-base` | Shared official bStats classes |
| `pnlibrary-distribution` | Relocated fat JARs for each platform |

Dependencies point in one direction:

```text
distribution → platform runtime → core → runtime-spi → api
                    │
                    └── bukkit-api
```

`api` never depends on runtime modules. `core` never imports a native platform
API. If shared code needs a platform operation, add the smallest useful
operation to the private `runtime-spi` and implement it for every platform.

The boundary is intentional:

- `api` contains public interfaces, data models, builders, and small
  API conveniences only;
- `core` is the common executable engine and keeps implementations `internal`
  whenever consumers do not need their concrete classes;
- `runtime-spi` contains technical bridges that consumers must not see;
- a platform runtime translates between the common engine and one native API;
- `distribution` assembles the engine and exactly one platform adapter.

Do not create a second implementation in `api`, and do not move a native server
type into a public common contract. A new subsystem normally gets one API
package and one matching implementation package in `core`.

Folia is not a separate module. The Bukkit adapter detects it at runtime and
uses its schedulers through reflection.

## Runtime composition

[`PnLibrary`](pnlibrary-api/src/main/kotlin/ru/privatenull/pnlibrary/api/runtime/PnLibrary.kt)
is the public facade:

```text
PnLibrary
├── plugins      global PluginId → PluginContext registry
├── services     typed PluginId-owned service providers
├── diagnostics  contributed state and diagnostic history
├── events       cross-platform application event bus
├── logging      native logs and message boxes
├── metrics      managed bStats sessions
├── updates      consumer plugin updates
├── tasks        owner-bound task scopes
```

Consumers call `PnLibraryProvider.get()` on every platform. Platform-native
service registries are not used.

[`PnLibraryImpl`](pnlibrary-core/src/main/kotlin/ru/privatenull/pnlibrary/core/runtime/PnLibraryImpl.kt)
is the composition root: it constructs and connects all service implementations.
Start here when you need to know where a service comes from.

[`PlatformAdapter`](pnlibrary-runtime-spi/src/main/kotlin/ru/privatenull/pnlibrary/spi/platform/PlatformAdapter.kt)
handles native logging, metadata, scheduler dispatch, diagnostics, bStats, and
binding platform commands/listeners. It is SPI, not consumer API. `PlatformType` describes one of the three
API families (`BUKKIT`, `BUNGEECORD`, or `VELOCITY`). A concrete implementation
such as Paper, Folia, NullCordX, or a private fork is runtime metadata only.

## Startup and shutdown

The native entry points are `PnLibraryBukkitPlugin`, `PnLibraryBungeePlugin`, and
`PnLibraryVelocityPlugin`. Each delegates shared lifecycle work to
`PnLibraryRuntimeHost`:

```text
native plugin starts
→ create PlatformAdapter
→ PnLibraryRuntimeHost.start()
   → load config.yml
   → PnLibraryBootstrap creates PnLibraryImpl
   → install PnLibraryProvider
   → bind platform commands/listeners
   → start the self-updater
   → print the startup message box
```

`PnLibraryBootstrap` guarantees one active runtime per JVM. A Velocity proxy and
three Paper servers therefore have four independent pnLibrary instances.

`PnLibraryRuntimeHost.close()` stops the self-updater, prints the shutdown box,
and closes the runtime. `PnLibraryImpl.close()` then releases events, tasks,
updates, metrics, diagnostics, the provider, and the adapter.

## Subsystems

### Diagnostics

A consumer registers a `DiagnosticContainer` with a component ID, a fast state
snapshot, allowed configuration files, and optional redaction rules. It can also
write current component state with `diagnostics.status()` and bounded events with
`diagnostics.record()`.

```text
/pndebug
→ DiagnosticCommandExecutor       parse, cooldown, async execution, reply dispatch
→ PnLibrary.createDiagnosticReport()
→ ReportGenerator
   ├── SystemCollector
   ├── PlatformAdapter.details()
   ├── DiagnosticsRegistry
   ├── ConfigReader                with --config
   └── DiagnosticLogBuffer         with --logs
→ redact → size limit → JSON → encrypt → save → optional upload
```

`ConfigReader` only reads declared files inside the consumer data directory.
Path traversal and symlinks are blocked. Values, nesting, event history, report
size, and concurrent report generation are bounded.

### Tasks

`TaskServiceImpl` returns one `TaskScope` per owner identity. Closing the scope
cancels all of its task handles.

| Method | Execution context |
|---|---|
| `global`, `later`, `repeat` | Platform main/global scheduler |
| `entity`, `laterEntity`, `repeatEntity` | Folia entity scheduler, global elsewhere |
| `async`, `repeatAsync` | pnLibrary executor |
| `asyncThen` | Work async, result back in global context |

The timer may run inside pnLibrary, but callbacks that touch the server are sent
through `PlatformAdapter`. Callback failures are caught and logged.

### Events

`EventService` is the platform-independent event bus. Event classes implement
`Event`; cancellable events additionally implement `Cancellable`. A plugin obtains
one plugin-ID scope and registers typed listeners on it:

```kotlin
data class ClanCreatedEvent(val clanId: String) : Event()

val events = pn.plugins.require("pnclans").events
events.subscribe<ClanCreatedEvent> { event ->
    logger.info("Created clan ${event.clanId}")
}
ClanCreatedEvent("knights").callEvent().thenAccept { allowed ->
    if (!allowed) logger.warn("Clan creation was cancelled")
}
```

`Event` exposes `eventName`, `mode`, `isAsynchronous`, and one convenience method:
`callEvent()`. `EventMode.SYNC` routes listeners through the platform's main/global
scheduler; `EventMode.ASYNC` routes them through pnLibrary's background executor.
The returned `CompletableFuture<Boolean>` completes after all listeners finish.
Compose it with `thenAccept`; do not block a platform-owned thread with `join()`.

A cancellable event composes both contracts instead of using a separate event
subclass:

```kotlin
class PurchaseEvent : Event(), Cancellable {
    override var isCancelled: Boolean = false
}
```

The annotation style uses the same dispatcher:

```kotlin
class ClanListener : Listener {
    @EventHandler(priority = 250)
    fun created(event: ClanCreatedEvent) { /* handle */ }
}

events.register(ClanListener())
```

Registration validates every annotated method up front. A handler accepts
exactly one `Event` subtype and returns `Unit`/`void`. The returned
`EventListenerRegistration` can remove all methods from that listener at once.

Dispatch is routed to the execution context selected by the event mode.
Priority is any integer;
smaller values run first. `EventPriority` provides spaced presets from `LOWEST`
(`-1000`) to `MONITOR` (`2000`), while callers may insert values such as `250`.
Equal priorities retain registration order, listener failures are isolated and
logged, and `ignoreCancelled` has identical behavior everywhere.
Closing the scope removes all of its subscriptions. Native Bukkit, BungeeCord,
or Velocity events remain inside adapters; shared plugins expose their own
domain events through this bus.
There is no per-event `HandlerList`: the central thread-safe bus already owns
registrations and plugin scopes own their lifecycle.

### Logging

`PlatformLoggingService` creates `PnLogger` and `MessageBox`. `PnLogger` writes to
the owner's native logger and stores a bounded diagnostic copy. A registered
plugin uses `context.lifecycle` for enabled/disabled boxes and
`context.messages.box(title)` for neutral operation reports. Every box buffers
rows and renders them together only on explicit `show()`.

### Services

`ServiceManager` is pnLibrary's platform-independent typed registry. Every
consumer publication belongs to a `PluginId`, but ownership is taken
automatically from the `PluginContext`.

```kotlin
interface EconomyService

context.registerService(EconomyService::class.java, economy, priority = 100)
val selected = pn.services.require(EconomyService::class.java)
```

The provider with the highest numeric priority wins; `getAll()` returns every
provider in priority order. One owner cannot publish the same contract twice.
`context.unregisterService(...)` removes one provider, while closing the plugin
context removes all services belonging to that owner. Consumers never create
scopes or pass a `PluginId` during registration. `ServiceManagerImpl` is the
thread-safe core implementation; consumers only compile against its interfaces.

### Metrics

```text
MetricsService → MetricsRegistry → PlatformMetricsFactory
→ native bStats Metrics → BStatsMetricsSession
```

Every consumer supplies its own bStats project ID. The registry owns open
sessions and closes leftovers during runtime shutdown. A `PluginContext` exposes
`MetricsController`, which can enable or disable metrics and change the project
ID at runtime while preserving chart configuration.

### Updates

The runtime host starts the pnLibrary self-updater. Consumer plugins register
their own `PluginUpdateRequest` through `pn.updates`.

```text
GitHub Releases → channel filter → SemVer comparison
→ select asset for PlatformType and Java → download
→ SHA-256 and JAR descriptor validation → plugins/update
```

`UpdateSnapshot` exposes `CHECKING`, `CURRENT`, `AVAILABLE`, `DOWNLOADED`, or
`FAILED`. Distribution selection uses the base `PlatformType`, so compatible
Bukkit forks always use the Bukkit descriptor and distribution.

### Configuration

There are two separate systems:

- `PnLibraryConfigLoader` strictly loads pnLibrary's own `config.yml`. Unknown
  keys and wrong types fail startup to protect security settings.
- `CodeFirstYaml<T>` is a consumer tool. It merges missing typed defaults into
  existing YAML, preserves values/comments, validates, backs up, and atomically
  replaces the file.

`ConfigGroup` manages several `ManagedConfig` handles together.

### Bukkit inventory GUI

`pnlibrary-bukkit-api/inventory` contains `Menu`, builders, `MenuService`, and
`PnMenus`. `pnlibrary-bukkit/inventory` contains the private shared listener and
service implementation. Sessions use a private `InventoryHolder` UUID instead
of the title. Owner menus close on plugin shutdown; Folia refreshes use the
player's entity scheduler.

## Consumer lifecycle

Consumers declare `pnlibrary-api` as `compileOnly`; they must not embed or
relocate it. Register once and keep one high-level `PluginContext`:

```kotlin
private lateinit var context: PluginContext

override fun onEnable() {
    val pn = PnLibraryProvider.get()
    context = pn.plugins.register(this) {
        it.metrics(12345) { metrics -> metrics.simplePie("storage") { "SQLITE" } }
        it.listener(ClanListener())
        it.diagnostics(dataFolder.toPath(), diagnosticContainer)
        it.updates("pnFolder", "pnClans") { updates ->
            updates.channel(UpdateChannel.STABLE)
                .automaticDownload(true)
                .artifact("(?i)^pnClans-.*\\.jar$", minimumJava = 17)
        }
    }
    context.lifecycle.enabled().ok("Storage", "ready").show()
}

override fun onDisable() {
    context.close()
    context.lifecycle.disabled().ok("Storage", "saved").show()
}
```

`pn.plugins.require("pnclans")` returns the same context globally. Native plugin
metadata, Java, and platform data are exposed by `context.metadata`.
The Bukkit-only `PnBukkit.server()` entry point exposes the core name, core
version, and parsed/raw Minecraft version without adding Bukkit concepts to the
cross-platform facade.
`context.lifecycle.enabled()` and `disabled()` return buffered message boxes;
rows print together only after explicit `show()`. One `close()`
releases updates, diagnostics, metrics, events, and tasks. General rule:
the code that calls `open`, `register`, `scope`, or `start` owns the returned
handle and must close it.

## Where to make a change

| Change | Start here |
|---|---|
| Public cross-platform contract | `pnlibrary-api` |
| Public Bukkit contract | `pnlibrary-bukkit-api` |
| Platform implementation boundary | `pnlibrary-runtime-spi` |
| Service composition | `PnLibraryImpl` |
| Typed service contracts/registry | `ServiceManager`, `PluginContext.registerService`, then `ServiceManagerImpl` |
| Startup/shutdown | `PnLibraryRuntimeHost`, then native entry points |
| Platform identity | `PlatformType` and adapters |
| `/pndebug` flow | `DiagnosticCommandExecutor`, then platform rendering |
| Report contents/security | `core/diagnostics` |
| Threads/timers | `TaskServiceImpl`, then `PlatformAdapter` |
| Cross-platform events | `EventService`, `EventScope`, then `EventServiceImpl` |
| Global plugin context | `PluginRegistry`, `PluginContext`, then `PluginRegistryImpl` |
| Plugin lifecycle UI | `PluginLifecycle`, `LifecycleReport`, then `PluginRegistryImpl` |
| Logs/message boxes | `PlatformLoggingService` |
| Metrics | `MetricsRegistry`, `BStatsMetricsSession`, platform factory |
| Updater | `UpdateServiceImpl`, `MandatoryUpdateService` |
| pnLibrary config | `PnLibraryConfig`, `PnLibraryConfigLoader` |
| Consumer code-first config | `ManagedConfig`, `CodeFirstYaml`, `YamlDefaultsMerger` |
| Bukkit GUI contract | `pnlibrary-bukkit-api/inventory` |
| Bukkit GUI execution | `pnlibrary-bukkit/inventory` |
| Final JAR contents | `pnlibrary-distribution/build.gradle.kts` |

## Fast reading order

Read these files, in order:

1. `PnLibrary.kt`
2. `PlatformType.kt`; for Bukkit code, `PnBukkit.kt` and `ServerInfo.kt`
3. `PlatformAdapter.kt` in `pnlibrary-runtime-spi`
4. `PnLibraryBootstrap.kt`
5. `PnLibraryImpl.kt`
6. One native entry point and its adapter
7. One vertical feature from interface to implementation

Example vertical slice:

```text
TaskService → TaskServiceImpl → PlatformAdapter → BukkitPlatformAdapter
```

Do not read all three adapters first. They implement the same boundary.

## Build

Bukkit and Bungee target Java 8 bytecode; Velocity targets Java 17. Build the
project on JDK 17 or newer.

```text
gradlew.bat clean test :pnlibrary-distribution:build
```

Artifacts are written to `pnlibrary-distribution/build/libs`. Always build the
distribution before publishing: module tests do not verify relocation, packaged
resources, or final platform JAR compatibility.
