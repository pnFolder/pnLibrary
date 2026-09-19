# Cross-platform Command System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let plugins define execution and suggestions once with a Kotlin/Java-friendly builder while pnLibrary automatically registers and removes the command through the active Bukkit, BungeeCord, or Velocity adapter.

**Architecture:** Public immutable command contracts live in `modules/api`; native registration contracts live in `modules/runtime-spi`; `CommandServiceImpl` in core validates permissions, ownership, execution, suggestions, rollback, and lifecycle. Each platform runtime implements only native registration and sender translation, and pnLibrary's `/pndebug` definition moves into shared core code.

**Tech Stack:** Kotlin, Java 8 API/core/Bukkit/Bungee bytecode, Java 17 Velocity bytecode, Adventure `Component`, `CompletionStage`, JUnit 5, Mockito Kotlin, Gradle Kotlin DSL.

**Spec:** `docs/superpowers/specs/2026-09-19-cross-platform-command-system-design.md`

## Global Constraints

- Preserve the public artifact names and the existing `modules/*`, `platforms/*`, and `distribution` Gradle hierarchy.
- Keep `modules:api`, `modules:core`, `modules:runtime-spi`, Bukkit, and BungeeCord compatible with Java 8 bytecode; Velocity remains Java 17.
- A portable command definition must contain no Bukkit, BungeeCord, or Velocity type.
- Native adapters must contain no `/pndebug` parsing, diagnostic permission policy, diagnostic messages, or diagnostic suggestions.
- Permission checks apply to both execution and suggestions; console bypass is opt-in.
- Registration, individual close, owner close, and service close must be transactional or idempotent as applicable.
- Preserve existing uncommitted user edits; stage only files owned by the current task.
- Do not migrate `CurrencyCommandExecutor` in this plan.

## Review Focus

- Names differing only by case or whitespace must normalize deterministically and invalid names must fail before native registration; covered in Task 1 API tests and Task 2 service tests.
- Aliases colliding with another command's primary name or alias must fail without leaking a native registration; covered in Task 2 collision tests.
- An unauthorized sender must receive no suggestions and must never reach the execution handler; covered in Task 2 permission tests.
- Closing a registration, its owner, and then the whole runtime in any order must unregister natively exactly once; covered in Task 2 lifecycle tests.
- A handler or asynchronous suggestion failure must be logged and converted to safe empty suggestions or a safe sender error, without exposing the exception text; covered in Task 2 failure tests.

---

### Task 1: Public command model and builder

**Files:**
- Create: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/commands/CommandDefinition.kt`
- Create: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/commands/CommandContext.kt`
- Create: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/commands/CommandSender.kt`
- Create: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/commands/CommandService.kt`
- Create: `modules/api/src/test/kotlin/ru/privatenull/pnlibrary/api/commands/CommandBuilderTest.kt`
- Modify: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/runtime/PnLibrary.kt`

**Interfaces:**
- Consumes: Adventure `Component`; JDK `CompletionStage`, `CompletableFuture`, `AutoCloseable`, and `Any` owner identity.
- Produces: `CommandDefinition`, `CommandContext`, `CommandSender`, `CommandService`, `CommandRegistration`, `command(name, configure)`, `CommandHandler`, and `SuggestionHandler`.

- [ ] **Step 1: Write failing builder and validation tests**

```kotlin
class CommandBuilderTest {
    @Test fun `builder creates immutable normalized command`() {
        val definition = command("  Hello ") {
            aliases("HI", "hello-there")
            permission("example.hello")
            consoleBypassesPermission()
            executes { sender.send(Component.text(arguments.joinToString())) }
            suggests { completedSuggestions(listOf("world")) }
        }
        assertEquals("hello", definition.name)
        assertEquals(setOf("hi", "hello-there"), definition.aliases)
        assertEquals("example.hello", definition.permission)
        assertTrue(definition.consoleBypassesPermission)
    }

