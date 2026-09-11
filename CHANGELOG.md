# Changelog

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
