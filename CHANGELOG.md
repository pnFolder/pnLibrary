# Changelog

## Unreleased

## 2.1.0

- Replaced the duplicated `PlayerAction` engine with one polymorphic `Action` model.
- Added reusable conditional branches, delayed conditions, permission/flag/chance checks, Bukkit effects, and particles.
- Removed the handwritten player-action serializer and runtime SPI action DTO.
- Added short configuration durations such as `500ms`, `10s`, and `5m`.
- Added extensible per-plugin options with PlaceholderAPI control, automatic Bukkit detection, restart recovery, and explicit publication states.
- Added a plugin-owned Currency API with single-call DSL and provider-class registration, inferred capability interfaces, aliases, access policies, precision validation, typed extensions, and automatic Vault/PlayerPoints bridges.
- Added managed currencies with atomic storage contracts, auditable transaction history, idempotency metadata, automatic balance placeholders, and Bukkit currency administration/payment commands.
- Added ready file and JDBC currency storage implementations with atomic file replacement, SQL commit/rollback, account locking, persistent balances, transaction history, and idempotency keys.
- Added storage snapshots and file-to-database/database-to-database migration with replace and safe merge strategies.
- Protected destructive currency commands with expiring, cryptographically random codes that only the server console can confirm or cancel.

## 2.1.0-beta.1

- Added one `PluginContext` for plugin-owned configuration, tasks, events, services, metrics, updates, logging, diagnostics, placeholders, components, and cooldowns.
- Added a platform-independent event bus with annotated listeners, cancellation, execution modes, and numeric priorities.
- Added annotation-driven code-first YAML, migrations, validation, nested collections, enum aliases, and custom serializers.
- Added immutable typed actions with polymorphic YAML, aliases, priorities, access policies, and `owner::type` namespaces.
- Added Adventure component formatting, multiline conversion, placeholder pipelines, conditions, and bounded caches.
- Added encrypted PN Support Archives, persistent diagnostic history, repeated-error aggregation, exact configuration capture, Catbox upload, and local fallback.
- Added buffered lifecycle and general-purpose message boxes.
- Added a typed service registry, managed bStats sessions, updater lifecycle, and Bukkit server-version API.
- Fixed Bukkit main-thread violations during diagnostic collection.
- Fixed lost headers, stack frames, indentation, and readability in decoded diagnostic errors.
- Fixed safe menu termination when either the owner or pnLibrary is disabled.
- Separated public API, Bukkit API, runtime SPI, core, and platform implementations.
- Removed obsolete platform API duplicates, the previous integration layer, old logging implementations, mclogs upload, and unused database routing.
