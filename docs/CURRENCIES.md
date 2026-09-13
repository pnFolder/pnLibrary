# Currency API

The Currency API is a platform-independent registry for Vault, PlayerPoints,
and plugin-defined currencies. API code never imports Bukkit, Vault, or
PlayerPoints. Platform bridges live in `pnlibrary-bukkit`.

## Built-in Bukkit bridges

When available, Bukkit automatically publishes:

- `vault:money` — precision and formatting come from the active Vault economy;
- `playerpoints:points` — whole numbers only.

Bridges follow plugin and Bukkit service lifecycle events. Consumers do not
restart when an integration disappears or returns. Use `get` for an optional
integration and `require` when absence must stop the current operation.

```kotlin
val money = context.currencies.get("vault:money") ?: return
val result = money.withdraw(player.uniqueId, BigDecimal("25.50"))
context.currencies.require("playerpoints:points").deposit(player.uniqueId, 10L)
```

Every mutation returns `CurrencyResult`. Check its `status` or `isSuccess`;
never use `has()` followed by `withdraw()` as a payment operation because the
balance can change between those calls.

## Inline registration

`register` completes the registration. There is no unfinished builder and no
trailing `.register()` call.

```kotlin
val tokens = context.currencies.register("tokens") { currency ->
    currency.descriptor { descriptor ->
        descriptor.displayName("Clan tokens")
        descriptor.symbol(" ✦")
        descriptor.wholeNumbers()
    }
    currency.aliases("token", "clan-token")
    currency.access { access ->
        access.owner()
        access.allow("pnshop", "pncases")
        access.allowMatching("pnclans-*")
    }
    currency.operations { operations ->
        operations.balance { account -> tokenStore.balance(account.playerId) }
        operations.deposit { account, amount -> tokenStore.deposit(account.playerId, amount.toLong()) }
        operations.withdraw { account, amount -> tokenStore.withdraw(account.playerId, amount.toLong()) }
    }
}
```

The balance operation is required. Other capabilities are inferred from the
configured operations; developers never maintain a duplicate capability list.
Use `balanceAsync`, `depositAsync`, and `withdrawAsync` for database-backed
implementations.

## Provider-class registration

For reusable or complex currencies, implement only the small interfaces that
the provider actually supports:

```kotlin
class GemCurrency(private val store: GemStore) :
    CurrencyProvider,
    CurrencyDeposits,
    CurrencyWithdrawals {

    override val descriptor = CurrencyDescriptor.Builder("Gems")
        .symbol(" ♦")
        .wholeNumbers()
        .build()

    override fun balance(account: CurrencyAccount) = store.balance(account.playerId)
    override fun deposit(account: CurrencyAccount, amount: BigDecimal) = store.deposit(account.playerId, amount)
    override fun withdraw(account: CurrencyAccount, amount: BigDecimal) = store.withdraw(account.playerId, amount)
}

val gems = context.currencies.register("gems", GemCurrency(store)) { options ->
    options.access(CurrencyAccess.shared())
    options.aliases("gem")
}
```

Capabilities are inferred with `is CurrencyDeposits`, `is CurrencyTransfers`,
and the other optional interfaces. `CurrencyProvider` itself requires only a
descriptor and balance lookup.

## Visibility and names

- `CurrencyAccess.ownerOnly()` — registering plugin only;
- `CurrencyAccess.shared()` — every plugin registered in pnLibrary;
- access builder — exact plugin IDs, wildcard patterns, and explicit denies.

Inside the owner plugin, `require("tokens")` resolves `owner:tokens`. Other
plugins use the canonical name, for example `require("pnclans:tokens")`.
Aliases stay inside the same owner namespace and cannot shadow another
currency.

## Runtime behavior

The registry validates names, aliases, amount sign, and fractional precision at
one boundary for every provider. Disabled and closed registrations cannot be
used. Synchronous provider exceptions and failed asynchronous stages are
converted into controlled failures. Closing `PluginContext` removes every
currency owned by that plugin and closes providers that implement
`AutoCloseable`.
