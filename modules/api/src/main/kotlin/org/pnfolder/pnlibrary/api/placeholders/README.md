# Плейсхолдеры

Система плейсхолдеров на аннотациях. Описание: `docs/placeholders.md`.

- Код: эта папка. Разбор классов через reflection лежит в `core/placeholders` (в `api` reflection запрещён).
- Примеры кода (компилируются вместе с тестами): `modules/core/src/test/kotlin/org/pnfolder/pnlibrary/core/placeholders/examples/`.
- Тесты работы: `modules/core/src/test/kotlin/org/pnfolder/pnlibrary/core/placeholders/PlaceholdersTest.kt`.

Подключение: `module.placeholders.register(Holder(), papi = "prefix")`, форматы: `module.placeholders.formats(Formats())`, без класса: `module.placeholders.value("a.b") { viewer -> ... }`, свой тип аргумента: `module.placeholders.converter(Clan::class.java, ClanByName())`. У каждого плейсхолдера есть владелец и видимость (`Visibility`), владельца можно написать в адресе: `{shop/main@shop.price}`, `{~@...}` (мой плагин), `{~~@...}` (мой модуль), `{~~~@...}` (библиотека); плейсхолдер может быть внутри плейсхолдера: `{clan.of.{player.name}.level}`. См. `Example10Owners.kt` в примерах.
