# Product Release and Dependencies Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace ambiguous component update metadata with a product model, add a coherent module dependency DSL and policy engine, generate verified multi-platform release manifests with a Gradle plugin, and maintain an offline-capable bounded release catalogue.

**Architecture:** The API module owns immutable product/dependency/release contracts. Core binds module declarations to the update/download services and enforces module-scoped transactional failure. The update feature owns strict JSON codecs, GitHub catalogue refresh, persistence, compatibility filtering, and resolution. A new Gradle plugin module generates embedded and aggregate descriptors from build facts plus explicit compatibility bounds.

**Tech Stack:** Kotlin 2.1, Java 8-compatible public runtime API where currently required, Gradle Kotlin DSL/Gradle Plugin Development Plugin, Gson, JUnit 5, MockWebServer or the repository's existing HTTP fakes, Kotlin Gradle plugin APIs.

**Spec:** `docs/superpowers/specs/2026-09-23-product-release-and-dependency-design.md`

## Global Constraints

- Product identity is a normalized lowercase `ProductId`; it is distinct from display name, `ModuleId`, repository, and filename.
- Release manifests contain product/artifact compatibility only and never contain dependency declarations.
- Dependencies are declared by a module in code and include managed products or external native plugins.
- `DownloadPolicy.FORCED` overrides only the ordinary automatic-download preference; hard host, integrity, subsystem, installation, size, path, and platform safety controls always win.
- Automatic external downloads require an exact HTTPS URL, positive size, and SHA-256; a download page is manual-only.
- A missing required dependency rolls back only the registering `ModuleContext`; sibling modules remain active.
- Compatibility dimensions are platform, Minecraft, platform API, pnLibrary API, Java, semantic product version, and release channel.
- The default catalogue retention is 50 valid releases per channel plus installed, persisted-plan/transaction, and compatibility-fallback pins.
- Cache replacement is atomic; refresh failure retains the last verified catalogue and marks it stale.
- No updater hot-loads arbitrary JARs; successful downloads are verified and staged for restart.
- Existing unrelated working-tree edits in `PluginBuilder.kt` and `DemoPlugin.kt` are user-owned and must be inspected and preserved.

## Review Focus

- A malicious manifest using duplicate JSON keys, path traversal filenames, or oversized values must be rejected before cache mutation; Task 4 adds codec tests for these inputs.
- A `FORCED` external dependency targeting a non-allow-listed redirect host must remain blocked; Task 3 adds policy precedence tests.
- Concurrent refreshes for one source must share work and never publish a partial catalogue; Task 6 adds concurrency and interrupted-write tests.
- A product version with several otherwise-matching artifacts must choose deterministically by the most specific compatibility bounds; Task 7 adds tie-break tests.
- Gradle builds with conflicting Java/Kotlin targets or several detectable platform APIs must fail with an actionable error rather than guess; Task 8 adds TestKit fixtures.

---

### Task 1: Product vocabulary and immutable release model

**Files:**
- Rename/modify: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/updates/UpdateModel.kt`
- Modify: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/updates/UpdateService.kt`
- Modify: all Kotlin sources importing `ComponentId`, `ComponentDescriptor`, `ComponentRelease`, `ComponentChange`, or `ComponentDependency`
- Test: `modules/api/src/test/kotlin/ru/privatenull/pnlibrary/api/updates/ProductModelTest.kt`
- Test: existing update/core/feature tests renamed mechanically to product vocabulary

**Interfaces:**
- Produces: `ProductId.of(String)`, `ProductDescriptor.builder(String, String)`, `ProductDependency`, `InstalledProduct`, `ProductRelease`, `ProductChange`.
- Removes: `PluginUpdateRequest.component`, `PluginUpdateRequest.Builder.component(String)`.
- Consumes: existing `SemanticVersion`, `ApiVersionRange`, `PlatformType`, and update state contracts.

- [ ] **Step 1: Write failing API tests for normalized product identity and descriptor ownership**

```kotlin
@Test fun `product id is normalized and rejects unsafe input`() {
    assertEquals("pnauth", ProductId.of(" PnAuth ").value)
    assertThrows<IllegalArgumentException> { ProductId.of("../auth") }
}

@Test fun `update request contains source policy but no product identity`() {
    val request = PluginUpdateRequest.builder()
        .repository("pnFolder", "pnAuth")
        .apiVersions(1, 2)
        .artifactPattern("(?i)^pnauth-.*\\.jar$")
        .build()
    assertEquals("pnAuth", request.repositoryName)
    assertFalse(PluginUpdateRequest::class.java.methods.any { it.name == "getComponent" })
}
```

