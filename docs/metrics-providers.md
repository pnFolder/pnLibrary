# Metrics providers

pnLibrary exposes one provider-neutral metrics contract. A plugin registers charts through
`PluginMetrics`; the runtime decides which transports receive those registrations.

## Providers

- **bStats** keeps the existing numeric project ID and chart API.
- **FastStats** uses a string project token and may additionally expose custom values,
  context attributes, and error tracking.

The core API does not import either provider's SDK. This is deliberate: provider SDKs have
different lifecycles and Java requirements. Platform modules adapt their native SDK to the
same `PluginMetrics` contract.

## One or both providers

FastStats-specific access is optional and follows the session lifecycle:

```kotlin
context.metrics.fastStatsOrNull()?.errorTracker()?.capture(
    throwable = error,
    operation = "orders.load",
    attributes = mapOf("storage" to "mysql", "retryable" to true),
)
```

`fastStatsOrNull()` returns null when metrics are disabled or only bStats is enabled.
Register shared charts with `context.metrics.configure { ... }`; this replays chart
registration whenever the session is recreated. The facade's `metrics` property exposes
the provider's chart interface; chart configuration must occur before the session starts.
SDK feature flags and raw SDK objects are not exposed by this facade.

The runtime can create one delegate or a `CompositePluginMetrics` session. Shared chart calls
are fanned out to every enabled delegate. Provider-specific features are guarded by
`MetricsCapability` and must not be silently converted into a chart when a provider cannot
represent them.

```kotlin
builder.metrics {
    it.bStats(32592)
    it.fastStats(fastStatsToken)
    it.charts { metrics ->
        metrics.simplePie("server_software") { serverName }
    }
}
```

The same block can call `it.enabled(false)` when a module should declare providers and
charts without starting their sessions immediately. Runtime chart changes remain available
through `context.metrics.configure { ... }`.

The collection-based `MetricsProviderConfiguration` overload remains available for advanced
integrations, but normal plugins do not need to construct those objects.

Every module now receives `ModuleContext.errors`. The local pipeline always sanitizes,
deduplicates and bounds events, even when no remote provider is enabled. When FastStats is
configured on Bukkit or BungeeCord, the sanitized event is forwarded to FastStats as well;
bStats remains charts-only. The pipeline does not itself persist a local diagnostic history.
Remote error reporting is active only while the FastStats session is active.

For code that wants to submit a handled exception explicitly, use the shared reporter:

```kotlin
try {
    repository.load()
} catch (error: Throwable) {
    context.metrics.errorReporterOrNull()?.capture(error)
}
```

The same reporter is also available as `context.errors`; the metrics accessor is provided for
code that keeps all telemetry operations together. Each FastStats session owns its tracker
and attaches it to the SDK context before submission starts. Context-aware tracking applies
to uncaught errors within the tracker's class loader; it does not guarantee interception of
exceptions caught by Bukkit, BungeeCord, Velocity, or another plugin's class loader.

Changing chart configuration restarts the session and replays all registered chart callbacks.
Disabling metrics closes the SDK context; enabling metrics creates a new context and tracker.

FastStats publishes Java 8 fallback artifacts for Bukkit and BungeeCord. Its Velocity artifact
requires Java 21, so it is packaged in the separate `faststats-velocity` module. The main
Velocity adapter remains Java 17-compatible and creates the FastStats adapter explicitly
after checking the Java version. bStats continues to work on Java 17.
