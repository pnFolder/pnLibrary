# Команды в pnLibrary

Одно описание команды работает на Bukkit, Spigot, Paper, Folia, BungeeCord и Velocity. Регистрировать её в
`plugin.yml` не нужно: pnLibrary делает это сама. На современном Paper команда регистрируется ещё и через родную
систему Brigadier, и клиент сам проверяет аргументы и подсказывает.

Этот файл самодостаточный: сначала идея, потом полный рабочий пример, потом справочник.

## 1. Идея в одном абзаце

Вы описываете дерево команды: слова (`literal`) и аргументы (`argument`) с типами. Если игрок ввёл что-то не так, у
него нет права, он не в сети, или ваша команда сама решила, что продолжать нельзя, возникает **ошибка**. Ошибка это
обычный класс (`CommandError`). Вы подписываетесь на **тип** ошибки и отвечаете как хотите: текстом, звуком, action bar,
записью в лог. Если вы не ответили, отвечает стандарт библиотеки из `config.yml`.

## 2. Полный пример

Ниже весь файл целиком, без пропусков. Чтобы попробовать, скопируйте его в свой плагин с `depend: [pnLibrary]`
и укажите в `plugin.yml` `main: com.example.shop.ShopExamplePlugin`. Команды в `plugin.yml` прописывать не нужно.