    @Test fun `blank and malformed names are rejected`() {
        assertThrows<IllegalArgumentException> { command(" ") {} }
        assertThrows<IllegalArgumentException> { command("bad name") {} }
        assertThrows<IllegalArgumentException> { command("good") { aliases("bad alias") } }
    }

    @Test fun `primary name cannot also be an alias`() {
        assertThrows<IllegalArgumentException> { command("hello") { aliases("HELLO") } }
    }
}
```

- [ ] **Step 2: Run the API test and verify that it fails because command contracts do not exist**

Run: `./gradlew :modules:api:test --tests '*CommandBuilderTest'`

Expected: compilation failure for unresolved `api.commands` symbols.

- [ ] **Step 3: Add the immutable model, functional handlers, context, sender, and builder**

```kotlin
fun interface CommandHandler {
    fun execute(context: CommandContext): CompletionStage<Void>
}

fun interface SuggestionHandler {
    fun suggest(context: CommandContext): CompletionStage<List<String>>
}

data class CommandContext(
    val sender: CommandSender,
    val arguments: List<String>,
    val invokedAlias: String,
    val currentInput: String,
)

interface CommandSender {
    val id: String
    val name: String
    val isConsole: Boolean
    fun hasPermission(permission: String): Boolean
    fun send(message: Component)
}

interface CommandService {
    fun register(owner: Any, command: CommandDefinition): CommandRegistration
    fun unregisterOwner(owner: Any)
}

interface CommandRegistration : AutoCloseable {
    val command: CommandDefinition
    val isClosed: Boolean
    override fun close()
}
```

Implement `CommandBuilder` so `executes` and `suggests` accept Java-friendly functional interfaces, default execution returns a completed `Void` stage, default suggestions return an empty completed list, names match `[a-z0-9][a-z0-9:_-]*`, collections are defensive immutable copies, and `PnLibrary` exposes `val commands: CommandService`.

- [ ] **Step 4: Run API tests and API compatibility validation**

Run: `./gradlew :modules:api:test :modules:api:apiCheck`

Expected: tests pass; `apiCheck` reports the intentional new public declarations until the API dump is updated.

- [ ] **Step 5: Update the checked-in API dump with the new command surface and commit**

Run: `./gradlew :modules:api:apiDump :modules:api:apiCheck`

Expected: both tasks pass.

Commit only Task 1 files with message: `feat(api): add portable command builder`.

### Task 2: Runtime SPI and shared command service

**Files:**
- Create: `modules/runtime-spi/src/main/kotlin/ru/privatenull/pnlibrary/spi/commands/PlatformCommandAdapter.kt`
- Modify: `modules/runtime-spi/src/main/kotlin/ru/privatenull/pnlibrary/spi/platform/PlatformAdapter.kt`
- Create: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/commands/CommandServiceImpl.kt`
- Create: `modules/core/src/test/kotlin/ru/privatenull/pnlibrary/core/commands/CommandServiceImplTest.kt`
- Modify: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/runtime/PnLibraryImpl.kt`
- Modify: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/plugin/PluginRegistryImpl.kt`
- Modify: `modules/core/src/test/kotlin/ru/privatenull/pnlibrary/core/plugin/PluginRegistryImplTest.kt`

**Interfaces:**
- Consumes: Task 1 command contracts, `PlatformAdapter.log`, and owner identity.
- Produces: `PlatformCommandAdapter.register(owner, definition, dispatcher)`, `PlatformCommandRegistration`, and `CommandServiceImpl`.

- [ ] **Step 1: Write service tests with a recording native adapter**

```kotlin
private class RecordingCommands : PlatformCommandAdapter {
    val registered = mutableListOf<CommandDefinition>()
    var unregisterCalls = 0
    override fun register(
        owner: Any,
        command: CommandDefinition,
        dispatcher: PlatformCommandDispatcher,
    ): PlatformCommandRegistration {
        registered += command
        return PlatformCommandRegistration { unregisterCalls++ }
    }
}
```

