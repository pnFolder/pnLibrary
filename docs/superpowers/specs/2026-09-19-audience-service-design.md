# Cross-platform Audience Service Design

## Goal

Provide one public audience service for Bukkit, BungeeCord, and Velocity so commands, actions, and
consumer plugins share identity, permission checks, chat delivery, action bars, and composition.
Native platform types and component conversion remain inside platform runtime modules.

This stage is transport only. Message catalogs, locale selection, configuration-backed templates,
and translation keys are explicitly deferred and may later build on this API and the existing
`ComponentService`.

## Public API

`PnLibrary.audiences` exposes `AudienceService`:

```kotlin
interface AudienceService {
    fun console(): AudienceSender
    fun player(uniqueId: UUID): LibraryPlayer?
    fun sender(native: Any): AudienceSender?
    fun onlinePlayers(): List<LibraryPlayer>
    fun all(): LibraryAudience
    fun combine(audiences: Iterable<LibraryAudience>): LibraryAudience
}
```

`AudienceSender` extends the existing `LibraryAudience` with stable identity and permission data:

```kotlin
interface AudienceSender : LibraryAudience {
    val id: String
    val name: String
    val isConsole: Boolean
    val isPlayer: Boolean
    fun hasPermission(permission: String): Boolean
}
```

`LibraryPlayer` extends `AudienceSender` and retains its UUID, effects, and particle operations.
Its `id` defaults to the UUID string, `isPlayer` is true, and `isConsole` is false. Existing
`sendMessage`, `actionBar`, and `playSound` methods remain source compatible.

`sender(native)` accepts a native command sender or player owned by the active platform. Unknown
objects return `null`; the service never guesses across platform boundaries. `player` also returns
`null` for offline/unknown players. `onlinePlayers` is a snapshot, while `all` is dynamic and
resolves the current online set for each operation.

Composite audiences forward each operation in input order, deduplicate identical object instances,
continue after one receiver rejects a sound, and return `false` from `playSound` if any receiver
rejects it. An empty composite is a safe no-op and accepts no sound request.

## Architecture

The API module owns audience contracts only. Runtime SPI adds `PlatformAudienceAdapter`, responsible
for resolving native senders, the console, players by UUID, and online players. Each platform
implements the adapter with native APIs and its existing component delivery strategy:

- Bukkit delegates chat/action bar/sound to `BukkitAudienceService` and keeps effects/particles in
  `BukkitLibraryPlayer`.
- Velocity sends Adventure components and sounds natively.
- BungeeCord converts components through its legacy-compatible serializer and reports unsupported
  sounds/effects/particles as `false`.

Core owns `AudienceServiceImpl`, composition, closed-state behavior, and the public service exposed
by `PnLibraryImpl`. Closing pnLibrary makes lookups return no receivers and composite delivery a
safe no-op; already obtained native wrappers remain platform objects and are not promised usable
after shutdown.

## Commands and actions

The portable `CommandSender` contract becomes an `AudienceSender`. Platform command adapters obtain
the wrapper from the platform audience adapter instead of maintaining separate component conversion
and identity logic. A failure to wrap a native command source is contained, logged, and the command
is not dispatched.

Existing action APIs continue to consume `LibraryAudience`/`LibraryPlayer`; their behavior does not
change. Platform-specific `LibraryPlayer.of` factories may remain for binary compatibility but must
delegate to the same wrapper implementation used by the audience adapter.

## Component handling

Audience methods accept already parsed Adventure `Component` values. String parsing, MiniMessage,
legacy `&`/`§`, JSON, placeholders, and caching stay in `ComponentService`; transport adapters do
not parse message syntax. This prevents different platforms from interpreting the same input
differently.

## Errors and lifecycle

Lookup failures return `null` or empty collections. Delivery errors are isolated per composite
receiver so one broken audience does not prevent delivery to later receivers; fatal JVM errors are
re-thrown. Platform adapters log native delivery failures with sanitized public behavior.

`AudienceService` itself is runtime-owned and read-only to consumers. It creates no independent
registrations requiring plugin-owner cleanup.

## Verification

Tests cover identity defaults, player/console distinction, permissions, unknown native objects,
offline UUID lookup, dynamic online membership, composite order/deduplication/error isolation,
sound aggregation, closed runtime behavior, command-sender reuse, and native delivery on Bukkit,
BungeeCord, and Velocity. Final verification runs all tests, API checks, and three distribution
builds.
