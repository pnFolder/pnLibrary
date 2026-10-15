# Platform boundaries

This document records the platform architecture established on the
`architecture/platform-phase-1` branch.

## Runtime boundary

Native platform implementations expose one internal `PlatformAdapter`. The
core runtime receives a single `PlatformRuntimeContext`, which contains the
identity, ownership, execution, readiness, diagnostics, logging, and native
service adapters required by the runtime. Core code must not depend on Bukkit,
BungeeCord, or Velocity classes.

`PlatformAdapter` is an internal composition boundary. Consumer plugins use
`PnLibrary.platform<T>()` and the published platform API artifacts instead of
casting the library facade or importing runtime adapters.

## Lifecycle rules

- Platform binding is transactional and rolls back tracked resources on failure.
- Tracked resources close in reverse registration order.
- Cleanup failures are aggregated without hiding the original failure.
- Post-close cleanup, such as detaching native log observers, runs even when a
  tracked resource fails.
- Platform adapters must make `close()` idempotent.

## Platform-specific responsibilities

- Bukkit keeps native metadata, owner control, lifecycle readiness, and native
  scheduling in its runtime adapter. JUL observation and Bukkit/Folia
  scheduling are isolated in dedicated internal bridges.
- Bungee and Velocity keep their native proxy scheduling and command binding in
  their adapters while sharing lifecycle semantics through `PlatformLifecycle`.
- Native platform APIs are published separately from runtime implementations.

## Module decision

Modules remain separate when they have an independent public artifact, a
different Java/runtime constraint, optional adoption, or a native dependency
boundary. In particular, platform API/runtime pairs, currency API/runtime,
optional Minecraft localization, Java 8 console style, and Velocity Java 21
internal tooling must not be merged merely to reduce the project count.

The next cleanup phase should remove or delete legacy API only as part of a
planned major-version migration. The current major line keeps deprecated
aliases with documented replacements for binary and source compatibility.
