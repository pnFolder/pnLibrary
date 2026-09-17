# Platform API Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provide small type-safe Bukkit, Bungee, and Velocity platform APIs without leaking platform types into the global API.

**Architecture:** `pnlibrary-api` exposes only a type-keyed `PlatformProvider`; each platform API module defines one native-backed entry interface. Core owns the provider implementation, and each runtime registers its matching implementation after bootstrap.

**Tech Stack:** Kotlin, Gradle, Bukkit/Spigot API, BungeeCord API, Velocity API, JUnit Jupiter

**Spec:** `docs/superpowers/specs/2026-09-17-pnlibrary-architecture-and-pnupdate-design.md`

## Global Constraints

- Global API must not import native platform classes.
- Platform API may expose native platform types when that removes redundant wrappers.
- Missing required platform access throws a descriptive exception.
- Runtime registration is closeable and rejects duplicate type registration.
- Existing `PnBukkit` remains as a compatibility bridge.

---

### Task 1: Global platform provider

**Files:**
- Create: `pnlibrary-api/src/main/kotlin/ru/privatenull/pnlibrary/api/platform/PlatformProvider.kt`
- Modify: `pnlibrary-api/src/main/kotlin/ru/privatenull/pnlibrary/api/runtime/PnLibrary.kt`
- Create: `pnlibrary-core/src/main/kotlin/ru/privatenull/pnlibrary/core/platform/PlatformProviderImpl.kt`
- Create: `pnlibrary-core/src/test/kotlin/ru/privatenull/pnlibrary/core/platform/PlatformProviderImplTest.kt`
- Modify: `pnlibrary-core/src/main/kotlin/ru/privatenull/pnlibrary/core/runtime/PnLibraryImpl.kt`
- Modify: `pnlibrary-core/src/main/kotlin/ru/privatenull/pnlibrary/core/runtime/PnLibraryRuntimeHost.kt`

**Interfaces:**
- Produces: `PlatformProvider.get(Class<T>)`, `require(Class<T>)`, and Kotlin `PnLibrary.platform<T>()`.
- Produces internally: `PlatformProviderImpl.register(Class<T>, T): AutoCloseable`.

- [ ] Test missing lookup, successful typed lookup, duplicate rejection, and removal on close; confirm RED.
- [ ] Implement the minimal provider and public extension; confirm focused tests GREEN.
- [ ] Wire provider ownership into `PnLibraryImpl` and registration into `PnLibraryRuntimeHost`.
- [ ] Refresh ABI dump, run API tests/core tests, and commit `feat: add typed platform provider`.

### Task 2: Public platform modules

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Create: `pnlibrary-bungee-api/build.gradle.kts`
- Create: `pnlibrary-bungee-api/src/main/kotlin/ru/privatenull/pnlibrary/bungee/api/BungeePlatform.kt`
- Create: `pnlibrary-velocity-api/build.gradle.kts`
- Create: `pnlibrary-velocity-api/src/main/kotlin/ru/privatenull/pnlibrary/velocity/api/VelocityPlatform.kt`
- Create: `pnlibrary-bukkit-api/src/main/kotlin/ru/privatenull/pnlibrary/bukkit/api/BukkitPlatform.kt`

**Interfaces:**
- `BukkitPlatform.server: org.bukkit.Server`, `serverInfo`, and `menus`.
- `BungeePlatform.proxy: net.md_5.bungee.api.ProxyServer`.
- `VelocityPlatform.server: com.velocitypowered.api.proxy.ProxyServer`.

- [ ] Add the two API modules with global API plus compile-only native dependencies.
- [ ] Define the three minimal interfaces and enable ABI validation for all public API modules.
- [ ] Refresh ABI dumps and commit `feat: add public platform API modules`.

### Task 3: Runtime implementations and compatibility bridge

**Files:**
- Modify: platform runtime Gradle dependencies.
- Create: `BukkitPlatformImpl.kt`, `BungeePlatformImpl.kt`, and `VelocityPlatformImpl.kt` in their runtime modules.
- Modify: each native plugin entry point to register the implementation.
- Modify: `pnlibrary-bukkit-api/src/main/kotlin/ru/privatenull/pnlibrary/bukkit/server/PnBukkit.kt` only to delegate through the new API while preserving source compatibility.

**Interfaces:**
- Consumes: `PnLibraryRuntimeHost.registerPlatform`.
- Produces: one registered platform interface per running platform.

- [ ] Add runtime contract tests where native APIs can be isolated without starting a server.
- [ ] Implement native-backed platform values and register them during startup.
- [ ] Keep runtime implementations out of API artifacts and distribution metadata intact.
- [ ] Run full verification and inspect all platform JARs.
- [ ] Commit `feat: provide native platform implementations`.

