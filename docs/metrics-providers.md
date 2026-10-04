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

The runtime can create one delegate or a `CompositePluginMetrics` session. Shared chart calls
are fanned out to every enabled delegate. Provider-specific features are guarded by
`MetricsCapability` and must not be silently converted into a chart when a provider cannot
represent them.

```kotlin
builder.metrics {
    bStats(32592)
    fastStats(fastStatsToken)
}

context.metrics.configure { metrics ->
    metrics.simplePie("server_software") { serverName }
}
```

The collection-based `MetricsProviderConfiguration` overload remains available for advanced
integrations, but normal plugins do not need to construct those objects.

Every module now receives `ModuleContext.errors`. The local pipeline always sanitizes,
deduplicates and bounds events, even when no remote provider is enabled. When FastStats is
configured on Bukkit or BungeeCord, the sanitized event is forwarded to FastStats as well;
bStats remains charts-only. This keeps error reporting opt-in remotely while preserving a
local diagnostic trail for every module.

For code that wants to submit a handled exception explicitly, use the shared reporter:

```kotlin
try {
    repository.load()
} catch (error: Throwable) {
    context.metrics.errorReporterOrNull()?.capture(error)
}
```

The same reporter is also available as `context.errors`; the metrics accessor is provided for
code that keeps all telemetry operations together. Uncaught FastStats errors are collected by
the provider's context-aware tracker automatically.

FastStats publishes Java 8 fallback artifacts for Bukkit and BungeeCord. Its Velocity artifact
requires Java 21, so it is packaged in the separate `faststats-velocity` module. The main
Velocity adapter remains Java 17-compatible and discovers that optional provider through the
runtime SPI; bStats continues to work on Java 17 without loading the Java 21 module.
