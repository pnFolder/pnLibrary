# Metrics facade design

## Goal

Expose one small public metrics API while keeping bStats and FastStats implementation details inside pnLibrary. A plugin should be able to enable either provider or both, publish shared metrics once, and access FastStats-only error tracking when available.

## Public surface

The plugin interacts with `context.metrics` only:

```kotlin
builder.metrics {
    it.bStats(32592)
    it.fastStats(FASTSTATS_TOKEN)
    it.charts { metrics ->
        metrics.simplePie("server_mode") { "survival" }
    }
}
```

Shared charts are registered once and fanned out to every enabled provider:

```kotlin
context.metrics.configure { metrics ->
    metrics.simplePie("server_mode") { "survival" }
}
```

FastStats-only functionality is optional:

```kotlin
context.metrics.fastStatsOrNull()?.errorTracker()?.capture(error)
```

No provider-specific configuration objects are required from plugin authors. The existing advanced configuration API remains available internally for tests and integrations.

## Provider behavior

- bStats receives shared chart metrics only.
- FastStats receives shared chart metrics and supports error tracking and provider-specific attributes.
- Enabling one provider must not require the other provider's SDK.
- Failure of a metrics provider must never prevent a plugin from starting.

## Error flow

FastStats context-aware tracking handles uncaught exceptions automatically. pnLibrary adds standard runtime metadata, sanitizes sensitive values, groups duplicate errors, and forwards the report. Handled exceptions can be explicitly submitted through `context.errors.capture(error)`. Operation names and custom attributes are optional.

## Non-goals

- Do not expose separate provider modules to plugin authors.
- Do not send player identities, credentials, raw files, or database contents.
- Do not make bStats responsible for error reporting.

## Acceptance criteria

1. A plugin can enable bStats, FastStats, or both with one builder block.
2. Shared metrics are emitted once per enabled provider.
3. `fastStatsOrNull()` is safe when FastStats is disabled or unavailable.
4. Uncaught FastStats errors are collected automatically; handled errors can be captured explicitly.
5. Existing integrations and advanced configuration remain source-compatible.
6. Tests cover provider combinations, fan-out, optional FastStats access, and error capture.