- [ ] **Step 2: Run the focused API tests and confirm compilation/test failure**

Run: `gradlew.bat :modules:api:test --tests "*ProductModelTest" --no-daemon`

Expected: FAIL because `ProductId` and the renamed types do not exist and `getComponent` still exists.

- [ ] **Step 3: Rename the public model and remove update-request identity duplication**

Implement the following public shape, updating dependent types consistently rather than retaining deprecated aliases:

```kotlin
class ProductId private constructor(val value: String) : Comparable<ProductId> {
    override fun compareTo(other: ProductId) = value.compareTo(other.value)
    override fun equals(other: Any?) = other is ProductId && value == other.value
    override fun hashCode() = value.hashCode()
    override fun toString() = value

    companion object {
        private val VALID = Regex("[a-z0-9][a-z0-9_.-]*")
        @JvmStatic fun of(value: String): ProductId {
            val normalized = value.trim().lowercase()
            require(VALID.matches(normalized)) { "Invalid product ID: $value" }
            return ProductId(normalized)
        }
    }
}

data class ProductDependency(val product: ProductId, val minimumVersion: SemanticVersion)
data class InstalledProduct(
    val product: ProductId,
    val version: SemanticVersion,
    val supportedApi: ApiVersionRange,
    val providesApi: Int? = null,
)
data class ProductRelease(
    val product: ProductId,
    val version: SemanticVersion,
    val channel: UpdateChannel,
    val supportedApi: ApiVersionRange,
    val providesApi: Int? = null,
    val repository: String? = null,
    val artifacts: List<ArtifactDescriptor> = emptyList(),
)
```

Delete the builder field and accessor for `PluginUpdateRequest.component`. Change update registration so callers provide product identity from `ProductDescriptor` rather than deriving it from repository name.

- [ ] **Step 4: Mechanically migrate all imports, property names, cache keys, blocker types, and tests**

Use `ProductId`, `ProductDescriptor`, `ProductRelease`, `ProductChange`, and `ProductDependency` everywhere. Serialized JSON renaming is deferred to Task 4; keep feature code compiling with the renamed Kotlin model first.

- [ ] **Step 5: Run API, update-feature, and core tests**

Run: `gradlew.bat :modules:api:test :modules:features:update:test :modules:core:test --no-daemon`

Expected: PASS.

- [ ] **Step 6: Commit the product vocabulary migration**

```powershell
git add modules/api modules/core modules/features platforms distribution examples
git commit -m "refactor: rename update components to products"
```

### Task 2: Unified dependency DSL and semantic version constraints

**Files:**
- Replace: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/plugin/PluginDependency.kt`
- Modify: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/plugin/PluginBuilder.kt`
- Create: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/plugin/DependencyBuilder.kt`
- Modify: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/updates/UpdateModel.kt`
- Test: `modules/api/src/test/kotlin/ru/privatenull/pnlibrary/api/plugin/DependencyBuilderTest.kt`

**Interfaces:**
- Produces: `DownloadPolicy`, `VersionConstraint`, `DependencyBuilder`, immutable `ManagedProductDependency` and `ExternalPluginDependency`.
- Produces: `PluginBuilder.dependencies(Consumer<DependencyBuilder>): PluginBuilder`.
- Preserves: `PluginBuilder.depends(PluginDependency)` as the low-level entry point.

- [ ] **Step 1: Write failing tests for both dependency kinds, ranges, duplicates, and policy validation**

```kotlin
@Test fun `builds managed product dependency`() {
    val builder = DependencyBuilder()
    builder.product("pneconomy") {
        it.minimumVersion("3.1.0")
            .maximumVersionExclusive("4.0.0")
            .github("pnFolder", "pnEconomy")
            .downloadPolicy(DownloadPolicy.AUTOMATIC)
    }
    val dependency = builder.build().single() as ManagedProductDependency
    assertTrue(dependency.versions.accepts(SemanticVersion.parse("3.9.0")))
    assertFalse(dependency.versions.accepts(SemanticVersion.parse("4.0.0")))
}

@Test fun `download page cannot be automatic`() {
    assertThrows<IllegalArgumentException> {
        DependencyBuilder().plugin("LuckPerms") {
            it.minimumVersion("5.4.0")
                .downloadPage("https://luckperms.net/download")
                .downloadPolicy(DownloadPolicy.AUTOMATIC)
        }
    }
}
```

