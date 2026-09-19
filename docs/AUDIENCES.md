# Cross-platform audiences

`PnLibrary.audiences` resolves message receivers without exposing Bukkit, BungeeCord, or Velocity
types to shared plugin code.

```kotlin
val message = library.components.parse("<green>Update installed")
library.audiences.console().sendMessage(message)
library.audiences.player(playerId)?.sendMessage(message)
library.audiences.all().actionBar(Component.text("A new version is available"))
```

`console()` returns the active platform console. `player(UUID)` returns an online player or `null`.
`sender(native)` wraps a native sender belonging to the active platform and returns `null` for an
unknown object. `onlinePlayers()` returns a snapshot; `all()` resolves current membership for every
delivery.

`AudienceSender` exposes `id`, `name`, `isConsole`, `isPlayer`, and `hasPermission`. `LibraryPlayer`
uses its UUID string as `id` and retains sound, effect, and particle operations. Portable command
senders implement the same audience contract.

```kotlin
val staff = library.audiences.combine(
    library.audiences.onlinePlayers().filter { it.hasPermission("example.staff") }
)
staff.sendMessage(Component.text("Staff notification"))
```

Composite audiences preserve input order, ignore repeated object instances, and continue delivery
if one receiver fails. `playSound` returns false when the audience is empty or any receiver rejects
the request.

Audience transports accept Adventure `Component` objects and do not interpret strings. Use the
existing `ComponentService` for MiniMessage, legacy colors, JSON, placeholders, and cached parsing.
Bukkit uses its Adventure bridge and fallback, Velocity delivers Adventure natively, and BungeeCord
uses its compatible legacy bridge.

Lookups return no player/native receiver after pnLibrary closes. Do not retain native wrappers past
the runtime lifecycle.
