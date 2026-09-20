# On-Demand Minecraft Localization Design

## Goal

Add a cross-platform `common` module and an optional `minecraft-localization` feature module. The feature obtains the official Minecraft Java Edition translation table for an explicitly requested game version and locale. It must download nothing during pnLibrary startup, reuse the existing `MinecraftVersion` type from `common`, cache verified files, and support both forward translation and reverse lookup of Minecraft objects by localized name.

This is a developer-facing Minecraft catalogue. It is not the message-localization system for pnLibrary or third-party plugins.

## Module boundary

Cross-platform Minecraft value types live in `modules/common`, published as `pnlibrary-common`. The localization implementation lives in `modules/features/minecraft-localization` and is published as the separate optional artifact `pnlibrary-minecraft-localization`. The feature depends on `common`; its typed `Material` and `Enchantment` lookup facade uses the Bukkit API as a compile-time/public integration dependency. It is not added to pnLibrary's mandatory runtime dependency graph. A server or plugin pays its localization memory, disk, and network cost only when it includes and invokes this feature.

The current proposed `MinecraftLocale` enum and `FlatJsonTranslations` parser are not retained. Locale identifiers are normalized strings because Mojang can add languages independently of pnLibrary releases. Gson, already used by the project, parses manifests, asset indexes, and language files.

## Existing version model

The existing `MinecraftVersion` and `MinecraftVersionRange` implementations move from the Bukkit API module into `modules/common` and the common package `ru.privatenull.pnlibrary.common.minecraft`. They remain the only public game-version types. The localization module does not introduce another version value class.

All pnLibrary source imports move to the common package. Keeping a Java-compatible class at the former Bukkit-qualified name would require a second runtime type, contradicting the single-model requirement, so this pre-release cleanup intentionally removes the former package location. The checked API baselines are regenerated for the move while `PnLibraryApi.VERSION` remains `1` as explicitly required for this project.

The enum gains a Java-friendly `supported()` function returning every known version except `UNKNOWN`, ordered newest first. Existing parsing and comparison semantics remain unchanged.

The localization service accepts `MinecraftVersion` directly:

```java
TranslationBundle bundle = localization.load(
    TranslationRequest.builder()
        .version(MinecraftVersion.V1_21_4)
        .locales("ru_ru", "de_de")
        .build()
).join();
```

`UNKNOWN` is rejected before any disk or network operation. Mojang releases that cannot be represented by the installed pnLibrary version are omitted from the public version list rather than being represented by a second, incompatible type.

## Version catalogue

`MinecraftLocalization.availableVersions()` returns `CompletionStage<List<MinecraftVersion>>`. It lazily obtains Mojang's version manifest, retains release entries, maps them through `MinecraftVersion.parse`, removes `UNKNOWN` and duplicates, and returns an immutable newest-first list.

The manifest has its own cache entry:

```text
<cache>/minecraft/
├── version-manifest.json
├── version-manifest.meta.json
└── translations/
```

The metadata records fetch time and content checksum. The default refresh interval is 24 hours and is configurable in the service builder. A fresh cache causes no request. If refresh fails, the last valid cache is returned. If neither network nor a valid cache is available, `MinecraftVersion.supported()` is returned, so a version selector can still be populated offline.

The manifest catalogue and translation cache have separate refresh rules. Refreshing the version list never downloads a language file.

## Explicit and lazy loading

The minimum downloadable unit is one complete Mojang language table for one Minecraft version. Mojang publishes a locale as one flat JSON object, so requesting a single translation key cannot reduce the network payload below that file.

No manifest, version metadata, asset index, language file, or client artifact is fetched during class initialization, pnLibrary startup, or service construction. Network work begins only after one of these explicit operations:

- `availableVersions()` requests the version catalogue;
- `load(request)` requests the listed locales;
- a lazy locale view receives its first translation or search operation;
- `refresh(request)` explicitly asks for revalidation.

`TranslationRequest` contains one required known `MinecraftVersion`, one or more normalized locale identifiers, and an optional fallback locale. The fallback is not implicit: `en_us` is downloaded only when requested directly or declared as fallback.

Equivalent Kotlin and Java builders construct the same immutable request. A convenience overload may accept the current Bukkit `ServerInfo.minecraftVersion`; version detection itself remains in the existing Bukkit runtime.

## Official resource resolution

For a requested version and locale, the resolver follows Mojang's official asset chain:

1. version manifest;
2. selected version metadata;
3. referenced asset index;
4. the `minecraft/lang/<locale>.json` asset object addressed by its SHA-1 hash.

Every downloaded document is subject to response-size limits, timeouts, HTTP status validation, and JSON shape validation. Whenever Mojang supplies a SHA-1 and size, both are verified before publication. Files are written to a temporary sibling and atomically moved into place, so an interrupted download never replaces a working cache entry.

Source URLs are internal implementation details and injectable in internal tests. Callers cannot supply arbitrary download URLs through the public API.

If a supported historical version stores a required base language in a version artifact instead of its asset index, the resolver may use the official artifact declared by that version metadata and extract only the language entry. This fallback is used only after the asset index proves that the locale is absent, and the artifact is cached and checksum-verified. It is never downloaded speculatively.

## Cache and concurrency

Translation files are stored by version and normalized locale:

