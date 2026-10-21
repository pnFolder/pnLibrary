# Действия

Действие — это строка в конфиге. Она начинается с тега в квадратных скобках, дальше идут аргументы. Строки выполняются по порядку.

```yaml
click_commands:
  - "[message] &aПривет, %player_name%!"
  - "[sound] ENTITY_PLAYER_LEVELUP 1 1"
  - "[console] give %player_name% diamond 1"
  - "[message] &7Это сообщение придёт через секунду<delay=20>"
  - "[broadcast] &eКто-то купил алмаз!<chance=10>"
```

Так же пишутся плейсхолдеры: `%player_name%` и любой другой плейсхолдер PlaceholderAPI. Внутри строки можно писать и `[[имя]]`, это значение, которое плагин передал в действие.

## Теги

| Тег | Что делает | Аргументы |
|---|---|---|
| `[message]` | сообщение игроку | текст |
| `[broadcast]` | сообщение всем игрокам | текст |
| `[minimessage]`, `[minibroadcast]` | то же, текст в формате MiniMessage | текст |
| `[json]`, `[jsonbroadcast]` | то же, текст в формате JSON | текст |
| `[actionbar]` | текст над панелью предметов | текст |
| `[console]` | команда от консоли | команда без `/` |
| `[player]`, `[commandevent]` | команда от игрока | команда без `/` |
| `[sound]` | звук игроку | `название громкость высота` |
| `[broadcastsound]` | звук всем игрокам | `название громкость высота` |
| `[log]` | запись в журнал сервера | текст |
| `[stop]` | остановить всю цепочку | нет |
| `[wait 2s]` | пауза: всё ниже в этом списке ждёт | время: тики (`40`) или секунды (`2s`) |
| `[title]`, `[broadcasttitle]` | большой заголовок игроку или всем | `заголовок;подзаголовок;появление;показ;исчезновение` |
| `[run]` | выполнить именованный шаблон | имя шаблона |

Название звука можно писать как в Bukkit (`ENTITY_VILLAGER_NO`) или ключом Minecraft (`minecraft:entity.villager.no`). Если в названии есть слова, склеенные подчёркиванием (`experience_orb`), пишите ключом: `entity.experience_orb.pickup`.

Громкость и высота необязательны, по умолчанию оба `1`.

## Задержка и шанс

В конце любой строки можно добавить теги в угловых скобках:

| Тег | Значение |
|---|---|
| `<delay=20>` | подождать 20 тиков (20 тиков = 1 секунда) |
| `<delay=2s>` | подождать 2 секунды |
| `<chance=25>` | выполнить с шансом 25 % |

Теги можно писать вместе: `"[message] Привет<delay=40><chance=50>"`. Шанс считается отдельно для каждой строки: две строки по 50 % могут сработать обе, одна или ни одной.

## Пауза: `[wait]`

`<delay=…>` откладывает одну строку. `[wait]` откладывает всё, что написано ниже, поэтому последовательность читается сверху вниз:

```yaml
- "[message] &eТри"
- "[wait 1s]"
- "[message] &eДва"
- "[wait 1s]"
- "[message] &eОдин"
```

Пауза действует только на список, в котором стоит. Если пауза внутри шаблона, то ждёт остаток шаблона, а вызвавший его список идёт дальше.

## Заголовок: `[title]`

```yaml
- "[title] &aПобеда;&7Награда выдана"             # время по умолчанию: 10, 70, 20 тиков
- "[title] &6Босс;&cПобеди его;5;100;10"           # появление, показ, исчезновение в тиках
- "[broadcasttitle] &eСтарт ивента"                 # всем игрокам
```

На прокси (BungeeCord, Velocity) заголовки не показываются: плагин напишет предупреждение в журнал и пойдёт дальше.

## Шаблоны: `[run]`

Одинаковые строки не нужно копировать. Опишите их один раз в своём конфиге и вызывайте по имени:

```yaml
templates:
  отказ:
    - "[sound] ENTITY_VILLAGER_NO"
    - "[actionbar] &cНевозможно"

buy:
  - "[run] отказ"
```

