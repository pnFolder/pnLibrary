# Smart Update Orchestrator Design

## Goal

Replace independent per-JAR update checks with one cross-component update orchestrator for pnLibrary and every plugin registered through its plugin context. The orchestrator must select a complete set of mutually compatible versions before downloading anything, explain the result in the console, stage verified updates atomically, notify authorized Bukkit administrators, and never let a plugin override the server owner's safety policy.

The current pnLibrary API generation remains `1`. This work may add API surface and refresh API baselines, but it does not increment `PnLibraryApi.VERSION`.

## Core rules

- pnLibrary and managed plugins are components in one dependency graph.
- A component release declares the inclusive pnLibrary API range it supports.
- A release may declare minimum versions of other required components.
- The resolver selects a complete compatible plan; it never updates one component in isolation when that would break another.
- A missing or incompatible required dependency blocks registration of the dependent plugin.
- No artifact is installed until every artifact in the plan has been downloaded and verified.
- Server configuration can make behavior more restrictive than component metadata, never less restrictive.
- Bukkit supports in-game administrator notifications and confirmation. BungeeCord and Velocity remain console-only.

## Component identity and installed metadata

Every managed component has a stable lower-case identifier independent of its display name and JAR filename. pnLibrary uses `pnlibrary`. Plugin identifiers use the identifier declared in their pnLibrary component descriptor.

One build-time component declaration is the source of truth:

```kotlin
pnComponent {
    id = "economy"
    version = "3.4.0"
    pnLibraryApi { minimum = 1; maximum = 2 }
    platforms { bukkit() }
    java { minimum = 8 }
    dependsOn("permissions", minimumVersion = "2.1.0")
}
```

The build produces `META-INF/pnlibrary/component.json` inside the JAR and a release-side `pn-update.json`. It also contributes the artifact digest to `checksums.sha256`. The same source values generate both descriptors, preventing manual drift.

At runtime installed metadata is resolved in this order:

1. embedded `META-INF/pnlibrary/component.json`;
2. explicit plugin-context builder values;
3. native platform metadata such as `plugin.yml`, only for fields that remain absent.

Explicit values may fill missing legacy metadata but cannot contradict an embedded descriptor. A version, identifier, API range, platform, or dependency conflict marks the registration invalid and disables update activity for that component. Required identity, version, and API compatibility must be known before a component is treated as managed.

The Java- and Kotlin-friendly runtime registration API supports explicit compatibility:

```kotlin
library.plugins.register(plugin) {
    component("economy")
    version("3.4.0")
    compatibility { pnLibraryApi(1, 2) }
    updates {
        github("pnFolder", "Economy")
        channel(UpdateChannel.STABLE)
    }
}
```

The builder's version defaults to native metadata only when no embedded descriptor exists. The API range has no guessed default for managed third-party plugins; legacy registrations without it are monitored as unmanaged and cannot participate in automatic plans.

## Release manifest

Each managed GitHub release publishes `pn-update.json`. It contains the component identifier, semantic version, release channel, pnLibrary API range, component dependencies, and all platform/Java-specific artifacts. Each artifact records an exact filename, byte size, and SHA-256.

```json
{
  "schema": 1,
  "component": "economy",
  "version": "3.4.0",
  "channel": "stable",
  "pnLibraryApi": { "minimum": 2, "maximum": 3 },
  "dependencies": [
    { "component": "permissions", "minimumVersion": "2.1.0" }
  ],
  "artifacts": [
    {
      "platform": "bukkit",
      "java": { "minimum": 17 },
      "file": "economy-bukkit-java17.jar",
      "size": 184320,
      "sha256": "..."
    }
  ]
}
```

The manifest is bounded, strictly parsed, schema-versioned, and cached with its GitHub release identity. The selected artifact must match the running platform and Java version. After download, the embedded descriptor must agree with the release manifest. A mismatch is an integrity failure.

pnLibrary releases use the same format. Their manifest declares the API generation provided by that release in addition to the API range accepted by their internal modules.

## Dependency declarations

Managed dependencies point to another pnLibrary component and an update catalogue:

```kotlin
dependencies {
    requirePlugin("permissions") {
        minimumVersion("2.1.0")
        github("pnFolder", "Permissions")
    }
}
```

External dependencies can describe plugins that do not publish pnLibrary manifests:

```kotlin
dependencies {
    requireExternal("Vault") {
        minimumVersion("1.7.3")
        downloadPage("https://github.com/MilkBowl/Vault/releases")
    }
}
```