Also test duplicate product IDs case-insensitively, duplicate native plugin names case-insensitively, invalid ranges, missing repository/source, non-HTTPS URLs, non-positive size, and malformed SHA-256.

- [ ] **Step 2: Run the focused dependency tests and confirm failure**

Run: `gradlew.bat :modules:api:test --tests "*DependencyBuilderTest" --no-daemon`

Expected: FAIL because the builder, enum, and constraint type do not exist.

- [ ] **Step 3: Implement immutable dependency types and enum policy**

```kotlin
enum class DownloadPolicy { MANUAL, AUTOMATIC, FORCED }

class VersionConstraint(
    val minimum: SemanticVersion,
    val maximumInclusive: SemanticVersion? = null,
    val maximumExclusive: SemanticVersion? = null,
) {
    init {
        require(maximumInclusive == null || maximumExclusive == null)
        require(maximumInclusive == null || maximumInclusive >= minimum)
        require(maximumExclusive == null || maximumExclusive > minimum)
    }
    fun accepts(version: SemanticVersion): Boolean =
        version >= minimum &&
            (maximumInclusive == null || version <= maximumInclusive) &&
            (maximumExclusive == null || version < maximumExclusive)
}
```

Make `PluginDependency` a sealed or otherwise immutable completed contract with `required`, `downloadPolicy`, and `versions`; avoid mutable `Consumer<PluginDependency>`.

- [ ] **Step 4: Implement `DependencyBuilder` and replace the user's incomplete method**

```kotlin
fun dependencies(configure: Consumer<DependencyBuilder>): PluginBuilder {
    val builder = DependencyBuilder()
    configure.accept(builder)
    return depends(*builder.build().toTypedArray())
}
```

Inspect the user's local edit before applying this change. Replace only the incomplete `fun dependency(configure: Consumer<PluginDependency>)` line and preserve unrelated edits.

- [ ] **Step 5: Run API tests**

Run: `gradlew.bat :modules:api:test --no-daemon`

Expected: PASS.

- [ ] **Step 6: Commit the dependency DSL**

```powershell
git add modules/api
git commit -m "feat: add product and plugin dependency DSL"
```

### Task 3: Module registration, policy precedence, and download materialization

**Files:**
- Modify: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/plugin/PluginRegistryImpl.kt`
- Create: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/downloads/DependencyDownloadPolicy.kt`
- Modify: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/downloads/DirectDownloadManager.kt`
- Modify: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/updates/UpdateConfiguration.kt`
- Test: `modules/core/src/test/kotlin/ru/privatenull/pnlibrary/core/plugin/PluginDependencyRegistrationTest.kt`
- Test: `modules/core/src/test/kotlin/ru/privatenull/pnlibrary/core/downloads/DependencyDownloadPolicyTest.kt`

**Interfaces:**
- Consumes: dependency models from Task 2 and the existing transactional module-registration lifecycle.
- Produces: `DependencyDownloadPolicy.decide(dependency, configuration): DownloadDecision`.
- Produces: structured `ALLOW`, `MANUAL`, and `BLOCKED(reason)` decisions.

- [ ] **Step 1: Write failing policy precedence tests**

```kotlin
@Test fun `forced bypasses soft automatic preference`() {
    val result = policy.decide(forcedVerifiedDependency, config(automatic = false, subsystem = true))
    assertEquals(DownloadDecision.ALLOW, result)
}

@Test fun `forced cannot bypass rejected host`() {
    val result = policy.decide(forcedUntrustedHostDependency, config(allowedHosts = setOf("github.com")))
    assertTrue(result is DownloadDecision.Blocked)
}

@Test fun `manual page remains manual`() {
    assertEquals(DownloadDecision.MANUAL, policy.decide(manualPageDependency, config(automatic = true)))
}
```

- [ ] **Step 2: Write a failing registration isolation test**

Register two modules under one `PluginContext`; make the second module declare an unavailable required product. Assert the second registration throws and rolls back while the first remains in `pluginContext.modules()` with active services.

- [ ] **Step 3: Run focused core tests and confirm failure**

Run: `gradlew.bat :modules:core:test --tests "*DependencyDownloadPolicyTest" --tests "*PluginDependencyRegistrationTest" --no-daemon`

Expected: FAIL because enum policy is not wired and module dependency preflight is absent.

- [ ] **Step 4: Implement explicit policy evaluation**

Evaluate hard controls first: subsystem enabled, install-new-plugin permission, HTTPS, exact integrity, host/redirect allow-list, size, platform, and owned destination. Only then evaluate `DownloadPolicy`:

```kotlin
return when (dependency.downloadPolicy) {
    DownloadPolicy.MANUAL -> DownloadDecision.MANUAL
    DownloadPolicy.AUTOMATIC -> if (configuration.automaticDependencies) ALLOW else MANUAL
    DownloadPolicy.FORCED -> ALLOW
}
```

Never encode `FORCED` as two booleans.

- [ ] **Step 5: Integrate dependency preflight into module registration transaction**

Validate duplicates and installed versions, register managed sources, materialize exact external artifacts into download declarations, and throw before publishing the `ModuleContext` when a required dependency is unsatisfied and cannot be staged. Add created registrations/downloads to the existing rollback stack in reverse-close order.

- [ ] **Step 6: Run focused and complete core tests**

Run: `gradlew.bat :modules:core:test --no-daemon`

Expected: PASS, including sibling-module survival.

- [ ] **Step 7: Commit runtime dependency policy**

```powershell
git add modules/core
git commit -m "feat: enforce module dependency download policy"
```

### Task 4: Strict product release manifest and codec

**Files:**
- Create: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/updates/ProductReleaseManifest.kt`
- Replace/modify: `modules/features/update/src/main/kotlin/ru/privatenull/pnlibrary/update/ComponentDescriptorCodec.kt`
- Rename/create: `modules/features/update/src/main/kotlin/ru/privatenull/pnlibrary/update/ProductReleaseCodec.kt`
- Modify: `modules/features/update/src/main/kotlin/ru/privatenull/pnlibrary/update/ArtifactVerifier.kt`
- Test: `modules/features/update/src/test/kotlin/ru/privatenull/pnlibrary/update/ProductReleaseCodecTest.kt`

**Interfaces:**
- Produces: `VersionRangeText`, `ArtifactCompatibility`, `ProductArtifact`, `ProductReleaseManifest`.
- Produces: `ProductReleaseCodec.decode(ByteArray): ProductReleaseManifest` and `encode(ProductReleaseManifest): ByteArray`.
- Consumes: `ProductId`, `ApiVersionRange`, `PlatformType`, `SemanticVersion`, `UpdateChannel`.

- [ ] **Step 1: Write failing round-trip and hostile-input tests**

Create a manifest fixture with Paper and Velocity artifacts. Assert round-trip equality and deterministic field/artifact ordering. Add rejection tests for a `dependencies` field, duplicate JSON keys, `../evil.jar`, invalid SHA-256, zero/negative size, unknown schema, inverted ranges, an oversized manifest, and an artifact whose embedded descriptor disagrees with `productId` or version.

- [ ] **Step 2: Run codec tests and confirm failure**

Run: `gradlew.bat :modules:features:update:test --tests "*ProductReleaseCodecTest" --no-daemon`

Expected: FAIL because the new schema model and codec do not exist.

- [ ] **Step 3: Implement compatibility and manifest models**

```kotlin
data class ArtifactCompatibility(
    val platform: PlatformType,
    val minecraft: VersionRangeText?,
    val platformApi: VersionRangeText?,
    val pnLibraryApi: ApiVersionRange,
    val minimumJava: Int,
    val maximumJava: Int?,
)

