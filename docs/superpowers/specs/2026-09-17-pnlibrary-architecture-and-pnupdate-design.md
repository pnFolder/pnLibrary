# pnLibrary Architecture and pnUpdate Design

## Status and intent

This document records the repository audit and the incremental design for the
architecture and pnUpdate specifications supplied on 2026-09-17. The work is
deliberately split into independently testable migrations. Existing public API
remains available through compatibility bridges until an explicit API-generation
change permits removal.

The current repository baseline is not green. `./gradlew test` fails while
compiling `pnlibrary-core` because `PlaceholderHub` references the missing
`LocalPlaceholderExpression` type. Restoring and testing that behavior is phase
zero; architectural movement must not hide the pre-existing defect.

## Constraints copied from the product specification

- The library semantic version and the integer API generation are independent.
- A compatible public addition does not increase the API generation; a breaking
  public change must increase it.
- CI must reject an accidental binary/public API break when the API generation
  remains unchanged.
- Plugins declare a supported inclusive API range and GitHub release/artifact
  coordinates; they do not implement their own updater.
- `STABLE`, `BETA`, `ALPHA`, and `DEV` are maximum-risk channels.
- Updates are automatic. There is no permanent disable switch.
- The resolver selects the newest allowed compatible release, not merely the
  repository's latest release, and changes the minimum necessary component set.
- An API-generation migration is atomic across pnLibrary and all affected plugins.
- Freeze is per component, lasts from 1 minute through 30 days, survives restart,
  expires automatically, and never means permanent disablement.
- Download, verification, staging, activation, health check, commit, and rollback
  are explicit transaction phases; live JAR hot-swap is not used.
- Global API contains only concepts that make sense on Bukkit, Velocity, and
  Bungee. Platform concepts live in their platform API module.
- API modules contain contracts; implementations live elsewhere.
- Core owns lifecycle, composition, registration, resolution, and resource
  ownership rather than every feature implementation.
- Distribution remains one ready-to-install JAR per supported platform.
- No reflection-based DI framework, annotation scanning, global mutable service
  locator, or redundant manager/factory/provider hierarchy is introduced.

## Repository audit

### Gradle modules

| Current module | Current responsibility | Problem | Target decision | Action |
| --- | --- | --- | --- | --- |
| `pnlibrary-api` | Public cross-platform contracts plus actions, configuration, currency, diagnostics, placeholders, tasks, and updates | Mostly correctly platform-neutral, but has no library-wide API generation; `PnLibrary` exposes many feature registries directly; current update contract models a downloader rather than a resolver | Stable global contracts and compatibility bridges | KEEP / SPLIT PUBLIC SURFACE |
| `pnlibrary-runtime-spi` | Private adapter and metrics factory contracts | Correct direction, but `PlatformAdapter` aggregates logging, scheduling, metadata, diagnostics, binding, and ownership details | Private runtime integration contracts, split only when a concrete seam is required | KEEP / SIMPLIFY |
| `pnlibrary-core` | Composition plus configuration, currency, diagnostics, upload, placeholders, updates, logging, metrics, events, tasks, and security | 8,128 main lines and nearly all feature implementations; contradicts the small-core requirement | Lifecycle/composition/plugin ownership/service container only | SPLIT |
| `pnlibrary-bukkit-api` | Public Bukkit inventory, items, entity, server, and version API | Correct platform location, but lacks a single `BukkitPlatform` entry contract and consistent package root | Public Bukkit platform API | KEEP / NORMALIZE |
| `pnlibrary-bukkit` | Bukkit runtime entry point and implementations | Correct location; command controller is large and runtime bootstrapping knows too many features | Bukkit runtime plus Bukkit feature adapters | KEEP / SPLIT INTERNAL CLASSES |
| `pnlibrary-bungee` | Bungee runtime entry point and implementations | Public Bungee platform API is absent | Runtime implementation depending on new `pnlibrary-bungee-api` | KEEP; ADD API MODULE |
| `pnlibrary-velocity` | Velocity runtime entry point and implementations | Public Velocity platform API is absent | Runtime implementation depending on new `pnlibrary-velocity-api` | KEEP; ADD API MODULE |
| `pnlibrary-bstats-base` | Vendored/shared bStats implementation | It is implementation detail but is separately published and leaks into core dependencies | Private metrics implementation dependency | KEEP INTERNALLY / STOP EXPANDING API |
| `pnlibrary-distribution` | Per-platform shaded JAR assembly | Correct user-facing model; must incorporate feature implementations and metadata/checksums | Distribution/composition output | KEEP / EXTEND |

