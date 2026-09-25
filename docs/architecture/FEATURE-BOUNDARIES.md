# Feature boundary classification

This document fixes the intended dependency direction before more packages are moved. A capability
is not part of the base API merely because it is currently exposed by `PnLibrary` or
`ModuleContext`.

## Base runtime contract

These capabilities define composition and ownership and remain in `pnlibrary-api`:

| Capability | Public access | Reason |
|---|---|---|
| plugin/module registry | `PnLibrary.plugins` | Creates every owned module context |
| typed services | `services` | Discovery point for optional feature APIs |
| tasks | `tasks` | Cross-platform lifecycle and asynchronous execution primitive |
| events | `events` | Cross-platform communication primitive |
| logging | `logging`, `ModuleContext.logger` | Required for lifecycle and failure reporting |
| platform provider | `platforms` | Explicit escape hatch to native platform APIs |
| metadata/lifecycle | `ModuleContext.metadata`, `lifecycle`, `messages` | Identity and owned shutdown contract |

Commands and audiences remain base contracts because actions and platform adapters use them as
portable primitives. Configuration remains base while plugin registration and runtime settings are
configuration-driven.

## Optional feature contracts

Optional capabilities are discovered through `ModuleContext.services`. Direct properties remain
only as compatibility facades until the next major API line.

| Capability | Current direct facade | Target module shape |
|---|---|---|
| currency | none; already service-discovered | `currency-api` + `currency-runtime` |
| diagnostics | `diagnostics` | `diagnostics-api` + `diagnostics-runtime` |
| updates/downloads | `updates`, `downloads` | `update-api` + `update-runtime` |
| metrics | `metrics` | `metrics-api` + platform runtime adapter |
| placeholders | `placeholders`, global adapters/values | `placeholders-api` + runtime + platform adapters |
| components/templates | `components` | `components-api` + runtime |
| cooldowns | `cooldowns` | `cooldowns-api` + runtime |
| actions | `actions` | `actions-api` + runtime |
| menus | Bukkit API only | stays in Bukkit feature/API modules |

## Dependency rules

1. Base API never imports an optional feature implementation.
2. Optional API modules may depend on `pnlibrary-api`; the reverse dependency is forbidden.
3. Runtime modules implement and publish their API through `ServiceManager`.
4. Platform adapters depend on the feature API, not on another platform runtime.
5. Compatibility properties delegate to service lookup and own no second implementation state.
6. Removing a compatibility property is reserved for a declared major release.

## Extraction order

1. Updates and downloads, because their implementation is already mostly isolated.
2. Metrics, because its public contract is small and its implementation is platform-backed.
3. Diagnostics, preserving command integration as a platform/runtime adapter.
4. Cooldowns and actions.
5. Placeholders and components together, because their current contracts are mutually coupled.

Every extraction must add a boundary test, Java compilation fixture, API dump, and a distribution
assembly test before the compatibility facade is deprecated.

## Current-major compatibility boundary

Currency could be extracted without preserving a direct `ModuleContext` property and therefore is
the reference implementation in the current major line. The remaining optional contracts occur in
published signatures of `ModuleContext`, `PluginBuilder`, or `PnLibrary`. Moving those types into an
API module that itself depends on `pnlibrary-api` would create a dependency cycle; making the base
API depend on the feature would preserve the same coupling under a different directory name.

For that reason, the current major line performs no fake extraction and keeps the deprecated/direct
facades binary-compatible. The next major line must migrate each domain atomically:

1. introduce the independent `<feature>-api` contract and service facade;
2. migrate runtime implementation and platform adapters;
3. replace direct base-API properties with service discovery;
4. add boundary, Java/Kotlin compilation, API-dump, and assembled-distribution tests;
5. only then remove the old signature.

This is a compatibility constraint, not permission to add new optional contracts to the base API.
