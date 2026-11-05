# Плейсхолдеры

Плейсхолдер — именованное значение, которое плагин отдаёт другим плагинам и текстам. Есть два способа писать их:

- **дерево** (рекомендуется): значения описываются как ветки с путями и аргументами, один блок на модуль;
- **ключи** (`ModuleContext.placeholders.register(...)`): по одному плейсхолдеру, ниже в разделе «Ключи».

Рабочий пример на все возможности: [placeholders-example.md](placeholders-example.md). Устройство и решения:
[placeholders-plan.md](placeholders-plan.md).

## Адрес

Полный адрес: `плагин/модуль:путь`, например `pnclans/war:score.Alex.kills`. Внутри своего плагина можно коротко:
`{score}` (ищется во всех модулях плагина). В тексте адрес пишут в фигурных скобках: `{pnclans/war:score}`.

В PlaceholderAPI тот же плейсхолдер виден как `%префикс_модуль~путь%`, если его группа опубликована
(`placeholders(papi = "clans") { ... }`): `%clans_war~score.Alex%`. Ветки с `inside()` наружу не выходят.

## Дерево

```kotlin
module.placeholders(papi = "clans") {
    int("score") {                         // {pnclans/war:score}
        value { clans.scoreOf(player) }    // player: для кого рендерим, может быть null
        player("who") {                    // {pnclans/war:score.Alex}   ник или UUID
            value { clans.scoreOf(arg) }
            int("kills") { value { clans.killsOf(arg) } }
        }
        int("top") {                       // {pnclans/war:score.top}
            value { clans.topScore(1) }
            param("place", int, range = 1..100) {   // {pnclans/war:score.top.3}
                value { clans.topScore(arg) }
            }
        }
    }
}
```

Слово ветки: `int`, `number`, `text`, `bool`, `value(name, Class)`. Аргумент из пути: `player(name)`, `param(name, тип)`,
`param(name, int, range)`, `choice(name, "a", "b")`. Внутри ветки:

| Что | Для чего |
|---|---|
| `value { ... }` | значение; `null` значит «нет значения». `valueAsync` — через `CompletionStage` |
| `arg`, `argOf("имя")` | последний аргумент пути, или любой по имени |
| `player`, `caller`, `values` | для кого рендерим, какой плагин спрашивает, значения от вызывающего |
| `format("имя")`, `format(none)` | формат ветки |
| `access { ... }` | кто может читать |
| `update(allowOnly("плагин")) { текст -> ... }` | разрешить запись значения |
| `inside()` | только для `{...}`, в PlaceholderAPI не публиковать |

Ветка без `value` только группирует: `{score}` ничего не вернёт, а `{score.kills}` вернёт.

## Генерация из данных

| Вызов | Что получится |
|---|---|
| `from<Clan>("clan") { clans.clanOf(player) }` | ветка на каждое свойство класса: `clan.name`, `clan.tag`; вложенный объект раскрывается на один уровень: `clan.leader.name` |
| `from<Clan>("clanof", by = "who") { clans.clanOf(arg) }` | то же, но игрок берётся из пути: `clanof.Alex.name` |
| `table("price", keys = { shop.items() }) { item -> shop.priceOf(item) }` | `price.sword`, `price.apple` |
| `list("top5", size = { 5 }) { place -> clans.topName(place) }` | `top5.1` … `top5.5` |

У результата `from` можно убрать лишнее и переименовать: `.only(Clan::name, Clan::members).rename(Clan::members, "size")`.
Простыми считаются поля `java.*`, `kotlin.*`, `net.kyori.*`, перечисления и массивы; остальное раскрывается.

## Форматы

Формат превращает значение в текст: `{score|group}` даёт `1 234`. Они выстраиваются цепочкой: `{score|round:1|pad:6}`.

Встроенные: `number`, `round`, `percent`, `money`, `group`, `short`, `duration`, `date`, `upper`, `lower`, `title`,
`capitalize`, `pad`, `trim`, `default`, `plural`, `boolean`. Аргументы пишутся после `:` через запятую: `{score|round:1}`,
`{score|pad:5,0}`.

