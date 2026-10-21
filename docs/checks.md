# Проверки

Проверка отвечает на вопрос «можно или нельзя» и ничего не меняет. Проверки живут отдельно от действий: их можно спросить из кода, из меню, из команды или вызвать из списка действий.

## Где пишутся

Проверки описываются в блоке `checks:` с именами. У каждой есть `check` (само условие), `deny` (что сделать при «нет») и `success` (что сделать при «да»). `deny` и `success` необязательны и содержат обычные действия.

```yaml
checks:
  has-100:
    check:
      type: '>='
      input: '%vault_eco_balance%'
      output: 100
    deny:
      - type: message
        message: '&cНужно 100 монет'

  vip:
    check:
      type: permission
      permission: group.vip
```

## Как вызвать из действий

| Запись | При «да» | При «нет» |
|---|---|---|
| `type: require`, `check: имя` | выполняется `success`, дальше идём | выполняется `deny`, цепочка **останавливается** |
| `type: if`, `check: имя`, `do`, `else` | выполняется `do` | выполняется `else`, дальше идём |

`if` только спрашивает: `deny` и `success` проверки не выполняются. `require` выполняет их.

Вместо имени можно написать проверку на месте:

```yaml
- type: require
  check:
    type: permission
    permission: shop.use
  deny:
    - type: message
      message: Нет доступа
```

## Типы

| `type` | Настройки |
|---|---|
| `permission` (`has permission`) | `permission` |
| `has permissions` | `permissions`, `minimum` |
| `has item` | `material`, `amount`, `name`, `name_contains`, `lore_contains` |
| `has exp` | `amount`, `level` |
| `is near` | `location` (`world,x,y,z`), `distance` |
| `==`, `!=`, `>=`, `<=`, `>`, `<` | `input`, `output` — сравнение чисел |
| `string equals`, `string equals ignorecase`, `string contains` | `input`, `output` |
| `string length` | `input`, `min`, `max` |
| `regex matches` | `input`, `regex` |
| `is object` | `input`, `object` (`INT`, `DOUBLE`, `UUID`) |
| `chance` | `probability` (`25%` или `0.25`) |

`!` перед типом переворачивает ответ: `type: '!has permission'`.

## Сочетания

Сочетания — тоже проверки, их можно вкладывать. В списке `checks` можно писать и имена из блока `checks:`.

```yaml
# все сразу
type: all
checks:
  - type: permission
    permission: shop.use
  - type: not
    check:
      type: permission
      permission: shop.banned

# любая
type: any
checks:
  - type: permission
    permission: group.admin
  - type: permission
    permission: group.moderator

# не меньше двух из трёх
type: at-least
count: 2
checks:
  - vip
  - has-100
  - type: '>='
    input: '%player_level%'
    output: 30
```

## Запись DeluxeMenus

Ключи блока как в DeluxeMenus: `requirements`, `optional`, `minimum_requirements`, `stop_at_success`, `deny_commands`, `success_commands`. Действия внутри пишутся через `type`, как везде.

```yaml
checks:
  veteran:
    requirements:
      level:
        type: '>='
        input: '%player_level%'
        output: 30
        optional: true
      vip:
        type: has permission
        permission: group.vip
        optional: true
    minimum_requirements: 1
    deny_commands:
      - type: message
        message: '&7Нужен 30 уровень или VIP'
```

Старое действие `type: requirements` с таким блоком тоже работает и означает `require`.

## Из кода

```kotlin
val context = module.actions.context(player, allPlayers, checks = config.checks)
val hasMoney: Boolean = config.checks.getValue("has-100").check(context)   // только ответ
val allowed: Boolean = config.checks.getValue("can-trade").test(context)   // ответ и deny/success
```
