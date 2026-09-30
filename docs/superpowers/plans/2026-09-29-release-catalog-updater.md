# Release Catalog Updater Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace GitHub release enumeration and embedded component metadata as the primary update source with a manually maintained `.pnlibrary/releases.json` catalog and deterministic compatibility-aware update planning.

**Architecture:** `ReleaseCatalogueClient` will load and validate one catalog document, then expose normalized `ProductRelease` values to the existing resolver. Artifact selection will filter channel, platform, Java, pnLibrary API, and applicable Minecraft/native API ranges before download. Download verification will inspect the platform plugin descriptor and bytecode rather than requiring `component.json`. The existing update orchestrator will build a dependency-aware atomic plan before staging any artifact.

**Tech Stack:** Kotlin/JVM 8-compatible library code, Gson, JUnit 5, Gradle, Bukkit/Paper/BungeeCord/Velocity platform adapters.

**Spec:** `docs/release-catalog.md`

## Global Constraints

- `.pnlibrary/releases.json` is the sole external release metadata source.
- No Gradle plugin generates the catalog; maintainers edit it manually.
- `size`, `sha256`, and `component.json` are not catalog fields.
- Non-applicable fields are omitted instead of written as `null`.
- Channels are `stable`, `beta`, `alpha`, and `dev`; filtering happens before SemVer sorting.
- Bukkit artifacts may declare `minecraft`; proxy artifacts may declare `platformApi`.
- Failed validation or incomplete dependency compatibility must not modify installed files.
- Catalog writes and local cache replacement are atomic.

## Review Focus

- A stable release must remain selectable behind hundreds of beta/dev releases.
- A malformed catalog entry must be rejected without crashing the server or installing anything.
- A catalog URL may point at a JAR whose plugin descriptor/version does not match the entry.
- A Java 21 server must reject an artifact compiled for Java 25.
- A library API upgrade must be rejected when a registered dependent plugin has no compatible release.

---

### Task 1: Define and validate the catalog model

**Files:**
- Create: `modules/features/update/src/main/kotlin/ru/privatenull/pnlibrary/update/ReleaseCatalog.kt`
- Modify: `modules/features/update/src/main/kotlin/ru/privatenull/pnlibrary/update/ComponentDescriptorCodec.kt` or create a dedicated codec beside it
- Test: `modules/features/update/src/test/kotlin/ru/privatenull/pnlibrary/update/ReleaseCatalogCodecTest.kt`

**Interfaces:**
- Produce `ReleaseCatalog`, `CatalogRelease`, and `CatalogArtifact` internal models.
- Produce `ReleaseCatalogCodec.decode(bytes: ByteArray): ReleaseCatalog`.
- Validate schema, product id, SemVer, channel, API ranges, Java ranges, artifact filename, HTTPS URL, and omitted-vs-null optional fields.

- [ ] Write failing tests for a complete Bukkit entry, a proxy entry with `platformApi`, omitted optional fields, duplicate versions, invalid channel, invalid URL, and explicit JSON null.
- [ ] Run `:modules:features:update:test --tests '*ReleaseCatalogCodecTest*'` and confirm the new tests fail.
- [ ] Implement the codec and model with bounded input size and clear `ManifestException` messages.
- [ ] Run the focused tests and confirm they pass.
- [ ] Commit: `feat: add releases catalog model and codec`.

### Task 2: Load the catalog with cache and atomic replacement

**Files:**
- Create or modify: `modules/features/update/src/main/kotlin/ru/privatenull/pnlibrary/update/ReleaseCatalogClient.kt`
- Modify: `modules/features/update/src/main/kotlin/ru/privatenull/pnlibrary/update/ReleaseCatalogueStore.kt`
- Test: `modules/features/update/src/test/kotlin/ru/privatenull/pnlibrary/update/ReleaseCatalogClientTest.kt`

**Interfaces:**
- `ReleaseCatalogClient.load(source: URI, refresh: RefreshMode): CompletableFuture<ReleaseCatalog>`.
- Cache key is the catalog URI; successful remote responses replace the cache through a temporary file and atomic move.
- A stale cache may be read for diagnostics but never authorizes automatic installation after a failed refresh.

- [ ] Test fresh-cache reuse, forced refresh, HTTP not-modified handling if supported by the transport, malformed remote JSON, and network failure preserving the previous cache.
- [ ] Implement bounded HTTPS loading and atomic cache writes.
- [ ] Run focused client tests and commit: `feat: add catalog loading and cache policy`.

### Task 3: Convert catalog releases into normalized product releases