data class ProductArtifact(
    val file: String,
    val compatibility: ArtifactCompatibility,
    val size: Long,
    val sha256: String,
    val downloadUri: URI? = null,
)
```

Validate safe filenames, positive sizes, Java minimum >= 8, normalized SHA-256, and non-inverted ranges in constructors.

- [ ] **Step 4: Implement a strict bounded codec**

Reject input larger than the configured manifest byte limit before parsing. Parse from `JsonReader` with duplicate-key detection rather than permissive object replacement. Require exactly schema 1, `productId`, `displayName`, semantic `version`, `channel`, and a non-empty artifacts array. Reject unknown top-level dependency policy and unsafe artifact fields; permit forward-compatible unknown informational fields only if explicitly decided and covered by tests.

- [ ] **Step 5: Update artifact verification against embedded product metadata**

Verify exact file size and SHA-256 first, then JAR readability and `META-INF/pnlibrary/product-release.json`. Require embedded product ID, version, platform, and compatibility facts to agree with the selected aggregate artifact.

- [ ] **Step 6: Run update feature tests**

Run: `gradlew.bat :modules:features:update:test --no-daemon`

Expected: PASS.

- [ ] **Step 7: Commit release manifest support**

```powershell
git add modules/api modules/features/update
git commit -m "feat: add strict product release manifests"
```

### Task 5: Atomic persistent product catalogue and retention

**Files:**
- Create: `modules/features/update/src/main/kotlin/ru/privatenull/pnlibrary/update/catalog/ProductCatalogue.kt`
- Create: `modules/features/update/src/main/kotlin/ru/privatenull/pnlibrary/update/catalog/ProductCatalogueStore.kt`
- Create: `modules/features/update/src/main/kotlin/ru/privatenull/pnlibrary/update/catalog/CatalogueRetention.kt`
- Test: `modules/features/update/src/test/kotlin/ru/privatenull/pnlibrary/update/catalog/ProductCatalogueStoreTest.kt`
- Test: `modules/features/update/src/test/kotlin/ru/privatenull/pnlibrary/update/catalog/CatalogueRetentionTest.kt`

**Interfaces:**
- Produces: `ProductCatalogue`, `CatalogueSource`, `CatalogueFreshness`, `ProductCatalogueStore.load/save`, and deterministic `CatalogueRetention.retain`.
- Consumes: validated `ProductReleaseManifest` values from Task 4.

- [ ] **Step 1: Write failing persistence and retention tests**

Test exact round-trip of repository, ETag, Last-Modified, checked time, release identity, publication time, trusted URLs, compatibility fields, and stale state. Assert corrupt/truncated JSON never replaces a valid prior file. Build 60 releases in each channel and assert retention keeps 50 per channel plus installed, plan, transaction, and compatibility-fallback pins.

- [ ] **Step 2: Run catalogue tests and confirm failure**

Run: `gradlew.bat :modules:features:update:test --tests "*ProductCatalogue*" --tests "*CatalogueRetention*" --no-daemon`

Expected: FAIL because catalogue classes do not exist.

- [ ] **Step 3: Implement normalized cache records and strict load**

Use a schema-versioned internal JSON document containing product/source/check validators and normalized releases. Apply independent limits for bytes, releases, artifacts per release, and strings. A missing file returns an empty result; a malformed file returns a typed corruption result and leaves the bytes untouched for diagnosis.

- [ ] **Step 4: Implement atomic save**

Write to a sibling temporary file, flush/close it, validate by reading it through the strict loader, then replace the target atomically when supported with a safe same-directory fallback. Never delete the last verified target before the replacement is ready.

- [ ] **Step 5: Implement deterministic bounded retention**

Sort by channel, version, and source publication identity. Keep the latest 50 valid releases per channel, union all pin sets, and retain the newest compatible fallback per observed platform/API bucket. Make the limit constructor-configurable for tests and server configuration.

- [ ] **Step 6: Run catalogue tests**

Run: `gradlew.bat :modules:features:update:test --tests "*ProductCatalogue*" --tests "*CatalogueRetention*" --no-daemon`

Expected: PASS.

- [ ] **Step 7: Commit persistent catalogue support**

```powershell
git add modules/features/update
git commit -m "feat: persist bounded product catalogues"
```

### Task 6: Conditional GitHub catalogue refresh

**Files:**
- Replace/modify: `modules/features/update/src/main/kotlin/ru/privatenull/pnlibrary/update/ReleaseCatalogueClient.kt`
- Create: `modules/features/update/src/main/kotlin/ru/privatenull/pnlibrary/update/catalog/GitHubCatalogueRefresher.kt`
- Modify: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/updates/UpdateOrchestrator.kt`
- Test: `modules/features/update/src/test/kotlin/ru/privatenull/pnlibrary/update/catalog/GitHubCatalogueRefresherTest.kt`

**Interfaces:**
- Produces: `refresh(source, previous): CompletableFuture<CatalogueRefreshResult>` with `NotModified`, `Updated`, and `Stale(previous, failure)` results.
- Consumes: Task 4 codec and Task 5 store/retention.

- [ ] **Step 1: Write failing HTTP refresh tests**

Use a local fake server to assert `If-None-Match`/`If-Modified-Since`, `304` behavior, GitHub Link-header pagination, fetch-only-new manifest assets, channel retention, trusted redirect validation, response limits, rate-limit/network fallback, and two concurrent calls sharing one request future.

- [ ] **Step 2: Run refresher tests and confirm failure**

Run: `gradlew.bat :modules:features:update:test --tests "*GitHubCatalogueRefresherTest" --no-daemon`

Expected: FAIL because the refresher does not exist and the current client fetches a fixed page count.

- [ ] **Step 3: Implement request coalescing and conditional pagination**