Свои форматы модуля:

```kotlin
module.formats {
    format<Int>("rank") { "#$it" }
    format<Int>("pad0") { n, args -> n.toString().padStart(args.first().toInt(), '0') }
}
```

Чужой формат вызывается по адресу: `{pnshop/main:price|pnclans/war:rank}`, с аргументами `|pnclans/war:pad0:3`.

Какой формат применится, решают четыре уровня, каждый следующий сильнее: нет формата → формат группы
(`format("group")` в блоке) → формат ветки → формат в тексте. `format(none)` отключает формат по умолчанию,
`|raw` в тексте отключает его для одного значения.

## Доступ

По умолчанию читать может любой плагин библиотеки. Меняется в `access { ... }` на группе или ветке:
`allowAll()`, `denyAll()`, `allowOnly("pnshop", "pnbank")`, `deny("pnspy")`. Запрет всегда сильнее разрешения. Отказ не
ломает текст: плейсхолдер остаётся в нём как есть, причина попадает в `/pn events`.

## Чтение и подстановка

`ModuleContext.placeholders` (`PlaceholderService`) читает и подставляет:

| Метод | Что делает |
|---|---|
| `resolve(expression, playerId, values)` | значение одного плейсхолдера; `null`, если он неизвестен |
| `render(template, playerId, values)` | подставляет все плейсхолдеры в текст: сначала `{...}`, потом остаток `%...%` через PlaceholderAPI |
| `update(expression, value, playerId, values)` | меняет записываемое значение |
| `contains(expression)` | есть ли такой видимый плейсхолдер |
| `provider(pluginId)` | доступ к ключам одного плагина |

Все методы возвращают `CompletionStage` и безопасны из любых потоков. Резолверы и форматы могут вызываться параллельно,
поэтому должны быть потокобезопасны. Неизвестный плейсхолдер остаётся в тексте как есть и один раз попадает в журнал.

Встроенные плейсхолдеры библиотеки (`pnlibrary/main:`): `version`, `plugins`, `uptime` (секунды), `stats.errors`, `stats.warnings`, `stats.tasks`, `stats.tasks.failed`, `stats.events`.

## Команда `/pn placeholders`

Нужно право `pnlibrary.placeholders`.

- `/pn placeholders [страница]` — список всех плейсхолдеров с типом, форматом и именами в PlaceholderAPI;
- `/pn placeholders test <адрес> [uuid игрока]` — посчитать значение и показать, что получилось;
- то же есть на странице `/pn gui`.

## Ключи

Ключ типизирован: `PlaceholderKey.of("balance", Long::class.java)`. Значение настраивается в билдере
(`PlaceholderBuilder`): откуда брать, кто может читать (`PlaceholderAccess`), как кэшировать (`PlaceholderCachePolicy`,
`PlaceholderCacheScope`).

```kotlin
val registration = context.placeholders.register("balance", Long::class.java) { builder ->
    // настройка резолвера, доступа и кэша
}
// registration.close() снимает плейсхолдер
```

Закрытие `PlaceholderService` снимает всё, что зарегистрировано через него.

В ключе можно объявить параметры в фигурных скобках: `player.stat.{name}` совпадёт с `player.stat.kills`, а резолвер
получит `request.parameter("name")`. Параметр занимает ровно один сегмент между точками.

Если плейсхолдер опубликован в PlaceholderAPI (`publishToPlaceholderApi()`), всё после ключа приходит одним значением:
`%pnrelog_player_Steve_Alex%` вызовет плейсхолдер `player`, а резолвер получит `request.parameter("value")` = `Steve_Alex`.
Выбирается самый длинный подходящий ключ, поэтому ключи не должны без нужды начинаться друг с друга.

Именованный формат для значений типа: `formatter(name, type, formatter)`. Адаптеры к внешним системам (например,
PlaceholderAPI): `adapters()`; возможности и состояние описывают `PlaceholderAdapterCapabilities` и `PlaceholderAdapterState`.
