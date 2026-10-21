# Валюта

pnLibrary даёт плагинам общий реестр валют. Плагин может:

- завести свою валюту;
- взять чужую (Vault, PlayerPoints или монеты другого плагина pnFolder);
- работать с любой из них одинаково: баланс, пополнение, списание, перевод, формат суммы.

Все операции асинхронные и возвращают `CompletionStage`, поэтому медленное хранилище не тормозит основной поток.

## Модули

| Модуль | Что в нём |
|---|---|
| `pnlibrary-currency-api` (`:modules:features:currency-api`) | контракты. От него зависит ваш плагин (`compileOnly`) |
| `pnlibrary-currency` (`:modules:features:currency-runtime`) | реализация: реестр, проводки, хранилища в файле и JDBC. Входит в jar pnLibrary |

Пакет API: `org.pnfolder.pnlibrary.api.currency`. В модулях валюты нет классов Bukkit, BungeeCord, Velocity, Vault или
PlayerPoints. Мосты к Vault и PlayerPoints и команда `/pncurrency` живут в `platforms/bukkit/runtime`.

## 1. Взять валюту

```kotlin
val currencies = module.currencies                 // Java: CurrencyApi.from(module)

val money = currencies.require("vault:money")      // Vault, если он установлен
val coins = currencies.get("coins")                // своя валюта по имени или псевдониму, либо null

money.balance(player.uniqueId).thenAccept { balance ->
    player.sendMessage("У вас ${money.format(balance)}")
}
money.withdraw(player.uniqueId, 100).thenAccept { result ->
    if (!result.isSuccess) player.sendMessage("Не удалось списать: ${result.status}")
}
```

Ссылка на валюту: своё имя или псевдоним (`coins`) либо полный ключ `владелец:имя` (`vault:money`,
`playerpoints:points`). `get` возвращает `null`, если валюты нет, она выключена или закрыта для вашего плагина.
`require` в этих случаях бросает исключение.

| Метод | Что делает |
|---|---|
| `balance(id)` | текущий баланс |
| `has(id, amount)` | хватает ли денег |
| `deposit(id, amount)` | пополнить |
| `withdraw(id, amount)` | списать |
| `setBalance(id, amount)` | установить ровно эту сумму |
| `reset(id)` | вернуть начальный баланс |
| `transfer(from, to, amount)` | атомарно перевести между двумя игроками |
| `format(amount)` | сумма текстом, с символом и нужным числом знаков после запятой |
| `supports(capability)` | умеет ли источник операцию (не каждый провайдер умеет `reset` или `transfer`) |

Суммы передаются как `BigDecimal` или `Long`. Результат операции (`CurrencyResult`) имеет статус:

| Статус | Значение |
|---|---|
| `SUCCESS` | выполнено |
| `REJECTED` | отказ по понятной причине, например не хватает денег |
| `UNSUPPORTED` | источник не умеет эту операцию |
| `UNAVAILABLE` | хранилище временно недоступно |
| `FAILED` | непредвиденная ошибка |

## 2. Своя валюта с хранилищем (рекомендуется)

Библиотека сама ведёт балансы, атомарные проводки и историю. Вам остаётся выбрать хранилище:

```kotlin
val keys = module.currencies.managed("keys") { currency ->
    currency.descriptor { it.displayName("Ключи").symbol("⚷").wholeNumbers() }
    currency.ownedStorage(module.currencyStorages.file(dataFolder.toPath().resolve("keys.db")))
    currency.aliases("key")
    currency.placeholders { it.placeholderApi(true) }   // опубликовать баланс и в PlaceholderAPI
    currency.commands { it.permissionPrefix("pncase.keys") }
}
```

Хранилища:

- `currencyStorages.file(path)`: файл в папке плагина;
- `currencyStorages.jdbc(dataSource, tablePrefix)`: любая база через JDBC.

Хранилище, переданное в `ownedStorage`, закрывается вместе с валютой. Хранилище из `storage` закрываете вы сами.

## 3. Своя валюта поверх своей логики

Если балансы уже где-то хранятся, опишите только операции:

```kotlin
val coins = module.currencies.register("coins") { definition ->
    definition.descriptor { it.displayName("Монеты").symbol("◈").fractionDigits(2) }
    definition.operations { operations ->
        operations.balance { account -> balances[account.playerId] ?: BigDecimal.ZERO }
            .deposit { account, amount -> add(account.playerId, amount) }
            .withdraw { account, amount -> take(account.playerId, amount) }
            .format { amount -> "$amount ◈" }
    }
}
```

Операции, которые вы не описали, валюта не поддерживает: `supports(...)` вернёт `false`, а вызов вернёт
`UNSUPPORTED`. Полный рабочий пример лежит в `examples/demo-bukkit/.../DemoCurrency.kt`.

## 4. Кто видит валюту

По умолчанию валюту видит только её владелец. Чтобы открыть доступ:

```kotlin
currency.access(CurrencyAccess.shared())                    // всем плагинам pnLibrary
currency.access { it.owner().allow("pnshop").allowMatching("pn*").deny("pnbeta") }
```

Запрет сильнее разрешения.

## 5. Команда `/pncurrency` (Bukkit)

Для управляемых валют библиотека сама регистрирует команду администратора. Её подкоманды:

- `list`, `balance`, `history`;
- `add`, `take`, `set`, `reset`, `pay`;
- `confirm` и `cancel` для подтверждения.

Право строится как `<префикс>.<операция>`. По умолчанию префикс `pnlibrary.currency.<валюта>`, своё значение
задаёт `permissionPrefix`.

Опасные операции по умолчанию подтверждаются из консоли (`CurrencyConfirmationMode.CONSOLE`). Время на
подтверждение настраивается в пределах 10–600 секунд. Тексты ответов меняются методами `prefix`, `success`,
`failure`, `balance` и `historyEmpty`.

## 6. Vault и PlayerPoints

Если на сервере включён Vault с экономикой, pnLibrary регистрирует её как `vault:money`, а PlayerPoints как
`playerpoints:points`. Обе валюты доступны всем плагинам pnLibrary через тот же интерфейс `Currency`.
