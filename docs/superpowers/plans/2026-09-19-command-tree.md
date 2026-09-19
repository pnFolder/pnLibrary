# Recursive Command Tree Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an arbitrary-depth portable command tree with typed arguments and one uniform access predicate, then migrate `/pncurrency` and remove Bukkit command declarations.

**Architecture:** Immutable API nodes describe literals, typed arguments, handlers, suggestions, permissions, and `availableIf`. A core router walks tokens, accumulates typed values, checks every node on the path, and dispatches through the existing single native root registration. Bukkit currency behavior is retained behind portable command handlers and a native player resolver.

**Tech Stack:** Kotlin/JVM, Java-compatible builders, Adventure components, JUnit 5, Gradle 9, Bukkit/BungeeCord/Velocity adapters.

**Spec:** `docs/superpowers/specs/2026-09-19-command-tree-design.md`

## Global Constraints

- Preserve the existing root command builder API and Java interoperability.
- Support alternating literals and arguments without a depth limit.
- Expose only `permission(...)` and `availableIf { Boolean }` as access mechanisms.
- Denied nodes are hidden from suggestions/help and blocked on direct execution.
- Consumer plugins require no platform command metadata.
- Preserve existing `/pncurrency` spellings, permissions, confirmation flow, and output.
- Do not stage or modify pre-existing user changes in API version or Velocity files.

## Review Focus

- An inaccessible literal that shares a prefix with an accessible literal must never leak through completion.
- A predicate exception must be logged and treated as denial, not escape into the platform callback.
- An exact literal must win over a sibling string argument for the same token.
- Duplicate sibling argument routes must fail at build time instead of selecting by insertion order.
- Suggestions after a trailing space must target the next node, while partial input filters the current node.

---

### Task 1: Immutable tree API and typed context

**Files:**
- Create: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/commands/CommandNode.kt`
- Create: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/commands/ArgumentType.kt`
- Modify: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/commands/CommandDefinition.kt`
- Modify: `modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/commands/CommandContext.kt`
- Test: `modules/api/src/test/kotlin/ru/privatenull/pnlibrary/api/commands/CommandTreeBuilderTest.kt`

**Interfaces:**
- Consumes: existing `CommandHandler`, `SuggestionHandler`, `CommandBuilder`, and root `command`.
- Produces: immutable `CommandNode`; `literal(name, configure)`; `argument(name, ArgumentType<T>, configure)`; reified Kotlin `argument<T>` for built-ins; `availableIf`; `CommandContext.get<T>(name)`.

- [ ] **Step 1: Write failing builder tests**

```kotlin
val definition = command("group") {
    argument<String>("group") {
        literal("member") {
            argument<Int>("page") { executes { } }
        }
    }
}
assertEquals("member", definition.root.children.single().children.single().name)
assertThrows<IllegalArgumentException> {
    command("bad") { argument<String>("a") {}; argument<Int>("b") {} }
}
```

- [ ] **Step 2: Run `./gradlew :modules:api:test --tests '*CommandTreeBuilderTest' --no-daemon` and verify compilation/test failure because tree APIs do not exist.**
- [ ] **Step 3: Implement immutable node builders, built-in parsers for String/Int/Long/BigDecimal/Boolean/enum, defensive child copies, sibling validation, access predicates, and typed parsed-value lookup.**
- [ ] **Step 4: Run API tests and `:modules:api:apiDump`, then `:modules:api:apiCheck`; verify green.**
- [ ] **Step 5: Commit `feat(api): add recursive command tree` with only Task 1 files and the API dump.**

### Task 2: Core router, access, usage, and suggestions

**Files:**
- Create: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/commands/CommandTreeRouter.kt`
- Create: `modules/core/src/test/kotlin/ru/privatenull/pnlibrary/core/commands/CommandTreeRouterTest.kt`
- Modify: `modules/core/src/main/kotlin/ru/privatenull/pnlibrary/core/commands/CommandServiceImpl.kt`
- Modify: `modules/core/src/test/kotlin/ru/privatenull/pnlibrary/core/commands/CommandServiceImplTest.kt`

**Interfaces:**
- Consumes: Task 1 `CommandNode`, `ArgumentType.parse`, node permission/predicate, parsed context values.
- Produces: `CommandTreeRouter.execute(definition, context)` and `suggest(definition, context)` used by `CommandServiceImpl`.

- [ ] **Step 1: Write failing router tests for mixed-depth traversal, literal precedence, typed parsing, trailing-space completion, partial completion, inherited permission, inherited `availableIf`, direct denial, predicate exceptions, and generated usage.**
- [ ] **Step 2: Run `./gradlew :modules:core:test --tests '*CommandTreeRouterTest' --no-daemon`; verify failures identify missing router behavior.**
- [ ] **Step 3: Implement token traversal that copies context with parsed values, evaluates ancestor-to-leaf access, treats predicate exceptions as denied failures, dispatches the deepest exact executable node, and produces usage from accessible children.**
- [ ] **Step 4: Route `CommandServiceImpl.execute/suggest` through the tree while preserving legacy root handlers and existing sanitized error logging.**
- [ ] **Step 5: Run focused core command tests and verify green.**
- [ ] **Step 6: Commit `feat(core): route recursive command trees`.**