**Files:**
- Modify: `modules/features/update/src/main/kotlin/ru/privatenull/pnlibrary/update/ReleaseCatalogueClient.kt`
- Test: `modules/features/update/src/test/kotlin/ru/privatenull/pnlibrary/update/ReleaseCatalogueSelectionTest.kt`

**Interfaces:**
- Add `selectReleases(catalog, acceptedChannel, product, request, runtime): List<ProductRelease>`.
- Filter channel first, then product/API/platform/Java and applicable version ranges, then sort descending by SemVer.
- Never treat array order or GitHub publication order as release priority.

- [ ] Test stable behind 400 beta/dev entries, prerelease channel boundaries, duplicate versions, and platform-specific artifact selection.
- [ ] Implement deterministic selection and normalized `ProductRelease` conversion without fetching JAR bytes.
- [ ] Run focused tests and commit: `feat: select compatible releases from catalog`.

### Task 4: Verify downloaded artifacts without component.json

**Files:**
- Modify: `modules/features/update/src/main/kotlin/ru/privatenull/pnlibrary/update/ArtifactVerifier.kt`
- Create if needed: `modules/features/update/src/main/kotlin/ru/privatenull/pnlibrary/update/JavaBytecodeVerifier.kt`
- Test: `modules/features/update/src/test/kotlin/ru/privatenull/pnlibrary/update/ArtifactVerifierTest.kt`

**Interfaces:**
- `ArtifactVerifier.verify(path: Path, expected: CatalogArtifact, runtime: RuntimeCompatibility): VerifiedArtifact`.
- Check downloaded size and locally computed SHA-256 only; no hash is required in the catalog.
- Inspect `plugin.yml`, `paper-plugin.yml`, `bungee.yml`, or `velocity-plugin.json` as applicable.
- Reject unsupported class-file major versions before installation.

- [ ] Add tests for matching descriptor, wrong product/version, Java 25 artifact on Java 21, unsafe filename, and digest mismatch when GitHub supplies a digest separately.
- [ ] Implement verification and clear user-facing failure reasons.
- [ ] Run focused verifier tests and commit: `feat: verify downloaded artifacts from platform metadata`.

### Task 5: Wire catalog loading into update service

**Files:**
- Modify: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/updates/UpdateServiceImpl.kt`
- Modify: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/updates/UpdateConfiguration.kt`
- Modify: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/updates/UpdateService.kt`
- Tests: existing core update tests plus `UpdateServiceCatalogTest.kt`

**Interfaces:**
- Preserve public update APIs; internally replace GitHub release enumeration with configured catalog source.
- Keep per-product channel configuration and default `STABLE` behavior.
- Expose snapshot states for catalog unavailable, no compatible release, update available, and staged.

- [ ] Test configuration parsing for catalog source and channel, default behavior, and disabled plugin update checks.
- [ ] Implement the catalog-backed service path while keeping platform adapters unchanged.
- [ ] Run `:modules:core:test` and update API consistency tests.
- [ ] Commit: `feat: use release catalog in update service`.

### Task 6: Implement dependency-aware atomic update planning

**Files:**
- Modify: `modules/features/update/src/main/kotlin/ru/privatenull/pnlibrary/update/UpdateResolver.kt`
- Modify: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/updates/UpdateOrchestrator.kt`
- Tests: `UpdateResolverTest.kt`, `UpdateOrchestratorTest.kt`

**Interfaces:**
- Resolve a graph of registered products and required pnLibrary API ranges.
- Produce one immutable plan containing all required artifacts or no plan at all.
- Prefer updating pnLibrary and dependent plugins together when the new API requires it.
- Never silently downgrade; never mutate files during graph resolution.

- [ ] Test library API upgrade with compatible dependents, missing dependent update, mixed stable/beta channels, and cyclic dependency rejection.
- [ ] Implement graph resolution and atomic plan validation.
- [ ] Run all update/core tests and commit: `feat: plan dependency-aware updates atomically`.

### Task 7: Documentation, migration, and end-to-end verification

**Files:**
- Modify: `docs/release-catalog.md`
- Create: `docs/release-catalog-migration.md`
- Modify: acceptance configuration and examples
- Test: acceptance build and end-to-end fixture tests

- [ ] Document migration from GitHub releases and `component.json` to `.pnlibrary/releases.json`.
- [ ] Add a fixture catalog containing stable, beta, alpha, and dev releases plus Bukkit and proxy artifacts.
- [ ] Run `:modules:features:update:test`, `:modules:core:test`, `:distribution:verifyEmbeddedMetadata`, and `:examples:acceptance-bukkit:assembleAcceptanceKit`.
- [ ] Verify no stale `component.json` requirement remains in the catalog path.
- [ ] Commit: `docs: document release catalog migration and verification`.