### Important classes and packages

| Current module/class | Responsibility | Observed problem | Target location | Action |
| --- | --- | --- | --- | --- |
| `PnLibraryImpl` | Creates services, diagnostics history, upload/encryption, worker, plugin registry, reports, and shutdown order | Composition root also owns feature initialization and business policy | `pnlibrary-core` facade plus runtime feature installers | SPLIT |
| `PnLibraryBootstrap` | Singleton bootstrap and provider binding | Small and cohesive; global diagnostics lifetime needs explicit ownership | `pnlibrary-core/runtime` | KEEP / TIGHTEN |
| `PnLibraryRuntimeHost` | Hosts lifecycle around platform startup | Composition concern | `pnlibrary-core/runtime` | KEEP |
| `PluginRegistryImpl` | Registration plus construction of config, metrics, updates, placeholders, currency, components, cooldowns, actions, events, tasks, and cleanup | 392-line feature factory and registry; constructor couples every feature | `pnlibrary-core/plugin` registry with injected `PluginContextFactory` | SPLIT |
| `ResourceCleanup` | Reverse-order owned-resource cleanup | Correct ownership primitive | `pnlibrary-core/plugin` | KEEP |
| `ServiceManagerImpl` / `ServiceManager` | Typed registration and lookup | Appropriate small container; naming differs from target terminology | Global API/core | KEEP; compatibility alias before any rename |
| `TaskServiceImpl` | Cross-platform task scopes over adapter scheduling | Common concept with platform implementation hook | `feature/tasks` implementation or small core service | MOVE |
| `EventServiceImpl` / `EventHandlerInspector` | Cross-platform event bus | Independent feature currently in core | `feature/events` | MOVE |
| `ConfigurationServiceImpl` and `config/yaml/*` | Code-first YAML, schema, migration, serialization | Independent feature, 1,181 core lines | `feature/config` | MOVE |
| `CurrencyHub` and `currency/*` | Currency registry, storage, mutation, providers | Independent feature, 1,404 core lines | `feature/currency`; Bukkit bridges stay in Bukkit runtime | MOVE |
| `DiagnosticsRegistry` and `diagnostics/*` | Diagnostics registry, history, reports, archive and command flow | Independent feature, 1,577 core lines; command presentation crosses platform boundary | `feature/diagnostics`, with native command adapters in platform runtime | MOVE / SPLIT |
| `upload/*` and `EncryptedEnvelopeCodec` | Diagnostic delivery, persistence, crypto | Diagnostics implementation detail currently presented as core infrastructure | `feature/diagnostics/upload` | MOVE |
| `PlaceholderHub` and `placeholders/*` | Local placeholders, formatting, adapters, global values | 389-line hub; currently fails compilation due to a missing parser type | `feature/placeholders`; native adapters remain platform-specific | MOVE / REFACTOR |
| `MetricsRegistry` / `BStatsMetricsSession` | Plugin metrics sessions | Independent feature with platform factory | `feature/metrics` | MOVE |
| `PlatformLoggingService` / `DiagnosticLogBuffer` | Logging plus diagnostic capture | Logging and diagnostic persistence are coupled | `feature/logging` plus diagnostics subscriber | SPLIT |
| `ComponentServiceImpl` | Adventure component rendering/cache | Common feature | `feature/text` | MOVE |
| `CooldownServiceImpl` | Plugin-owned cooldown state | Small common feature, not composition | `feature/cooldowns` | MOVE |
| `MandatoryUpdateService` | Poll GitHub, pick one release, download and stage one JAR | Static object combines source, channel policy, network, checksum, validation, scheduling and logging; supports neither API compatibility nor transaction/rollback/freeze; config still permits disabling download | `feature/update` components | REPLACE INCREMENTALLY |
| `UpdateServiceImpl` | Registration and updater threads | Thin wrapper around the monolith; assumes a single artifact/current JAR | `feature/update` facade backed by repository/resolver/transaction engine | REFACTOR |
| `BukkitPlatformAdapter` | Runtime logging, scheduling, diagnostics, command binding | Private runtime adapter is useful but is not the public Bukkit platform API | `pnlibrary-bukkit` | KEEP / NARROW |
| `BungeePlatformAdapter` | Bungee runtime services and diagnostic command | Mixes runtime bridge and diagnostics UI; no public Bungee platform API | `pnlibrary-bungee` plus new API module | SPLIT |
| `VelocityPlatformAdapter` | Velocity runtime services and diagnostic command | Mixes runtime bridge and diagnostics UI; no public Velocity platform API | `pnlibrary-velocity` plus new API module | SPLIT |
| `BukkitCommandController` | Bukkit diagnostics and currency administration commands | 435-line multi-feature controller | Relevant Bukkit feature adapters | SPLIT |
| `PnBukkit` | Static access to Bukkit server information/menu services | Existing platform-specific access pattern, but not generic `platform<T>()` | Compatibility bridge over `BukkitPlatform` | REFACTOR / DEPRECATE LATER |