Key in-flight work by normalized owner/repository and return the existing future. Send cache validators, follow only valid GitHub `Link` pagination up to configured page/release limits, and accept redirects only through the existing trusted-host policy. Reuse unchanged releases by immutable GitHub release/asset identity.

- [ ] **Step 4: Merge, retain, and publish atomically**

Decode every new manifest before changing persistent state. On complete success merge with retained/pinned history, apply `CatalogueRetention`, and call atomic save. On any HTTP, parsing, or persistence failure return `Stale` with the previous catalogue and do not publish a partial merge.

- [ ] **Step 5: Wire configured periodic refresh into the orchestrator**

Use the configured check interval, including six hours, and resolve immediately from the last verified cache. Prevent overlapping scheduled refreshes from multiplying HTTP calls.

- [ ] **Step 6: Run feature and core tests**

Run: `gradlew.bat :modules:features:update:test :modules:core:test --no-daemon`

Expected: PASS.

- [ ] **Step 7: Commit catalogue refresh**

```powershell
git add modules/features/update modules/core
git commit -m "feat: refresh product catalogues conditionally"
```

### Task 7: Full compatibility filtering and deterministic artifact selection

**Files:**
- Modify: `modules/features/update/src/main/kotlin/ru/privatenull/pnlibrary/update/UpdateResolver.kt`
- Create: `modules/features/update/src/main/kotlin/ru/privatenull/pnlibrary/update/RuntimeCompatibility.kt`
- Modify: platform runtime adapters to expose Minecraft and native API versions
- Test: `modules/features/update/src/test/kotlin/ru/privatenull/pnlibrary/update/RuntimeCompatibilityTest.kt`
- Test: `modules/features/update/src/test/kotlin/ru/privatenull/pnlibrary/update/UpdateResolverTest.kt`

**Interfaces:**
- Produces: `RuntimeCompatibility(platform, minecraftVersion, platformApiVersion, pnLibraryApi, javaFeature)`.
- Produces: `ProductArtifact.supports(RuntimeCompatibility): Boolean` and deterministic selection.
- Consumes: cached `ProductReleaseManifest` candidates and module `VersionConstraint` values.

- [ ] **Step 1: Write failing multidimensional compatibility tests**

Cover each dimension independently and combined: Paper vs Velocity, Minecraft min/max, platform API min/max, pnLibrary API min/max, Java min/max, channel, and dependency version range. Add an artifact tie where two artifacts match and assert the narrower compatible range wins, then higher minimum Java/API, then lexical filename as a stable final tie-break.

- [ ] **Step 2: Run resolver tests and confirm failure**

Run: `gradlew.bat :modules:features:update:test --tests "*RuntimeCompatibilityTest" --tests "*UpdateResolverTest" --no-daemon`

Expected: FAIL because current resolver lacks Minecraft/platform-API dimensions and deterministic artifact specificity.

- [ ] **Step 3: Implement typed version comparison and platform facts**

Parse dotted platform/Minecraft versions into validated comparable tokens rather than lexicographic strings. Platform adapters expose detected values without depending on update-feature internals. An unavailable runtime fact fails only artifacts that constrain that dimension.

- [ ] **Step 4: Implement deterministic filtering and blocker details**

Filter release artifacts before graph search. When none remain, return an `INCOMPATIBLE` blocker naming the failed dimensions and observed runtime values. Sort matching artifacts by range specificity, descending lower bounds, then filename.

- [ ] **Step 5: Apply `VersionConstraint` in graph resolution**

Use minimum/inclusive-maximum/exclusive-maximum constraints for managed products. Preserve complete-plan and backtracking behavior: no download begins unless all required products and external plugins have a satisfiable outcome.

- [ ] **Step 6: Run all update and platform tests**

Run: `gradlew.bat :modules:features:update:test :platforms:bukkit:runtime:test :platforms:bungee:runtime:test :platforms:velocity:runtime:test --no-daemon`

Expected: PASS.

- [ ] **Step 7: Commit compatibility resolution**

```powershell
git add modules/features/update platforms
git commit -m "feat: resolve platform-specific product artifacts"
```

### Task 8: Gradle release metadata plugin