An external dependency with only a download page is never installed automatically. It produces an actionable blocked result with the page URL. An external dependency may provide an exact HTTPS artifact URL, version, size, and SHA-256. It can be staged only when external installation is enabled and the host is explicitly allow-listed in server configuration.

Missing or incompatible required dependencies reject the dependent plugin's pnLibrary registration before its managed services, commands, events, tasks, or placeholders are exposed. The native plugin may remain loaded because pnLibrary cannot safely disable arbitrary platform plugins during their own initialization; the registration failure is explicit and the plugin receives an exception it must handle or allow to fail startup.

## Compatibility resolver

The resolver consumes an immutable installed-component snapshot, cached release catalogues, platform type, Java feature version, selected channels, and freeze policies. It produces an immutable plan without downloading artifacts.

Resolution is a deterministic constraint search, not a greedy newest-version loop:

1. Build a domain containing the installed version and allowed candidate releases for every component.
2. Filter candidates by release channel, platform, Java, freeze policy, and manifest validity.
3. Select a pnLibrary candidate and its provided API generation.
4. Remove plugin candidates whose pnLibrary API range excludes that generation.
5. Apply minimum component-version dependencies and introduce missing managed dependencies when policy allows installation.
6. Propagate constraints until stable; backtrack when a selection makes another component impossible.
7. Prefer the newest compatible versions while minimizing unnecessary component changes.
8. Return the stable plan or a structured set of blockers explaining every unsatisfied constraint.

When a plugin update requires a newer pnLibrary API, the resolver evaluates the pnLibrary update and then reevaluates every installed dependent plugin. Compatible installed versions stay unchanged. Incompatible plugins receive a compatible update when available. If any required installed plugin has no compatible version, the entire plan is blocked.

Cycles are accepted only when one simultaneous version assignment satisfies all edges. An invalid or unsatisfiable cycle is reported as a blocker. Component identifiers and versions are unique within a plan.

## Update states and API

The existing update state model is consolidated around graph-aware states:

- `CHECKING`: catalogues are being refreshed;
- `UP_TO_DATE`: the installed graph is current and compatible;
- `UPDATE_AVAILABLE`: a compatible plan exists but is not staged;
- `INCOMPATIBLE`: an installed or candidate component violates API, Java, or platform constraints;
- `BLOCKED`: missing releases, dependencies, permissions, or policy prevent a complete plan;
- `DOWNLOADING`: all artifacts are being fetched into staging;
- `UPDATE_STAGED`: a verified transaction is ready for restart;
- `FAILED`: the latest check or transaction failed;
- `FROZEN`: policy deliberately prevents a component from changing.

The public service exposes the current graph snapshot, current plan, structured blockers, `checkNow`, `download(planId)`, `confirm(planId, confirmationToken)`, and immutable history. Mutating calls are asynchronous and reject stale plan identifiers. Java callers receive ordinary builders and `CompletionStage` results.

## Transaction and recovery

An accepted plan becomes one transaction:

1. Reserve a unique transaction directory and persist the plan journal.
2. Download every artifact into staging with connection/read timeouts and strict size limits.
3. Verify SHA-256, declared size, JAR readability, platform descriptor, and embedded pnLibrary descriptor.
4. Run the compatibility resolver again using downloaded descriptors.
5. Record the verified plan and create recoverable backups where direct replacement is required.
6. Atomically publish every artifact to the platform's update directory or configured new-plugin destination.
7. Mark the transaction staged only after all moves succeed.
8. On failure, remove staged outputs from this transaction and restore any replaced files.

The journal is fsynced at state boundaries and allows startup recovery. Recovery completes an unambiguous publish or rolls it back; it never silently ignores an interrupted transaction. Only files recorded in that transaction can be moved or deleted.

## Configuration and persistence

`plugins/pnLibrary/updates.yml` controls the orchestrator:

```yaml
updates:
  enabled: true
  checks:
    enabled: true
    interval: 30m
  notifications:
    console: true
    administrators: true
    repeat-interval: 6h
    permission: pnlibrary.updates.notify
    operators: true
  downloads:
    automatic: false
    allow-managed-plugins: true
    allow-external-urls: false
    allowed-hosts:
      - github.com
      - objects.githubusercontent.com
  installation:
    allow-new-plugins: false
    require-sha256: true
    restart-after-confirmation: false
    restart-command: "restart"
  safety:
    maximum-online-for-one-click: 10
    require-second-confirmation-above-limit: true
```