Плагин передаёт шаблоны в `module.actions.context(player, allPlayers, templates = config.templates)`. Неизвестное имя даёт предупреждение с подсказкой. Шаблоны могут вызывать друг друга до 16 уровней, дальше вызов отклоняется. `[stop]` внутри шаблона останавливает всю цепочку.

## Требования

Требования проверяют условия и решают, что выполнять. Каждое требование имеет имя. Они проверяются по порядку.

```yaml
- type: requirements
  requirements:
    места_хватает:
      type: ">="
      input: "%player_empty_slots%"
      output: 1
      deny_commands:
        - "[message] &cОсвободите место в инвентаре"
    денег_хватает:
      type: ">="
      input: "%vault_eco_balance%"
      output: 100
      deny_commands:
        - "[message] &cНужно 100 монет"
  deny_commands:
    - "[sound] ENTITY_VILLAGER_NO"
  success_commands:
    - "[console] eco take %player_name% 100"
    - "[console] give %player_name% diamond 1"
    - "[message] &aГотово!"
```

Первое не прошедшее требование выполняет свои `deny_commands`, потом общие, и вся цепочка останавливается: следующие после него строки уже не выполнятся. Если прошли все, выполняются `success_commands` требований, потом общие.

| Ключ | Где | Значение |
|---|---|---|
| `requirements` | требования | именованные требования |
| `deny_commands` | требование, общий | действия при отказе |
| `success_commands` | требование, общий | действия при успехе |
| `optional` | требование | не обязано проходить, но считается в `minimum_requirements` |
| `minimum_requirements` | общий | сколько необязательных требований должно пройти |
| `stop_at_success` | общий | когда нужное число набрано, остальные необязательные не проверяются |

### Короткая запись

Когда не нужен ни `type`, ни `input`/`output`, условие можно написать строкой через `if`, а отказ одной фразой через `deny`:

```yaml
- type: requirements
  requirements:
    деньги:
      if: "%vault_eco_balance% >= 100"
      deny: "&cНужно 100 монет"
    права:
      if: "permission shop.use"
      deny:
        - "&cНет доступа"
        - "[sound] ENTITY_VILLAGER_NO"
    трезвый: "%drunk% == 0"          # только условие, без отказа
  success_commands:
    - "[message] &aГотово!"        # у самого действия ключи прежние
```

| Что написано | Как читается |
|---|---|
| `%a% >= 5` | сравнение чисел: `==`, `!=`, `>=`, `<=`, `>`, `<` |
| `%rank% is vip` | текст равен, без учёта регистра |
| `%name% contains Al` | текст содержит |
| `%code% matches [A-Z]{2}-\d+` | весь текст подходит под выражение |
| `permission shop.use` | у игрока есть право |
| `!…` или `not …` перед любым из них | наоборот |

`deny` и `success` работают только внутри требования. `deny` и `success`: одна строка без тега это сообщение игроку, строка с тегом (`[sound] …`) это действие, список может смешивать оба вида. Полная форма с `deny_commands` и `success_commands` остаётся, но писать вместе `deny` и `deny_commands` нельзя, как и `if` вместе с `type`.

### Необязательные требования

Пример с необязательными требованиями. Хватит одного из двух:

```yaml
- type: requirements
  minimum_requirements: 1
  requirements:
    vip:
      type: has permission
      permission: shop.vip
      optional: true
    staff:
      type: has permission
      permission: shop.staff
      optional: true
  deny_commands:
    - "[message] &cТолько для VIP или персонала"
```

Чтобы перевернуть условие, поставьте `!` перед типом и возьмите значение в кавычки:

```yaml
type: "!has permission"
permission: shop.banned
```

## Типы требований

Название ключей можно писать как угодно: `name_contains`, `name-contains` и `nameContains` одно и то же.

