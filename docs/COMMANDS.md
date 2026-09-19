# Cross-platform commands

pnLibrary registers one command definition through the native adapter of the active Bukkit,
BungeeCord, or Velocity runtime. Command behavior and suggestions therefore live in one place and
do not import a server-platform API.

## Kotlin

```kotlin
import net.kyori.adventure.text.Component
import ru.privatenull.pnlibrary.api.commands.command

val registration = library.commands.register(plugin, command("hello") {
    aliases("hi")
    permission("example.hello")

    suggests { context ->
        listOf("world", "server").filter {
            it.startsWith(context.currentInput, ignoreCase = true)
        }
    }

    executes { context ->
        val target = context.arguments.firstOrNull() ?: "world"
        context.sender.send(Component.text("Hello, $target!"))
    }
})
```

Commands may contain literals and typed arguments at any depth. Suggestions on a later argument
receive every value parsed earlier in the selected route:

```kotlin
library.commands.register(plugin, command("group") {
    argument("group", ArgumentType.string()) {
        suggests { groups.visibleTo(it.sender.id) }

        literal("member") {
            argument("player", ArgumentType.string()) {
                suggests { context -> groups.members(context.get("group")) }

                literal("role") {
                    argument("role", ArgumentType.string()) {
                        permission("groups.role.set")
                        availableIf { context ->
                            groups.canManage(context.sender.id, context.get("group"))
                        }
                        executes { context ->
                            groups.setRole(
                                context.get("group"),
                                context.get("player"),
                                context.get("role"),
                            )
                        }
                    }
                }
            }
        }
    }
})
```

The tree has no depth limit. Exact literals take precedence over argument nodes. Built-in argument
types cover strings, integers, longs, decimals, booleans, and enums; custom `ArgumentType<T>`
implementations may parse other single-token values.

`permission` and `availableIf` may be placed on any node and are inherited through the selected
path. When either check returns false, that node and its descendants are absent from completion and
help and cannot be invoked by typing them manually. `availableIf` receives values parsed before its
node and can enforce ownership, feature flags, server state, or any other synchronous yes/no rule.

`executes` and `suggests` are intended for immediate work. Use `executesAsync` or
`suggestsAsync` when the handler already returns a `CompletionStage`:

```kotlin
val lookup = command("lookup") {
    suggestsAsync { context -> repository.suggest(context.currentInput) }
    executesAsync { context -> repository.execute(context.arguments) }
}
```

The command service checks `permission` before execution and before producing suggestions. Console
senders obey the same rule unless the definition calls `consoleBypassesPermission()`. A denied
sender cannot observe protected suggestions.

## Java

```java
import java.util.Arrays;
import net.kyori.adventure.text.Component;
import ru.privatenull.pnlibrary.api.commands.CommandDefinition;
import ru.privatenull.pnlibrary.api.commands.CommandRegistration;

CommandDefinition hello = CommandDefinition.builder("hello")
    .aliases("hi")
    .permission("example.hello")
    .suggests(context -> Arrays.asList("world", "server"))
    .executes(context -> context.getSender().send(Component.text("Hello!")))
    .build();

CommandRegistration registration = library.getCommands().register(plugin, hello);
```

## Sender and lifecycle

`CommandContext` exposes only portable data: `sender`, arguments, invoked alias, and current
completion input. The sender provides stable identity, display name, console state, permission
checks, and Adventure `Component` replies. Native Bukkit, BungeeCord, and Velocity sender classes
are deliberately absent from the public contract.

The returned `CommandRegistration` may be closed manually and repeated `close()` calls are safe.
pnLibrary also associates every registration with its `owner`: closing the owner's registered
`PluginContext`, calling `unregisterOwner`, or shutting down pnLibrary removes all corresponding
native commands automatically.

Primary names and aliases are normalized to lowercase. Blank names, whitespace inside names, an
alias equal to its primary name, and collisions with another live command fail before native
registration. pnLibrary never silently replaces another command.

The bundled `/pndebug` command uses this API on all supported platforms. Bukkit's `/pn` command is
also a `CommandDefinition`; only its genuinely Bukkit-specific restart and rich-message operations
remain in the Bukkit runtime. `/pncurrency` is also a tree command. None of these commands are
declared in Bukkit `plugin.yml`, BungeeCord `bungee.yml`, or Velocity metadata; each native adapter
registers and removes its root commands dynamically.
