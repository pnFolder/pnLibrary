# Currency API

`context.currencies` provides one API for Vault, PlayerPoints, and plugin-owned
currencies. Bukkit automatically exposes `vault:money` and
`playerpoints:points` when those plugins are available. Their removal and
re-enabling are handled without restarting consumer plugins.

## Using a currency

```kotlin
val money = context.currencies.require("vault:money")
val points = context.currencies.require("playerpoints:points")

money.withdraw(player.uniqueId, BigDecimal("25.50"))
points.deposit(player.uniqueId, BigDecimal("10"))
```

Vault uses the precision reported by its economy provider. PlayerPoints has
`fractionDigits = 0`, so values such as `10.5` are rejected before reaching its
API. Never implement a transfer as `has()` followed by `withdraw()`; inspect the
result returned by the atomic withdrawal instead.

Available operations are `balance`, `has`, `deposit`, `withdraw`, `setBalance`,
`reset`, `transfer`, `format`, `supports`, and typed `extension`. Unsupported
operations return `UNSUPPORTED`; provider failures return `FAILED`.

## Registering with a builder

```kotlin
val tokens = context.currencies.currency("tokens")
    .displayName("Clan tokens")
    .symbol(" ✦")
    .fractionDigits(0)
    .access { it.owner().allow("pnshop", "pncases") }
    .balance { account -> tokenStore.balance(account.playerId) }
    .deposit { account, amount -> tokenStore.deposit(account.playerId, amount.toInt()) }
    .withdraw { account, amount -> tokenStore.withdraw(account.playerId, amount.toInt()) }
    .register()
```

Use `CurrencyAccess.ownerOnly()` for the registering plugin, `shared()` for all
pnLibrary plugins, or the access builder for IDs, wildcard patterns, and denies.
An unqualified lookup such as `require("tokens")` searches the caller's own
namespace; another plugin uses `require("pnclans:tokens")`.

## Registering a provider class

```kotlin
val registration = context.currencies.register(
    "gems",
    GemsCurrencyProvider(database),
    CurrencyAccess.shared(),
)
```

Implement `CurrencyProvider` when the currency needs reusable logic, asynchronous
storage, custom formatting, or typed extensions. Registrations are owned by the
plugin context and are removed automatically when that context closes.
