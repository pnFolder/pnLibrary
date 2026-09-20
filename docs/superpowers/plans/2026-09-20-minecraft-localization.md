# On-Demand Minecraft Localization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the single Minecraft version model into a reusable common module and add an optional, on-demand, cached official Minecraft translation catalogue with Java and Kotlin APIs.

**Architecture:** `modules/common` owns platform-neutral Minecraft versions. `modules/features/minecraft-localization` owns immutable requests/results, official Mojang asset resolution, verified disk/memory caching, and Bukkit typed reverse indexes; nothing downloads until an explicit catalogue or translation operation.

**Tech Stack:** Kotlin/JVM 8, Java 8 API surface, Gradle Kotlin DSL, Gson 2.11, Java `HttpURLConnection`, `CompletableFuture`, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-20-minecraft-localization-design.md`

## Global Constraints

- `PnLibraryApi.VERSION` remains exactly `1`.
- `MinecraftVersion` and `MinecraftVersionRange` exist as one runtime type each in `ru.privatenull.pnlibrary.common.minecraft`; no Bukkit-package duplicate is retained.
- The localization feature is optional and never initializes or downloads during pnLibrary startup.
- A download requires an explicit version and locale request; fallback locales are downloaded only when explicitly configured.
- Public APIs are convenient from Java and Kotlin and compile to Java 8 bytecode.
- Remote content is bounded, checksum-verified when metadata supplies a hash, written atomically, and never fetched in tests.
- Per the user's requested sequence, primary implementation precedes the consolidated test-writing phase; every test failure found there is fixed before release.

## Review Focus

- A locale containing separators or `..` must be rejected before constructing a cache path; Task 6 pins this.
- Two simultaneous requests for the same version/locale must perform one network load; Task 6 pins this.
- A missing explicit fallback must not cause an `en_us` request; Task 6 pins this.
- A future manifest version mapping to `UNKNOWN` must be excluded while the offline supported list remains usable; Task 6 pins this.
- A corrupt cached translation followed by an offline failure must quarantine the file and fail rather than serve it; Task 6 pins this.

---

### Task 1: Create the common module and move Minecraft versions

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Create: `modules/common/build.gradle.kts`
- Move: `platforms/bukkit/api/src/main/kotlin/ru/privatenull/pnlibrary/bukkit/version/MinecraftVersion.kt` → `modules/common/src/main/kotlin/ru/privatenull/pnlibrary/common/minecraft/MinecraftVersion.kt`
- Move: `platforms/bukkit/api/src/main/kotlin/ru/privatenull/pnlibrary/bukkit/version/MinecraftVersionRange.kt` → `modules/common/src/main/kotlin/ru/privatenull/pnlibrary/common/minecraft/MinecraftVersionRange.kt`
- Modify: `platforms/bukkit/api/build.gradle.kts`
- Modify: every production/test import of the former Bukkit version package
- Move: `platforms/bukkit/api/src/test/kotlin/ru/privatenull/pnlibrary/bukkit/version/MinecraftVersionTest.kt` → `modules/common/src/test/kotlin/ru/privatenull/pnlibrary/common/minecraft/MinecraftVersionTest.kt`

**Interfaces:**
- Produces: `MinecraftVersion.parse(String?): MinecraftVersion`, `MinecraftVersion.supported(): List<MinecraftVersion>`, and existing range APIs in the common package.

- [ ] Add `:modules:common`, configure `pnlibrary-common` publication/API validation/JVM 8, and make Bukkit API depend on it with `api(project(":modules:common"))`.
- [ ] Move the two types and their tests, change their package declarations/imports, and add:

```kotlin
@JvmStatic
fun supported(): List<MinecraftVersion> = knownNewestFirst
```

where `knownNewestFirst` is one immutable precomputed list excluding `UNKNOWN` and sorted by numeric coordinates descending.
- [ ] Add common JAR/sources to root Dokka and distribution developer artifacts.
- [ ] Run `./gradlew :modules:common:test :platforms:bukkit:api:test apiCheck --no-daemon` and repair all source/API baseline changes caused by the intentional package move.
- [ ] Commit with `refactor: move Minecraft versions into common module`.

### Task 2: Add the localization public model and module

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Create: `modules/features/minecraft-localization/build.gradle.kts`
- Create: `modules/features/minecraft-localization/src/main/kotlin/ru/privatenull/pnlibrary/localization/TranslationRequest.kt`
- Create: `modules/features/minecraft-localization/src/main/kotlin/ru/privatenull/pnlibrary/localization/TranslationSource.kt`
- Create: `modules/features/minecraft-localization/src/main/kotlin/ru/privatenull/pnlibrary/localization/TranslationException.kt`
- Create: `modules/features/minecraft-localization/src/main/kotlin/ru/privatenull/pnlibrary/localization/TranslationMatch.kt`
- Create: `modules/features/minecraft-localization/src/main/kotlin/ru/privatenull/pnlibrary/localization/LocaleTranslations.kt`
- Create: `modules/features/minecraft-localization/src/main/kotlin/ru/privatenull/pnlibrary/localization/TranslationBundle.kt`
- Create: `modules/features/minecraft-localization/src/main/kotlin/ru/privatenull/pnlibrary/localization/MinecraftLocalization.kt`
- Create: `modules/features/minecraft-localization/src/main/kotlin/ru/privatenull/pnlibrary/localization/MinecraftLocalizationDsl.kt`

**Interfaces:**
- Consumes: common `MinecraftVersion`; Bukkit `Material` and `Enchantment`.
- Produces: `MinecraftLocalization.builder()`, `availableVersions()`, `load(TranslationRequest)`, `lazy(version, locale)`, bundle/locale lookup APIs.

- [ ] Configure the optional `pnlibrary-minecraft-localization` publication with API dependencies on common/Bukkit API, implementation Gson, and Java 8 bytecode; add it to Dokka and API validation but not any runtime distribution.
- [ ] Implement immutable request/value contracts and Java builders. Validate known version, non-empty locale set, normalized `[a-z0-9_]+` locale IDs, positive sizes/timeouts, and non-negative TTL.
- [ ] Define `TranslationException.Reason` as `UNSUPPORTED_VERSION`, `INVALID_LOCALE`, `UNAVAILABLE_LOCALE`, `OFFLINE`, `REMOTE`, `INTEGRITY`, `MALFORMED_DATA`, and `CLOSED`.
- [ ] Define the service facade so all network-producing calls return `CompletionStage`; `close()` is idempotent. Add a Kotlin DSL that constructs the same Java-facing request/builder objects.
- [ ] Run `./gradlew :modules:features:minecraft-localization:compileKotlin apiDump --no-daemon` and commit with `feat(localization): define optional localization API`.

### Task 3: Implement official asset resolution and verified storage

**Files:**
- Create: `modules/features/minecraft-localization/src/main/kotlin/ru/privatenull/pnlibrary/localization/internal/LocalizationHttpClient.kt`
- Create: `modules/features/minecraft-localization/src/main/kotlin/ru/privatenull/pnlibrary/localization/internal/MojangModels.kt`
- Create: `modules/features/minecraft-localization/src/main/kotlin/ru/privatenull/pnlibrary/localization/internal/MojangAssetResolver.kt`
- Create: `modules/features/minecraft-localization/src/main/kotlin/ru/privatenull/pnlibrary/localization/internal/VerifiedFileStore.kt`
- Create: `modules/features/minecraft-localization/src/main/kotlin/ru/privatenull/pnlibrary/localization/internal/TranslationJsonCodec.kt`

**Interfaces:**
- Produces: internal `resolveVersions()`, `resolveLocales(version)`, and `resolveLanguage(version, locale): ResolvedLanguage` with verified bytes/hash/source.

- [ ] Implement a bounded `HttpURLConnection` client with connect/read timeouts, allowed HTTPS Mojang hosts, status checks, byte limits, and no redirects to unapproved hosts.
- [ ] Parse manifest → version metadata → asset index → hashed language object with Gson. Resolve only release versions represented by `MinecraftVersion`.
- [ ] Implement SHA-1/size verification, safe cache paths, temporary sibling writes, atomic move with a non-atomic replacement fallback, and corrupt-file quarantine.
- [ ] Implement the official version-artifact language-entry fallback only when the asset index lacks the requested locale; verify the artifact metadata before extraction.
- [ ] Run module compilation and commit with `feat(localization): resolve official Minecraft language assets`.

### Task 4: Implement lazy caches, version catalogue, and lifecycle

**Files:**
- Create: `modules/features/minecraft-localization/src/main/kotlin/ru/privatenull/pnlibrary/localization/internal/VersionManifestCache.kt`
- Create: `modules/features/minecraft-localization/src/main/kotlin/ru/privatenull/pnlibrary/localization/internal/TranslationCache.kt`
- Create: `modules/features/minecraft-localization/src/main/kotlin/ru/privatenull/pnlibrary/localization/internal/InFlightLoads.kt`
- Create: `modules/features/minecraft-localization/src/main/kotlin/ru/privatenull/pnlibrary/localization/internal/DefaultMinecraftLocalization.kt`

**Interfaces:**
- Consumes: resolver from Task 3 and public contracts from Task 2.
- Produces: operational `availableVersions`, `load`, lazy locale access, refresh, and close behavior.

- [ ] Implement the independently cached manifest with configurable TTL and `MinecraftVersion.supported()` offline fallback.
- [ ] Implement bounded access-order in-memory caching plus persistent per-version/locale storage. A valid disk hit must perform no translation download.
- [ ] Deduplicate same-key loads through `ConcurrentHashMap<TranslationKey, CompletableFuture<...>>`; remove completed and failed futures safely. Limit different-key downloads with a semaphore.
- [ ] Implement explicit fallback loading, stale-valid-cache reporting, executor ownership, and close behavior without deleting disk cache.
- [ ] Run module compilation and commit with `feat(localization): add lazy translation caches`.

### Task 5: Implement translation and reverse indexes

**Files:**
- Create: `modules/features/minecraft-localization/src/main/kotlin/ru/privatenull/pnlibrary/localization/internal/SearchNormalizer.kt`
- Create: `modules/features/minecraft-localization/src/main/kotlin/ru/privatenull/pnlibrary/localization/internal/TranslationIndex.kt`
- Create: `modules/features/minecraft-localization/src/main/kotlin/ru/privatenull/pnlibrary/localization/internal/BukkitObjectResolver.kt`
- Modify: public result implementations from Task 2

**Interfaces:**
- Produces: `translate`, `translateOrKey`, generic `findExact/search`, `materials`, `enchantments`, and effect/potion match accessors.

- [ ] Build immutable exact/prefix/substring indexes once per parsed locale. Normalize Unicode, case, whitespace, and Russian `ё/е` while retaining original values.
- [ ] Map `item.minecraft.*` and `block.minecraft.*` to available `Material`, enchantment keys to available `Enchantment`, and retain generic matches for keys unknown to the compile-time Bukkit API.
- [ ] Sort matches by exact, prefix, substring, then translation key; return immutable lists and preserve collisions.
- [ ] Compile both Kotlin and Java-facing signatures, then commit with `feat(localization): add reverse Minecraft translation lookup`.

### Task 6: Add the consolidated test suite and fix findings

**Files:**
- Create: `modules/features/minecraft-localization/src/test/kotlin/ru/privatenull/pnlibrary/localization/LocalizationApiTest.kt`
- Create: `modules/features/minecraft-localization/src/test/kotlin/ru/privatenull/pnlibrary/localization/MojangAssetResolverTest.kt`
- Create: `modules/features/minecraft-localization/src/test/kotlin/ru/privatenull/pnlibrary/localization/TranslationCacheTest.kt`
- Create: `modules/features/minecraft-localization/src/test/kotlin/ru/privatenull/pnlibrary/localization/TranslationIndexTest.kt`
- Create: `modules/features/minecraft-localization/src/test/kotlin/ru/privatenull/pnlibrary/localization/LocalizationLifecycleTest.kt`
- Create: `modules/features/minecraft-localization/src/test/java/ru/privatenull/pnlibrary/localization/JavaLocalizationApiTest.java`
- Modify: implementation files only where a failing test identifies a defect

**Interfaces:**
- Verifies every public and internal responsibility without contacting public network services.

- [ ] Add a local `HttpServer` fixture serving manifest, version metadata, asset indexes, hashed resources, malformed payloads, and controlled failures.
- [ ] Test builder/Java calls, `UNKNOWN`, invalid locale/path traversal, empty locale lists, and explicit fallback behavior.
- [ ] Test version ordering/filtering, manifest TTL, offline supported fallback, locale discovery, hash/size failure, byte limits, redirects, atomic writes, and quarantine.
- [ ] Test memory/disk hits, LRU eviction, stale valid cache, same-key concurrent deduplication, different-key concurrency limit, close races, and owned/injected executor behavior.
- [ ] Test exact/prefix/substring ordering, collisions, Unicode, whitespace, `ё/е`, materials, enchantments, and unknown generic keys.
- [ ] Run `./gradlew :modules:common:test :modules:features:minecraft-localization:test --no-daemon`; fix every failure and repeat until successful.
- [ ] Commit with `test(localization): cover downloads caches and reverse lookup`.

### Task 7: Document, verify, and publish the branch

**Files:**
- Create: `docs/MINECRAFT_LOCALIZATION.md`
- Modify: `README.md`
- Modify: `docs/PACKAGES_RU.md`
- Modify: API dump files produced by `apiDump`

**Interfaces:**
- Documents dependency coordinates and equivalent Java/Kotlin examples for version selection, explicit preload, lazy load, fallback, translation, reverse lookup, cache, offline behavior, and close.

- [ ] Write the guide and package map, emphasizing that one locale/version file is the minimum download unit and that nothing downloads at startup.
- [ ] Run `./gradlew apiDump --no-daemon`, inspect every baseline change, and confirm `PnLibraryApi.VERSION == 1`.
- [ ] Run `./gradlew clean test apiCheck :distribution:build --warning-mode all --no-daemon` and require a zero exit code.
- [ ] Remove only generated untracked test artifacts after resolving and validating their exact workspace paths; preserve unrelated user files.
- [ ] Commit with `docs: explain Minecraft localization module`, push the feature branch, and verify local/remote HEAD equality.