```kotlin
package com.example.shop

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.pnfolder.pnlibrary.api.audiences.LibraryPlayer
import org.pnfolder.pnlibrary.api.commands.ArgumentType
import org.pnfolder.pnlibrary.api.commands.BadUsage
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandError
import org.pnfolder.pnlibrary.api.commands.CommandRegistration
import org.pnfolder.pnlibrary.api.commands.InvalidArgument
import org.pnfolder.pnlibrary.api.commands.NoPermission
import org.pnfolder.pnlibrary.api.commands.PlayerNotFound
import org.pnfolder.pnlibrary.api.commands.ensure
import org.pnfolder.pnlibrary.api.plugin.onCommandError
import org.pnfolder.pnlibrary.api.runtime.PnLibraryProvider
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** An error of this plugin. It is an ordinary class, so a handler gets real fields, not a map of strings. */
class NoMoney(val price: Int) : CommandError() {
    override fun placeholders() = mapOf("price" to price) // for texts such as "Нужно {price} монет"
}

/**
 * A complete example: a small shop plugin that shows every level at which a command can answer an error.
 *
 * Nothing is declared in plugin.yml for the commands: pnLibrary registers them itself.
 */
class ShopExamplePlugin : JavaPlugin() {
    private val balances = ConcurrentHashMap<UUID, Int>()
    private var shop: CommandRegistration? = null
    private val prices = mapOf("sword" to 50, "apple" to 2, "bow" to 35)

    override fun onEnable() {
        val library = PnLibraryProvider.getOrNull()
        if (library == null) {
            logger.severe("pnLibrary is not available; disabling the shop")
            server.pluginManager.disablePlugin(this)
            return
        }

        // 1. Register this plugin and one module of it. Every later level hangs off these two objects.
        val registration = library.plugins.register(this)
        val module = registration.registerModule("shop")

        // 2. PLUGIN level: how every command of this plugin, in every module, answers a sender without permission.
        //    In the lambda `this` is the command context, so `sender` is right there.
        registration.onCommandError<NoPermission> {
            sender.send(Component.text("[Магазин] У вас нет доступа к этой команде.", NamedTextColor.RED))
        }

        // 3. MODULE level: answers for all commands of this module. A text may use the error's placeholders.
        module.onCommandError<PlayerNotFound>("Игрока {value} нет на сервере.")
        module.onCommandError<NoMoney> { error ->
            sender.send(Component.text("Не хватает монет: нужно ${error.price}.", NamedTextColor.GOLD))
        }

        // 4. The command itself. No plugin.yml entry is needed.
        shop = module.registerCommand("shop") {
            aliases("магазин")

            // PERMISSION CHECK. You only declare the permission; pnLibrary checks it itself, BEFORE any code below
            // runs. A sender without "shop.use" never reaches `executes`: the library raises NoPermission("shop.use")
            // and it is answered by the levels below (here: the plugin level, step 2).
            permission("shop.use")

            // COMMAND level: wins over the module, the plugin and the library for everything below.
            // A handler for a base class also answers its subclasses: BadUsage covers a wrong argument, an
            // unknown word and an unfinished command; the more specific InvalidArgument is answered first.
            onError<InvalidArgument>("«{value}» не подходит для «{argument}». Попробуйте: {usage}")
            onError<BadUsage> { error -> sender.send(Component.text("Магазин: ${error.usage}", NamedTextColor.YELLOW)) }

            executes { it.sender.send(Component.text("Команды: /shop buy <товар> [кол-во], /shop give <игрок> <монеты>")) }

            literal("buy") {
                playerOnly() // the console gets the library's "only in game" answer on its own

                // choice(...) is checked and suggested by the client on modern Paper, and suggested everywhere else.
                argument("item", ArgumentType.choice("sword", "apple", "bow")) {
                    executes { buy(it, 1) }

                    // The bounds are checked before the command runs; 0 or 100 is an InvalidArgument.
                    argument("amount", ArgumentType.integer(1, 64)) {
                        executes { buy(it, it.get<Int>("amount")) }
                    }
                }
            }

            literal("give") {
                // The same check, on a subcommand only: /shop give needs "shop.admin" on top of "shop.use".
                permission("shop.admin")

                // SUBCOMMAND level: the most specific one. Text, sound, anything.
                onError<NoPermission> {
                    sender.send(Component.text("Выдавать монеты могут только администраторы.", NamedTextColor.RED))
                    sender.playSound(Sound.sound(Key.key("entity.villager.no"), Sound.Source.MASTER, 1f, 1f))
                }

                // A name nobody answers to becomes PlayerNotFound, which the module answers above.
                val onlinePlayer = ArgumentType.player { name ->
                    Bukkit.getPlayerExact(name)?.let { library.audiences.player(it.uniqueId) }
                }
                argument("player", onlinePlayer) {
                    argument("coins", ArgumentType.integer(1, 1000)) {
                        executes { context ->
                            val target = context.get<LibraryPlayer>("player")
                            val coins = context.get<Int>("coins")
                            balances.merge(target.uniqueId, coins, Int::plus)
                            context.sender.send(Component.text("${target.name} получил $coins монет."))
                        }
                    }
                }
            }

            literal("reload") {
                consoleOnly() // a player gets the library's "console only" answer
                executes { it.sender.send(Component.text("Настройки магазина перезагружены.")) }
            }
        }
    }

    /** Takes the coins; with too little money the command stops with our own error, and the module answers it. */
    private fun buy(context: CommandContext, amount: Int) {
        val buyer = UUID.fromString(context.sender.id)
        val item = context.get<String>("item")

        // A permission check made in code, for when it depends on what was asked for: only VIPs may buy the bow.
        // `ensure(condition) { error }` reads "this must be true, otherwise stop with this error". Here it must be
        // true that the sender has "shop.vip"; the check applies only when the item is the bow.
        // It raises the very same NoPermission error, so the same five levels answer it, and the command stops here.
        if (item == "bow") {
            context.ensure(context.sender.hasPermission("shop.vip")) { NoPermission("shop.vip") }
        }

        val price = prices.getValue(item) * amount
        val balance = balances.getOrDefault(buyer, 100)

        context.ensure(balance >= price) { NoMoney(price) }

        balances[buyer] = balance - price
        context.sender.send(Component.text("Куплено за $price. Осталось: ${balance - price}."))
    }

    override fun onDisable() {
        shop?.close()
    }
}
```

### Что в этом примере происходит

| Что ввёл игрок | Какая ошибка | Кто отвечает |
|---|---|---|
| `/shop buy sword 99` (число вне 1–64) | `InvalidArgument` | команда: ««99» не подходит для «amount». Попробуйте: /shop buy <item> <amount>» |
| `/shop zzz` | `UnknownArgument` (это `BadUsage`) | команда, через обработчик `BadUsage` |
| `/shop buy` | `IncompleteCommand` (это `BadUsage`) | команда, через обработчик `BadUsage` |
| `/shop give Vasya 10`, Васи нет | `PlayerNotFound` | модуль: «Игрока Vasya нет на сервере.» |
| `/shop give …` без права `shop.admin` | `NoPermission` | подкоманда: текст и звук |
| `/shop` без права `shop.use` | `NoPermission` | плагин: «[Магазин] У вас нет доступа…» |
| `/shop buy bow` без права `shop.vip` (проверка в коде) | `NoPermission` | плагин: «[Магазин] У вас нет доступа…» |
| `/shop buy bow 5` и не хватает денег | `NoMoney` (своя ошибка) | модуль: «Не хватает монет: нужно 175.» |
| консоль: `/shop buy sword` | `PlayerOnly` | стандарт библиотеки из `config.yml` |
| игрок: `/shop reload` | `ConsoleOnly` | стандарт библиотеки из `config.yml` |

