# Component Metadata Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Generate and validate compact embedded JAR metadata with separate product version, release channel, API range, and optional Java/platform constraints.

**Architecture:** The metadata reader/writer remains in the update feature. The Gradle/Maven integrations generate `META-INF/pnlibrary/component.json`; release discovery uses GitHub assets and does not require a separate manifest. Existing readers accept the legacy `component` key while new output uses `product`.

**Tech Stack:** Kotlin, Gson, Gradle, Maven plugin, JUnit.

**Spec:** `docs/superpowers/specs/2026-09-27-component-metadata-design.md`

## Global Constraints

- Java baseline remains 8 for portable modules.
- Null or absent maximum values mean unbounded compatibility.
- Beta numbering uses SemVer prerelease identifiers such as `2.3.0-beta.1`.
- `pn-update.json` is not required for release discovery.

## Review Focus

- Legacy embedded descriptors using `component` still decode.
- New descriptors use `product` and preserve the full prerelease version.
- Missing maximum Java does not reject newer Java runtimes.
- GitHub fallback works without a release manifest.
- Invalid product/channel/API metadata is rejected clearly.

### Task 1: Codec contract and tests

**Files:**
- Modify: `modules/features/update/src/main/kotlin/ru/privatenull/pnlibrary/update/ProductReleaseCodec.kt`
- Test: `modules/features/update/src/test/kotlin/ru/privatenull/pnlibrary/update/ProductReleaseCodecTest.kt`

- [ ] Add failing tests for `product`, beta SemVer, omitted maximum Java, and legacy `component` decoding.
- [ ] Update the codec to emit `product` and accept legacy `component`.
- [ ] Run the focused codec tests.

### Task 2: Embedded descriptor generation

**Files:**
- Modify: `tools/component-metadata/core/...`
- Modify: `tools/component-metadata/gradle/...`
- Modify: `tools/component-metadata/maven/...`
- Test: corresponding plugin tests.

- [ ] Generate the new compact schema from build coordinates and API settings.
- [ ] Preserve optional maximum fields as absent/null rather than inventing limits.
- [ ] Verify Gradle and Maven generated resources contain the expected entry.

### Task 3: Runtime validation and release discovery

**Files:**
- Modify: `modules/features/update/src/main/kotlin/ru/privatenull/pnlibrary/update/EmbeddedDescriptorReader.kt`
- Modify: `modules/features/update/src/main/kotlin/ru/privatenull/pnlibrary/update/ReleaseCatalogueClient.kt`
- Test: embedded reader and release catalogue tests.

- [ ] Validate product/version/channel/API/Java after download.
- [ ] Keep GitHub asset fallback operational without `pn-update.json`.
- [ ] Add tests covering multi-asset selection and digest verification.

### Task 4: Build and documentation verification

- [ ] Build API, update feature, Gradle/Maven metadata plugins, and Bukkit distribution.
- [ ] Inspect the resulting JAR for `META-INF/pnlibrary/component.json`.
- [ ] Run `git diff --check` and focused tests.