Add tests proving: successful dispatch, case-normalized collisions across names and aliases, rollback after native failure, denial before handler invocation, console bypass only when configured, denied suggestions are empty, synchronous handler failure sends `Component.text("Command execution failed.")`, failed suggestions become empty, registration close is idempotent, owner close unregisters all owned commands once, and service close rejects new registrations.

- [ ] **Step 2: Run the focused core test and verify it fails**

Run: `./gradlew :modules:core:test --tests '*CommandServiceImplTest'`

Expected: compilation failure because SPI and implementation are absent.

- [ ] **Step 3: Add the SPI boundary and service implementation**

```kotlin
interface PlatformCommandAdapter : AutoCloseable {
    fun register(
        owner: Any,
        command: CommandDefinition,
        dispatcher: PlatformCommandDispatcher,
    ): PlatformCommandRegistration
}

interface PlatformCommandDispatcher {
    fun execute(command: CommandDefinition, context: CommandContext): CompletionStage<Void>
    fun suggest(command: CommandDefinition, context: CommandContext): CompletionStage<List<String>>
}

fun interface PlatformCommandRegistration : AutoCloseable
```

Add `val commandAdapter: PlatformCommandAdapter` to `PlatformAdapter`. `CommandServiceImpl` must reserve the primary name and every alias before native registration, roll reservations back on failure, use an `IdentityHashMap<Any, MutableSet<Registration>>` for ownership, check permission for both dispatcher methods, catch synchronous exceptions, sanitize asynchronous failures, log the original error, and close registrations once.

- [ ] **Step 4: Compose command lifecycle into pnLibrary and plugin lifecycle**

Construct `CommandServiceImpl(platform.commandAdapter, platform)` before `PluginRegistryImpl`; expose it as `PnLibrary.commands`; pass it into `PluginRegistryImpl`; call `commands.unregisterOwner(owner)` from `Context.closeInternal()`; and close the command service before the platform adapter during `PnLibraryImpl.close()`.

- [ ] **Step 5: Run focused and core tests**

Run: `./gradlew :modules:core:test --tests '*CommandServiceImplTest' --tests '*PluginRegistryImplTest'`

Expected: all selected tests pass, including exact-once unregister behavior when plugin context and runtime close overlap.

- [ ] **Step 6: Commit the shared engine**

Commit only Task 2 files with message: `feat(core): manage cross-platform commands`.

### Task 3: Shared `/pndebug` command definition