## 3. Пять уровней ответа

Побеждает самый конкретный уровень, у которого есть ответ:

1. **Подкоманда**: `literal("give") { onError<NoPermission> { … } }`
2. **Команда**: `registerCommand("shop") { onError<InvalidArgument>("…") }`
3. **Модуль плагина**: `module.onCommandError<NoMoney> { … }`
4. **Весь плагин**: `registration.onCommandError<NoPermission> { … }`
5. **Библиотека**: стандартные тексты и звук в `config.yml`

На одном уровне обработчик ближайшего класса побеждает: `InvalidArgument` отвечает раньше, чем `BadUsage`.
Обработчик на подкоманде действует и на всё, что ниже неё. Ошибка в вашем обработчике пишется в лог и в журнал, а
игрок получает ответ следующего уровня, а не текст исключения.

## 4. Встроенные ошибки

| Класс | Когда | Поля |
|---|---|---|
| `NoPermission` | нет права или узел скрыт правилом доступности | `permission` |
| `BadUsage` | база для трёх ниже | `usage` |
| `UnknownArgument` | неизвестное или лишнее слово | `usage`, `value` |
| `IncompleteCommand` | команда не дописана | `usage` |
| `InvalidArgument` | аргумент не разобрался | `usage`, `argument`, `value` |
| `PlayerNotFound` | игрока нет в сети (это `InvalidArgument`) | как у `InvalidArgument` |
| `PlayerOnly` | команда только для игроков, пишет консоль | |
| `ConsoleOnly` | команда только для консоли, пишет игрок | |
| `OnCooldown` | слишком частое использование (поднимаете сами) | `remaining` |
| `CommandFailed` | база для всего остального и последний запас | |

## 5. Свои ошибки

```kotlin
class NoMoney(val price: Int) : CommandError() {
    override fun placeholders() = mapOf("price" to price)   // для текстов "Нужно {price} монет"
}

class ShopClosed : CommandError("Магазин закрыт")           // если никто не ответит, скажет это
```

Поднять ошибку внутри команды:

```kotlin
executes { context ->
    context.ensure(balance >= price) { NoMoney(price) }      // как require/check; ошибка создаётся, только если нужна
    if (closed) context.fail(ShopClosed())                   // безусловно
}
```

Без `throw` и без `try/catch`: библиотека сама находит ошибку, смотрит её тип и идёт по уровням. Обычные исключения
(скажем, `NullPointerException`) этим путём не идут: они пишутся в лог, а игрок видит «Command execution failed.».

## 6. Как ответить

В Kotlin внутри лямбды `this` это контекст команды, поэтому `sender` под рукой, а `error` имеет свои типизированные поля:

```kotlin
onError<NoPermission> { error ->
    sender.send(Component.text("Нужно право ${error.permission}"))
    sender.playSound(Sound.sound(Key.key("entity.villager.no"), Sound.Source.MASTER, 1f, 1f))
}
onError<InvalidArgument>("«{value}» не подходит для «{argument}». {usage}")   // текстом
```

В Java:

```java
builder.onError(NoPermission.class, (context, error) -> context.getSender().send(Component.text("Нет права")));
throw new CommandFailure(new NoMoney(50));
```

В тексте можно использовать `{имя}` из полей ошибки: `{permission}`, `{usage}`, `{argument}`, `{value}`, `{seconds}` и свои из
`placeholders()`.

## 7. Типы аргументов

| Фабрика | Что принимает | Что делает клиент на современном Paper |
|---|---|---|
| `ArgumentType.string()` | одно слово | подсказывает слово |
| `ArgumentType.integer()`, `integer(1, 64)` | целое, с границами или без | проверяет число и границы |
| `ArgumentType.long(…)`, `decimal(…)` | то же для long и дробных | то же |
| `ArgumentType.boolean()` | `true`/`false`/`yes`/`no`/`on`/`off`/`1`/`0` | подсказывает `true` и `false` |
| `ArgumentType.choice("a", "b")` | одно из значений без учёта регистра | подсказывает значения |
| `ArgumentType.enumeration(MyEnum::class.java)` | константа enum | подсказывает константы |
| `ArgumentType.player { name -> … }` | игрок, которого вы находите сами | при неудаче `PlayerNotFound` |

