# Плейсхолдеры

Плейсхолдер — значение, которое плагин отдаёт текстам, меню, сценариям и PlaceholderAPI. Значения описываются **классом с аннотациями**, а не строками регистрации.

Код лежит в `api/placeholders`, движок в `core/placeholders`. Примеры кода (компилируются вместе с тестами): `modules/core/src/test/java/org/pnfolder/pnlibrary/core/placeholders/examples/`.

## Адрес

`{группа.ветка.аргумент.поле|формат|формат}`. Скобки в коде необязательны, в тексте обязательны.

```kotlin
@Placeholders("clan")                       // группа
class ClanValues {
    @Placeholder("Уровень клана")                    // значение
    fun level(@Viewer viewer: ViewerInfo): Reply<Int> = Reply.of(Clans.of(viewer.requirePlayerId())?.level)

    @Placeholder fun top(@Argument(range = "1..100") place: ArgumentValue<Int>): Reply<Clan> = Reply.next(Clans.top(place.required()))

    @Placeholder val tag: Reply<String> = Reply.of("WLF")
}
```

Подключение в `onEnable` модуля, закрывается вместе с модулем:

```kotlin
module.placeholders.formats(ClanFormats())
module.placeholders.registerPublished("pnclans", ClanValues())   // без PlaceholderAPI: register(ClanValues())
```

## Что можно в методе

- `@Viewer viewer: ViewerInfo` в параметрах (аннотация обязательна): кто спрашивает (`playerId`, `language`, `viaPapi`). Без игрока `viewer.requirePlayerId()` тихо оставляет плейсхолдер пустым.
- `ArgumentValue<T>`: часть адреса. `value`, `text`, `error`, `required()`, `orMe(viewer)`, `orElse(...)`. Параметры уточняет `@Argument(range, choices, name)`; несколько аргументов собираются в класс, параметр с ним помечается `@Group`: `fun kills(@Group query: KillsQuery)`.
- `Reply.none()` (или `Reply.of(null)`) значит «значения нет». Что показать вместо него, решает `@Placeholder(onInvalid = KEEP/DEFAULT/EMPTY, defaultText = "...")`.
- `Reply.next(объект)`: объект с такими же `@Placeholder`: так получаются ветки `clan.war.score`. `CompletableFuture` годится для асинхронных значений.
- `@Hide` прячет значение из списков и из PlaceholderAPI.

Типы аргументов: числа, `Boolean`, `UUID`, `Duration` (`1h30m`), `PlayerRef` (имя или UUID, `me`), перечисления. Свой тип: `placeholders.converter(Clan::class.java, ClanByName())` в модуле (он снимается вместе с модулем); для всего сервера есть `ArgumentAdapters.register`.

## Форматы

Формат после чёрточки: `{clan.money|short}`, цепочкой `{clan.money|round:1|group}`. Свой формат пишется методом:

```kotlin
class ClanFormats {
    @Format("rank") fun rank(n: Int): String = "#$n"
    @Format("pad0") fun pad0(n: Int, @Argument width: ArgumentValue<Int>): String = n.toString().padStart(width.required(), '0')
}
```

`@UseFormat("имя")` на методе или классе задаёт формат по умолчанию, `|raw` его отключает. Формат модуля главнее встроенного.

Встроенные (числа и время зависят от языка зрителя): `group`, `short`, `round`, `percent`, `pad`, `sign`, `roman`, `ifpos`, `upper`, `lower`, `title`, `cut`, `wrap`, `default`, `yesno`, `icon`, `map`, `left`, `hms`. Список: `/pn placeholders formats`.

## Имена, которые известны только во время работы

Для валют, настроек и т. п. без класса:

```kotlin
val handle = module.placeholders.value("shop.coins", "Монеты игрока") { viewer -> coins(viewer.playerId) }
handle.close()   // убрать раньше времени
```

## Текст и значения запуска

```kotlin
module.placeholders.render("Клан {clan.tag}, монет {amount|group}", playerId, mapOf("amount" to 1500))
```

Имя из `values` главнее зарегистрированных. После подстановки PlaceholderAPI заполняет свои `%токены%`.

## PlaceholderAPI

