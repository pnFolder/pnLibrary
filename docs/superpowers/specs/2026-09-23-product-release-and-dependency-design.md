# Product releases and module dependencies

## Intent

pnLibrary must give each logical module one clear way to declare the plugins it needs, while release authors get a generated, verifiable description of every published artifact. Runtime dependency declarations remain in plugin code. Release manifests describe products and artifacts only; they do not duplicate dependency policy.

This specification replaces the component naming and release-manifest/dependency-declaration sections of `2026-09-20-smart-update-orchestrator-design.md`. The transaction, notification, recovery, and general resolver safety rules from that design remain applicable unless changed below.

## Vocabulary and ownership

- A **physical plugin** is the native Bukkit, Paper, BungeeCord, or Velocity plugin registered as a `PluginContext`.
- A **module** is a logical pnLibrary consumer registered as a `ModuleContext` beneath a physical plugin.
- A **product** is an independently versioned and published unit known to the update system. One product release may contain several platform-specific artifacts.
- An **artifact** is one exact JAR for one supported runtime/platform combination.
- A **dependency** is a runtime requirement declared by a module.

The update API renames ambiguous component terminology:

- `ComponentId` becomes `ProductId`;
- `ComponentDescriptor` becomes `ProductDescriptor`;
- `ComponentRelease` becomes `ProductRelease`;
- serialized `component` fields become `productId`.

`ProductId` is a normalized, stable, lowercase identifier such as `pnauth`. It is not a display name, module-local ID, repository name, or JAR filename.

## Module dependency DSL

Dependencies are declared while registering a module. `PluginBuilder.depends(PluginDependency)` remains the primitive operation. The incomplete `dependency(Consumer<PluginDependency>)` signature is replaced by a real mutable DSL builder because an interface representing a completed dependency cannot safely be configured in place.

The Java-friendly shape is:

```kotlin
pluginContext.registerModule("auth") { module ->
    module.dependencies { dependencies ->
        dependencies.product("pneconomy") { dependency ->
            dependency.minimumVersion("3.1.0")
            dependency.maximumVersionExclusive("4.0.0")
            dependency.github("pnFolder", "pnEconomy")
            dependency.required(true)
            dependency.downloadPolicy(DownloadPolicy.AUTOMATIC)
        }

        dependencies.plugin("LuckPerms") { dependency ->
            dependency.minimumVersion("5.4.0")
            dependency.downloadPage("https://luckperms.net/download")
            dependency.required(true)
            dependency.downloadPolicy(DownloadPolicy.MANUAL)
        }

        dependencies.plugin("SomeLegacyPlugin") { dependency ->
            dependency.minimumVersion("2.0.0")
            dependency.artifact(
                "https://downloads.example.org/SomeLegacyPlugin-2.0.0.jar",
                1_483_920,
                "<sha256>"
            )
            dependency.downloadPolicy(DownloadPolicy.FORCED)
        }
    }
}
```

Kotlin may use the same API through SAM conversion. Convenience factories may remain, but they delegate to the same immutable dependency models and validation.

### Managed product dependency

A product dependency contains:

- `productId`;
- a required semantic-version constraint: minimum, optional inclusive maximum, or optional exclusive maximum;
- release source coordinates, initially GitHub owner and repository;
- `required`, defaulting to `true`;
- `downloadPolicy`, defaulting to `MANUAL`.

The source identifies where pnLibrary can refresh the product catalogue. A Git branch is not part of release resolution: published GitHub Releases and their manifests are the source of installable versions.

### External plugin dependency

An external plugin dependency contains:

- native plugin name;
- a semantic-version constraint;
- `required`, defaulting to `true`;
- `downloadPolicy`, defaulting to `MANUAL`;
- either a manual HTTPS download page or an exact HTTPS artifact descriptor.

An exact artifact descriptor contains URL, byte size, and SHA-256. A download page is informational and can never be installed automatically. pnLibrary does not scrape web pages to discover files.

### Download policy and server authority

Boolean combinations such as `automaticDownload` plus `forceAutomaticDownload` are replaced by:

```kotlin
enum class DownloadPolicy {
    MANUAL,
    AUTOMATIC,
    FORCED,
}
```