### Existing strengths to preserve

- Public API and runtime implementations are already separate Gradle modules.
- Global API/core/runtime SPI do not import Bukkit, Bungee, or Velocity classes.
- Plugin contexts already own several closeable scopes and cleanup is tested.
- Distribution already produces one shaded JAR per platform.
- Kotlin/JVM bytecode targets are explicit: Java 8 for Bukkit/Bungee/common and
  Java 17 for Velocity.
- There are useful unit suites for configuration, diagnostics, currency, events,
  tasks, services, and lifecycle; these should move with their implementations.

### Missing or conflicting behavior

- There is no library-wide integer API generation. `DiagnosticsService.API_VERSION`
  is feature-local and must not be mistaken for it.
- No binary compatibility validator is configured in Gradle or CI.
- Release artifacts do not include generated plugin metadata and per-artifact
  SHA-256 files in the required form.
- No `pnlibrary-velocity-api` or `pnlibrary-bungee-api` module exists.
- No typed `pnLibrary.platform<T>()` registration/lookup mechanism exists.
- The update channels omit `DEV`; existing channel selection does not fully model
  maximum permitted risk.
- Existing configuration permits `auto-download: false`, contrary to the new
  mandatory automatic-update contract.
- There is no compatible-release search by API range, dependency resolver,
  transition plan, freeze state, durable transaction journal, health-check commit,
  or rollback manager.
- Update errors are free-form strings rather than structured blocked/failure reasons.
- Platform entry modules and the new updater behavior have little or no direct test
  coverage.

## Considered migration approaches

### Recommended: vertical incremental extraction

First restore the baseline, introduce architectural seams and compatibility checks,
then extract one complete feature at a time. Build the updater as its own feature
behind the new contracts. Every phase ends with a green build and releasable
platform distributions.

This has more temporary bridges than a rewrite, but it is the only approach that
meets the explicit compatibility and gradual-migration requirements.

### Alternative: module-only reshuffle

Move current packages into feature Gradle modules without changing object
responsibilities. This is quick, but preserves `PluginRegistryImpl`, updater and
platform-adapter coupling behind new directory names. It is rejected because it
does not simplify the system.

### Alternative: clean-slate v3 rewrite

Create the desired tree and port behavior afterward. This gives clean code fastest
on paper, but creates a long non-releasable branch and high regression/API risk. It
directly violates the requirement not to rewrite everything at once and is rejected.

## Target architecture

The source tree converges on these modules; feature modules are implementation
modules and are shaded into distributions rather than imposed on server owners.

```text
pnlibrary-api
  public global contracts, API generation, plugin metadata/ranges

pnlibrary-bukkit-api
pnlibrary-velocity-api
pnlibrary-bungee-api
  public platform entry interfaces and genuinely platform-specific contracts

pnlibrary-core
  lifecycle, bootstrap, PluginRegistry, PluginContextFactory,
  ServiceContainer, resource ownership, platform implementation registry

pnlibrary-runtime-spi
  private runtime/platform integration seams

pnlibrary-feature-config
pnlibrary-feature-events
pnlibrary-feature-tasks
pnlibrary-feature-text
pnlibrary-feature-placeholders
pnlibrary-feature-currency
pnlibrary-feature-metrics
pnlibrary-feature-diagnostics
pnlibrary-feature-update
  cohesive implementations; modules may be introduced only as each feature moves

pnlibrary-bukkit
pnlibrary-velocity
pnlibrary-bungee
  native entry points, public platform implementations, and native feature bridges

pnlibrary-distribution
  one installable shaded JAR per platform plus release metadata/checksums
```