С `registerPublished("pnclans", ...)` адрес `clan.war.score` виден как `%pnclans_clan_war_score%` (`_` разделяет части; настоящий `_` в аргументе пишется `~5F`). Префикс: 2–32 символа `a-z 0-9 -`. Выключить для модуля целиком: `placeholderApi(false)` в настройках модуля.

Части адреса с точкой, чёрточкой, двоеточием или запятой берут в кавычки (`clan.friend."a.b"`); код `~XX` пишет символ по его шестнадцатеричному коду (`~7C` — `|`).

## Команды и меню

`/pn placeholders [страница]` список (адрес, тип, формат, описание, имена в PlaceholderAPI), `/pn placeholders info <адрес>` (владелец, видимость и все формы адреса: как есть, этот модуль, весь плагин, мой модуль, мой плагин, с форматом), `/pn placeholders formats`, `/pn placeholders test <адрес> [uuid]`. Право `pnlibrary.placeholders`. Тот же список в `/pn gui`.

Свои значения библиотеки: `{pnlibrary.version}`, `{pnlibrary.plugins}`, `{pnlibrary.uptime}`, `{pnlibrary.server.platform}`, `{pnlibrary.stats.errors}` и другие.

## Маршрут и ранний ответ

- `@Route route: RouteInfo` отдаёт методу весь адрес по частям. Каждая часть помечена: `GROUP` (группа), `BRANCH` (ветка), `ARGUMENT` (аргумент), `FIELD` (поле, последнее слово со значением), `FORMAT` (формат, с аргументами после двоеточия). Есть `route.path`, `route.field`, `route.of(kind)`, `route.after("war")`, `route.hasFormat("pad0")`.
- `Reply.stop(значение)` внутри метода-ветки обрывает адрес и отдаёт это значение: ниже методы не вызываются.
- `@Placeholder(onInvalid = EMPTY)` на ветке действует на все текстовые листья под ней, у которых своего `onInvalid` нет.
- Без `@Route` и `@Viewer` параметры типов `RouteInfo` и `ViewerInfo` не принимаются: при регистрации будет ошибка.

## Свой разбор аргумента

`ArgumentConverter<T>` превращает текст аргумента в объект: `read(input)` (синхронно) или `readLater(input)` (когда ответ приходит позже, например из базы). Указывается на параметре: `@Argument(converter = ClanByName::class) clan: ArgumentValue<Clan>`, а для типа целиком: `placeholders.converter(Clan::class.java, ClanByName())`. В `ArgumentInput` лежат `text`, `viewer`, `route`, `name`, `position`, `previous` (аргументы слева); `input.fail("причина")` говорит, почему текст не подошёл: причина попадёт в `ArgumentValue.error`. `suggest(input)` отдаёт подходящие слова для подсказок. Примеры: `modules/core/src/test/.../placeholders/examples/ClanByName.java`.

## Асинхронные значения и ветки

Метод (лист) и ветка могут вернуть `CompletableFuture`: `fun stats(): CompletableFuture<ServerStats>`. Следующие методы адреса ждут ответа, поток вызова не блокируется. Ветка, которая позже оказалась пустой (`null`), заканчивает адрес тихо; `answer(...)` внутри работает так же. Блокирующий вызов в обычном методе (без `CompletableFuture`) блокирует вызывающего: из PlaceholderAPI на главном потоке ответ пропадёт.

## Reply: ответ, продолжение или ничего

Каждый метод и свойство с `@Placeholder` возвращает `Reply<T>` или `CompletableFuture<Reply<T>>`; обычный тип возврата (`Int`, объект) при регистрации класса отвергается ошибкой.

- `Reply.value(x)`: ответ, адрес заканчивается. `Reply.value(x, format = "coins")`: ответ со своим форматом (адрес без формата использует его, `|raw` отключает).
- `Reply.next(объект)`: продолжение, дальше методы объекта с `@Placeholder`.
- `Reply.stop(x)`: ответ из ветки, ниже ничего не вызывается.
- `Reply.none()`: значения нет, адрес остаётся как написан. `Reply.of(x)`: ответ, если `x` есть, иначе `none`.

Тип внутри решает, что это: `Reply<Int>` лист, `Reply<WarInfo>` ветка. Асинхронно: `CompletableFuture<Reply<WarInfo>>`. Для `Duration` по умолчанию формат `left` (`1ч 35м`, а не `PT1H35M`). `@Placeholder(onInvalid = DEFAULT/EMPTY)` теперь работает и на числовых листьях: вместо адреса подставляется текст.

