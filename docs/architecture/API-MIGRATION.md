# API migration guide

Deprecated entry points remain binary-compatible throughout the current major line. New code should
use the canonical form in the right column; adapters delegate to it and will be removed only in a
declared major release.

| Previous API | Canonical API |
|---|---|
| `PluginRegistry.registrations()` | `PluginRegistry.all()` |
| `PluginRegistration.modules()` | `PluginRegistration.all()` |
| `UpdateService.registrations()` | `UpdateService.all()` |
| `UpdateService.find(product)` | `UpdateService.get(product)` or `require(product)` |
| `ServiceManager.getAll(type)` | `ServiceManager.all(type)` |
| `TaskService.find(id)` | `TaskService.get(id)` or `require(id)` |
| `TaskScope.find(id)` | `TaskScope.get(id)` or `require(id)` |
| `TaskScope.findByKey(key)` | `TaskScope.getByKey(key)` |
| `TaskScope.global/async/entity/later/repeat...` | `TaskScope.schedule(TaskSpec.builder()...build())` |
| `PlaceholderService.placeholder(...).register()` | `PlaceholderService.register(key) { ... }` |
| Kotlin `placeholder<T>(name) { ... }` | Kotlin `register<T>(name) { ... }` |
| `DiagnosticConfiguration.file(path)` | `DiagnosticConfiguration.builder(path)` |
| `ConfigTypeAccess.local()` | `ConfigTypeAccess.ownerOnly()` |
| `ConfigTypeAccess.global()` | `ConfigTypeAccess.everyone()` |
| `PluginBuilder.remotePolicy { ... }` | `PluginBuilder.remotePolicy(RemotePolicy.builder()...build())` |
| `RemotePolicyBuilder()` | `RemotePolicy.builder()` |
| `PluginBuilder.register()` | Registration is completed by `PluginRegistry.register(...)` / `registerModule(...)` |

## Optional capabilities

Optional APIs are obtained from `ModuleContext.services`. Currency is the first fully separated
feature and is available through `CurrencyApi.get(context)`. Compatibility properties for other
features remain valid until their module extraction is complete.

## Lifecycle

Keep every returned registration and close it when ownership ends. Closing is idempotent, and
registrations expose `isClosed`, `isActive`, or a typed state where applicable. A
`PluginRegistration` owns its modules; a `ModuleContext` owns registrations created during module
configuration and releases them in reverse acquisition order.
