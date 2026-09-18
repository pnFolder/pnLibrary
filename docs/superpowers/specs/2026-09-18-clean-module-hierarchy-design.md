# Clean Module Hierarchy Design

## Goal

Remove repetitive `pnlibrary-*` names from the repository layout and Gradle project tree while preserving every external artifact coordinate and runtime behavior.

## Target repository layout

```text
modules/
  api/
  core/
  runtime-spi/
  features/
    update/
  internal/
    bstats/
platforms/
  bukkit/
    api/
    runtime/
  bungee/
    api/
    runtime/
  velocity/
    api/
    runtime/
distribution/
```

`modules` is an organizational container, not a published artifact. `features` contains optional business implementations. `internal` contains implementation details that are not a public pnLibrary API. `platforms` owns platform contracts and runtimes. `distribution` assembles installable JARs.

## Gradle hierarchy

| New Gradle path | Physical directory | Previous Gradle path |
| --- | --- | --- |
| `:modules:api` | `modules/api` | `:pnlibrary-api` |
| `:modules:core` | `modules/core` | `:pnlibrary-core` |
| `:modules:runtime-spi` | `modules/runtime-spi` | `:pnlibrary-runtime-spi` |
| `:modules:features:update` | `modules/features/update` | `:pnlibrary-feature-update` |
| `:modules:internal:bstats` | `modules/internal/bstats` | `:pnlibrary-bstats-base` |
| `:platforms:bukkit:api` | `platforms/bukkit/api` | `:pnlibrary-bukkit-api` |
| `:platforms:bukkit:runtime` | `platforms/bukkit/runtime` | `:pnlibrary-bukkit` |
| `:platforms:bungee:api` | `platforms/bungee/api` | `:pnlibrary-bungee-api` |
| `:platforms:bungee:runtime` | `platforms/bungee/runtime` | `:pnlibrary-bungee` |
| `:platforms:velocity:api` | `platforms/velocity/api` | `:pnlibrary-velocity-api` |
| `:platforms:velocity:runtime` | `platforms/velocity/runtime` | `:pnlibrary-velocity` |
| `:distribution` | `distribution` | `:pnlibrary-distribution` |

Container projects such as `:modules`, `:modules:features`, `:modules:internal`, `:platforms:bukkit`, `:platforms:bungee`, and `:platforms:velocity` carry no source sets and publish nothing.

## External compatibility

The cleanup changes internal Gradle paths and physical directories only. The following remain unchanged:

- Maven `groupId`, `artifactId`, and version values;
- archive base names such as `pnLibrary-api` and `pnLibrary-bukkit`;
- Kotlin and Java packages;
- API dump contents;
- platform descriptors and distribution filenames;
- release metadata schema and checksum filenames.

CI, release workflows, scripts, Dokka aggregation and module-to-module dependencies must be updated to use the new internal paths and physical locations.

## API compatibility policy paths

The checked-in global ABI baseline moves to `modules/api/api/api.api`. Each platform API uses the same local `api/api.api` convention. The API generation source moves to `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/version/PnLibraryApi.kt`. The policy script defaults and CI `git cat-file` lookup use the new paths while retaining a read-only fallback for base revisions created before the structural move.

## Working-tree preservation

The existing uncommitted change to `PnLibraryApi.VERSION` moves together with the API module but remains unstaged and excluded from the structural commit. Verification temporarily stashes that single change because the existing test baseline expects generation `4`, then restores it immediately.

## Verification

The change is accepted when:

1. `gradlew projects` displays the new hierarchy and no `:pnlibrary-*` project paths.
2. Searches find no active build, CI, release or tooling reference to an old Gradle path or obsolete physical directory.
3. `clean test apiCheck :distribution:build --warning-mode all` succeeds with the unrelated API-generation edit temporarily excluded.
4. `:distribution:verifyReleaseMetadata` succeeds.
5. published artifact IDs and archive base names remain unchanged.
6. the repository root contains `modules`, `platforms`, and `distribution`, not seven separate `pnlibrary-*` module directories.

## Commit and publication

The structural move, Gradle references, scripts and current documentation form one commit. Historical specifications and implementation plans retain old paths where they intentionally describe earlier repository states. The commit is pushed to `feat/pnlibrary-architecture-pnupdate` after verification.
