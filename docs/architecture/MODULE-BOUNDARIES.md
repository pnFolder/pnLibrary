# Module boundaries

This document records why the current published modules remain separate and which boundaries are
safe to revisit in a future major release.

## Keep separate

| Modules | Reason |
| --- | --- |
| `common` / `api` | `common` contains reusable Minecraft value types and is consumed directly by platform APIs. Merging would change published coordinates and transitive API ownership. |
| `api` / `core` | `api` is the Java 8 consumer contract; `core` contains the implementation and platform runtime wiring. Consumers must not compile against the implementation. |
| `runtime-spi` / `core` | `runtime-spi` is the adapter boundary implemented by platform runtimes. Moving it into `core` would make platform adapters depend on implementation internals. |
| `currency-api` / `currency-runtime` | The API is independently consumable; runtime adds persistence and implementation dependencies. |
| `platforms/*/api` / `platforms/*/runtime` | Platform API artifacts are safe for consumers to compile against, while runtime artifacts contain native server dependencies and lifecycle wiring. |
| `bootstrap:bukkit` / platform runtime | The bootstrap is an optional Java 8 installer and must remain usable before the shared runtime is present. |

## Keep separate for toolchain reasons

| Module | Constraint |
| --- | --- |
| `design:console` | Dependency-free Java 8 artifact used by bootstrap and core. |
| `internal:faststats-velocity` | Internal integration targets Java 21 while Velocity runtime targets Java 17. |
| `features:minecraft-localization` | Optional Bukkit-facing feature with a different release cadence and native API dependency. |

## Safe cleanup already applied

- Removed the redundant direct `currency-runtime -> api` dependency; `currency-api` already exposes the
  API transitively.
- Kept platform implementation details behind `PlatformRuntimeContext` and the internal
  `PlatformRegistration` capability.

## Revisit in the next major release

The deprecated compatibility methods have now been removed from the published API. Consumers must
use the canonical contracts documented in the API modules and the migration table.
