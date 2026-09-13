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
PlaceholderRegistration<String> clanName = context.getPlaceholders()
    .placeholder("clan.name", String.class)
    .resolve(request -> clanService.name(request.requirePlayerId()))
    .access(PlaceholderAccess.shared())
    .publishToPlaceholderApi("pnclans", "clan_name")
    .register();
```

В pnLibrary это `{pnclans:clan.name}`, во внешнем API —
`%pnclans_clan_name%`. Bukkit-runtime сам обнаруживает PlaceholderAPI. Если он
не установлен, внутренняя регистрация продолжает работать, а
`clanName.getPublications().get(0).getState()` возвращает `UNAVAILABLE`.
Автоподключение управляется параметром `placeholder-api-integration` в
`plugins/pnLibrary/config.yml`.

## Действия и cooldown

Единая модель действий описана в [ACTIONS.md](ACTIONS.md). Старые `PlayerAction`, `PlayerActionService` и ручной сериализатор удалены.

Cooldown остаётся отдельным сервисом `context.cooldowns` и не смешивается с выполнением действий.
