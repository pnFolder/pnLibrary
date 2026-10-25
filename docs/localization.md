# Локализация Minecraft

pnLibrary загружает официальные переводы Mojang и отдаёт их плагинам. Плагин может:

- получить название любого предмета, сущности, зачарования, эффекта, биома, цвета или режима игры на языке игрока без строк,
  написанных вручную;
- найти предмет или сущность по названию, даже с опечаткой или в неверной раскладке клавиатуры.

Все загрузки асинхронные и возвращают `CompletionStage`. Файлы кэшируются на диске по версии и языку, один раз загруженный
язык доступен всем плагинам.

## Модули

| Модуль | Что в нём |
|---|---|
| `minecraft-localization-api` (`:modules:features:minecraft-localization-api`) | нейтральные контракты, без классов платформ. От него зависит ваш плагин (`compileOnly`) |
| `platforms/bukkit/localization` | перевод объектов Bukkit: `Material`, `EntityType`, `Enchantment` и другие. Входит в jar pnLibrary |

Локализация есть **только на игровых серверах** (Bukkit и его форки). На прокси (BungeeCord, Velocity) обращение к ней бросает
`UnsupportedOperationException`.

## 1. Получить службу

Единственный путь — контекст вашего модуля. Закрывать ничего не нужно: язык и версия, которые загрузил один плагин, кэшируются для
всех, а раскладки клавиатуры, зарегистрированные вашим модулем, снимаются, когда он закрывается.

```kotlin
val localization = module.localization

localization.locale(version, MinecraftLocale.RU_RU).thenAccept { ru ->
    val bukkit = BukkitLocaleTranslations.wrap(ru)
    sender.sendMessage(bukkit.translate(Material.DIAMOND_SWORD).text)   // «Алмазный меч»
}
```

`version` — это `MinecraftVersion` сервера. Языки задаются типом `MinecraftLocale` (опечатку в коде поймает компилятор); строку с кодом
языка тоже можно передать. Загрузить сразу несколько языков с запасным:

```kotlin
val request = TranslationRequest.builder()
    .version(version)
    .locales(MinecraftLocale.RU_RU, MinecraftLocale.UK_UA)
    .fallback(MinecraftLocale.EN_US)
    .build()
localization.load(request)      // CompletionStage<TranslationBundle>
```

## 2. Перевести объект

`translate` возвращает `Translation`, а не голую строку:

```kotlin
val result = bukkit.translate(EntityType.CREEPER)
result.text         // «Крипер»
result.localized    // true, если текст взят из таблицы
result.key          // ключ перевода, например entity.minecraft.creeper
```

Если ни один из ключей не найден (например, у предмета нового релиза в старой таблице), `localized == false`, а `text` — читаемое
имя, собранное из самого значения. Строка не пустая и не «сырой» ключ.

Для своего типа достаточно описать `TranslatableType<T>`: список возможных ключей по приоритету и запасное имя.

## 3. Поиск по названию

У каждой категории есть индекс: `bukkit.materials()`, `entities()`, `enchantments()`, `effects()`, `biomes()`, `colors()`,
`gameModes()`. Для произвольных ключей — `keys()`.

| Метод | Что делает |
|---|---|
| `findExact(text)` | точное совпадение названия |
| `search(text)` | совпадения по тексту, детерминированный порядок |
| `fuzzy(text)` | с опечатками (порог сходства 0,7, до 5 результатов) |
| `suggest(text)` | то же, но ещё и читает запрос в других раскладках |
| `best(text)` | один лучший результат или `null` |

```kotlin
bukkit.materials().best("fkvfp")?.value      // Material.DIAMOND: «алмаз», набранный в английской раскладке
bukkit.materials().suggest("алмаз")          // до 5 вариантов по убыванию близости
```

Каждый результат — `TranslationMatch` с ключом, найденным текстом и объектом Bukkit (`value`, `null` для ключей, неизвестных API).

### Свои раскладки клавиатуры

Встроены английская и русская. Свою раскладку регистрируют один раз, и она действует для всех таблиц службы:

```kotlin
val handle = localization.layouts.register(KeyboardLayout.of("myplugin-dvorak", "..."))
handle.close()   // необязательно: закрытие модуля снимает раскладку само
```

## 4. Версии и языки

```kotlin
localization.availableVersions()          // известные релизы, новые первыми
localization.availableLocales(version)    // языки, доступные для версии
localization.refresh(request)             // принудительно перепроверить и обновить
```

Версии до 1.13 покрыты частично: у них другая схема файлов переводов.

## Ошибки

Сеть и диск могут подвести. Ошибки загрузки приходят в `CompletionStage` как `TranslationException`; поле `reason` говорит, что
случилось (неподдерживаемая версия, неверный или недоступный язык, нет сети и другое), а плагин сам решает, показать запасное имя
или повторить попытку. Если язык ещё не загружен, поиск по нему возможен только после завершения `locale(...)`: поиск идёт
по загруженным таблицам.
