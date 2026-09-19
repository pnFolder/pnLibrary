# Cross-platform command system design

## Goal

pnLibrary must own the lifecycle of commands without embedding command behavior in Bukkit,
BungeeCord, or Velocity runtime classes. A caller creates one platform-neutral command definition
and hands it to pnLibrary. pnLibrary registers execution and suggestions through the adapter of the
active platform and unregisters the command when its scope or the library closes.

The first migrated commands are pnLibrary's own diagnostics commands. The same contracts are
designed to be usable by consumer plugins without importing native platform command APIs.

## Responsibility boundary

The command definition owns all portable behavior:

- primary name and aliases;
- permission policy;
- execution handler;
- suggestion handler;
- portable sender identity and replies.

The command service owns:

- accepting command definitions from pnLibrary and consumer plugins;
- selecting the already-created adapter for the running platform;
- tracking registrations by owner;
- rejecting duplicate or invalid registrations;
- closing registrations in reverse order;
- removing all registrations belonging to a plugin or the pnLibrary runtime.

Each native platform adapter owns only translation:

- registering a native command delegate;
- wrapping the native sender as a portable sender;
- forwarding arguments to the portable handler;
- forwarding completion requests to the portable suggestion handler;
- converting portable replies to the native component/message representation;
- unregistering the native command.

No native adapter may contain `/pndebug` parsing, diagnostics permissions, diagnostics messages, or
diagnostics suggestions.

## Public programming model

A consumer constructs a command and gives it to the command service:

```kotlin
val registration = library.commands.register(plugin, command("hello") {
    aliases("hi")
    permission("example.hello")

    suggests { context ->
        listOf("world").filter { it.startsWith(context.currentInput, ignoreCase = true) }
    }

    executes { context ->
        context.sender.send("Hello, ${context.arguments.firstOrNull() ?: "world"}!")
    }
})
```

The returned registration is closeable. pnLibrary also associates it with `plugin`, so unloading the
owner removes the native registration even if the caller does not close it manually.

The builder is construction syntax only. The resulting `CommandDefinition` is immutable and can be
validated before it reaches a native platform.

## Contracts

`CommandService` is the public entry point exposed by `PnLibrary`. Its minimal surface is:

```kotlin
interface CommandService {
    fun register(owner: Any, command: CommandDefinition): CommandRegistration
    fun unregisterOwner(owner: Any)
}
```

`CommandDefinition` contains immutable metadata and the two portable handlers:

```kotlin
class CommandDefinition internal constructor(
    val name: String,
    val aliases: Set<String>,
    val permission: String?,
    val execution: CommandHandler,
    val suggestions: SuggestionHandler,
)
```

`CommandContext` contains only data common to every supported platform: the portable sender,
arguments, invoked alias, and current completion input. Native sender access is deliberately not
part of the primary API; platform-specific extensions can be added separately if a real use case
requires them.

`PlatformCommandAdapter` belongs to runtime SPI, not public consumer API:

```kotlin
interface PlatformCommandAdapter : AutoCloseable {
    fun register(owner: Any, command: CommandDefinition): PlatformCommandRegistration
}
```

`PlatformAdapter` exposes its command adapter to the core runtime. The core constructs one
`CommandService` and publishes it through `PnLibrary.commands`.

## Permissions and console behavior

Permission policy is evaluated once in the shared command service before either execution or
suggestion generation. Native platform declarations may additionally receive the permission string
as metadata, but correctness must not depend on the native platform enforcing it consistently.

The portable sender reports whether it represents a console. Commands may use a builder option to
allow the console to bypass a permission, but the default is to apply the permission uniformly.
Denied execution produces one shared configurable message rather than three platform-specific
strings.

## Execution and suggestions

Native callbacks enter the shared service. The service creates a context, checks lifecycle and
permission state, and calls the immutable command definition. The definition decides business
behavior; the adapter only transports requests and replies.

Suggestions use the same definition and permission policy as execution. Bukkit's synchronous tab
completion and the proxy platforms' asynchronous APIs are normalized by the service around a
completion-stage result. A synchronous suggestion handler is adapted to an already-completed stage.

## Lifecycle and errors

Registration is transactional: if native registration fails, no shared registration is retained.
Command names and aliases are normalized with a locale-independent lowercase form. A second live
registration for the same name fails clearly instead of silently replacing another plugin's
command.

Closing an individual registration is idempotent. Closing an owner removes all of its commands.
Closing pnLibrary closes the shared service and then the platform adapter. Exceptions from handlers
are caught at the service boundary, logged with the owning plugin, and converted to one safe sender
message.

## Migration

1. Add portable command contracts to the public API and the native registration boundary to the
   runtime SPI.
2. Implement the shared command service in core with unit tests for validation, permissions,
   execution, suggestions, ownership, rollback, and idempotent closing.
3. Implement thin Bukkit, BungeeCord, and Velocity command adapters.
4. Express `/pndebug` once as a `CommandDefinition`; remove its behavior and messages from all three
   platform adapters.
5. Express Bukkit's `/pn` tree through the same system. Platform-only rich click events remain a
   portable message capability or a narrowly scoped optional extension, not command logic inside the
   Bukkit registration adapter.
6. Remove the old command delegates after parity tests pass.

The existing Bukkit currency command is not migrated in the first slice. It remains functional and
becomes a separate follow-up migration after the common command system proves stable.

## Non-goals for the first version

- annotation-based command discovery;
- reflection-based parameter injection;
- a Brigadier-style typed argument tree;
- exposing Bukkit, BungeeCord, or Velocity sender classes through the common API;
- silently taking over command names owned by another plugin.

