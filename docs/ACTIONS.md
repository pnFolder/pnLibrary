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
- `sequence` — executes a nested list of actions in order;
- `set-value`, `copy-value`, `remove-value` — manage temporary values inside the current flow;
- `switch` — selects one branch from several named value cases;
- `noop` — explicitly does nothing, useful for silent fallback branches;
- `delay` — executes its `then` steps later without hiding condition logic;
- `if` — evaluates one check and executes its `then` or `else` branch.

Unsupported client-side actions return `false` from the platform bridge and produce a warning instead of disabling the plugin.

## Configuration

The configuration format is intentionally explicit: every action has a `type`, and
the remaining fields belong only to that action. Actions can be nested inside
`sequence`, `if`, `switch`, and `delay`, so a complete flow remains readable as a tree.

### One example of every built-in action

```yaml
demo-actions:
  # Sends one component assembled from all lines in `messages`.
  - type: message
    messages:
      - "<green><bold>Welcome, [[player]]!</bold>"
      - "<gray>Your profile is ready."
    serializer-type: MINI_MESSAGE
    target: PLAYER

  # Sends a short message above the hotbar.
  - type: action-bar
    text: "<yellow>Quest started"
    target: PLAYER

  # Plays an Adventure sound for the invoking player.
  - type: sound
    key: minecraft:entity_player_levelup
    source: MASTER
    volume: 1.0
    pitch: 1.0
    target: PLAYER

  # Applies a level-II speed effect for two minutes.
  # Amplifier is zero-based: 0 = level I, 1 = level II.
  - type: effect
    key: speed
    duration: 2m
    amplifier: 1
    ambient: true
    particles: false
    icon: true

  # Spawns particles around the invoking player.
  - type: particle
    key: flame
    count: 24
    offset-x: 0.35
    offset-y: 0.8
    offset-z: 0.35
    speed: 0.02

  # Writes a server-side log line. It is not sent to the player.
  - type: console
    level: INFO
    text: "A player entered the welcome flow"
```

`target` accepts `PLAYER` or `ALL_PLAYERS`. It is available on `message`,
`action-bar`, and `sound`; effects and particles always target the invoking player.
`serializer-type` may be omitted to inherit the serializer configured in the action
context. The supported values are `ADAPTIVE`, `MINI_MESSAGE`, `LEGACY_AMPERSAND`,
`LEGACY_SECTION`, `ADVENTURE_JSON`, and `PLAIN_TEXT`.

### Composing an action flow

Use `sequence` when one logical action consists of several steps. The nested list
is executed from top to bottom and every nested item is a normal polymorphic action.
This is the form to use when a single event should show a message, play a sound,
apply an effect, and then perform another conditional flow.

```yaml
join-actions:
  - type: sequence
    steps:
      - type: message
        messages:
          - "<green><bold>Welcome, [[player]]!"
          - "<gray>Your starter package is ready."
      - type: sound
        key: minecraft:entity_player_levelup
        volume: 0.8
        pitch: 1.1
      - type: effect
        key: speed
        duration: 30s
        amplifier: 0
      - type: particle
        key: happy_villager
        count: 12
      - type: delay
        duration: 3s
        then:
          - type: if
            all:
              - type: permission
                node: example.rewards
            then:
              - type: message
                messages: ["<yellow>Open your reward menu with <white>/rewards"]
            else:
              - type: action-bar
                text: "<gray>Use <white>/help <gray>to see available commands."
```

The important distinction is that `sequence` is a container action, while
`message`, `sound`, and `effect` are leaf actions. A container can contain another
container, so complex behavior is expressed without adding unrelated fields to
every action type.

### Passing values between checks

Values can be prepared by one action and consumed by a later condition. They belong
only to the current `ActionContext`; a later player execution starts with a separate
set of values. This makes nested checks deterministic without adding global state.

```yaml
- type: sequence
  steps:
    # Prepare a value for the next check.
    - type: set-value
      key: reward-tier
      value: vip

    # Copying is useful when a flow wants a stable, descriptive local name.
    - type: copy-value
      from: reward-tier
      to: current-reward

    - type: if
      all:
        - type: compare
          source: current-reward
          operator: equals
          value: vip
      then:
        - type: message
          messages:
            - "<green>VIP reward selected."
      else:
        - type: message
          messages:
            - "<gray>Standard reward selected."

    # Remove temporary state when the flow is finished.
    - type: remove-value
      key: current-reward
```

