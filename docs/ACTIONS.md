# Actions

The action subsystem has one model: every configured item is an implementation of `Action`.
There is no universal object containing unrelated fields and no handwritten action serializer.

## Built-in actions

- `message` — sends one or several text lines;
- `action-bar` — displays an action bar;
- `sound` — plays an Adventure sound;
- `effect` — applies a Bukkit potion/status effect;
- `particle` — displays a Bukkit particle;
- `console` — writes through the plugin logger;
- `delay` — executes nested actions later and may select a conditional branch.

Unsupported client-side actions return `false` from the platform bridge and produce a warning instead of disabling the plugin.

## Configuration

```yaml
join-actions:
  - type: message
    messages:
      - "<green>Welcome, {player}!"

  - type: effect
    key: speed
    duration: 10s
    amplifier: 1

  - type: particle
    key: flame
    count: 20
    offset-x: 0.4
    offset-y: 1.0
    offset-z: 0.4

  - type: delay
    duration: 3s
    conditions:
      - type: value
        key: verified
        comparison: equals
        expected: "true"
    actions:
      - type: message
        messages: ["<green>Verification completed"]
    otherwise:
      - type: message
        messages: ["<red>Verification failed"]
```

`delay` does not block a server thread. Conditions are checked after the delay. Its scheduled task belongs to the plugin `TaskScope`, so disabling the plugin cancels it automatically.

Durations accept both ISO-8601 (`PT10S`) and short values: `500ms`, `10s`, `5m`, `2h`, `1d`.

## Configuration class

```kotlin
class MainConfig {
    var joinActions: List<Action> = emptyList()
}
```

`@ConfigPolymorphic` and `@ConfigTypes` on `Action` select the concrete class by `type`. Nested actions and conditions use the same configuration engine recursively.

## Execution

```kotlin
val player = audienceService.player(bukkitPlayer)
val allPlayers = audienceService.onlinePlayers()

context.actions.execute(
    player = player,
    allPlayers = allPlayers,
    actions = config.joinActions,
    values = mapOf(
        "player" to bukkitPlayer.name,
        "verified" to true,
    ),
)
```

`ActionContext` also exposes components, logger, task scope, named values and optional typed runtime objects.

## Custom action

```kotlin
data class DepositAction(
    val amount: Double = 0.0,
) : Action {
    override fun execute(context: ActionContext) {
        economy.deposit(context.player.uniqueId, amount)
    }
}
```

Register it as a configuration type owned by the plugin:

```kotlin
context.configs.type(
    Action::class.java,
    DepositAction::class.java,
    "deposit",
)
```

The default registration is owner-only. Use `ConfigTypeAccess` only when another pnLibrary plugin must be allowed to reference the type.
