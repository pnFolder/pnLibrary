# Managed update catalog

## Scope

Only pnLibrary and components explicitly registered with its update service appear in the catalog.
The native plugin inventory is used for dependency checks, not as a catalog of arbitrary plugins.
Bukkit, Velocity and Bungee share the catalog, policy, journal and transaction implementation.
Platform adapters own command delivery and publication of prepared artifacts at restart.
Never replace an active plugin JAR or claim that a prepared update is already running.

## Existing foundation

- RuntimeUpdateMonitor registers pnLibrary itself.
- UpdateAutomaticPolicy checks administrator and per-product automatic download permissions.
- UpdateGraphResolver resolves compatible component sets.
- UpdateStateStore persists plan snapshots, not a complete administrator audit journal.
- UpdateTransaction preserves JAR backups and supports recovery and rollback.
- ProxyUpdateCommand exposes plan, check, stage, list, available and paginated history.
- UpdateEventJournal persists event time, actor, operation, outcome and version transitions.
- Bukkit exposes catalog pages through `/pn updates list|available|history [page]`.

## Target workflow

Check catalogs -> resolve compatibility -> authorize operation -> snapshot configured paths
-> download and verify -> prepare transaction -> restart -> reconcile loaded versions
-> record applied or failed outcome.

Automatic staging requires explicit administrator permission, compatible API/Java/platform and
dependencies, trusted artifacts, complete rollback data, and no conflicting active transaction.
Compatibility is not proof that arbitrary plugin code is safe. Do not automatically restart.
Without a verified platform restart installer, report staging as unsupported instead of success.
Published catalog artifacts must contain their actual SHA-256 digests with the default
`require-sha256: true` policy. A missing digest or a mismatch rejects staging; the downloader's
own computed digest is never substituted for publisher integrity metadata.

Self-update checks are registered automatically. To opt into automatic preparation, edit the
existing `updates.yml` (do not replace unrelated settings) and set `updates.enabled: true` and
`updates.library.automatic-download: true`. The library channel remains independently selected
by `updates.library.channel`. This permits verified preparation, not a live JAR replacement or
an automatic server restart. Every changed module in the same atomic plan must also be allowed
by its own policy; permission for pnLibrary alone cannot bypass a disabled module.

## Persistent layout beneath the library data directory

```
updates/
  state.json                 latest resolver snapshot (existing)
  history/                   bounded resolver snapshots (existing)
  transactions/<id>/         durable transaction and JAR backups (existing)
  events/                    timestamped, append-only action records (new)
  configurations/<id>/      explicitly selected configuration backups
  configuration-restores/   offline restore journals and pre-restore snapshots
  install.lock              excludes concurrent runtime/offline installation
```

Each event records schema, event ID, transaction/plan ID, UTC instant, actor kind and stable ID,
action, product IDs, old/new versions and outcome. System actions use an explicit SYSTEM actor.
Record failed update operations without leaking credentials or arbitrary configuration values.
Read events newest first with bounded pagination; the API also supports a product filter.
Do not infer installation time from release publication time or filesystem modification time.

## Commands

Bukkit uses `/pn updates` for catalog/history/backups/selected rollback. Proxy servers use
`/pnupdate`; proxy mutations are console-only:

- `list [page]`: managed components, running/available version and state.
- `available [page]`: only components with an available/prepared update.
- `check` (Bukkit: `/pn check`): refresh catalogs and resolve a plan.
- `plan` (Bukkit: `/pn update-status`): selected plan state and blockers.
- `stage <plan-id>` (proxy): stage exactly the requested plan, never silently substitute another plan.
- `history [page]`: timestamp, actor, product, version transition and result.
- `rollback <transaction-id>`: prepare a specific retained JAR set for restart.
- `rollback <transaction-id> configs`: explicitly include its configuration snapshot.

Bukkit keeps its existing `/pn update <product>` and player-bound `/pn update-confirm <token>`
workflow for preparing an update. A selected console rollback requires the final `confirm` word.

Sensitive actions require permissions and confirmation bound to actor, operation and transaction.
Clickable chat controls invoke the same validated handlers as typed commands.
Console output remains readable without hover or click. Changelog links open human-readable release
notes; never download and render their full Markdown into Minecraft chat.

## Configuration backup rules