**Files:**
- Create: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/diagnostics/DiagnosticCommand.kt`
- Create: `modules/core/src/test/kotlin/ru/privatenull/pnlibrary/core/diagnostics/DiagnosticCommandTest.kt`
- Modify: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/diagnostics/DiagnosticCommandExecutor.kt`
- Modify: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/runtime/PnLibraryImpl.kt`

**Interfaces:**
- Consumes: `CommandDefinition`, `CommandService`, `DiagnosticCommandExecutor`, `DiagnosticCommandEvent`, and plugin registry snapshots.
- Produces: one immutable `diagnosticCommand(library)` definition registered by core for the pnLibrary owner.

- [ ] **Step 1: Write diagnostic definition tests**

Build the definition with a fake `PnLibrary` and assert name `pndebug`, alias `pnlib`, permission `pnlibrary.debug`, console bypass enabled, suggestions for `all`, registered plugin IDs, and four flags filtered by `currentInput`. Execute invalid and successful requests and assert shared Adventure messages rather than native strings.

- [ ] **Step 2: Run the diagnostic command tests and verify they fail**

Run: `./gradlew :modules:core:test --tests '*DiagnosticCommandTest'`

Expected: failure because `DiagnosticCommand` does not exist.

- [ ] **Step 3: Implement the complete portable definition**

```kotlin
internal fun diagnosticCommand(library: PnLibrary): CommandDefinition = command("pndebug") {
    aliases("pnlib")
    permission("pnlibrary.debug")
    consoleBypassesPermission()
    suggests { context -> completedSuggestions(diagnosticSuggestions(library, context.currentInput)) }
    executes { context ->
        DiagnosticCommandExecutor(library).execute(
            context.arguments.toTypedArray(),
            prefixed = context.invokedAlias.equals("pnlib", true),
            requesterId = context.sender.id,
            recipient = context.sender,
        ) { event -> context.sender.send(event.toComponent()) }
        completedExecution()
    }
}
```

Move all event-to-message rendering into `DiagnosticCommand.kt`. Keep report creation/cooldown in `DiagnosticCommandExecutor`, but stop making adapters depend on `DiagnosticCommandEvent`.

- [ ] **Step 4: Register the definition from core startup and verify tests**

Register it exactly once for `library.owner` after `PnLibraryImpl` is fully composed and before native `bind` returns. Run: `./gradlew :modules:core:test --tests '*DiagnosticCommand*'`.

Expected: existing executor tests and new definition tests pass.

- [ ] **Step 5: Commit the portable diagnostics command**

Commit only Task 3 files with message: `refactor: define diagnostics command once`.

### Task 4: Bukkit native adapter and `/pn` migration

**Files:**
- Create: `platforms/bukkit/runtime/src/main/kotlin/ru/privatenull/pnlibrary/bukkit/commands/BukkitCommandAdapter.kt`
- Create: `platforms/bukkit/runtime/src/test/kotlin/ru/privatenull/pnlibrary/bukkit/commands/BukkitCommandAdapterTest.kt`
- Create: `platforms/bukkit/runtime/src/main/kotlin/ru/privatenull/pnlibrary/bukkit/commands/BukkitControlCommand.kt`
- Create: `platforms/bukkit/runtime/src/test/kotlin/ru/privatenull/pnlibrary/bukkit/commands/BukkitControlCommandTest.kt`
- Modify: `platforms/bukkit/runtime/src/main/kotlin/ru/privatenull/pnlibrary/bukkit/BukkitPlatformAdapter.kt`
- Modify: `platforms/bukkit/runtime/src/main/kotlin/ru/privatenull/pnlibrary/bukkit/BukkitCommandController.kt`
- Modify: `platforms/bukkit/runtime/src/main/kotlin/ru/privatenull/pnlibrary/bukkit/PnLibraryBukkitPlugin.kt`

**Interfaces:**
- Consumes: Task 2 SPI dispatcher and Task 3 portable diagnostics command.
- Produces: Bukkit registration, sender bridge, synchronous completion bridge, and a `/pn` definition built with the shared API. The `/pn` behavior stays in the Bukkit module because server restart, online-player broadcast, and join notification are genuinely Bukkit-specific; it does not move into the native registration adapter.

- [ ] **Step 1: Write Bukkit adapter tests around mocked `CommandMap` and senders**

Cover declared command reuse, dynamic command registration, alias propagation, invoked label propagation, execution dispatch, tab completion dispatch, Adventure message delivery through `BukkitAudienceService`, and exact-once dynamic unregistration.

- [ ] **Step 2: Run the focused Bukkit tests and verify they fail**

Run: `./gradlew :platforms:bukkit:runtime:test --tests '*BukkitCommandAdapterTest'`

Expected: compilation failure because the adapter is absent.

- [ ] **Step 3: Implement the Bukkit bridge**

The adapter creates a `PluginCommand`, or reuses a matching declaration, and installs one `CommandExecutor`/`TabCompleter` delegate that constructs:

```kotlin
CommandContext(
    sender = BukkitCommandSender(nativeSender, audiences),
    arguments = args.toList(),
    invokedAlias = label,
    currentInput = args.lastOrNull().orEmpty(),
)
```

Wait only for suggestion completion because Bukkit's legacy tab-completion contract is synchronous; bound the wait to one second and return an empty list on failure. Execution completion is never blocked. `BukkitCommandSender` keeps its native sender privately and the Bukkit runtime unwraps it only inside `executeReply`, so Folia player replies still use the entity scheduler without exposing native types through public API.

- [ ] **Step 4: Write and implement the portable `/pn` tests and definition**

Move `status`, `updates`, `check`, `update`, `restart`, `debug`, `support`, `error`, `error-repeat`, and `error-chain` behavior and suggestions from `BukkitCommandController` into `BukkitControlCommand`. The class returns a shared `CommandDefinition`, while its injected Bukkit operations perform restart and player broadcast. Use Adventure click and hover events for restart, check, update, and support actions. Preserve `pnlibrary.admin`, confirmation expiry, update state text, and the current command spelling.

- [ ] **Step 5: Narrow the old Bukkit controller**

Remove diagnostics and `/pn` execution/completion/registration from `BukkitCommandController`. Retain only Bukkit listeners that react to administrator join and plugin disable; rename it to `BukkitLifecycleListener` if no command interface remains. Construct `BukkitCommandAdapter` once in `BukkitPlatformAdapter`, expose it as `commandAdapter`, and remove command registration from `bind`.

- [ ] **Step 6: Run Bukkit and core command tests**

Run: `./gradlew :platforms:bukkit:runtime:test :modules:core:test --tests '*Command*'`

Expected: adapter, `/pn`, `/pndebug`, and pre-existing command executor tests pass.

- [ ] **Step 7: Commit Bukkit migration**

Commit only Task 4 files with message: `refactor(bukkit): use shared command system`.

### Task 5: BungeeCord native adapter

**Files:**
- Create: `platforms/bungee/runtime/src/main/kotlin/ru/privatenull/pnlibrary/bungee/commands/BungeeCommandAdapter.kt`
- Create: `platforms/bungee/runtime/src/test/kotlin/ru/privatenull/pnlibrary/bungee/commands/BungeeCommandAdapterTest.kt`
- Modify: `platforms/bungee/runtime/src/main/kotlin/ru/privatenull/pnlibrary/bungee/BungeePlatformAdapter.kt`

**Interfaces:**
- Consumes: Task 2 SPI and shared `/pndebug` definition.
- Produces: BungeeCord native registration and sender bridge.

- [ ] **Step 1: Write failing BungeeCord adapter tests**

Mock `PluginManager` and assert name/aliases are registered, `CommandSender` identity/permission/console status are translated, execution reaches the dispatcher with the invoked name, Adventure text is serialized safely for legacy BungeeCord, and close unregisters once.

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `./gradlew :platforms:bungee:runtime:test --tests '*BungeeCommandAdapterTest'`

Expected: compilation failure because the adapter is absent.

- [ ] **Step 3: Implement registration, sender translation, and completion behavior**

Use a native `Command` delegate for execution. When the available BungeeCord API supports `TabExecutor`, implement it and bridge suggestions with the same one-second bounded wait used by Bukkit; otherwise return an empty native completion without changing portable execution.

- [ ] **Step 4: Remove diagnostics behavior from `BungeePlatformAdapter`**

Delete its `DiagnosticCommandExecutor`, native `debugCommand`, event message renderer, registration in `bind`, and unregistration in `close`. Expose the constructed `BungeeCommandAdapter` through `commandAdapter` and let core own registration lifecycle.

- [ ] **Step 5: Run and commit**

Run: `./gradlew :platforms:bungee:runtime:test :platforms:bungee:runtime:compileKotlin`.

Expected: tests and compilation pass. Commit Task 5 files with message: `refactor(bungee): use shared command system`.

### Task 6: Velocity native adapter

**Files:**
- Create: `platforms/velocity/runtime/src/main/kotlin/ru/privatenull/pnlibrary/velocity/commands/VelocityCommandAdapter.kt`
- Create: `platforms/velocity/runtime/src/test/kotlin/ru/privatenull/pnlibrary/velocity/commands/VelocityCommandAdapterTest.kt`
- Modify: `platforms/velocity/runtime/src/main/kotlin/ru/privatenull/pnlibrary/velocity/VelocityPlatformAdapter.kt`

**Interfaces:**
- Consumes: Task 2 SPI and shared `/pndebug` definition.
- Produces: Velocity native registration, asynchronous suggestions, and Adventure-native sender bridge.

- [ ] **Step 1: Write failing Velocity adapter tests**

Mock `CommandManager` and assert metadata contains aliases and plugin ownership, execution constructs the portable context, `suggestAsync` returns the dispatcher's stage without blocking, `Component` replies are sent unchanged, and close unregisters once by metadata/name.

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `./gradlew :platforms:velocity:runtime:test --tests '*VelocityCommandAdapterTest'`

Expected: compilation failure because the adapter is absent.

- [ ] **Step 3: Implement the native adapter**

Register one `SimpleCommand` per portable definition. Its `execute` delegates immediately; its `suggestAsync` maps the portable `CompletionStage<List<String>>` to `CompletableFuture<List<String>>`; sender replies use Velocity's Adventure-native `CommandSource.sendMessage(Component)`.

- [ ] **Step 4: Remove diagnostics behavior from `VelocityPlatformAdapter` without overwriting user edits**

Keep the user's current logger refactor and formatting edits. Delete only diagnostic imports, the `/pndebug` meta/delegate in `bind`, the event renderer, and name-based unregister code. Expose `VelocityCommandAdapter` through `commandAdapter` and let core lifecycle close it.

- [ ] **Step 5: Run and commit**

Run: `./gradlew :platforms:velocity:runtime:test :platforms:velocity:runtime:compileKotlin`.

Expected: tests and compilation pass. Commit only Task 6-owned hunks with message: `refactor(velocity): use shared command system`; do not stage the user's unrelated Velocity hunks.

### Task 7: Documentation, compatibility, and whole-build verification

**Files:**
- Create: `docs/COMMANDS.md`
- Modify: `README.md`
- Modify: API dump files generated by `:modules:api:apiDump` if Task 1 did not already capture final signatures.

**Interfaces:**
- Consumes: completed public API and all native adapters.
- Produces: consumer documentation and verified distribution artifacts.

- [ ] **Step 1: Document Kotlin and Java registration examples**

Document `library.commands.register(owner, command("name") { ... })`, permission and console behavior, synchronous and asynchronous suggestions, `CommandRegistration.close()`, owner-driven cleanup, Adventure replies, name collision errors, and the absence of native sender types in the portable context.

- [ ] **Step 2: Run formatting and repository consistency checks**

Run: `git diff --check` and `rg -n "DiagnosticCommandExecutor|DiagnosticCommandEvent|pndebug" platforms -g '*.kt'`.

Expected: no whitespace errors; platform hits are limited to metadata/resources or tests explicitly proving native registration, with no diagnostics execution or rendering in platform adapters.

- [ ] **Step 3: Protect the user's API version edit while running verification**

Record `git status --short`. Temporarily stash only `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/version/PnLibraryApi.kt` if its user-owned `VERSION = 1` change is still present, run verification, and restore that exact stash immediately afterward. Do not stash or stage unrelated user files.

- [ ] **Step 4: Run the full build**

Run: `./gradlew clean test apiCheck :distribution:build --warning-mode all`.

Expected: exit code 0; all command tests, API validation, compilation targets, and distribution packaging pass.

- [ ] **Step 5: Inspect packaged output and final diff**

Run: `git status --short`, `git diff --stat`, and inspect the distribution JAR task outputs. Confirm user-owned `PnLibraryApi.kt` and unrelated Velocity edits remain unstaged and unchanged relative to their pre-task content.

- [ ] **Step 6: Commit documentation and final generated compatibility files**

Commit only Task 7 files with message: `docs: document cross-platform commands`.

- [ ] **Step 7: Perform review before push**

Invoke `superpowers:requesting-code-review`, address verified findings, rerun the affected focused tests plus the full build, then push the completed branch only after the user-requested review and verification are clean.
