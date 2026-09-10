# Components, placeholders and cooldowns

## Адаптивный текст

```java
context.getComponents().setDefaultSerializerType(ComponentSerializerType.ADAPTIVE);

Component text = context.getComponents().deserialize(
    "&aLegacy <gradient:#ff0000:#ffff00>MiniMessage</gradient> §7Section &#55ff55HEX"
);
```

Доступны `ADAPTIVE`, `MINI_MESSAGE`, `LEGACY_AMPERSAND`, `LEGACY_SECTION`,
`ADVENTURE_JSON` и `PLAIN_TEXT`. `ADAPTIVE` принимает смешанную строку. Результат
всегда является Adventure `Component`.

Кеш может принадлежать одному плагину или всему runtime библиотеки:

```java
context.getComponents().configureCache(new ComponentCachePolicy(
    ComponentCacheScope.PLUGIN,
    1_000,
    Duration.ofMinutes(30).toMillis()
));
```

## Placeholder одного плагина

```java
PlaceholderKey<String> clanName = PlaceholderKey.of("clan.name", String.class);

context.getPlaceholders()
    .placeholder(clanName)
    .resolve(request -> clanService.name(request.requirePlayerId()))
    .access(PlaceholderAccess.ownerOnly())
    .fallback("Без клана")
    .register();
```

## Общий и ограниченный доступ

```java
PlaceholderAccess access = PlaceholderAccess.builder()
    .owner()
    .allow("pnmenus", "pnmarket")
    .allowMatching("pnadmin-*")
    .deny("unsafe-plugin")
    .build();
```

Владелец использует `{clan.name}`, другой разрешённый плагин —
`{pnclans:clan.name}`. Явный `deny` имеет больший приоритет.

## Параметры, formatters и условия

Зарегистрированный ключ может быть шаблоном:

```java
context.getPlaceholders()
    .placeholder("clan.member.{name}.rank", String.class)
    .resolve(request -> clanService.rank(request.parameter("name")))
    .access(PlaceholderAccess.shared())
    .register();
```

```text
{pnclans:clan.member.Notch.rank}
{balance|default:0}
{balance|upper}
{cooldown|duration}
{online|plural:игрок,игрока,игроков}

{?clan.exists}<green>Клан: {clan.name}{:}<gray>Без клана{/}
```

`upper`, `lower`, `default`, `boolean`, `plural` и `duration` встроены. Свой
formatter регистрируется типизированно через `PlaceholderService.formatter`.

## PlaceholderAPI

```java
context.getPlaceholders().adapters().register(new PlaceholderApiAdapter(plugin));

context.getPlaceholders()
    .placeholder("clan.name", String.class)
    .resolve(request -> clanService.name(request.requirePlayerId()))
    .access(PlaceholderAccess.shared())
    .publish(new PlaceholderPublication("placeholderapi", "pnclans", "clan_name"))
    .register();
```

В pnLibrary это `{pnclans:clan.name}`, во внешнем API —
`%pnclans_clan_name%`. Отсутствующий PlaceholderAPI не ломает внутреннюю
регистрацию. Для другой системы реализуется `PlaceholderAdapter` и добавляется в
тот же registry.

## Действия и cooldown

Player actions автоматически разрешают placeholders и условия перед передачей
готовых компонентов платформе:

Действия расширяются обработчиками, а не `switch` внутри пользовательского
плагина:

```java
PlayerActionRegistration deposit = context.getActions().register(
    "economy:deposit",
    PlayerActionAccess.builder()
        .allow("pnshop", "pnmenus")
        .allowMatching("pneconomy-addon-*")
        .build(),
    PlayerActionHandler.immediate(action -> {
        UUID playerId = action.getPlayerId();
        int amount = action.requireInt("amount");
        Component message = action.getMessage();
        Object payload = action.getPayload();

        economy.deposit(playerId, amount);
        return PlayerActionResult.success();
    })
);
```

Владелец определяется автоматически из `PluginContext`; передать чужой ID при
регистрации нельзя. Одинаковые локальные ключи разных плагинов не конфликтуют.
Владелец вызывает `economy:deposit`, разрешённый внешний плагин —
`pneconomy::economy:deposit`. `PlayerActionAccess.ownerOnly()` закрывает handler,
`PlayerActionAccess.shared()` открывает всем участникам pnLibrary, builder задаёт
несколько ID, wildcard allow и deny. `PlayerActionRegistration` сообщает owner,
handler, policy и автоматически закрывается вместе с контекстом.

```yaml
rewardActions:
  actions:
    - type: economy:deposit
      text: '<green>Получено: {reward}'
      arguments:
        amount: '{reward}'
```

`message`, `title`, `action_bar`, `kick`, `teleport`, `sound` и
`player_command` зарегистрированы тем же механизмом как встроенные handlers.
Контекст обработчика содержит владельца, UUID игрока, ключ, исходное действие,
готовые Adventure-компоненты, произвольные аргументы и runtime payload.

```java
context.getActions().execute(playerId, config.joinActions, Map.of("reward", 500));
```

```java
CooldownResult result = context.getCooldowns().acquire(
    playerId, "clan.create", Duration.ofMinutes(5)
);

if (!result.getAllowed()) {
    context.getActions().execute(playerId, config.cooldownActions, Map.of(
        "cooldown", result.getRemaining()
    ));
}
```

Cooldown использует монотонное время, изолирован контекстом плагина и полностью
очищается при его выключении.