For values supplied by the plugin, pass them when creating `ActionContext`:

```kotlin
ActionContext(
    player = player,
    allPlayers = allPlayers,
    components = components,
    logger = logger,
    tasks = tasks,
    values = mapOf(
        "player-level" to playerLevel,
        "reward-tier" to rewardTier,
    ),
)
```

The action graph can then check those values, update temporary values, branch again,
and continue recursively. `set-value` currently stores text values by design; typed
runtime values such as numbers and booleans should be supplied by the plugin through
`ActionContext.values`.

### Selecting many alternatives with `switch`

Use `switch` when the flow has several named outcomes. It is clearer than repeating
the same `if` block for every possible value.

```yaml
- type: switch
  on: player-state
  cases:
    new:
      - type: message
        messages:
          - "<green>Добро пожаловать, [[player]]!"
      - type: sound
        key: minecraft:entity_player_levelup

    returning:
      - type: message
        messages:
          - "<aqua>С возвращением, [[player]]!"
      - type: action-bar
        text: "<gray>Твой уровень: [[player-level]]"

    banned:
      - type: message
        messages:
          - "<red>Доступ запрещён."
      - type: console
        level: WARNING
        text: "Blocked player attempted to join"

  default:
    - type: message
      messages:
        - "<yellow>Состояние игрока не определено."
```

`switch` reads the value once, compares it with the case names, and executes only
one branch. `default` handles missing or unknown values and is separate from `cases`,
so no case name is reserved. `default` and `$default` may both be real case values.
Case matching is
case-insensitive by default; set `ignore-case: false` for exact matching.

When an unknown value should be ignored silently, make that behavior explicit with
`noop` instead of leaving the fallback ambiguous:

```yaml
- type: switch
  on: player-state
  cases:
    new:
      - type: message
        messages: ["<green>Welcome!"]

    vip:
      - type: message
        messages: ["<gold>Welcome, VIP!"]

  default:
    - type: noop
```

`noop` produces no message, log record, sound, effect, or error. Its aliases are
`ignore` and `silent`.

Every local context value is also available in component text as `[name]`, for
example `[player]`, `[player-level]`, and `[reward-id]`. Single square brackets
identify action-local values, while registered library placeholders keep the
`{clan.name}` and `{pnclans:clan.name}` syntax. Both forms use the same resolver
and formatter pipeline, so a value changed by `set-value` is visible to following messages.

Local values support the same basic inline formatting vocabulary without becoming
library registrations:

```text
[player-level|default:0]
[player-name|upper]
[has-premium|boolean:VIP:Обычный игрок]
```

### Updating library-owned values

A registered placeholder is read-only by default. Its owner may explicitly expose
an updater and a separate write-access policy:

```java
context.getPlaceholders()
    .placeholder("profile.title", String.class)
    .resolve(request -> profiles.title(request.requirePlayerId()))
    .update((request, value) -> {
        profiles.setTitle(request.requirePlayerId(), value);
        return value;
    })
    .access(PlaceholderAccess.shared())
    .updateAccess(
        PlaceholderAccess.builder()
            .owner()
            .allow("pnmenus")
            .build()
    )
    .register();
```

An action owned by `pnmenus` can then update that value through its namespaced
placeholder address. The reference does not include `{}` because it identifies the
value to update rather than rendering text:

```yaml
- type: update-placeholder
  reference: pnprofiles:profile.title
  value: Veteran
  result: updated-title

- type: message
  messages:
    - "<green>Новый титул: [[updated-title]]"
    - "<gray>Проверка через registry: {pnprofiles:profile.title}"
```

The owner-defined updater parses and validates the assignment, writes it to its own
storage, and returns the resulting typed value. pnLibrary invalidates that
placeholder's cache after a successful update. Attempts to update a read-only
placeholder or a value denied by `updateAccess` fail instead of bypassing ownership.

### Shared placeholder value store

`PnLibrary.placeholderValues` is a process-wide store for small values that must be
shared between actions and plugins. A parameter is registered once with either
`PLAYER` scope (one value per player UUID) or `GLOBAL` scope (one value for the
whole server process):

```java
PlaceholderValueStore values = PnLibraryProvider.get().getPlaceholderValues();
UUID playerId = player.getUniqueId();

values.create("player_total_time_played", PlaceholderValueScope.PLAYER, "0");
values.set("player_total_time_played", "3600", playerId);
values.increment("player_total_time_played", 60, playerId);

String seconds = values.get("player_total_time_played", playerId);
```