**Files:**
- Modify: `settings.gradle.kts`
- Create: `gradle-plugin/build.gradle.kts`
- Create: `gradle-plugin/src/main/kotlin/ru/privatenull/pnlibrary/gradle/PnLibraryReleasePlugin.kt`
- Create: `gradle-plugin/src/main/kotlin/ru/privatenull/pnlibrary/gradle/PnLibraryReleaseExtension.kt`
- Create: `gradle-plugin/src/main/kotlin/ru/privatenull/pnlibrary/gradle/GenerateProductDescriptorTask.kt`
- Create: `gradle-plugin/src/main/kotlin/ru/privatenull/pnlibrary/gradle/GenerateReleaseManifestTask.kt`
- Create: `gradle-plugin/src/main/kotlin/ru/privatenull/pnlibrary/gradle/VerifyReleaseManifestTask.kt`
- Create: `gradle-plugin/src/main/kotlin/ru/privatenull/pnlibrary/gradle/BuildFactDetector.kt`
- Test: `gradle-plugin/src/test/kotlin/ru/privatenull/pnlibrary/gradle/PnLibraryReleasePluginTest.kt`

**Interfaces:**
- Produces Gradle plugin ID: `ru.privatenull.pnlibrary.release`.
- Produces extension: `pnLibraryRelease` with `productId`, `displayName`, `channel`, and compatibility blocks.
- Produces tasks: `generatePnProductDescriptor`, `generatePnReleaseManifest`, `verifyPnReleaseManifest`.
- Consumes: finalized archive tasks, Java/Kotlin target settings, and resolved compile dependency metadata.

- [ ] **Step 1: Add the plugin module and failing Gradle TestKit fixtures**

Configure `java-gradle-plugin`, `kotlin-dsl`, JUnit 5, and TestKit. Write fixtures for a single Paper project and a root project aggregating Paper plus Velocity subprojects. Assert task names and expected output paths.

- [ ] **Step 2: Add failing inference and contradiction tests**

Assert inference of `project.version`, archive filename, finalized size/SHA-256, Java target, known platform dependency, and pnLibrary API dependency. Assert actionable failure for missing `productId`, unspecified channel, conflicting Java/Kotlin targets, ambiguous Paper+Velocity dependencies in one artifact, declared minimum below bytecode target, and contradictory product/version across aggregate artifacts.

- [ ] **Step 3: Run plugin tests and confirm failure**

Run: `gradlew.bat :gradle-plugin:test --no-daemon`

Expected: FAIL because plugin implementation/tasks do not exist.

- [ ] **Step 4: Implement the typed extension without guessing maxima**

```kotlin
abstract class PnLibraryReleaseExtension @Inject constructor(objects: ObjectFactory) {
    val productId: Property<String> = objects.property(String::class.java)
    val displayName: Property<String> = objects.property(String::class.java)
    val channel: Property<ReleaseChannel> = objects.property(ReleaseChannel::class.java)
    val compatibility: CompatibilitySpec = objects.newInstance(CompatibilitySpec::class.java)
}
```

Convention `displayName` from `project.name`; require `productId` and `channel`. Only infer build facts proven by configured tasks/dependencies. Require explicit compatibility maxima.

- [ ] **Step 5: Implement cacheable generation tasks**

Declare every fact as Gradle task input and each JSON path as an output. Generate embedded `META-INF/pnlibrary/product-release.json` before JAR assembly. Calculate final size/SHA-256 only from finalized artifacts and aggregate stable-sorted platform records into root `pn-release.json`.

- [ ] **Step 6: Implement independent verification task and publishing wiring**

Read generated JSON through the same schema rules or a shared schema-compatible implementation, recalculate each artifact digest/size, reject cross-project identity/version conflicts, and make supported publish tasks depend on verification without forcing publication itself.

- [ ] **Step 7: Run TestKit and reproducibility tests**

Run the same fixture twice with a clean output directory and assert byte-identical JSON; then run `gradlew.bat :gradle-plugin:test --no-daemon`.

Expected: PASS.

- [ ] **Step 8: Commit the Gradle plugin**

```powershell
git add settings.gradle.kts gradle-plugin
git commit -m "feat: generate pnLibrary product release metadata"
```

### Task 9: Apply the Gradle plugin to the demo and complete public migration

**Files:**
- Modify: `examples/demo-bukkit/build.gradle.kts`
- Modify: `examples/demo-bukkit/src/main/kotlin/ru/privatenull/pnlibrary/demo/DemoPlugin.kt`
- Modify: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/runtime/PnLibraryRuntimeHost.kt`
- Modify: `modules/api/api/api.api`
- Modify if affected: `modules/common/api/common.api`
- Modify if affected: `modules/features/minecraft-localization/api/minecraft-localization.api`
- Test: demo build output and existing registration tests

**Interfaces:**
- Consumes: all runtime APIs and Gradle plugin tasks from Tasks 1-8.
- Produces: one compilable example of product declaration, managed dependency, manual external plugin, and generated metadata.

- [ ] **Step 1: Update the demo build with explicit release metadata**

```kotlin
plugins { id("ru.privatenull.pnlibrary.release") }