На платформах без Brigadier подсказки для `choice`, `boolean` и `enumeration` тоже работают, их даёт сама библиотека.
Свой тип: реализуйте `ArgumentType<T>` (метод `parse`), при желании переопределите `error(...)`, чтобы при неудаче
возвращалась ваша ошибка, а не `InvalidArgument`.

## 8. Проверка права

Проверку права писать не нужно, её делает библиотека. Есть два способа, и оба приходят к одной и той же ошибке `NoPermission`,
которую отвечают те же пять уровней.

**Объявить право.** Библиотека проверяет его сама ещё до вашего кода:

```kotlin
registerCommand("shop") {
    permission("shop.use")                 // нет права: до executes дело не доходит, отвечает NoPermission("shop.use")
    literal("give") {
        permission("shop.admin")           // та же проверка, но только для этой подкоманды
        executes { /* сюда попадёт только тот, у кого есть и shop.use, и shop.admin */ }
    }
}
```

**Проверить в коде.** Нужно, если право зависит от того, что именно попросили (например, купить можно только некоторым игрокам):

```kotlin
val item = context.get<String>("item")
if (item == "bow") {
    context.ensure(context.sender.hasPermission("shop.vip")) { NoPermission("shop.vip") }
}
```

`ensure(условие) { ошибка }` читается как «это должно быть верно, иначе остановиться с этой ошибкой». Здесь должно быть верно, что у отправителя есть
`shop.vip`, а проверка применяется, только если выбран лук. Иначе команда останавливается на этой строке, а игрок получает ответ на
`NoPermission` с того уровня, который его задал.

## 9. Что ограничивает отправителя

- `playerOnly()`: консоль получает `PlayerOnly`.
- `consoleOnly()`: игрок получает `ConsoleOnly`.
- `permission("…")`: нет права, `NoPermission`. Консоль можно освободить: `consoleBypassesPermission()`.
- `noPermissionMessage("текст")`: короткая форма для текста `NoPermission` на этом узле.

Право, не объявленное в `plugin.yml`, по умолчанию получают только операторы. Если право должно быть у всех, выдайте
его через плагин прав или объявите в своём `plugin.yml`.

## 10. Стандарт библиотеки

В `config.yml` настраивается один текст:

```yaml
no-permission-message: Недостаточно прав для этой команды.
```

Остальные стандартные ответы («Использование», «Неверное значение», «Игрок не в сети», «Только в игре», «Только из консоли»,
«Команда не выполнилась») заданы в коде. Они оформлены так же, как остальные сообщения библиотеки: значок и цвет, а в строке
использования `<аргументы>` выделены, и по клику команда вставляется в чат. Свой ответ любой плагин или команда задаёт обработчиком
`onError`.

Это последний уровень цепочки: он отвечает, только если никто выше не ответил. У ошибки без собственного текста берётся текст
ближайшего базового класса, а для совсем незнакомой ошибки: «Не удалось выполнить команду.».

### Описание команды

`description("строка", "ещё строка")` на узле объясняет, что команда делает. Игрок, который остановился на этом узле (ввёл команду не
до конца), видит эти строки под строкой использования. Если у вложенных слов тоже есть описание, они перечисляются как
«слово — что делает».

```kotlin
literal("create") {
    description("Создаёт пустую группу серверов. Имя любое, до 32 символов, без пробелов.")
    argument("name", ArgumentType.string()) { executes { /* … */ } }
}
```

## 11. Paper и Brigadier

На Paper с Java 21 команды дополнительно регистрируются через Brigadier. Из вашего дерева строится родное дерево: клиент
проверяет числа и границы и подсказывает значения. Права, ответы и выполнение остаются за pnLibrary, поэтому всё ведёт себя
так же, как на других платформах. Что нужно знать:

- Любой плагин с обработчиком этого события выключает на сервере `/reload`. Это правило Paper. Чтобы отказаться от
  нативного пути, запустите сервер с `-Dpnlibrary.native-commands=false`.
- Команды, зарегистрированные после того, как Paper построил своё дерево, работают через прежний путь без клиентской проверки.
- Клиентская проверка зависит от регистра: `/pn events ERROR` не пройдёт, нужно `error`.
- Если Brigadier недоступен (старый Paper, Spigot, Java ниже 21), всё работает прежним путём.