The dependency rule is:

```text
platform API -> global API
core -> global API + runtime SPI
feature implementation -> global API + narrow core/runtime contracts
platform runtime -> matching platform API + core + selected features
distribution -> platform runtime and all selected implementations
```

No API module depends on core, a feature implementation, or a platform runtime.

## Public API design

`PnLibrary` remains the stable facade. It gains an integer API-generation constant
and a typed platform accessor. Existing direct service properties remain as
deprecated bridges until a future API-generation bump allows removal.

The platform mechanism is intentionally small:

```kotlin
interface PlatformProvider {
    fun <T : Any> get(type: Class<T>): T?
    fun <T : Any> require(type: Class<T>): T
}

inline fun <reified T : Any> PnLibrary.platform(): T =
    platforms.require(T::class.java)
```

Each runtime registers exactly its known platform interface during composition.
Unknown platform requests fail with a descriptive exception; `null` is available
only through explicit `get`, not as normal platform control flow.

`PluginContext` remains the lifecycle owner. Its implementation is assembled by a
small `PluginContextFactory` from feature contributors. The registry only validates
identity, calls the factory, stores contexts, and closes them. A contributor is a
composition-time internal contract, not a public extension framework.

## API compatibility and release metadata

The global API generation starts from an explicitly selected integer during the
first implementation phase. The current repository has already shipped breaking
2.x API changes, so the number must be treated as product data rather than inferred
from `DiagnosticsService.API_VERSION`.

Gradle records a checked-in public API baseline. CI compares the current API JAR
against the latest baseline. If a binary-incompatible change is detected while the
integer generation is unchanged, verification fails. An intentional breaking change
requires both a reviewed API-generation increment and a regenerated baseline.

A small Gradle convention generates `META-INF/pnlibrary/plugin.json` from declared
plugin ID, semantic version, and inclusive API range. Release tasks publish the JAR,
matching `.meta.json`, and `.jar.sha256`. Metadata schemas are versioned and parsed
strictly, with unknown optional fields tolerated for forward compatibility.

## pnUpdate architecture

The public surface remains compact (`plugin.updates` and immutable snapshots). The
implementation is divided by behavior, not by generic manager layers:

- `ReleaseSource` obtains GitHub releases and their small metadata assets.
- `ReleaseCatalog` normalizes versions, channels, API ranges, dependencies and
  artifact checksums.
- `UpdateResolver` searches newest-first while satisfying API ranges, freezes,
  dependency constraints, installed components, and the minimum-change rule.
- `UpdatePlan` is an immutable current-to-target component set plus structured
  reasons for blocked candidates.
- `ArtifactDownloader` writes bounded temporary files.
- `ArtifactVerifier` verifies exact SHA-256, size, artifact identity and embedded
  metadata before a file can enter staging.
- `UpdateTransaction` journals download, verify, stage, activate, health-check,
  commit and rollback states durably.
- `FreezePolicy` persists absolute expiration instants and validates the 1m..30d
  range.
- `UpdateCoordinator` schedules discovery and executes one plan at a time.

Resolution is deterministic. Candidate versions are ordered newest-first within the
configured maximum-risk channel. The resolver first tries to keep every unrelated
component at its installed version, expands the change set only when a dependency or
API migration requires it, and rejects partial solutions. A blocked future API
generation does not prevent compatible updates in the installed generation.

The old `MandatoryUpdateService` is kept only behind an internal bridge while the
new catalog/resolver becomes capable of reproducing current single-component update
behavior. It is removed only after transaction and rollback integration is tested.

## Transaction, recovery, and errors

Every plan has a durable ID and journal under the pnLibrary data directory. Downloads
are written to a plan-specific temporary directory, verified in full, and then moved
to a staging directory. Activation happens only at restart. Before activation, the
current component set is snapshotted to a recoverable backup manifest.

