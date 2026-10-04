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
module.metrics(
    providers = listOf(
        MetricsProviderConfiguration(
            provider = MetricsProvider.BSTATS,
            projectId = 32592,
        ),
        MetricsProviderConfiguration(
            provider = MetricsProvider.FASTSTATS,
            token = fastStatsToken,
        ),
    ),
) { metrics ->
    metrics.simplePie("server_software") { serverName }
}
```

Every module now receives `ModuleContext.errors`. The local pipeline always sanitizes,
deduplicates and bounds events, even when no remote provider is enabled. When FastStats is
configured on Bukkit or BungeeCord, the sanitized event is forwarded to FastStats as well;
bStats remains charts-only. This keeps error reporting opt-in remotely while preserving a
local diagnostic trail for every module.

FastStats publishes Java 8 fallback artifacts for Bukkit and BungeeCord. Its Velocity artifact
requires Java 21, while pnLibrary's Velocity adapter remains Java 17-compatible. Therefore the
Velocity bridge is isolated behind reflection: on Java 21+ install the FastStats Velocity SDK
alongside the adapter; on Java 17 the provider is simply unavailable and bStats continues to
work normally.
