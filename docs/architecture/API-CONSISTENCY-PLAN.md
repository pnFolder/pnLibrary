# API consistency migration plan

The migration is deliberately incremental. Each phase must leave the repository buildable and must
not silently remove a published entry point.

## Current audit

| Area | Current inconsistency | Target |
|---|---|---|
| Module boundaries | Optional capabilities have historically lived in `pnlibrary-api` and core | `<feature>-api` + `<feature>-runtime` + platform adapters |
| Builders | Nested Java builders, callback builders, and duplicated Kotlin state builders coexist | One canonical nested builder; Kotlin DSL only delegates |
| Registration | Some callbacks register immediately while older APIs require a second terminal call | Service `register` is always terminal |
| Lookups | Registry vocabulary is mostly consistent but not enforced | Uniform `get` / `require` / `all` contract |
| Lifecycle | Most handles are closeable, but ownership is documented unevenly | Idempotent owned handles and transactional creation |
| Java interop | Most entry points work, but annotations and callback style are inconsistent | Static factories and Java functional interfaces everywhere |
| Package layout | API and implementation intent is not always visible from the module path | Public contract, runtime implementation, platform adapter separation |

## Phase 1 — establish and enforce the language

- [x] Publish the design rules.
- [x] Split currency contract from the base API.
- [x] Add a boundary test preventing currency from leaking back into `pnlibrary-api`.
- [x] Add architecture tests for every optional feature introduced in this major line.
- [x] Add a Java compilation fixture for the canonical builder pattern.
- [x] Add Java compilation fixtures for service-owned registration patterns.

## Phase 2 — builders and declarations

- [x] Inventory every public builder and classify it as value construction or registration.
- [x] Remove duplicated builder state; Kotlin DSLs delegate to canonical builders.
- [x] Standardize `builder(...)`, fluent return types, validation, and `@JvmStatic` exposure.
- [x] Deprecate aliases that express the same operation with a different vocabulary.
- [x] Add builder contract tests for required values, invalid values, and immutable results.

Order: tasks, commands, configuration, diagnostics, downloads, updates, currency, Bukkit menus.

Progress:

- tasks: Kotlin scheduling DSL now delegates to the canonical `TaskSpec.Builder` state and
  validation;
- commands: Kotlin receiver overloads delegate to the Java `Consumer` implementation and are hidden
  from Java bytecode lookup, removing lambda ambiguity;
- diagnostics: `DiagnosticConfiguration.builder(path)` is canonical; `file(path)` is a deprecated
  compatibility alias; declaration collections and collected snapshots are immutable;
- configuration: access policies and groups expose immutable snapshots, groups have idempotent
  lifecycle, and ambiguous `local/global` aliases are deprecated;
- registries: plugin, module, and update registries now expose the canonical `all/get/require`
  vocabulary while retaining deprecated compatibility aliases;
- services: typed service lookup now follows `get/require/all`; `getAll` remains as a deprecated
  compatibility alias and runtime results are immutable;
- dependencies: `DependencyBuilder.build()` now returns an immutable declaration snapshot;
- tasks: task declarations and queries now expose immutable collection snapshots; textual query
  filters are normalized when the query is built; task lookup follows `get/require`, with `find`
  retained as a deprecated compatibility alias; meaningless blank filters fail during `build()`;
- remote policies: platform-name matching is locale-stable and native handles are captured in an
  immutable context snapshot; policy construction now uses a canonical fluent builder, validates
  an absolute HTTPS source, and returns immutable custom values; `PluginBuilder.remotePolicy`
  accepts the completed declaration directly, while its callback overload remains a deprecated
  compatibility adapter;
- identifiers and protocol values: normalization in commands, downloads, placeholders, cooldowns,
  platform adapters, and trusted update hosts is locale-independent;
- actions and placeholder provider views return immutable value/registry snapshots;
- task queries and online-audience enumeration now return immutable runtime snapshots;
- lifecycle: plugin modules close in reverse registration order; rollback and normal context
  shutdown release download/update handles in reverse acquisition order; registrations expose
  observable state and close idempotently; closing a placeholder atomically removes it from lookup
  and prevents accidental re-enabling;
- downloads: runtime state queries now return immutable snapshots;
- placeholders: service-level `register(key, configure)` is now the canonical terminal operation;
  the former two-step `placeholder(...).register()` flow remains only as a deprecated adapter;
- Bukkit menus: builder output protects its slot map and common Java overloads are generated and
  covered by a Java compilation test.

## Phase 3 — registries and lifecycle

- [x] Standardize `get`, `require`, `all`, duplicate handling, and immutable snapshots.
- [x] Make every returned registration idempotently closeable.
- [x] Verify context shutdown closes children in reverse dependency order.
- [x] Document thread guarantees on every shared registry and async operation.

## Phase 4 — feature boundaries

Currency extraction is complete for the current major line. Remaining extractions require removal
of published compatibility signatures and are therefore explicitly scheduled for the next major
line; see `FEATURE-BOUNDARIES.md`.

- [x] Classify every property currently exposed by `ModuleContext` and `PnLibrary` as base or optional.
- [ ] Extract optional domains using the proven currency pattern.
- [x] Keep platform-specific contracts in platform API modules and implementations in runtimes.
- [ ] Reduce core to composition, ownership, lifecycle, and service publication.

Likely extraction candidates are diagnostics, downloads/updates, metrics, placeholders, and menus.
Extraction is decided by actual consumer frequency and coupling, not merely package size.

## Phase 5 — compatibility release

- [x] Keep deprecated adapters for the current major API line.
- [x] Publish a migration table mapping every old call to its canonical replacement.
- [x] Compile Kotlin and Java smoke consumers against the assembled Bukkit, BungeeCord, and Velocity JARs.
- [ ] Remove deprecated surface only when the next major API version is declared.

## Definition of done

The cleanup is complete when a consumer can predict an unfamiliar pnLibrary API without opening its
implementation: construction, lookup, registration, ownership, optional feature access, and Java
interop all follow the rules in `API-DESIGN-RULES.md`.