Back up only registered component-owned configuration paths, not entire plugin/server directories.
Reject symlinks, paths outside registered roots and collisions; impose file/count/size limits.
Never include databases, caches, credentials or arbitrary files implicitly.
Store a manifest and hashes. Snapshot current files again before a destructive restore.
Configuration restore is opt-in, requires a stopped component/restart installation, and must not
delete unrelated files or newly created files outside the snapshot manifest.
Retention must preserve backups referenced by rollback-capable transactions.

## Required verification

Persist/reopen journal; actor and timestamp fidelity; pagination and filtering; no foreign plugins;
explicit auto-update policy; artifact rejection; requested-plan identity; concurrent actions;
restart reconciliation; interrupted transaction recovery; exact transaction rollback;
configuration path/symlink/limit checks; restore failure recovery; Bukkit and both proxy commands.
Live-server acceptance must distinguish prepared, applied and rolled-back running versions.

## Pre-start application

The distribution includes a separate `pnLibrary-updater-<version>.jar`. Keep it outside `plugins`
and run it only after the server/proxy JVM has completely stopped:

```
java -jar pnLibrary-updater-<version>.jar --server-stopped plugins/pnLibrary
```

The server launcher must start the JVM only when the updater exits successfully. The updater
does not stop a running server and does not restart it. Its file lock rejects an active pnLibrary
runtime or another installer; it is not a substitute for the operator's stopped-server check.
Do not run it during startup or after merely disabling pnLibrary while other plugins remain active.
The GitHub release workflow includes the standalone updater alongside the platform plugin JARs;
the updater is not a server plugin and must not be copied into `plugins`.

Application checks preserved artifact hashes again, keeps a separate recovery transaction,
publishes the installed JAR set and consumes matching platform update files. Runtime health
confirmation happens after the subsequent server start, never during preparation in the same
process. Interrupted installed-JAR writes are recovered only by the offline installer.

## Configuration restore consent

Declare explicit files with `PluginUpdateRequest.builder().configurationBackup(dataDirectory,
"config.yml", "messages.yml")`. Only changed registered components are captured; pnLibrary
preserves its own `updates.yml`. The snapshot ID is stored in the same update transaction.

An ordinary selected rollback leaves configuration untouched. To include the retained files:

- Bukkit: `/pn updates rollback <transaction-id> configs`, then use the player-bound confirmation.
- Proxy console: `/pnupdate rollback <transaction-id> configs confirm`.

The command queues intent; it never restores live files. Stop the JVM and run the updater before
starting again. Current configuration is captured at actual restoration time, including files
that were absent in the original snapshot. Unrelated paths are never removed. A failed enclosing
installation attempts to recover both the pre-operation JARs and the pre-operation configuration;
unresolved recovery causes a nonzero updater exit and must prevent the launcher from starting.

## Startup verification

Startup events retain the originating transaction/plan and its actual version transitions, even
if a newer remote check has already produced another plan. Rollback confirmation is a distinct
`HEALTH_ROLLBACK` event. Confirmation verifies loaded registered versions, not arbitrary plugin
business logic or future behavior.

Background checks start after startup reconciliation. On proxies, expected registrations get a
bounded 30-second initialization grace period. A failed verification prevents further automatic
staging for affected registered products. A subsequent check may clear that condition only after
observing the expected versions; merely restarting or fetching a catalog does not clear it.

## Reproducing native acceptance

Native tests require explicitly supplied existing platform JARs. They create unique directories
under `build/update-catalog-native`, bind loopback ports, disable metrics and retain phase logs.
They do not change existing server directories. The synthetic release changes native/component
version metadata only; it is never uploaded or presented as a published release. The tests verify
installed JAR hashes, commands, original transaction attribution, real process restarts, durable
health events and explicit configuration restoration. Network catalogue/resolver/staging policy
is covered separately by core tests, not inferred from the synthetic release test.

```
gradlew :modules:core:verifyNativeProxyUpdates -PvelocityServerJar=<velocity.jar> -PbungeeServerJar=<bungeecord.jar>
gradlew :modules:core:verifyNativeBukkitUpdates -PpaperServerJar=<paper.jar> -PpaperEulaFile=<already-accepted-eula.txt>
```

The Paper test must be authorized by the operator and supplied an already accepted EULA; it does
not accept terms. Only server software caches are copied, never worlds, plugin data or secrets.