## Владелец и видимость

Каждый плейсхолдер, формат и конвертер принадлежит модулю (`плагин/модуль`) и снимается, когда модуль закрывается. Кто может пользоваться плейсхолдером, задаёт `Visibility` на свойстве, методе или классе (`@Placeholder(visibility = ...)`, `@Placeholders("shop", visibility = ...)`); если заданы оба, действует более узкий. Форматы и конвертеры всегда общие.

| Значение | Кому видно |
|---|---|
| `MODULE` | только модулю, который зарегистрировал |
| `PLUGIN` | всем модулям этого же плагина |
| `ALL` (по умолчанию) | всем плагинам и PlaceholderAPI |

```kotlin
@Placeholders("shop")
class ShopValues {
    @Placeholder("Цена")
    val price: Reply<Int> get() = Reply.of(10)                          // ALL

    @Placeholder("Счётчик", visibility = Visibility.MODULE)
    val counter: Reply<Int> get() = Reply.of(42)                        // только этот модуль
}

module.placeholders.registerPublished("shop", ShopValues())
module.placeholders.value("shop.level", "", null, Visibility.PLUGIN) { 10 }   // без аннотации
```

`/pn placeholders test` — для оператора: вычисляет и скрытые плейсхолдеры.

Если имя встречается несколько раз, отвечает ближайший: свой модуль, свой плагин, остальные, встроенные. Одинаковые адреса у двух владельцев дают предупреждение. В `/pn placeholders` у адресов не `ALL` стоит пометка `[module]` или `[plugin]`.

### Владелец в адресе

Перед адресом и перед форматом можно написать владельца и `@`:

```
{shop.price}                  ближайший
{shop/main@shop.price}        именно этот модуль
{shop@shop.price}             любой модуль плагина shop
{~@shop.price}                только мой плагин
{~~@shop.price}               только мой модуль
{~~~@shop.price}              только библиотека
{shop/main@shop.name|~~~@upper}   владелец у плейсхолдера и у формата
```

`~` (плагин), `~~` (модуль), `~~~` (библиотека): чем больше знаков, тем ближе к модулю, три знака это библиотека. Они не могут совпасть с именем плагина. Если владелец написан, значения запуска (`values`) не читаются, ищется только среди зарегистрированного. Если такого владельца нет или он не виден, адрес остаётся в тексте как написан, а формат пропускается.

### Аргумент прямо под группой

Обычно слово метода входит в адрес. Чтобы слот шёл сразу после группы (или ветки), пометьте метод `bare = true`: слова у него нет, адрес идёт дальше аргументом. Нужно для старых адресов вроде `%pncases_keys_donat%`.

```java
@Placeholders("keys")
public class KeysValues {
    @Placeholder(bare = true) // keys.<кейс>
    public Reply<Integer> keys(@Argument ArgumentValue<String> name) {
        return Reply.value(keys.count(name.required()));
    }

    @Placeholder // keys.total: слово метода главнее голого слота
    public Reply<Integer> total() { return Reply.value(keys.all()); }
}

@Placeholders("player")
public class PlayerValues {
    @Placeholder(bare = true) // player.<имя>.<ключ>, в PlaceholderAPI player_<имя>_<ключ>
    public Reply<Stats> player(@Argument ArgumentValue<String> name) {
        return Reply.next(stats.of(name.required()));
    }
}
```

Правила: у такого метода есть хотя бы один `@Argument`; на одном уровне класса он один (иначе ошибка при регистрации); сначала ищется точное слово соседних методов, потом голый слот. Работает с `Reply.next(объект)`, в PlaceholderAPI и в `/pn placeholders` (слот виден как `{имя}`).

### Плейсхолдер внутри плейсхолдера

Любая часть адреса может быть плейсхолдером: `{clan.of.{player.name}.level}`, аргумент формата `{score|pad:{width}}`, владелец `{{owner}@shop.price}`. Внутренний читается первым, его значение подставляется в кавычках (если в нём есть точка, пробел и т.п.). Вложенность до 16 уровней, больше отклоняется с предупреждением. Если внутренний не дал значения, внешний остаётся в тексте как написан. Значение с `{` внутри заново не разбирается.