```text
<cache>/minecraft/translations/
└── 1.21.4/
    ├── ru_ru.json
    ├── ru_ru.meta.json
    ├── de_de.json
    └── de_de.meta.json
```

Metadata contains the resolved asset hash, byte size, source version, and verification time. Cache file names are derived only from validated version and locale identifiers; path separators and traversal segments are rejected.

Parsed bundles are immutable and held in a bounded in-memory cache keyed by `(MinecraftVersion, locale)`. The builder configures the maximum entry count; least-recently-used inactive entries are evicted. Disk entries remain available after memory eviction.

Concurrent requests for the same key share one in-flight `CompletableFuture`. Different locales or versions may load concurrently within a configurable download-concurrency limit. No network or disk parsing work runs on a Minecraft main thread unless the caller deliberately supplies such an executor.

Closing the service rejects new operations, completes or cancels owned work according to the configured close policy, releases its owned executor, and clears only memory. It does not delete the persistent cache.

## Translation API

`TranslationBundle` is the immutable result of a request. It exposes the selected version, loaded locale views, optional fallback relation, and lookup facilities.

```java
Optional<String> translated = bundle.locale("ru_ru")
    .translate("item.minecraft.diamond_sword");

String translatedOrKey = bundle.locale("ru_ru")
    .translateOrKey("item.minecraft.diamond_sword");
```

Missing keys consult the explicitly configured fallback locale and then remain missing. The API never invents localized fallback phrases such as `Unknown item`; presentation text belongs to the consuming plugin.

Callers can inspect available locale identifiers from the selected version's asset index without downloading all of their language files. That locale catalogue is immutable and cached with the version metadata.

## Reverse lookup

Each parsed locale builds indexes once and reuses them for every query. Index entries retain the translation key and resolved Bukkit object when one exists.

Supported typed catalogues initially cover:

- materials using `item.minecraft.*` and `block.minecraft.*`;
- enchantments using `enchantment.minecraft.*`;
- potion/effect names using their Minecraft translation-key families.

Exact lookup returns a list because multiple keys or Bukkit objects may have the same localized text:

```java
List<MaterialMatch> matches = bundle.locale("ru_ru")
    .materials()
    .findExact("Алмазный меч");
```

Search supports exact and contains modes. Normalization performs Unicode normalization, locale-independent case folding, trimming, repeated-whitespace collapse, and Russian `ё`/`е` equivalence. The original localized value is preserved in every match. Results are deterministic: exact normalized matches first, then prefix matches, then substring matches, with translation key as the final tie-breaker.

Unknown keys and objects introduced after the compile-time Bukkit API remain accessible through generic translation-key matches even when they cannot be represented as a local `Material` or `Enchantment` enum constant.

## Failure model

Asynchronous operations fail with a public localization exception carrying a stable reason:

- unsupported or `UNKNOWN` version;
- invalid or unavailable locale;
- offline with no valid cache;
- remote protocol or HTTP failure;
- checksum or size mismatch;
- malformed cached or downloaded JSON;
- closed service.

A corrupt cache entry is quarantined with a `.corrupt-<timestamp>` suffix before a recovery download. If recovery is impossible, the operation fails rather than silently serving unverified content. An expired but valid cache may be served when remote refresh fails; the result reports that it came from stale cache through immutable source metadata.

## Builder and configuration

The Java builder is the primary construction API, with a Kotlin DSL delegating to it. Defaults are usable without configuration.

```java
MinecraftLocalization localization = MinecraftLocalization.builder()
    .cacheDirectory(dataFolder.resolve("translations"))
    .manifestTtl(Duration.ofHours(24))
    .connectTimeout(Duration.ofSeconds(5))
    .readTimeout(Duration.ofSeconds(15))
    .memoryEntries(8)
    .downloadConcurrency(2)
    .build();
```

The builder also accepts an executor. When none is supplied, the service owns a small bounded executor and closes it with the service. Network size limits and allowed Mojang hosts have safe library defaults and are not relaxed by ordinary requests.

## Documentation and compatibility

The module guide shows:

- dependency coordinates for the optional artifact;
- Java and Kotlin creation;
- listing selectable Minecraft versions;
- explicit multi-locale preload;
- first-use lazy loading;
- forward translation;
- exact and partial material lookup;
- fallback behavior;
- cache location, offline behavior, and shutdown.

The localization feature is optional. The Bukkit API gains a lightweight dependency on `pnlibrary-common`, and all platforms may reuse that artifact without depending on Bukkit. `MinecraftVersion` receives the additive static-style `supported()` method. The intentional pre-release package move and regenerated baselines retain API generation `1`.

## Verification

Tests are added after the primary implementation, as explicitly requested, and are concentrated by responsibility:

- request/builder validation and Java-callable API;
- version-manifest filtering, ordering, TTL, and offline fallback;
- asset-chain resolution and locale discovery against a local fake HTTP server;
- checksum, size-limit, malformed JSON, quarantine, and atomic-write behavior;
- memory/disk cache hits and shared concurrent loads;
- explicit fallback without accidental `en_us` download;
- exact, prefix, substring, collision, Unicode, and `ё`/`е` reverse lookup;
- Bukkit material/enchantment mapping across known and unavailable keys;
- close and executor ownership behavior;
- common, Bukkit, and localization API baselines plus the full distribution build.

No test contacts Mojang or any other public network service.
