# Platform Module Layout Design

## Goal

Make the repository root easier to scan by grouping every platform-specific API and runtime module under a single `platforms` directory.

## Target layout

```text
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
```

The following physical-directory mapping will be used:

| Existing directory | New directory | Gradle project path |
| --- | --- | --- |
| `pnlibrary-bukkit-api` | `platforms/bukkit/api` | `:pnlibrary-bukkit-api` |
| `pnlibrary-bukkit` | `platforms/bukkit/runtime` | `:pnlibrary-bukkit` |
| `pnlibrary-bungee-api` | `platforms/bungee/api` | `:pnlibrary-bungee-api` |
| `pnlibrary-bungee` | `platforms/bungee/runtime` | `:pnlibrary-bungee` |
| `pnlibrary-velocity-api` | `platforms/velocity/api` | `:pnlibrary-velocity-api` |
| `pnlibrary-velocity` | `platforms/velocity/runtime` | `:pnlibrary-velocity` |

## Compatibility

Only physical source locations change. `settings.gradle.kts` explicitly maps the existing Gradle project paths to the new directories. This preserves:

- every `project(":pnlibrary-...")` dependency;
- Gradle task paths used by CI and developers;
- Maven group, artifact ID and version coordinates;
- generated JAR names and release metadata;
- Kotlin and Java package names;
- public and binary API baselines.

No compatibility alias directories or duplicate modules will remain at the repository root.

## Build configuration

`settings.gradle.kts` remains the single directory-mapping source. It includes the six existing project names, then assigns their `projectDir` values to the matching directories below `platforms`.

Root Dokka aggregation and distribution assembly continue to address modules through their unchanged Gradle project paths, so those files should need no semantic changes. Any configuration that refers directly to a filesystem path must be updated to the new physical path.

## Move procedure

Each complete module directory is moved with Git history preserved, including build scripts, API dumps, resources and tests. The move is performed without modifying source contents. Afterward, searches verify that no build script, workflow, documentation command or tool still depends on the old physical directories.

The existing unrelated working-tree modification in `PnLibraryApi.kt` is preserved and excluded from the layout commits.

## Verification

The change is accepted when:

1. Gradle lists all six original project paths.
2. `clean test apiCheck` passes.
3. all three platform distribution JARs build successfully;
4. release metadata verification passes;
5. no old platform-module directory remains at the repository root;
6. no tracked configuration references an obsolete physical path;
7. the API generation and ABI dumps are unchanged by the move.

## Commit strategy

Use one structural commit for the directory moves and Gradle mapping, followed only by focused fixes if verification exposes a direct path dependency. Push the existing feature branch after the complete verification suite passes.
