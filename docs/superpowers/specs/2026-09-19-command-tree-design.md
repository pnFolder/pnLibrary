# Recursive command tree design

## Goal

Extend pnLibrary's portable command API with an arbitrary-depth command tree. A plugin describes
the command once; pnLibrary parses it, filters suggestions, validates access, and sends it through
the existing Bukkit, BungeeCord, or Velocity adapter. Consumer plugins do not declare commands in
platform metadata.

## Public model

The existing root `command`, `executes`, `suggests`, and async variants remain source compatible.
Every command node may contain any number of literal and typed argument children, so literals and
arguments may alternate without a depth limit.

```kotlin
command("group") {
    argument<String>("group") {
        suggests { context -> groups.visibleTo(context.sender.id) }

        literal("member") {
            argument<String>("player") {
                suggests { context -> groups.members(context["group"]) }

                literal("role") {
                    argument<String>("role") {
                        executes { context ->
                            groups.setRole(
                                context["group"],
                                context["player"],
                                context["role"],
                            )
                        }
                    }
                }
            }
        }
    }
}
```

The first implementation supplies argument types needed by pnLibrary commands: `String`, `Int`,
`Long`, `BigDecimal`, `Boolean`, and enums. Invalid values produce an explanatory error and the
usage for the matched branch. Parsed values are stored by argument name and exposed through typed
context access.

## Access

There are exactly two access mechanisms:

- `permission("node")` uses the sender's platform permission provider.
- `availableIf { context -> Boolean }` is an arbitrary synchronous check owned by the caller.

Both may be attached to any node and inherit from its ancestors. A node for which either check is
false is absent from suggestions and generated help, cannot reveal descendant suggestions, and
cannot be executed by typing it manually. Execution repeats all checks; suggestion filtering is
not treated as security. Permission checks remain a convenience over the same allow/deny model.

The context passed to a check contains the values parsed before that node. A check cannot read a
later argument. A denied direct invocation uses the command service's standard denial message and
does not reveal whether permission or the custom predicate rejected it.

## Parsing and suggestions

The core walks the tree one token at a time. Exact accessible literal matches take precedence over
argument nodes. Ambiguous sibling argument routes are rejected while building the definition.
Suggestions are requested from the children of the last fully matched node and filtered by each
child's inherited access rules. Argument suggestion callbacks receive all values parsed earlier in
the path.

An executable node may have children: executing the exact path invokes its handler, while another
token continues into a child. Missing or unmatched tokens return the generated usage for the
deepest matched accessible node.

## Currency migration and metadata

`/pncurrency` moves from the Bukkit `CurrencyCommandExecutor` to the portable tree. Currency
storage and business rules remain in the shared currency services; Bukkit-only player lookup is
provided through a small injected resolver. The migration preserves existing command spellings,
permissions, confirmation flow, and output behavior.

Bundled `pn`, `pndebug`, and `pncurrency` commands are registered only through `CommandService`.
The Bukkit `plugin.yml` command section is removed. BungeeCord and Velocity continue to require no
command declarations.

## Lifecycle and failures

The existing owner registration owns the complete root tree. Closing it unregisters the one native
root command and all routes beneath it. Exceptions from user predicates, parsers, suggestion
providers, or handlers are contained and logged through the existing command failure path.

## Verification

Tests cover unlimited mixed literal/argument traversal, typed values, literal precedence, route
ambiguity, context-aware suggestions, permission and `availableIf` inheritance, hidden descendants,
manual denial, generated usage, lifecycle cleanup, and the migrated currency routes. The final
verification runs the complete test suite, API validation, and all three distribution builds.
