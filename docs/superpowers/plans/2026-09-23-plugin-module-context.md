# Plugin and Module Context Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the competing plugin-registration APIs with one physical `PluginContext` per native plugin and isolated capability-rich `ModuleContext` instances beneath it.

**Architecture:** `PluginRegistryImpl` stores physical contexts in an `IdentityHashMap`. Each physical context stores logical modules by local `ModuleId`; module resources continue to use a registry-wide `PluginId` key composed from the native plugin ID and local module ID so different plugins may both register `core` safely.

**Tech Stack:** Kotlin, Java-compatible `Consumer` API, Gradle, JUnit 5

**Spec:** `docs/superpowers/specs/2026-09-23-plugin-module-context-design.md`

## Global Constraints

- A physical platform plugin is registered once and receives no implicit module.
- `PluginContext` represents the physical owner; `ModuleContext` represents one logical module.
- Old builder-based registry overloads and `PluginModules` are removed rather than kept deprecated.
- Physical owners are compared by object identity.
- Module registration remains transactional and cleanup is idempotent.
- Existing uncommitted user changes must be incorporated, not discarded.

## Review Focus

- Registering the same owner object twice must fail while an equal but distinct object is treated independently.
- Two different owners may both register local module ID `core` without colliding in global services.
- A failed module build must not leave the module visible from its parent.
- Closing one module must not close sibling modules or unregister owner-wide commands prematurely.
- Platform disable and registry close must close every module exactly once and reject later registrations.
- Maximum-length native and module IDs must still produce a valid registry-wide service key.

---

### Task 1: Define the public context hierarchy

**Files:**
- Modify: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/plugin/PluginRegistry.kt`
- Modify: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/plugin/PluginContext.kt`
- Modify: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/plugin/ModuleContext.kt`
- Delete: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/plugin/PluginModules.kt`
- Modify: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/plugin/PluginMetadata.kt`
- Test: `modules/api/src/test/kotlin/ru/privatenull/pnlibrary/api/plugin/ModuleIdTest.kt`

**Interfaces:**
- Consumes: existing `PluginBuilder`, `PluginMetadata`, and capability service interfaces.
- Produces: `PluginRegistry.register(Any): PluginContext`, physical `PluginContext`, capability-rich `ModuleContext`, and normalized `ModuleId`.

- [ ] **Step 1: Add failing `ModuleId` normalization tests**

```kotlin
class ModuleIdTest {
    @Test fun `IDs normalize and compare by value`() {
        assertEquals(ModuleId.of("core"), ModuleId.of(" CORE "))
    }

