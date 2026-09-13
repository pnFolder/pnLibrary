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

## Managed currencies and transaction history

A managed currency delegates persistence and atomicity to `CurrencyStorage`.
The same storage transaction changes balances and appends the ledger record;
implementations must never perform those as two independent writes.

pnLibrary includes two ready implementations:

```kotlin
val fileStorage = context.currencyStorages.file(
    plugin.dataFolder.toPath().resolve("currencies.json"),
    100_000, // retained transactions
)

val jdbcStorage = context.currencyStorages.jdbc(
    dataSource,
    "pnclans_currency",
)
```

The file implementation keeps balances, the complete ledger, and idempotency
keys in one human-readable JSON document. Writes use a temporary file followed
by an atomic replacement; an unsuccessful write rolls the in-memory mutation
back as well.

The JDBC implementation creates `<prefix>_accounts` and
`<prefix>_transactions`. Every mutation runs with a database transaction and
serializable isolation, locks involved accounts where the database supports
`FOR UPDATE`, changes balances, inserts the ledger row, and then commits. Any
error causes a rollback. It works through a supplied `DataSource`, so connection
pool and database selection remain under the consumer plugin's control.

### Moving data between storages

File, SQLite, MySQL, MariaDB, and PostgreSQL storage can be migrated through the
same API. Currency IDs may remain unchanged or be remapped during import.

```kotlin
context.currencyStorages.migrate(
    source = fileStorage,
    sourceCurrency = CurrencyKey(PluginId.of("pnclans"), "coins"),
    target = jdbcStorage,
    targetCurrency = CurrencyKey(PluginId.of("pnclans"), "coins"),
    mode = CurrencyImportMode.MERGE_KEEP_TARGET,
).thenAccept { report ->
    logger.info("Imported ${report.import.importedAccounts} accounts")
}
```

Import modes:

- `REPLACE` removes the target currency before restoring the snapshot;
- `MERGE_KEEP_TARGET` preserves existing balances and imports missing data;
- `MERGE_OVERWRITE` overwrites balances from the snapshot but keeps unrelated
  target transactions.

Snapshots preserve transaction UUIDs, timestamps, actors, source/target
accounts, before/after balances, metadata, failure details, and idempotency
keys. Existing transaction UUIDs and idempotency keys are skipped during merge,
so retrying an interrupted migration does not duplicate history.

```kotlin
val coins = context.currencies.managed("coins") { currency ->
    currency.descriptor { it.displayName("Coins").symbol(" ⛃") }
    currency.storage(myJdbcCurrencyStorage) // borrowed; use ownedStorage(...) for exclusive storage
    currency.service("pnclans.rewards")
    currency.access(CurrencyAccess.shared())
    currency.placeholders { placeholders ->
        placeholders.access(PlaceholderAccess.shared())
        placeholders.placeholderApi("pnclans")
    }
    currency.commands { commands ->
        commands.permissionPrefix("pnclans.coins")
        commands.prefix("&8[&6{currency}&8] ")
        commands.balance("&fBalance: &a{balance}")
        commands.success("&aCompleted: {amount}")
        commands.failure("&c{error}")
        commands.historyEmpty("&7No transactions found.")
    }
}
```

Managed currencies automatically expose these internal placeholders:

```text
{currency.coins.balance}
{currency.coins.formatted}
{currency.coins.symbol}
```

With the PlaceholderAPI namespace above they are also published as:

```text
%pnclans_coins_balance%
%pnclans_coins_formatted%
%pnclans_coins_symbol%
```

Use the ledger extension when the caller must identify who initiated a change
and why:

```kotlin
val ledger = coins.extension(CurrencyLedger::class.java)!!
ledger.transact(
    CurrencyTransactionRequest(
        type = CurrencyTransactionType.CREDIT,
        amount = BigDecimal("250"),
        target = CurrencyAccount(player.uniqueId),
        actor = CurrencyActor.service("daily-quest"),
        service = "pnclans.quests",
        reason = "Completed quest: miner-5",
        metadata = mapOf("quest" to "miner-5", "season" to "12"),
        idempotencyKey = "quest:miner-5:${player.uniqueId}:12",
    )
)
```

Every ledger row records its transaction ID, currency, operation, source,
target, actor, service, reason, metadata, before/after balances, timestamp,
status, and failure message. `CurrencyHistoryQuery` filters by account, actor,
service, operation type, time range, offset, and limit. Idempotency keys allow a
storage implementation to prevent duplicated rewards after retries or server
restarts.

## Bukkit commands

pnLibrary registers one stable command instead of injecting a command into every
consumer plugin:

```text
/pncurrency list
/pncurrency <namespace:name> balance [player]
/pncurrency <namespace:name> add <player> <amount>
/pncurrency <namespace:name> take <player> <amount>
/pncurrency <namespace:name> set <player> <amount>
/pncurrency <namespace:name> reset <player>
/pncurrency <namespace:name> pay <player> <amount>
/pncurrency <namespace:name> history [player]
```

Each operation has an independent permission:

```text
pnlibrary.currency.list
pnlibrary.currency.<owner>.<name>.balance
pnlibrary.currency.<owner>.<name>.add
pnlibrary.currency.<owner>.<name>.take
pnlibrary.currency.<owner>.<name>.set
pnlibrary.currency.<owner>.<name>.reset
pnlibrary.currency.<owner>.<name>.pay
pnlibrary.currency.<owner>.<name>.history
```

Commands execute storage stages without blocking the Minecraft thread and move
the final sender message back to the server thread. Managed command mutations
record the actual player/server actor and `pnlibrary.command` service in the
ledger. `pay` records the paying player as both source and actor.