The same store is published to PlaceholderAPI under `pnlibrary_defaultvalue`:

```text
%pnlibrary_defaultvalue_create_[player_total_time_played]%
%pnlibrary_defaultvalue_set_[player_total_time_played]_[3600]%
%pnlibrary_defaultvalue_get_[player_total_time_played]%
%pnlibrary_defaultvalue_increment_[player_total_time_played]_[60]%
%pnlibrary_defaultvalue_exists_[player_total_time_played]%
%pnlibrary_defaultvalue_remove_[player_total_time_played]%
```

Global parameters use `createglobal` and do not require a player:

```text
%pnlibrary_defaultvalue_createglobal_[server_restart_count]%
%pnlibrary_defaultvalue_set_[server_restart_count]_[12]%
%pnlibrary_defaultvalue_get_[server_restart_count]%
```

Square brackets delimit every argument. Therefore
`set_[player_total_time_played]_[VIP_player_01]` unambiguously addresses
`player_total_time_played` and stores `VIP_player_01`; underscores remain valid
inside both the parameter and its value. Brackets are part of the command syntax,
not part of the stored parameter or value.

PlaceholderAPI can resolve a placeholder more than once while rendering text.
Use its `get` and `exists` commands freely, but perform `set`, `increment`,
`decrement`, and `remove` through `PlaceholderValueStore` whenever the operation
must happen exactly once. Values are held in memory and are cleared when the
library process stops.

### Conditions

Checks read values from the `ActionContext` created by the plugin. They do not
invent values and they do not mutate the context. Place checks directly in an
`if` action's `all` or `any` list; use `not` to negate one check.

```yaml
checks:
  # Named runtime value converted to a boolean.
  - type: flag
    name: welcome-enabled
    equals: true

  # Player permission.
  - type: permission
    node: example.welcome

  # Text and numeric comparisons use the same `value` condition.
  - type: compare
    source: rank
    operator: equals
    value: vip
    ignore-case: true

  - type: compare
    source: balance
    operator: greater-or-equal
    value: "1000"

  - type: compare
    source: referral-code
    operator: present

  # Randomly matches 25% of the time.
  - type: chance
    probability: 0.25

  # Negates one check.
  - type: not
    check:
      type: flag
      name: rewards-disabled
      equals: true
```

For `compare`, the available operators are `equals`, `not-equals`, `contains`,
`greater-than`, `greater-or-equal`, `less-than`, `less-or-equal`, `present`, and
`absent`. Ordering comparisons require values that can be parsed as numbers.

### Conditional flow: check, yes-branch, no-branch

Read an `if` block as three visible parts:

1. `all` or `any` — which checks must pass;
2. `then` — actions executed when the result is true;
3. `else` — actions executed when the result is false.

The branch names describe their control-flow role directly. `then` and `else`
are lists of ordinary actions, not special commands. Each list can contain one
action or a complete `sequence`.

```yaml
join-actions:
  # CHECK: does the player have both the feature flag and the permission?
  - type: if
    all:
      - type: flag
        name: welcome-enabled
        equals: true
      - type: permission
        node: example.welcome

    # YES: execute this list from top to bottom.
    then:
      - type: message
        messages:
          - "<green>Welcome, [[player]]!"
      - type: sound
        key: minecraft:entity_player_levelup
        volume: 0.8
        pitch: 1.2

    # NO: execute this list instead.
    else:
      - type: console
        level: INFO
        text: "Welcome message skipped"

  # A second, independent check starts here.
  - type: delay
    duration: 5s
    then:
      - type: if
        all:
          - type: compare
            source: verified
            operator: equals
            value: "true"
        then:
          - type: action-bar
            text: "<green>Verification completed"
        else:
          - type: message
            messages:
              - "<red>Verification is still pending."
```

`delay` does not block a server thread. It only schedules `then`; a nested `if`
makes it explicit that a condition is evaluated after the delay. Its scheduled task
belongs to the plugin `TaskScope`, so disabling the plugin cancels it automatically.

Checks support runtime values, boolean flags, permissions, probability, and negation.
Put them directly under `all` when every check is required, or under `any` when one
successful check is enough. An `if` cannot contain both fields at the same time.

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
