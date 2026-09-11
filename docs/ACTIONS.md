# Configured actions

## Model

An action is an immutable configuration value that also knows how to execute.
It contains no Bukkit, BungeeCord, or Velocity class.

```kotlin
data class DepositAction(
    val amount: Double = 0.0,
) : Action {
    override fun execute(context: Action.Context) {
        val economy = context.require(EconomyService::class.java)
        economy.deposit(context.player.uniqueId, amount)
    }
}
```

`Action.Context` supplies the current player, all-player audience, component
service, logger, task scope, and explicitly added typed objects. An action must
not obtain a platform singleton or block the current thread.

## Built-in types

`Action` declares its built-in implementations once:

```kotlin
@ConfigPolymorphic(discriminator = "type")
@ConfigTypes(
    ConfigType(MessageAction::class, "message", aliases = ["messages", "msg"]),
    ConfigType(ActionBarAction::class, "action-bar", aliases = ["actionbar"]),
    ConfigType(SoundAction::class, "sound"),
    ConfigType(ConsoleLogAction::class, "console", aliases = ["log"]),
    ConfigType(DelayAction::class, "delay", aliases = ["later"]),
)
fun interface Action
```

No hand-written action serializer or `when` statement is involved. The generic
configuration codec reads `type`, selects the declared class, and maps its
ordinary fields recursively.

```yaml
actions:
  - type: message
    messages:
      - '<green>Completed'
    target: PLAYER

  - type: sound
    key: minecraft:entity.experience_orb.pickup
    source: MASTER
    volume: 1.0
    pitch: 1.2
    target: PLAYER

  - type: delay
    duration: PT2S
    actions:
      - type: action-bar
        text: '<yellow>Two seconds passed'
```

## Plugin-owned types

A plugin can publish a custom implementation through its own `ConfigScope`.
The scope supplies the owner automatically; callers cannot impersonate another
plugin ID.

Owner-only publication:

```kotlin
val registration = context.configs.type(
    Action::class.java,
    DepositAction::class.java,
    "deposit",
)
```

Its own configuration uses the short name:

```yaml
- type: deposit
  amount: 500.0
```

Selected consumers:

```kotlin
context.configs.type(
    baseType = Action::class.java,
    implementation = DepositAction::class.java,
    name = "deposit",
    aliases = setOf("add-money"),
    priority = 100,
    access = ConfigTypeAccess.builder()
        .allow("pnclans", "pnmarket")
        .allowMatching("pn-*-addon")
        .deny("pn-test")
        .build(),
)
```

Public publication:

```kotlin
context.configs.type(
    Action::class.java,
    DepositAction::class.java,
    "deposit",
    emptySet(),
    100,
    ConfigTypeAccess.global(),
)
```

A consumer refers to another plugin explicitly:

```yaml
- type: pneconomy::deposit
  amount: 500.0
```

The format is `owner::type`. A foreign dynamic type is never selected by a
qualified name belonging to another owner. The access policy is checked before
the type is exposed to the consumer codec.

## Access rules

| Policy | Visible to owner | Visible to others |
|---|---:|---:|
| `ConfigTypeAccess.local()` | yes | no |
| `ConfigTypeAccess.plugins("pnclans")` | yes | exact IDs only |
| `allowMatching("pn-*-addon")` | yes | matching IDs |
| `ConfigTypeAccess.global()` | yes | every registered pnLibrary plugin |

`deny` wins over public, exact allow, and wildcard allow. Patterns are safe glob
expressions, not regular expressions. Only `*` is special.

## Names, aliases, and priority

- `name` is the canonical value written to new YAML.
- `aliases` are accepted while reading old YAML but are never written.
- `priority` resolves intentional overlaps.
- equal-priority matches fail with an ambiguity error instead of selecting a
  provider based on load order.
- names and aliases are normalized to lowercase and validated during
  publication.

One owner cannot publish overlapping names for the same base type. Different
owners may use the same short name because `owner::type` remains unambiguous.

## Lifecycle

Publish dynamic types before loading configurations that use them:

```kotlin
val typeRegistration = context.configs.type(
    Action::class.java,
    DepositAction::class.java,
    "deposit",
)

val settings = context.configs.yaml(
    "config.yml",
    Settings::class.java,
    ::Settings,
)
settings.load()
```

The codec resolves the live registry on every load and reload. Closing an
individual `ConfigTypeRegistration` removes that publication. Closing the
plugin `ConfigScope` removes every publication owned by that plugin and releases
its classes, which is required for safe plugin unloading.

## Configuration-field extensions

For a closed, statically known model, a configuration field can append types
without runtime publication:

```kotlin
class Settings {
    @field:ConfigTypes(
        ConfigType(DepositAction::class, "deposit")
    )
    var actions: List<Action> = emptyList()
}
```

Use field declarations for types compiled into the same plugin. Use dynamic
publication when another plugin must consume the type or when its lifetime must
follow a plugin registration.

## Failure behavior

Configuration loading fails with the full property path when:

- `type` is missing;
- the type is unknown or not visible to the consumer;
- two visible registrations have the same winning priority;
- the implementation does not implement the declared base type;
- the implementation cannot be constructed;
- one of its fields has an invalid value.

Malformed YAML is not replaced. Existing configuration backup and atomic-write
rules still apply.

## Current release boundary

The typed `Action` model and its polymorphic configuration support are the new
API. The older `PlayerAction` execution engine still exists for compatibility
and must not be removed until the typed execution service is wired into every
platform adapter. Do not advertise the old and new models as one API: they are
currently separate migration stages.
