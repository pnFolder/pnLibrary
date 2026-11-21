# Сценарии: действия и требования

Сценарий — список **действий** (`Action`) из конфига. Действие что-то делает, **требование** (`Requirement`) только отвечает «да» или «нет».

## Действие и его итог

Каждое действие возвращает `ActionResult`:

| Итог | Что значит | Что дальше |
|---|---|---|
| `Done` | шаг выполнен | идёт следующий |
| `Stopped(reason)` | сценарий закончен нарочно, например требование не выполнено | список обрывается, ничего не откатывается |
| `Failed(code, message)` | шаг не смог | список обрывается, **откатываются** прошлые шаги (деньги возвращаются) |

Исключение внутри действия превращается в `Failed("action.error", ...)`.

```kotlin
val result = module.actions.execute(config.onBuy, player, values = mapOf("item" to "diamond"))
```

## Типы действий

`message`, `actionbar`, `title`, `sound`, `command`, `log`, `effect`, `particle` — вывод и команды.
`delay`, `repeat` — отложенное и повторяющееся (отдельные сценарии, ошибка там пишется в лог).
`if`, `require`, `attempt`, `stop` — управление.
`charge`, `give` — деньги.

## Требования

`permission`, `compare`, `chance`, `item`, `experience`, `near`, `all`, `any`, `not`, плюс `balance` для денег.
`compare` берёт любые значения: `[значение сценария]`, `{плейсхолдер pnLibrary}`, `%внешний%`, обычное число.

## Деньги и ошибки

`charge` списывает деньги и регистрирует откат. Если следующий шаг упал, деньги вернутся сами.
`attempt` ловит ошибку, откатывает свой блок и показывает сообщение через `{error}` и `{error_code}`.

```yaml
on-buy:
  - type: require
    when:
      type: compare
      left: '%vault_eco_balance%'
      operator: greater-or-equal
      right: 100
    deny:
      - { type: message, messages: ['&c{reason}'] }
  - type: attempt
    do:
      - { type: charge, currency: money, amount: 100 }
      - { type: command, command: 'give {player} diamond 1' }
    fail:
      - { type: message, messages: ['&cНе вышло: {error}'] }
```

Коды ошибок денег: `currency.insufficient-funds`, `currency.unavailable`, `currency.unsupported`, `currency.invalid-amount`, `currency.rejected`, `currency.failed`.

## Свои действия и требования

```kotlin
val greet = Action { ctx -> ctx.player.sendMessage(Component.text("Привет")); ActionResult.Done }
val vip = Requirement { ctx -> Verdict.of(ctx.player.hasPermission("vip"), "только для VIP") }
```

## Расширение: куда и как регистрировать

Слово действия или требования добавляется одним механизмом `configs.type<Action, MyAction>().name("my")`. Область задаётся через `into(...)`:

| Вызов | Где слово работает |
|---|---|
| без `into` | в конфигах своего модуля |
| `.into(plugin)` или `.into("quests")` | во всех файлах плагина |
| `.into("quests/main")` | в одном модуле |
| `.into("quests/main:quests.yml")` или `.into(config)` | в одном файле |
| `.everywhere()` | во всех плагинах (на всю библиотеку) |
| `.registerEach(listOf(a, b))` | в нескольких местах сразу, при ошибке откатывается всё |

Конфликты имён: `.onConflict(ConfigTypeConflict.FAIL)` (по умолчанию, ошибка), `KEEP` (оставить старое, не регистрировать), `REPLACE` (заменить своё и то, у чего приоритет ниже). Слова самой библиотеки и слова плагина-хозяина файла чужие плагины перебить не могут, при равных словах решает `.priority(n)`. Снятие: закрыть `ConfigTypeRegistration`, оно закрывается и вместе с владельцем.

Сервисы для своих действий: зарегистрируйте их в `module.services`, они доступны из `ctx.service(Type::class.java)`.

## Выражения: математика и логика

Требование `expression` и текст `{= ... }` считают что угодно: числа, строки, права, плейсхолдеры.

```yaml
when:
  type: expression
  expression: "level() >= 10 && (permission('shop.vip') || {shop:discount} > 20) && chance(0.5)"
```

```yaml
- { type: set, name: price, expression: "round(base * (1 - {shop:discount} / 100), 2)" }
- { type: message, messages: ['Цена: {price}, уровень {= level() * 2}'] }
```

Значения: имя (`balance`) читает значение сценария, потом плейсхолдер pnLibrary; `{плагин:ключ}` — плейсхолдер, `%имя%` — внешний, `[имя-с-дефисом]` — значение сценария.
Операторы: `! -`, `^`, `* / %`, `+ -`, `< <= > >=`, `== !=`, `&&`, `||`, `условие ? a : b`.
Функции: `min max abs round floor ceil sqrt pow clamp random chance len upper lower contains startsWith endsWith has if permission level exp items distance`.
Свою функцию добавляет плагин: `module.actions.defineFunction("kills") { ctx, args -> ... }`, и в выражении пишут `kills() >= 100`.
Ошибка в тексте выражения видна при загрузке конфига, ошибка при расчёте даёт «не выполнено» с причиной.

## Свои именованные действия и требования

```kotlin
module.actions.define("give-kit") { ctx -> kits.give(ctx.player, ctx.format("{kit}")); ActionResult.Done }
module.actions.defineRequirement("is-vip", Requirement { ctx -> Verdict.of(ctx.player.hasPermission("vip"), "только VIP") })
```

```yaml
- { type: call, name: give-kit, args: { kit: starter } }     # args видны только внутри вызова
- { type: require, when: { type: ref, name: is-vip } }
```

`scope = DefinitionScope.LIBRARY` делает имя общим для всех плагинов (закрывайте handle при выключении плагина), `conflict = FAIL | KEEP | REPLACE` решает, что делать с занятым именем. Имя модуля перекрывает общее.

## Ловить ошибки по типу

`attempt` умеет разные реакции на коды ошибок:

```yaml
- type: attempt
  do: [ { type: charge, currency: money, amount: 100 } ]
  on:
    - codes: [currency.insufficient-funds]
      do: [ { type: message, messages: ['&cНужно 100 монет'] } ]
    - codes: ['currency.*']
      do: [ { type: message, messages: ['&cБанк не отвечает'] } ]
  fail: [ { type: message, messages: ['&cОшибка: {error}'] } ]
```

Ещё: `set` (запомнить значение), `random` (выбор с весами).

## Откат в конфиге

Откат можно выключить прямо в конфиге: `undo: false` у `attempt` (блок ничего не откатывает) или у `charge` / `give` (эта операция не откатывается).

```yaml
- type: attempt
  undo: false          # деньги и всё остальное внутри останутся, даже если шаг упал
  do: [ { type: charge, currency: money, amount: 100 }, { type: command, command: 'risky' } ]
  fail: [ { type: message, messages: ['&cОшибка'] } ]
- { type: charge, currency: money, amount: 50, undo: false }   # только эта операция не откатится
```