On startup the coordinator reads the journal:

- a staged plan is activated as one unit;
- a successfully started plan enters health check and then commits;
- a critical startup/health failure restores the complete previous set;
- an interrupted download or verification is discarded safely;
- an interrupted activation is resolved from the journal and verified manifests,
  never guessed from filenames.

Public snapshots expose structured `BlockedReason` and `FailureReason` variants.
Logging renders actionable messages with target library/API, blocking component,
installed version, supported and required API, repository, and suggested reporting
path. No server automatically opens GitHub issues.

## Configuration

The generated YAML contains commented defaults for:

```yaml
updates:
  library-channel: STABLE
  plugin-channel: STABLE
  plugins: {}
  freeze: {}
```

Freeze entries are converted to absolute expiry timestamps in updater state so a
restart does not reset their duration. Invalid/permanent durations fail validation
with an actionable message. Legacy `channel` and `auto-download` fields receive a
one-time migration: channel maps to `library-channel`; `auto-download: false` is
reported and removed because permanent disablement is no longer supported.

## Testing strategy

- Restore the current test suite before moving code.
- Add architecture tests that reject platform imports in global API/core and reject
  implementation dependencies from API modules.
- Add binary API compatibility verification to local `check` and CI.
- Move existing feature tests with their implementations without weakening them.
- Unit-test channel ordering, API range inclusion, newest compatible selection,
  minimal-change scoring, dependency closure, freeze blocking, and structured errors.
- Use fixture GitHub responses and temporary directories for download, metadata,
  checksum, staging and recovery tests; ordinary tests perform no live network calls.
- Add transaction crash-point tests after every journal transition.
- Build and inspect all three distribution JARs for descriptors, generated metadata,
  absence of server API classes, and expected shaded implementations.
- Add platform contract tests around registration and typed retrieval; use native
  API mocks only at the runtime boundary.

## Incremental delivery phases

1. **Baseline recovery:** restore the missing placeholder expression behavior, add
   regression tests, and make `clean test :pnlibrary-distribution:build` green.
2. **Compatibility guardrails:** introduce the library API generation, API baseline
   check, architecture checks, and generated release metadata/checksums.
3. **Platform API foundation:** add Velocity/Bungee API modules, define the three
   platform entry interfaces, implement typed platform registration/retrieval, and
   bridge existing Bukkit entry points.
4. **Small core:** add `PluginContextFactory`/internal feature contributors, reduce
   `PluginRegistryImpl`, and turn `PnLibraryImpl` into composition/facade code.
5. **Feature extraction:** move one tested vertical feature at a time, beginning with
   low-coupling metrics/tasks/events and ending with diagnostics/currency/placeholders.
6. **pnUpdate domain and resolver:** implement metadata/catalog/channel/API/dependency
   resolution, minimal change plans, freeze, and structured states without activation.
7. **pnUpdate transaction:** implement bounded download, verification, durable staging,
   restart activation, health check, commit and rollback.
8. **Distribution and migration completion:** wire every platform distribution,
   migrate config, remove the legacy updater bridge, update user/developer docs, and
   run the full release verification path.

Each phase is independently reviewable and releasable. A phase does not begin by
deleting the legacy behavior it replaces; removal is the final step after its tests
and compatibility bridge are proven.

## Acceptance criteria

- The full Gradle verification and all three distribution builds pass from a clean
  checkout.
- Public API breakage without an API-generation increment fails CI.
- A plugin can depend only on global API or add exactly its platform API.
- `pnLibrary.platform<BukkitPlatform>()`, `VelocityPlatform`, and `BungeePlatform`
  return type-safe registered implementations on their matching runtimes.
- Core contains no updater, diagnostics upload, currency, placeholder, config or
  metrics business implementation.
- `PnLibraryImpl` is a facade/composition participant and `PluginRegistryImpl` only
  owns registry/lifecycle concerns.
- The resolver demonstrates newest-compatible, old-generation fallback, pre-compatible
  plugins, dependency closure, per-component freeze, and atomic API migration cases.
- A failed multi-component activation restores the complete previous component set.
- Server owners still install one pnLibrary JAR appropriate to their platform.