pnLibraryRelease {
    productId.set("pndemo")
    displayName.set("pnDemo")
    channel.set(ReleaseChannel.DEV)
    compatibility {
        minecraft("1.20.4", "1.21.4")
        java(maximum = 21)
        pnLibraryApi(maximum = 1)
    }
}
```

Adapt exact syntax to the implemented extension while preserving the user's unrelated local demo edits.

- [ ] **Step 2: Update demo module registration and dependency examples**

Declare `ProductDescriptor("pndemo", project version)` as the single identity source. Add one managed product dependency and one manual external plugin example. Do not add duplicate product identity to `PluginUpdateRequest`.

- [ ] **Step 3: Generate and inspect descriptors**

Run: `gradlew.bat :examples:demo-bukkit:jar :examples:demo-bukkit:generatePnReleaseManifest :examples:demo-bukkit:verifyPnReleaseManifest --no-daemon`

Expected: PASS; JAR contains `META-INF/pnlibrary/product-release.json`, and generated `pn-release.json` contains no `dependencies` field.

- [ ] **Step 4: Refresh API baselines and run API checks**

Run: `gradlew.bat apiDump apiCheck --no-daemon`

Expected: PASS with product vocabulary, dependency DSL, and no update-request component accessor.

- [ ] **Step 5: Commit the example and API migration**

```powershell
git add examples modules platforms distribution
git commit -m "docs: demonstrate product releases and dependencies"
```

### Task 10: Documentation, migration notes, and complete verification

**Files:**
- Modify: `README.md`
- Modify: `HELP-README.md`
- Modify: `INTEGRATION.md`
- Modify: `PUBLISHING.md`
- Modify: `RELEASE-CHECKLIST.md`
- Modify: `CHANGELOG.md`
- Modify: `docs/superpowers/specs/2026-09-20-smart-update-orchestrator-design.md` only to add a pointer to the superseding spec, not rewrite history

**Interfaces:**
- Consumes: final public API and task names.
- Produces: copy-pasteable setup, dependency, publishing, cache, and troubleshooting documentation.

- [ ] **Step 1: Document product release generation**

Include the Gradle plugin ID, extension fields, detected versus explicit metadata table, task outputs, GitHub Release upload of `pn-release.json`, and failure messages for ambiguous targets.

- [ ] **Step 2: Document module dependencies and policy precedence**

Include complete managed-product, manual external-page, exact external-artifact, optional dependency, and version-range examples. State clearly that `FORCED` never overrides hard server security policy.

- [ ] **Step 3: Document catalogue behavior**

Describe default refresh/retention, ETag/Last-Modified use, offline stale fallback, cache location, safe deletion/rebuild procedure, and why unlimited history is not retained.

- [ ] **Step 4: Run focused static checks**

Search source/docs for stale public vocabulary and removed booleans:

```powershell
Get-ChildItem modules,platforms,examples,README.md,HELP-README.md,INTEGRATION.md,PUBLISHING.md -Recurse -File |
    Select-String -Pattern 'ComponentId|ComponentDescriptor|ComponentRelease|forceAutomaticDownload|automaticDownload'
```

Expected: no stale public update-model uses; any remaining `automaticDownload` belongs only to top-level update behavior explicitly retained by the spec and is reviewed manually.

- [ ] **Step 5: Run the complete verification suite**

Run: `gradlew.bat clean test apiCheck :distribution:build :gradle-plugin:validatePlugins --no-daemon`

Expected: `BUILD SUCCESSFUL` with no failed tests or plugin validation warnings.

- [ ] **Step 6: Inspect the final diff and working tree**

Run: `git diff --check`, `git status --short`, and `git log --oneline --decorate -15`.

Expected: no whitespace errors; only intentional user-owned uncommitted files remain, or the worktree is clean if those edits were incorporated with the user's content preserved.

- [ ] **Step 7: Commit documentation and migration notes**

```powershell
git add README.md HELP-README.md INTEGRATION.md PUBLISHING.md RELEASE-CHECKLIST.md CHANGELOG.md docs
git commit -m "docs: publish product release workflow"
```

- [ ] **Step 8: Perform branch completion review**

Use `superpowers:verification-before-completion`, then `superpowers:requesting-code-review`, and finally `superpowers:finishing-a-development-branch`. Report verification commands and exact outcomes; do not merge or push without the user's direction.