| `type` | Ключи | Что проверяет |
|---|---|---|
| `has permission` | `permission` | одно право |
| `has permissions` | `permissions`, `minimum` | все права или не меньше `minimum` |
| `has item` | `material`, `amount`, `name`, `name_contains`, `lore_contains`, `name_ignorecase` | в инвентаре есть такие предметы |
| `has exp` | `amount`, `level` | очки опыта, а с `level: true` уровни |
| `is near` | `location` (`мир,x,y,z`), `distance` | игрок рядом с точкой |
| `string equals` | `input`, `output` | значения равны |
| `string equals ignorecase` | `input`, `output` | равны без учёта регистра |
| `string contains` | `input`, `output` | `input` содержит `output` |
| `string length` | `input`, `min`, `max` | длина значения |
| `is object` | `input`, `object` (`INT`, `DOUBLE`, `UUID`) | значение читается как этот тип |
| `regex matches` | `input`, `regex` | значение целиком подходит под выражение |
| `==`, `!=`, `>=`, `<=`, `>`, `<` | `input`, `output` | сравнение чисел |

В `input` и `output` пишите плейсхолдеры: `%vault_eco_balance%`, `%player_empty_slots%`. Деньги проверяются так же, через плейсхолдер. Списывать и начислять их можно командой: `[console] eco take %player_name% 100`.

## Остановка

`[stop]` останавливает всю цепочку, в том числе внешний список. Его запускает и любой отказ требования. Строка `[stop]` нужна, когда выйти надо без проверок, например в ветке `deny_commands`:

```yaml
deny_commands:
  - "[message] &cСегодня закрыто"
  - "[stop]"
```

## Ошибки в конфиге

Конфиг проверяется при загрузке. Опечатка не пропадает молча, а показывается с подсказкой:

```text
Unknown action tag [mesage]. Did you mean [message]? Tags: [message], [broadcast], ...
Unknown condition type 'has permision'. Did you mean 'has permission'? Types: ...
Unknown key 'deny_comands' for type 'has permission'. Did you mean 'deny_commands'? Valid keys: ...
```

Это отличает наш формат от DeluxeMenus, где неверный ключ просто не срабатывает.

## Обёртки: `type` и `do`

Действие можно записать так, чтобы у каждого узла был `type`. Обёртка управляет тем, что лежит у неё в `do:`: когда, с каким шансом, при каком условии и сколько раз.

```yaml
join:
  - type: message
    message: Привет, %player_name%

  - type: delay
    duration: 2s
    do:
      - type: message
        message: Через две секунды

  - type: chance
    probability: 10%
    do:
      - type: message
        message: Вам повезло!

  - type: if
    check:
      type: permission
      permission: group.vip
    do:
      - type: message
        message: Бонус VIP
    else:
      - type: message
        message: Купите VIP

  - type: repeat
    times: 3
    every: 1s
    do:
      - type: message
        message: Тик

  - type: group
    do:
      - type: message
        message: Несколько действий как одно
```

| Тип | Настройки | Что делает |
|---|---|---|
| `delay` | `duration`, `do` | выполняет `do` после паузы |
| `chance` | `probability` (`10%`), `do`, `else` | выполняет `do` с заданным шансом, иначе `else` |
| `if` | `check`, `do`, `else` | выполняет `do`, если условие верно, иначе `else` |
| `repeat` | `times`, `every`, `do` | выполняет `do` `times` раз; первый раз сразу |
| `group` | `do` | собирает действия в один узел |

Обёртки вкладываются друг в друга. В `message` можно писать `message:` (одна строка) или `messages:` (список). Строки `[message] текст` и прежняя запись продолжают работать, их можно смешивать в одном списке.

## Старая форма

Действие можно записать и объектом: `{ type: message, messages: ["Привет"] }`. Эта форма даёт больше настроек (формат текста, получатель) и продолжает работать. Для коротких действий используйте строки.

## Из кода

Действия, которые плагин получает из конфига, выполняет `ActionService`:

```kotlin
val context = module.actions.context(player, allPlayers, values = mapOf("shop" to "main"))
context.execute(config.clickCommands)
```