    @Test fun `invalid IDs are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { ModuleId.of("bad id") }
    }
}
```

- [ ] **Step 2: Run the focused API test and confirm failure**

Run: `./gradlew :modules:api:test --tests '*ModuleIdTest'`

Expected: FAIL because the final `ModuleId` contract is not yet compiled into the API.

- [ ] **Step 3: Replace the public interfaces**

```kotlin
interface PluginRegistry : AutoCloseable {
    fun register(owner: Any): PluginContext
    fun get(owner: Any): PluginContext?
    fun require(owner: Any): PluginContext =
        get(owner) ?: error("Platform plugin is not registered in pnLibrary")
    fun unregister(owner: Any)
    fun registrations(): List<PluginContext>
    override fun close()
}

interface PluginContext : AutoCloseable {
    val owner: Any
    val isClosed: Boolean
    fun registerModule(id: ModuleId, configure: Consumer<PluginBuilder> = Consumer {}): ModuleContext
    fun registerModule(id: String, configure: Consumer<PluginBuilder> = Consumer {}): ModuleContext =
        registerModule(ModuleId.of(id), configure)
    fun getModule(id: ModuleId): ModuleContext?
    fun getModule(id: String): ModuleContext? = getModule(ModuleId.of(id))
    fun requireModule(id: ModuleId): ModuleContext =
        getModule(id) ?: error("Module $id is not registered")
    fun unregisterModule(id: ModuleId)
    fun modules(): List<ModuleContext>
    override fun close()
}
```

Move the existing capability properties from `PluginContext` to `ModuleContext`, change its public `id` to `ModuleId`, and change `PluginMetadata.id` to `ModuleId`. Keep a private registry-wide `PluginId` inside core for services that still require global keys.

- [ ] **Step 4: Remove `PluginModules` and compile the API**

Run: `./gradlew :modules:api:compileKotlin :modules:api:test --tests '*ModuleIdTest'`

Expected: PASS for API sources and `ModuleIdTest`; downstream modules may still fail until Task 2.

- [ ] **Step 5: Commit the public API change**

```bash
git add modules/api/src/main modules/api/src/test
git commit -m "refactor(api): separate plugin and module contexts"
```

### Task 2: Implement physical ownership and isolated modules

**Files:**
- Modify: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/plugin/PluginRegistryImpl.kt`
- Modify: `modules/core/src/test/kotlin/ru/privatenull/pnlibrary/core/plugin/PluginRegistryImplTest.kt`

**Interfaces:**
- Consumes: public interfaces produced by Task 1 and existing resource factories.
- Produces: identity-based physical registration, local module lookup, composite internal keys, module-only teardown, and owner-wide teardown.

- [ ] **Step 1: Replace old registration tests with failing hierarchy tests**

```kotlin
@Test fun `one owner registers multiple isolated modules`() {
    val owner = Any()
    val registry = registry()
    val plugin = registry.register(owner)
    val core = plugin.registerModule("core") { }
    val economy = plugin.registerModule("economy") { }

    assertSame(plugin, registry.get(owner))
    assertSame(core, plugin.getModule("CORE"))
    assertEquals(setOf("core", "economy"), plugin.modules().map { it.id.value }.toSet())
}

@Test fun `different owners may use the same local module ID`() {
    val registry = registry()
    val first = registry.register(Any()).registerModule("core") { }
    val second = registry.register(Any()).registerModule("core") { }
    assertNotSame(first, second)
}

@Test fun `maximum length IDs produce a valid distinct service namespace`() {
    val firstOwner = ownerWithId("a".repeat(64))
    val secondOwner = ownerWithId("b".repeat(64))
    val registry = registry(platform = platformFor(firstOwner, secondOwner))
    registry.register(firstOwner).registerModule("c".repeat(64)) { }
    registry.register(secondOwner).registerModule("c".repeat(64)) { }
    assertEquals(2, registry.registrations().flatMap { it.modules() }.size)
}

@Test fun `closing one module keeps sibling live`() {
    val plugin = registry().register(Any())
    val first = plugin.registerModule("first") { }
    val second = plugin.registerModule("second") { }
    first.close()
    assertTrue(first.isClosed)
    assertFalse(second.isClosed)
    assertSame(second, plugin.getModule("second"))
}
```

Also add tests for duplicate owner identity, duplicate local module ID, unsupported owner, rollback invisibility, plugin close, platform unregister, and registry close rejection.

- [ ] **Step 2: Run the core tests and confirm compilation/test failure**

Run: `./gradlew :modules:core:test --tests '*PluginRegistryImplTest'`

Expected: FAIL because `PluginRegistryImpl` still implements the removed flat API.

- [ ] **Step 3: Introduce physical and module implementation objects**

Use these storage boundaries:

```kotlin
private val plugins = IdentityHashMap<Any, PhysicalContext>()

private inner class PhysicalContext(
    override val owner: Any,
    private val nativeId: PluginId,
) : PluginContext {
    private val modules = linkedMapOf<ModuleId, Module>()
    private val closed = AtomicBoolean(false)
    // registerModule/getModule/unregisterModule/modules/close
}

private inner class Module(
    private val parent: PhysicalContext,
    override val id: ModuleId,
    private val serviceKey: PluginId,
    // existing capability constructor arguments
) : ModuleContext
```

`register(owner)` validates `platform.acceptsOwner(owner)`, derives the native ID from `ownerDetails`, checks the identity map under synchronization, and publishes a `PhysicalContext` atomically.

- [ ] **Step 4: Move transactional materialization behind `PhysicalContext.registerModule`**

Build a deterministic service key that always fits `PluginId`'s 64-character limit. Hash the full `nativeId + NUL + moduleId` value with SHA-256, retain short readable prefixes, and use the digest suffix for collision resistance:

```kotlin
private fun serviceKey(nativeId: PluginId, moduleId: ModuleId): PluginId {
    val canonical = "${nativeId.value}\u0000${moduleId.value}"
    val hash = MessageDigest.getInstance("SHA-256")
        .digest(canonical.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    val readable = "m-${nativeId.value.take(18)}-${moduleId.value.take(18)}-${hash.take(16)}"
    return PluginId.of(readable)
}
```

Check the local `ModuleId` before materialization; only add the module after `createModule` succeeds. Adapt metadata and component descriptors to expose the local `ModuleId` while internal event/service/placeholder/currency registries receive this composite key.

Owner-scoped command cleanup must run when the physical context closes, not whenever one module closes:

```kotlin
override fun close() = detachPlugin(owner)?.closeInternal()

private fun closeInternal() {
    if (!closed.compareAndSet(false, true)) return
    modules.values.toList().forEach(Module::closeInternal)
    commands?.unregisterOwner(owner)
}
```

- [ ] **Step 5: Run focused tests until green**

Run: `./gradlew :modules:core:test --tests '*PluginRegistryImplTest'`

Expected: PASS, including two owners with `core`, sibling isolation, rollback, and complete cleanup.

- [ ] **Step 6: Commit the core implementation**

```bash
git add modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/plugin/PluginRegistryImpl.kt modules/core/src/test/kotlin/ru/privatenull/pnlibrary/core/plugin/PluginRegistryImplTest.kt
git commit -m "refactor(core): isolate modules under plugin contexts"
```

### Task 3: Migrate lifecycle integration and example consumers

**Files:**
- Modify: `platforms/bukkit/runtime/src/main/kotlin/ru/privatenull/pnlibrary/bukkit/BukkitLifecycleListener.kt`
- Modify: any Bungee and Velocity lifecycle adapters returned by source search for `unregisterOwner`
- Modify: `examples/demo-bukkit/src/main/kotlin/ru/privatenull/pnlibrary/demo/DemoPlugin.kt`
- Modify: Kotlin sources returned by source search for capability-rich `PluginContext`
- Modify: `modules/runtime-spi/src/main/kotlin/ru/privatenull/pnlibrary/spi/platform/PlatformAdapter.kt`

**Interfaces:**
- Consumes: `PluginRegistry.unregister(owner)`, `PluginContext.registerModule`, and `ModuleContext` from Tasks 1-2.
- Produces: platform lifecycle cleanup and a compiling demonstration of the new public API.

- [ ] **Step 1: Update lifecycle and demo code**

```kotlin
private lateinit var pluginContext: PluginContext
lateinit var context: ModuleContext; private set

pluginContext = library.plugins.register(this)
context = pluginContext.registerModule("pndemo") { builder ->
    DemoDeclarations.configure(builder, dataFolder.toPath())
}

override fun onDisable() {
    command?.close()
    pulse?.close()
    localization?.close()
    if (::pluginContext.isInitialized) pluginContext.close()
}
```

Change platform disable handling from `unregisterOwner(event.plugin)` to `unregister(event.plugin)`. Retain `PlatformAdapter.acceptsOwner` because it is the explicit validation boundary.

- [ ] **Step 2: Rename capability-context parameters and imports across source**

Search command: `Get-ChildItem -Recurse -File -Include *.kt | Select-String 'PluginContext|PluginModules|unregisterOwner|plugins\.register'`

For every capability consumer, replace the old type with `ModuleContext`; do not rename variables mechanically when `pluginContext` now specifically means the physical context.

- [ ] **Step 3: Compile all production sources**

Run: `./gradlew compileKotlin`

Expected: PASS with no unresolved `PluginModules`, old register overload, or `unregisterOwner` reference.

- [ ] **Step 4: Commit migrated consumers**

```bash
git add platforms examples modules/runtime-spi modules
git commit -m "refactor: migrate consumers to module contexts"
```

### Task 4: Refresh the API dump and verify the repository

**Files:**
- Modify: `modules/api/api/api.api`
- Modify: documentation snippets found to reference the removed registration API

**Interfaces:**
- Consumes: completed API and implementation from Tasks 1-3.
- Produces: checked-in binary API declaration and repository-wide verification evidence.

- [ ] **Step 1: Locate stale documentation and API references**

Run: `git grep -n -E 'PluginModules|unregisterOwner|plugins\.modules|plugins\.register\([^)]*,|PluginContext' -- ':!**/build/**'`

Expected: only intentional physical `PluginContext` references and implementation internals remain.

- [ ] **Step 2: Regenerate or update the binary API dump using the repository's existing Gradle task**

Run: `./gradlew :modules:api:apiDump`

Expected: `modules/api/api/api.api` exposes the new hierarchy and contains no `PluginModules` or deprecated flat registry overloads.

- [ ] **Step 3: Run focused and repository-wide verification**

Run: `./gradlew :modules:api:test :modules:core:test :examples:demo-bukkit:compileKotlin`

Then run: `./gradlew test`

Expected: both commands exit successfully with all tests passing.

- [ ] **Step 4: Check the final diff for accidental loss of user work**

Run: `git status --short` and `git diff --check`

Expected: no whitespace errors, no unexpected deleted changes, and only intentional architecture migration files remain.

- [ ] **Step 5: Commit generated API and documentation changes**

```bash
git add modules/api/api/api.api docs examples
git commit -m "docs: publish plugin module context API"
```