- `MANUAL`: report the missing/outdated dependency and never initiate an automatic download.
- `AUTOMATIC`: download only when ordinary automatic dependency downloads are enabled in server configuration.
- `FORCED`: bypass the ordinary automatic-download preference, but never bypass hard safety controls.

Hard safety controls always win. Disabling the entire update/download subsystem, rejecting an external host, requiring HTTPS/SHA-256, denying new-plugin installation, size limits, filesystem boundaries, or platform restrictions cannot be overridden by module code. `FORCED` means "required for this module to operate", not "ignore administrator security policy".

If a required dependency cannot be satisfied, registration of that `ModuleContext` fails transactionally before its capabilities become visible. Other modules belonging to the same physical plugin stay active. An optional dependency produces an observable unavailable state but does not block module registration.

## Product descriptor

Each module participating in managed updates declares one `ProductDescriptor`. It is the runtime source of product identity, installed version, and supported pnLibrary API range. The update request contains only source/check/download behavior; it no longer contains a duplicate product ID.

Conceptually:

```kotlin
module.product(
    ProductDescriptor.builder("pnauth", "2.4.0")
        .pnLibraryApi(1, 2)
        .build()
)
```

`PluginUpdateRequest.Builder.component(String)` and `PluginUpdateRequest.component` are removed. Registration passes the descriptor's `ProductId` and installed version to the update service explicitly.

## Generated release metadata

A dedicated pnLibrary Gradle plugin generates release metadata. Developers do not hand-author JSON.

Each platform subproject embeds:

```text
META-INF/pnlibrary/product-release.json
```

The root release task aggregates the platform outputs into the GitHub Release asset:

```text
pn-release.json
```

The aggregate manifest contains product-level metadata and all artifacts:

```json
{
  "schema": 1,
  "productId": "pnauth",
  "displayName": "pnAuth",
  "version": "2.4.0",
  "channel": "stable",
  "artifacts": [
    {
      "file": "pnAuth-paper-2.4.0.jar",
      "platform": "PAPER",
      "minecraft": {
        "minimum": "1.20.4",
        "maximum": "1.21.4"
      },
      "platformApi": {
        "minimum": "1.20.4-R0.1"
      },
      "pnLibraryApi": {
        "minimum": 1,
        "maximum": 2
      },
      "java": {
        "minimum": 17,
        "maximum": 21
      },
      "size": 1845321,
      "sha256": "<sha256>"
    }
  ]
}
```

The manifest deliberately has no `dependencies` field. Dependencies belong to module code. The schema distinguishes Minecraft protocol/server compatibility, native platform API compatibility, and pnLibrary API compatibility. Omitted optional maximums mean unbounded; omitted `minecraft` or `platformApi` sections mean that dimension is not constrained by the manifest.

### Gradle DSL and inference

The release author configures only values Gradle cannot determine reliably:

```kotlin
pnLibraryRelease {
    productId = "pnauth"
    displayName = "pnAuth"
    channel = ReleaseChannel.STABLE

    compatibility {
        minecraft("1.20.4", "1.21.4")
        java(maximum = 21)
        pnLibraryApi(maximum = 2)
    }
}
```

The plugin derives and validates:

- product version from `project.version`;
- artifact filename from the selected archive task;
- artifact size and SHA-256 from the finalized JAR;
- minimum Java from the Java/Kotlin toolchain or JVM target;
- platform and detected minimum platform API from known compile dependencies;
- detected minimum pnLibrary API from the pnLibrary dependency.

The author explicitly supplies `productId`, release channel, and compatibility maximums. `displayName` defaults to `project.name`. Explicit overrides are permitted only for supported fields and are validated against detected facts. For example, declared minimum Java cannot be lower than the compiled bytecode target. Unknown or contradictory required metadata fails the manifest-generation task.

The plugin contributes deterministic tasks along these lines:

- `generatePnProductDescriptor` for the embedded descriptor;
- `generatePnReleaseManifest` for the aggregate release asset;
- `verifyPnReleaseManifest` for schema, artifact, digest, and cross-project consistency checks.

Generation runs after archive finalization, and publishing tasks depend on verification. Generated JSON uses stable ordering and reproducible formatting.

## Release catalogue cache

pnLibrary periodically refreshes a persistent catalogue for every managed product source. The default interval remains configurable, with six hours supported as an ordinary value.

