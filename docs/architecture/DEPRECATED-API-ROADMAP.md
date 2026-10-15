# Deprecated API roadmap

The cleanup branch removes the deprecated task, service, plugin, placeholder, update, and
platform entry points from the published API. The table below records the canonical replacements
for consumers migrating from the previous API.

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

## Cleanup status

The deprecated declarations and their compatibility implementations have been removed. Existing
consumers must migrate to the canonical APIs above; the API dumps are updated to enforce that no
second public vocabulary is reintroduced.
