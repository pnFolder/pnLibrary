# pnLibrary API design rules

These rules define the public language of pnLibrary. New API is not accepted when it introduces a
second way to express an existing concept.

## 1. Module boundary

- `pnlibrary-api` contains only stable, platform-neutral capabilities required by most plugins.
- Every optional feature is split into `<feature>-api` and `<feature>-runtime`.
- A feature API is obtained from `ModuleContext.services` through a Java facade named `<Feature>Api`.
- Kotlin may additionally expose an extension property, but that property must delegate to the same
  facade. It must not introduce another lifecycle or implementation.
- Platform API modules contain public platform types. Platform runtime modules contain adapters.
- `runtime-spi` contains only contracts implemented by a platform runtime for core. Consumer plugins
  do not depend on it.
- Implementation packages never appear in signatures of public API modules.

## 2. Construction

Use one of these patterns; do not mix their responsibilities.

### Immutable value or reusable declaration

```kotlin
val spec = TaskSpec.builder()
    .name("cleanup")
    .execution(TaskExecution.async())
    .action { /* ... */ }
    .build()
```

- Entry point: `Type.builder(requiredArguments...)`.
- Builder: nested `Type.Builder`, with an `internal` constructor.
- Mutators: `property(value): Builder` and return `this`.
- Terminal operation: exactly one `build()`.
- Validation happens in `build()`, not at an arbitrary later use site.
- Java entry points are `@JvmStatic`; default arguments exposed to Java use `@JvmOverloads` only
  when every generated overload is meaningful.

### Registration owned by a service

```kotlin
val registration = service.register("coins") { definition ->
    definition.descriptor { it.displayName("Coins") }
}
```

- `register(...)` is the terminal operation and returns a lifecycle handle.
- The configuration callback only describes the registration. It never requires a trailing
  `.register()` or `.build()`.
- Handles implement `AutoCloseable`, expose `isClosed` when useful, and closing is idempotent.
- Owned registrations close with their `ModuleContext`.

### Stateful runtime object

- Use an explicit verb such as `open`, `load`, `start`, or `schedule`.
- Do not call a side-effecting operation `build` or `create`.

## 3. Lookup vocabulary

All registries use the same meanings:

- `get(key)` returns a nullable value and never throws for absence.
- `require(key)` returns the value or throws an `IllegalStateException` containing the key.
- `all()` returns an immutable snapshot, never a mutable backing collection.
- `contains(key)` is optional convenience and must have the same normalization as `get`.
- `register` rejects duplicates unless replacement is explicitly named `replace`.
- `unregister` is an imperative removal; closing the returned registration must have equivalent
  ownership semantics.

Identifiers are normalized in their value type (`PluginId.of`, `ModuleId.of`, `ProductId.of`), not
independently by every registry.

## 4. Configuration vocabulary

- Boolean builder methods accept a value: `enabled(value)`, `automaticDownload(value)`.
- A no-argument convenience is allowed only when its meaning is unmistakable, such as
  `wholeNumbers()`.
- Collections use a singular adder (`tag(value)`) and plural bulk form (`tags(values)`).
- Nested configuration methods are nouns matching the configured concept:
  `metadata {}`, `updates {}`, `diagnostics(...)`, `commands {}`.
- `configure`, `withX`, `setX`, Kotlin mutable properties, and fluent methods must not coexist for
  the same public concept.
- Required values belong in `builder(requiredValue)` when known at construction time.

## 5. Kotlin and Java

- Kotlin is the implementation language, but every public API must be pleasant from Java.
- The canonical ABI uses Java functional interfaces (`Consumer`, `Function`, dedicated `fun
  interface` contracts) where callbacks cross the public boundary.
- Kotlin receiver DSLs are thin `@JvmSynthetic` extensions in a dedicated DSL file. They delegate
  to the canonical builder/service and contain no validation or state.
- Public Kotlin types must not expose `Unit`, function types, `Result`, mutable collections, or
  platform implementation classes to Java unless intentionally Kotlin-only.
- Public factories and constants receive deliberate JVM names and static exposure.

## 6. Lifecycle and failure

- Creation is transactional: either the object is fully usable or all acquired resources are
  closed in reverse order.
- `close()` is idempotent.
- Async operations return `CompletionStage` at the public boundary.
- Expected business outcomes use a typed result/status. Programming errors and invalid declarations
  use exceptions.
- Errors name the rejected value and the violated rule.

## 7. Compatibility

- Public API dumps are checked in CI.
- An inconsistent existing method is deprecated first and delegates to the canonical method.
- Removal happens only in a declared major API version.
- Every optional feature has a boundary test proving its classes are absent from `pnlibrary-api`.
- Every public builder receives Kotlin and Java compilation examples or tests.

## 8. Review checklist

Before merging public API, verify:

1. Is the type in the smallest correct API module?
2. Does it follow one construction pattern above?
3. Are `get`, `require`, registration, and lifecycle semantics conventional?
4. Is there one source of validation and state?
5. Can Kotlin and Java call it without adapters invented by the consumer?
6. Is ownership and thread behavior documented?
7. Did `apiCheck`, tests, and the optional-feature boundary checks pass?

