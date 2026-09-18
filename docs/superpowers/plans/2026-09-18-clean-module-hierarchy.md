# Clean Module Hierarchy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace repetitive root-level `pnlibrary-*` directories and Gradle paths with a concise hierarchical module layout while preserving external artifacts.

**Architecture:** Common modules live below `modules`, platform modules remain below `platforms`, and assembly lives in `distribution`. Gradle adopts paths matching those directories. Build scripts retain explicit archive and Maven artifact names for external compatibility.

**Tech Stack:** Gradle Kotlin DSL, Kotlin/JVM, PowerShell CI/release tooling, GitHub Actions

**Spec:** `docs/superpowers/specs/2026-09-18-clean-module-hierarchy-design.md`

## Global Constraints

- Preserve all Maven artifact IDs, archive names, packages, descriptors, API dumps and release filenames.
- Update active build, CI, release, documentation and policy paths; do not rewrite historical plan/spec paths.
- Preserve the unrelated unstaged `PnLibraryApi.VERSION` change while moving its file.
- Do not leave old Gradle aliases or duplicate directories.

---

### Task 1: Move common and distribution modules

**Files:**
- Move: `pnlibrary-api` to `modules/api`
- Move: `pnlibrary-core` to `modules/core`
- Move: `pnlibrary-runtime-spi` to `modules/runtime-spi`
- Move: `pnlibrary-feature-update` to `modules/features/update`
- Move: `pnlibrary-bstats-base` to `modules/internal/bstats`
- Move: `pnlibrary-distribution` to `distribution`

- [ ] Validate every source exists, every target is absent, and all resolved paths stay under the repository root.
- [ ] Move the complete directories with Git history preserved.
- [ ] Confirm the root has no `pnlibrary-*` module directory.

### Task 2: Define the concise Gradle hierarchy

**Files:**
- Modify: `settings.gradle.kts`
- Modify: root and module `build.gradle.kts` files

- [ ] Replace includes with the exact project paths defined in the design spec.
- [ ] Replace every `project(":pnlibrary-...")`, Dokka project and `evaluationDependsOn` reference with its new hierarchical path.
- [ ] Update distribution metadata source lookup to `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/version/PnLibraryApi.kt`.
- [ ] Run `gradlew projects` and confirm no `:pnlibrary-*` path remains.

### Task 3: Update automation and current documentation

**Files:**
- Modify: `.github/workflows/ci.yml`
- Modify: `.github/workflows/release.yml`
- Modify: `.github/workflows/publish-maven-central.yml`
- Modify: `tools/api/check-api-generation.ps1`
- Modify: `tools/release/release.ps1`
- Modify: current root documentation where physical paths are shown

- [ ] Update Gradle task paths, artifact directories, API policy paths and publish matrix project paths.
- [ ] Keep Maven artifact IDs in the publish matrix unchanged where the workflow addresses external coordinates; use new Gradle paths where it invokes projects.
- [ ] Run `tools/api/check-api-generation.tests.ps1`.
- [ ] Search active configuration and current documentation for obsolete paths.

### Task 4: Verify, commit and push

- [ ] Temporarily stash only the unrelated API-generation edit.
- [ ] Run `gradlew clean test apiCheck :distribution:build --warning-mode all`.
- [ ] Run `gradlew :distribution:verifyReleaseMetadata`.
- [ ] Restore the API-generation edit and confirm it remains unstaged at `modules/api/.../PnLibraryApi.kt`.
- [ ] Confirm explicit Maven artifact IDs and archive names still use their original values.
- [ ] Commit the structural work as `refactor: simplify module hierarchy` without staging the API-generation edit.
- [ ] Push `feat/pnlibrary-architecture-pnupdate` and confirm local and remote HEAD match.