Missing or malformed values fall back to conservative defaults and emit one clear warning. Disabling `updates.enabled` stops checks, notifications, downloads, and installation. Disabling checks preserves manual inspection of persisted state. Disabling automatic downloads still permits an authorized manual download.

Persistent state lives below `plugins/pnLibrary/updates/`:

```text
state.json
catalog/
staging/
transactions/
backups/
history/
```

State and history writes are bounded and atomic. Secrets and confirmation tokens are never persisted in plaintext. Catalog cache failures degrade to the last verified data and are labelled stale.

## Console and administrator experience

Every platform prints a concise console summary when a plan becomes available, blocked, staged, or failed. Repeated identical announcements are throttled to once per six hours by default. A changed plan or blocker set is announced immediately. Output includes current and target versions, pnLibrary API transition, dependent component changes, missing dependencies, policy blockers, and the command required for the next action.

Bukkit administrators receive a message after joining when they are OP and operator bypass is enabled, or when they have `pnlibrary.updates.notify`. The message contains `Подробнее` and, when authorized, `Скачать` actions. `Подробнее` opens a paginated text plan. The action permissions are:

```text
pnlibrary.updates.notify
pnlibrary.updates.view
pnlibrary.updates.download
pnlibrary.updates.install
pnlibrary.updates.restart
```

Click events execute nonce-bearing pnLibrary commands rather than embedding URLs or filesystem paths. Tokens are short-lived, bound to player UUID, plan ID, action, and current plan revision, and are single-use.

`Скачать` downloads and stages the plan; it does not hot-reload plugins. If restart-after-confirmation is enabled, a staged plan offers `Перезапустить`. When online players exceed `maximum-online-for-one-click`, the first click displays the player count and requires a separate `Да, перезапустить` token. The restart action checks permission, token, plan freshness, transaction state, and online count again immediately before dispatching the configured console command.

BungeeCord and Velocity register no player join listener and send no player chat notification. They expose console status and console commands only. pnLibrary never tries to restart a proxy by itself.

## Security boundaries

- Only HTTPS sources are accepted.
- GitHub repository coordinates and external hosts are validated before network access.
- Redirects are accepted only to configured trusted hosts.
- Manifest, checksum, and artifact responses have independent size limits.
- SHA-256 is mandatory for automatic staging.
- JAR paths, component identifiers, filenames, and transaction identifiers cannot escape owned directories.
- A plugin declaration cannot enable downloads, external hosts, installation, or restart when server policy disables them.
- Console actions are trusted; player actions require permissions and single-use confirmation tokens.
- Update work runs off the platform main thread. Player messaging and command dispatch return through the platform adapter when required.

## Migration

The existing independent `MandatoryUpdateService`, `UpdateServiceImpl`, feature resolver, freeze store, downloader, verifier, and transaction code are consolidated behind one orchestrator. Existing plugin registrations using `PluginUpdateRequest` remain source-compatible through an adapter, but registrations without an explicit API range are excluded from automatic graph changes and receive a migration warning.

The current `updates.yml` channel and `auto-download` values are migrated into the new structure on first load. The old file is backed up. Existing staged platform updates are not adopted into a new transaction; they are reported and left untouched for the administrator.

## Testing and verification

Tests use local fixtures and fake platform adapters; no test accesses GitHub.

Coverage includes:

- build descriptor generation and Java/Kotlin runtime registration;
- strict manifest parsing, schema rejection, cache expiry, and offline fallback;
- compatible pnLibrary API migration across several dependent plugins;
- rollback when one installed plugin has no compatible release;
- managed missing dependency installation and external manual-only dependency reporting;
- Java, platform, channel, freeze, cycles, and minimum-version constraints;
- deterministic selection and minimal-change tie-breaking;
- concurrent checks sharing catalogue requests;
- complete transaction staging, checksum/JAR/descriptor failures, rollback, and crash recovery;
- conservative configuration migration and disabled-mode behavior;
- console announcement throttling and immediate changed-plan announcements;
- Bukkit join permission/OP behavior, hidden actions, token expiry/replay, online threshold, and restart revalidation;
- absence of player listeners on BungeeCord and Velocity;
- public API baselines with `PnLibraryApi.VERSION == 1`;
- the complete `clean test apiCheck :distribution:build` verification.

