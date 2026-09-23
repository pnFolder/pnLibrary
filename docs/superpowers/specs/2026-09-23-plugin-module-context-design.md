# Plugin and module context redesign

## Intent

pnLibrary must distinguish a physical platform plugin from the logical modules that plugin exposes. A physical plugin is registered once. It may then create several isolated logical modules that behave like separate library consumers while sharing the same native owner.

## Public model

`PluginRegistry` owns physical plugin registrations:

```kotlin
val plugin = library.plugins.register(nativePlugin)
val economy = plugin.registerModule("economy") { builder ->
    // module declarations
}
```

- `PluginRegistry.register(owner)` returns a `PluginContext`.
- `PluginContext` represents one physical platform plugin and owns its modules.
- `ModuleContext` represents one logical module and exposes the existing module-scoped capabilities such as events, tasks, services, configuration, logging, metrics, updates, and downloads.
- There is no implicit default module. Every logical module is registered explicitly.

## PluginRegistry

The registry exposes one coherent physical-plugin API:

- `register(owner): PluginContext`
- `get(owner): PluginContext?`
- `require(owner): PluginContext`
- `unregister(owner)`
- `registrations(): List<PluginContext>`
- `close()`

Registration uses native object identity, not `equals`. Registering the same live owner twice fails. Unsupported owner types fail before a context becomes visible.

The old builder-based `register` overloads and `modules(owner)` facade are removed rather than retained as deprecated aliases. `PluginModules` is removed.

## PluginContext

The physical-plugin context exposes:

- the native `owner`;
- immutable native plugin metadata where useful;
- `registerModule(ModuleId, configure): ModuleContext` plus a string convenience overload;
- `getModule`, `requireModule`, `unregisterModule`, and `modules()`;
- `isClosed` and idempotent `close()`.

Module IDs must be unique within this plugin context. Closing the plugin context closes all of its modules, removes the physical registration, and prevents new module registration.

## ModuleContext

The current capability-rich `PluginContext` contract is renamed to `ModuleContext`. Its normalized identity is `ModuleId`, and all module-scoped pnLibrary capabilities remain on it.

Global services that require a stable textual namespace use a module key derived from the physical plugin identity and module ID. This prevents two different physical plugins from colliding when both contain a module named `core`. User-facing metadata may retain the declared module ID separately from the internal global key.

Closing a module context releases only that module's resources and removes it from its parent `PluginContext`. Other modules owned by the same native plugin remain live.

## Lifecycle and atomicity

Physical registration is lightweight and does not create module services. Module registration remains transactional: either every configured capability is created and the module becomes visible, or all partially created resources are closed in reverse order.

Platform disable hooks call `PluginRegistry.unregister(owner)`. This closes the plugin context and all module contexts. Closing the registry closes all physical plugin contexts and permanently rejects further registration.

## Migration

The demo becomes:

```kotlin
private lateinit var pluginContext: PluginContext
private lateinit var mainModule: ModuleContext

pluginContext = library.plugins.register(this)
mainModule = pluginContext.registerModule("pndemo") { builder -> /* ... */ }
```

Consumers use `ModuleContext` wherever they previously accepted the capability-rich `PluginContext`. Because the unwanted deprecated API is not intended to remain compatible, this is an explicit breaking API cleanup.

## Verification

Tests cover duplicate physical registration, unsupported owners, multiple modules under one owner, identical local module IDs under different owners, module-only close, plugin-wide close, platform disable cleanup, transactional rollback, lookup behavior, and registry close. The API dump and demo are updated, then the relevant Gradle test suite is run.
