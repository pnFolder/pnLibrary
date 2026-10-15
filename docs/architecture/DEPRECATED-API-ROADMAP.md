# Deprecated API roadmap

The current branch keeps deprecated methods for source and binary compatibility. The library's
production code no longer depends on the legacy task, service, plugin, placeholder, or update
entry points listed below.

## Current replacements

| Deprecated area | Use instead |
| --- | --- |
| `TaskService.global/async/entity/later` | `TaskService.schedule(TaskSpec)` with an explicit `TaskExecution` |
| legacy task lookup helpers | `get(id)` or `getByKey(key)` |
| service lookup extensions | `ServiceManager.all(type)` / `all<T>()` |
| plugin registration lookup aliases | `all()` |
| update lookup aliases | `all()` / `get(product)` |
| placeholder builder shortcuts | `PlaceholderService.register(key, configure)` |
| legacy remote-policy builder overloads | `RemotePolicy.builder()` and pass the policy directly |
| `ConfigTypeAccess` builder aliases | `ownerOnly()` / `everyone()` |

## Removal policy

1. Keep the deprecated declarations during the current major line.
2. Keep migration messages and `ReplaceWith` hints accurate.
3. Verify that production source contains no calls to the deprecated declarations.
4. Remove them only in the next major release, together with an explicit migration note.

The compatibility layer is intentionally not moved into a new module: doing so would add another
published artifact without reducing the public API surface or the platform dependency graph.