### Task 3: Migrate the pnLibrary control command

**Files:**
- Modify: `platforms/bukkit/runtime/src/main/kotlin/ru/privatenull/pnlibrary/bukkit/commands/BukkitControlCommand.kt`
- Modify: `platforms/bukkit/runtime/src/test/kotlin/ru/privatenull/pnlibrary/bukkit/commands/BukkitControlCommandTest.kt`

**Interfaces:**
- Consumes: Task 1 literals/arguments and Task 2 router.
- Produces: `/pn` routes represented as nodes while retaining Bukkit-native restart and rich-message operations in handlers.

- [ ] **Step 1: Write failing tests proving `status`, `update <plugin>`, `restart confirm`, error count suggestions, and permission-hidden branches are present in the tree.**
- [ ] **Step 2: Run the focused Bukkit control tests and verify failure against the current manual argument switch.**
- [ ] **Step 3: Replace manual first-token dispatch with tree nodes and contextual suggestions; keep behavior methods unchanged.**
- [ ] **Step 4: Run Bukkit command and core router tests; verify green.**
- [ ] **Step 5: Commit `refactor(bukkit): describe control command as tree`.**

### Task 4: Migrate `/pncurrency`

**Files:**
- Create: `platforms/bukkit/runtime/src/main/kotlin/ru/privatenull/pnlibrary/bukkit/currency/BukkitCurrencyCommand.kt`
- Create: `platforms/bukkit/runtime/src/test/kotlin/ru/privatenull/pnlibrary/bukkit/currency/BukkitCurrencyCommandTest.kt`
- Modify: `platforms/bukkit/runtime/src/main/kotlin/ru/privatenull/pnlibrary/bukkit/PnLibraryBukkitPlugin.kt`
- Delete: `platforms/bukkit/runtime/src/main/kotlin/ru/privatenull/pnlibrary/bukkit/currency/CurrencyCommandExecutor.kt`

**Interfaces:**
- Consumes: tree API/router, `CurrencyProviderRegistry`, native Bukkit sender held by `BukkitCommandSender`, and existing currency APIs.
- Produces: `BukkitCurrencyCommand.definition()` registered through `library.commands` with routes for help/list/confirm/cancel and `<currency> <balance|add|take|set|reset|pay|history>`.

- [ ] **Step 1: Write failing route tests covering dynamic currency suggestions, operation filtering by generated permission, player/amount continuation, disabled currencies, console confirmation, and preservation of asynchronous result handling.**
- [ ] **Step 2: Run focused currency tests and verify failure because no portable currency definition exists.**
- [ ] **Step 3: Extract the existing business operations into portable handlers; expose native sender only through an internal Bukkit bridge; express operation paths with literals/arguments and `availableIf` checks.**
- [ ] **Step 4: Register the definition during Bukkit startup and rely on owner lifecycle for shutdown; remove direct `getCommand` executor/tab-completer wiring.**
- [ ] **Step 5: Run Bukkit runtime and core command tests; verify green.**
- [ ] **Step 6: Commit `refactor(bukkit): migrate currency command to command tree`.**

### Task 5: Metadata, documentation, compatibility, and release verification

**Files:**
- Modify: `platforms/bukkit/runtime/src/main/resources/plugin.yml`
- Modify: `docs/COMMANDS.md`
- Modify: `README.md` only if its command example needs a new link or syntax.
- Test: existing adapter, API, core, and platform test suites.

**Interfaces:**
- Consumes: all prior tasks.
- Produces: metadata-free dynamic registration and documented Kotlin/Java tree examples.

- [ ] **Step 1: Add/adjust an adapter integration test proving an undeclared Bukkit root is dynamically registered and unregistered.**
- [ ] **Step 2: Run it and verify it fails if the code still depends on `JavaPlugin.getCommand`.**
- [ ] **Step 3: Remove the entire `commands:` section from `plugin.yml`; document recursive routes, typed context, permission, and `availableIf`.**
- [ ] **Step 4: Run `./gradlew clean test apiCheck :distribution:build --warning-mode all --no-daemon`, temporarily isolating and byte-for-byte restoring only the known user-owned API version edit if it still conflicts with its baseline test.**
- [ ] **Step 5: Inspect generated artifacts, remove test-only output, confirm only user-owned changes remain unstaged, and commit `docs: document recursive portable commands`.**
- [ ] **Step 6: Push `feat/pnlibrary-architecture-pnupdate` and verify remote HEAD equals local HEAD.**