Catalogues live below:

```text
plugins/pnLibrary/updates/catalog/<product-id>.json
```

Each cache record contains all information required to resolve offline:

- schema version;
- product ID and display name;
- source repository;
- last successful check time;
- HTTP `ETag` and/or `Last-Modified` validator;
- retained release versions and channels;
- every retained artifact's platform, Minecraft range, platform API range, pnLibrary API range, Java range, filename, size, SHA-256, and trusted download URL;
- source release identity and publication time;
- cache freshness/staleness state.

The cache is an internal normalized representation, not a byte-for-byte copy of GitHub responses. It is written atomically only after every accepted manifest has passed strict parsing and validation.

Refresh behavior:

1. Load the last verified cache immediately so resolution does not wait for the network.
2. Use conditional GitHub requests with `ETag`/`Last-Modified`.
3. On `304 Not Modified`, update check metadata without refetching unchanged manifests.
4. On change, paginate releases up to configured safety limits and fetch only new or changed `pn-release.json` assets.
5. Validate, normalize, merge, and atomically replace the catalogue.
6. On network or validation failure, retain the previous verified catalogue and mark it stale.

Retention is bounded rather than attempting to store an unlimited repository history. The default keeps the latest 50 valid releases per channel, all installed versions, every version referenced by a persisted plan/transaction, and the newest known compatible fallback for each observed platform/API compatibility bucket. Limits are configurable and enforced for remote pages, manifest bytes, release count, and local cache size.

## Resolution flow

For each module dependency, pnLibrary:

1. inspects installed native plugins and registered products;
2. checks the declared semantic-version constraint;
3. refreshes or reads the appropriate verified catalogue;
4. filters releases by channel;
5. filters artifacts by platform, Minecraft version, platform API, pnLibrary API, and Java;
6. resolves the complete product graph before downloading anything;
7. applies module download policy and server safety policy;
8. downloads to transaction staging, verifies exact size/SHA-256/JAR metadata, and stages the complete plan;
9. reports that restart is required rather than hot-loading arbitrary JARs.

No matching artifact produces a structured `INCOMPATIBLE` reason. A missing manual external plugin produces an actionable blocker with its download page. A policy-denied automatic artifact produces a policy blocker rather than silently downloading or silently ignoring the dependency.

## Compatibility and migration

This is an intentional public API cleanup on the current feature branch:

- ambiguous component types and serialized fields are renamed to product types;
- `PluginUpdateRequest.component` is removed;
- dependency booleans migrate to `DownloadPolicy`;
- duplicate dependency declarations in `ProductDescriptor`, `PluginUpdateRequest`, and release manifests are removed;
- existing `depends(PluginDependency)` remains as the low-level immutable entry point;
- the new grouped `dependencies(Consumer<DependencyBuilder>)` becomes the preferred DSL.

Old persisted catalogues are not trusted as new-schema catalogues. They are backed up or ignored and rebuilt from release sources. Existing staged transactions retain their original journal format until completed or rolled back.

## Testing and acceptance

Tests must cover:

- Gradle metadata inference for Java, platform dependencies, pnLibrary API, file size, and SHA-256;
- explicit channel and compatibility overrides;
- generation failure on missing, contradictory, or unverifiable metadata;
- deterministic embedded and aggregate JSON;
- multiple platform artifacts for one product/version;
- strict schema parsing and rejection of a manifest containing dependency policy;
- product and external-plugin DSL validation, duplicate detection, and semantic ranges;
- `MANUAL`, `AUTOMATIC`, and `FORCED` precedence against soft preferences and hard safety controls;
- module-only rollback when a required dependency is unavailable;
- optional dependency behavior;
- catalogue pagination, conditional requests, merging, bounded retention, atomic writes, and offline stale fallback;
- resolver filtering across platform, Minecraft, platform API, pnLibrary API, Java, version, and channel;
- checksum, size, JAR, embedded-descriptor, redirect, host, and path validation;
- Java API usability, Kotlin SAM usability, API dump updates, examples, and documentation;
- the full Gradle test and distribution build.

Acceptance requires that a sample multi-platform product can generate its descriptors without hand-written JSON, publish one `pn-release.json`, be discovered into the local catalogue, and be selected or rejected with a precise reason for every supported runtime combination.
