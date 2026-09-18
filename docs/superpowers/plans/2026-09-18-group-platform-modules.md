# Group Platform Modules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move all six platform API/runtime modules into a clear `platforms/<platform>/{api,runtime}` hierarchy without changing their Gradle identities or published artifacts.

**Architecture:** `settings.gradle.kts` keeps the existing project names and assigns each one an explicit `projectDir`. Whole module directories move unchanged, while documentation that describes physical locations follows the new paths. Gradle project references continue to use their existing stable names.

**Tech Stack:** Gradle Kotlin DSL, Kotlin/JVM, Git

**Spec:** `docs/superpowers/specs/2026-09-18-platform-module-layout-design.md`

## Global Constraints

- Preserve `:pnlibrary-bukkit-api`, `:pnlibrary-bukkit`, `:pnlibrary-bungee-api`, `:pnlibrary-bungee`, `:pnlibrary-velocity-api`, and `:pnlibrary-velocity`.
- Preserve Maven coordinates, archives, packages, resources and ABI dumps.
- Do not alter the unrelated working-tree change in `pnlibrary-api/src/main/kotlin/ru/privatenull/pnlibrary/api/version/PnLibraryApi.kt`.
- Leave no compatibility aliases or duplicate platform modules at the root.

---

### Task 1: Map stable Gradle projects to grouped directories

**Files:**
- Modify: `settings.gradle.kts`

**Interfaces:**
- Consumes: the six existing stable Gradle project paths.
- Produces: explicit physical-directory mappings below `platforms/`.

- [ ] Add the following mappings after the six platform `include` calls:

```kotlin
project(":pnlibrary-bukkit-api").projectDir = file("platforms/bukkit/api")
project(":pnlibrary-bukkit").projectDir = file("platforms/bukkit/runtime")
project(":pnlibrary-bungee-api").projectDir = file("platforms/bungee/api")
project(":pnlibrary-bungee").projectDir = file("platforms/bungee/runtime")
project(":pnlibrary-velocity-api").projectDir = file("platforms/velocity/api")
project(":pnlibrary-velocity").projectDir = file("platforms/velocity/runtime")
```

- [ ] Move each complete source directory to its mapped destination with Git history preserved.
- [ ] Run `./gradlew projects` and confirm all six original Gradle paths appear.

### Task 2: Update current physical-path documentation

**Files:**
- Modify: `HELP-README.md`

**Interfaces:**
- Consumes: the new directory layout from Task 1.
- Produces: accurate current module-location guidance.

- [ ] Replace current physical references:

```text
pnlibrary-bukkit-api/inventory -> platforms/bukkit/api/inventory
pnlibrary-bukkit/inventory -> platforms/bukkit/runtime/inventory
```

- [ ] Search tracked non-historical configuration and documentation for old physical paths. Historical implementation plans remain unchanged because they describe the repository state in which they were written.
- [ ] Run `git diff --check`.

### Task 3: Verify and publish the structural change

**Files:**
- Verify: all moved build scripts, sources, resources, tests and API dumps.

**Interfaces:**
- Consumes: grouped physical layout with stable Gradle project paths.
- Produces: the same tested and distributable artifacts as before the move.

- [ ] Run `./gradlew clean test apiCheck :pnlibrary-distribution:build --warning-mode all` and require `BUILD SUCCESSFUL`.
- [ ] Run `./gradlew :pnlibrary-distribution:verifyReleaseMetadata` and require success.
- [ ] Verify that the six old root module directories no longer exist.
- [ ] Verify the unrelated `PnLibraryApi.kt` modification remains unstaged.
- [ ] Commit only the layout move, settings, documentation and this plan with message `refactor: group platform modules by platform`.
- [ ] Push `feat/pnlibrary-architecture-pnupdate` and verify local HEAD equals the remote branch HEAD.
