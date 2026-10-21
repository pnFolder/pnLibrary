# Действия

Действия делают что-то с игроком: пишут в чат, играют звук, выполняют команду. Они выполняются сверху вниз. Проверки («можно или нельзя») описаны отдельно, в [checks.md](checks.md).

У каждого действия есть `type`, его настройки пишутся под ним:

```yaml
join:
  - type: message
    message: Привет, %player_name%

  - type: title
    title: Привет
    subtitle: '%player_name%'
    stay: 3s

  - type: delay
    duration: 2s
    do:
      - type: sound
        sound: entity.player.levelup
```

В тексте работают плейсхолдеры PlaceholderAPI (`%player_name%`) и значения, которые плагин передал в действие (`[[имя]]`).

## Действия

| `type` | Что делает | Настройки |
|---|---|---|
| `message` | сообщение игроку | `message` (строка) или `messages` (список), `target: all_players` — всем |
| `actionbar` | текст над панелью предметов | `text` |
| `title` | большой заголовок | `title`, `subtitle`, `fade-in`, `stay`, `fade-out` |
| `sound` | звук | `sound`, `volume`, `pitch` |
| `console` | команда от консоли | `command` без `/` |
| `player` | команда от игрока | `command` без `/` |
| `log` | запись в журнал сервера | `text`, `level` |
| `effect` | эффект зелья | см. `EffectAction` |
| `particle` | частицы | см. `ParticleAction` |
| `run` | выполнить именованный шаблон | `name` |
| `stop` | остановить всю цепочку | нет |

## Обёртки

Обёртка управляет действиями, которые лежат у неё в `do:`: когда они выполнятся, с каким шансом, при каком условии и сколько раз. Обёртки вкладываются друг в друга.

| `type` | Настройки | Что делает |
|---|---|---|
| `delay` | `duration`, `do` | выполняет `do` после паузы; без `do` — ставит на паузу всё, что ниже |
| `chance` | `probability` (`10%`), `do`, `else` | выполняет `do` с шансом, иначе `else` |
| `if` | `check`, `do`, `else` | выполняет `do`, если проверка прошла, иначе `else`; список дальше идёт в любом случае |
| `require` | `check`, `deny` | если проверка не прошла — выполняет отказ и **останавливает** цепочку |
| `repeat` | `times`, `every`, `do` | выполняет `do` `times` раз (до 1000), первый раз сразу |
| `group` | `do` | собирает несколько действий в одно |

```yaml
buy:
  - type: require          # «нет» → отказ проверки и стоп
    check: has-100
  - type: console
    command: give %player_name% diamond 1
  - type: if               # «нет» → else, дальше идём
    check: vip
    do:
      - type: message
        message: Бонус VIP
    else:
      - type: message
        message: Купите VIP
  - type: chance
    probability: 10%
    do:
      - type: message
        message: Вам повезло!
  - type: repeat
    times: 3
    every: 1s
    do:
      - type: sound
        sound: block.note_block.pling
```

`check:` принимает имя проверки из блока `checks:` или проверку прямо на месте (`type: permission` и т. д.). Подробно — в [checks.md](checks.md).

## Короткая запись строкой

Для одного действия можно писать строку, как в DeluxeMenus. Это то же самое, что запись с `type`.

```yaml
- '[message] &aПривет, %player_name%!'
- '[console] give %player_name% diamond 1'
- '[message] Через секунду<delay=20>'
- '[broadcast] Кто-то купил алмаз!<chance=10>'
- '[wait 2s]'
```

| Тег | То же самое, что |
|---|---|
| `[message]`, `[broadcast]` | `message` игроку / всем |
| `[minimessage]`, `[minibroadcast]`, `[json]`, `[jsonbroadcast]` | `message` в формате MiniMessage или JSON |
| `[actionbar]` | `actionbar` |
| `[console]`, `[player]`, `[commandevent]` | `console`, `player` |
| `[sound]`, `[broadcastsound]` | `sound`: `название громкость высота` |
| `[title]`, `[broadcasttitle]` | `title`: `заголовок;подзаголовок;появление;показ;исчезновение` (тики) |
| `[log]` | `log` |
| `[run] имя` | `run` |
| `[stop]` | `stop` |
| `[wait 2s]` | `delay` без `do`: всё ниже ждёт |
| `<delay=20>`, `<delay=2s>` в конце строки | `delay` вокруг этой строки |
| `<chance=25>` в конце строки | `chance` вокруг этой строки |

Звук можно писать как в Bukkit (`ENTITY_VILLAGER_NO`) или ключом Minecraft (`entity.villager.no`). Если в названии есть слова через подчёркивание (`experience_orb`), пишите ключом.

## Шаблоны

Одинаковые куски описываются один раз и вызываются по имени: `type: run` с `name: refuse` или `[run] refuse`. Шаблоны передаёт плагин. Неизвестное имя даёт предупреждение с подсказкой, зацикливание останавливается на 16 уровнях.

## Ошибки в конфиге

Ошибка говорит, где она и что не так, и подсказывает при опечатке:

```
Invalid configuration value at buy[1]: Unknown action tag [mesage]. Did you mean [message]?
```

## Из кода

```kotlin
class ShopConfig(
    val checks: Map<String, Requirement> = emptyMap(),
    val buy: List<Action> = emptyList(),
)

val context = module.actions.context(player, allPlayers, templates = config.templates, checks = config.checks)
context.execute(config.buy)
```
