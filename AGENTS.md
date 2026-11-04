# pnLibrary: заметки для нейросети

Прочитай этот файл первым. Здесь правила работы и журнал: на чём остановились, что сделано, что дальше.
Подробности по устройству: [docs/architecture.md](docs/architecture.md), выпуск: [docs/releases.md](docs/releases.md).

## Правила
- Отвечать по-русски, коротко и просто. Если что-то плохо или так нельзя, говорить прямо.
- Никаких подписей Claude в коммитах и PR (`Co-Authored-By`, «Generated with Claude Code»).
- Коммитить, пушить и мёрджить только по просьбе. PR только в `main`. Автомерж не включать.
- Чужие локальные изменения не трогать и не коммитить.
- Всё настраиваемое хранить в коде (аннотации конфигов), а не в `.properties` и текстовых файлах.
- Не добавлять параметров, которые не просили. Не повторять то, от чего пользователь отказался.
- Публичный API проверяется на бинарную совместимость: после правки API сначала `apiDump`, потом отдельным запуском `apiCheck`.
- Версии выпускаются как обычные релизы, не как pre-release.

## Ветки
- `main`: линия `0.0.x`, только исправления ошибок пользователей. Новых функций и рефакторинга сюда не добавлять.
- `next`: новая библиотека, версия `0.1.0-dev.N`. Весь рефакторинг и новое здесь. Исправления из `main` периодически
  вливать в `next`. Из `next` выпускать только в каналы `dev` и `alpha`, в общий каталог до готовности не публиковать.
- Старый API заморожен: не менять, помечать `@Deprecated` с `replaceWith`. Новое ядро пишется заново, старый API
  остаётся тонкими адаптерами над ним. Главная проверка: плагин, собранный на `0.0.1-dev.6`, запускается на новой версии без пересборки.

## Стиль кода (для ветки `next`)
Пишем простой, строгий и короткий код. Старый код в `main` по этим правилам не переписываем, только новый в `next`.
- **Комментарии:** только «почему», где причина неочевидна. Без пересказа кода. KDoc только на публичном API, который видит автор плагина, и там пишем, что метод делает и когда его вызывать.
- **Размер:** файл до 300 строк, метод до 30. Больше — делим по смыслу, а не механически.
- **Один класс — одна задача.** Нет «центральных» классов с десятками зависимостей. Сборка частей вместе живёт в одном месте (корень композиции), остальной код получает только то, что ему нужно.
- **Названия:** без `Impl`, `Manager`, `Helper`, `Utils`. Имя говорит, что класс делает. Интерфейс заводим, только если есть вторая реализация или он нужен автору плагина.
- **Папки:** по смыслу функции (`commands`, `config`, `updates`), а не по технической роли (`details`, `lifecycle`, `runtime`).
- **Слои:** `api` (то, что видит плагин) → `core` (вся логика, ничего не знает про Bukkit, Velocity, Bungee) → `platforms` (адаптеры под каждую платформу). Зависимости только в сторону `api`, никогда обратно. Внутри `core` каждая функция лежит в своей папке целиком, и папки зависят друг от друга только через `api`.
- **Платформы:** Paper/Bukkit, Velocity, BungeeCord, Folia.
- **Язык:** Kotlin. Настраиваемое хранится в коде (аннотации), не в `.properties`.

## Как собирать
```powershell
./gradlew.bat check apiCheck :distribution:build
```

## Где что
- `modules/`: api, core, common, runtime-spi, updates, bootstrap, console, features (config, currency, localization).
- `platforms/`: bukkit, bungee, velocity, loader.
- `.pnlibrary/releases.json` и `.pnlibrary/changelogs/<версия>.md`: каталог релизов библиотеки.
- `integration/`: инструкция и заготовка `.pnlibrary` для плагинов на pnLibrary.

## Журнал (новое сверху)
Обновляй эту секцию в конце каждой сессии: одна запись, что сделано и что осталось.


### 2026-11-04
- Система плейсхолдеров в `next`: адрес `плагин/модуль:имя`, дерево-DSL с аргументами, форматы по уровням, генерация `from/table/list`, доступ, публикация в PlaceholderAPI из той же регистрации (`~` между модулем и именем), `/pn placeholders`, право `pnlibrary.placeholders`. План и отличия: `docs/placeholders-plan.md`, раздел 12. Встроенные форматы round, percent, money, pad, title, date добавлены. Добавлены: адреса форматов `плагин/модуль:имя`, вложенные объекты в `from`, страница в `/pn gui`, тесты `ExternalNames` и подбора имён PlaceholderAPI (нашли и исправили: точное имя шло после шаблона). Решено не делать: настройки форматов аннотациями. `PlaceholderInstaller`/`PlaceholderGeneration` оставлены в `api` как `internal`. На живом сервере не проверялось.
### 2026-11-02
- Все 13 плагинов (pnAuth, pnBans, pnCaptcha, pnCases 1.5.4-remaster, pnChat, pnClans, pnMarket, pnRelics, pnRelog, pnRtp, pnSeller, pnSouls, pnTrades) выпущены обычными релизами и подключены к pnLibrary `0.0.1-dev.6`. В каждом заполнены `.pnlibrary/releases.json` и changelog.
- В `main` влиты PR #87 (флаг `record` у логгера) и #88 (конфиг: `privacy` по умолчанию false, пример `excluded-paths`, аннотация `@ConfigSecret`, путь `плагин/файл.yml:ключ`). Релиз `0.0.1-dev.7` с ними ещё не выпущен.
- Создана ветка `next` (версия `0.1.0-dev.1`) под новую библиотеку. Претензия пользователя к текущему коду: стиль написания и запутанность; выбрана строгая архитектура, правила записаны в «Стиль кода». Патч 1 сделан: `LibraryMenu.kt` (2159 строк) разбит на экраны по разделам (`*Screens.kt`), общую сессию, стиль, текст и константы; поведение не менялось. Патч 2 сделан: `EventBusCommand.kt` (681) разбит на страницы по смыслу (`Bus*Pages.kt`), общий текст (`BusChat.kt`) и настройки (`BusCommandSettings.kt`). Патч 3 сделан: из `TaskCommand.kt` (450) вынесены `TaskPeriod`, `TaskState`, `TaskLabels`. Патч 4 сделан: из `PnLibraryImpl.kt` вынесены запросы к журналу (`JournalQueries`) и отчёт по файлам настроек (`configFileDiagnostics`); сам класс остаётся корнем сборки (313 строк), порядок создания частей в нём не трогали. Патч 5 сделан: `CurrencyCommandExecutor.kt` (439) разбит на разбор команды, операции с валютой (`CurrencyOperations`) и сообщения игроку (`CurrencyChat`). Патч 6 сделан: из `EventDispatcher.kt` (410 -> 314) вынесены `EventTracer`, `EventCounters`, `HandlerCache`, `ListenerRegistration`; логика блокировок перенесена дословно, подписки и области остались внутри сервиса. Патч 7 сделан: `JournalCommand.kt` (357) разбит на строки (`JournalRows`), список (`JournalList`), карточку (`JournalCard`) и константы (`JournalLayout`). Дальше по очереди: остальные файлы длиннее 300 строк (искать: `wc -l` по `src/main`), потом `ScheduledTasks`, `UpdateCatalogCommands`, `GroupsCommand`. Патчи 8–10 сделаны: статистика (почасовые срезы в журнал, `/pn stats`, страница меню), нарезка остальных файлов длиннее 300 строк (кроме `PluginRegistryImpl`, `PnLibraryImpl`, `ScheduledTasks`-подобных корней сборки), переименование 17 классов с `Impl`/`Manager` по смыслу (`ScheduledTasks`, `EventDispatcher`, `CommandRegistry`, `ModuleLogging`, `InMemoryCooldowns`, `ServiceRegistry`, `ExpansionRegistry`, `MetricsSessions`, `PlatformAudiences`, `CachedComponents`, `DownloadBatch`, `ModuleMetrics`, `ObservabilityHub`, `NativePlatform`, `TranslationSearchIndex`, `BuiltInCurrencyStorage`, `DirectDownloads`). Остались с `Impl`: `PluginRegistryImpl`, `PnLibraryImpl`, `ConfigurationServiceImpl`, `UpdateServiceImpl` (публичные или корни), платформенные `*PlatformImpl`, `MenuServiceImpl`, `ItemServiceImpl`. Дальше: аудит циклов между пакетами `core`, дизайн API 2 на одной возможности.
- Удалены 67 влитых веток; остались `main`, `next`, ветки dependabot и `backup/journal-wip-before-rebase-2026-10-20`.
- Почищен проект: удалены `examples/` (приёмочный и demo-плагины) и локальный мусор сборки.
- pnClans остаётся `1.3.0-beta.5`, канал `beta`: пользователи канала `stable` его не получат.

### Дальше
- Аудит пакетов `core`: `runtime` зависит от 21 пакета (корень сборки, так и задумано, но `PnLibraryImpl` держит 56 импортов), `plugin` от 11. `lifecycle` разобран: `JournalGroup`/`JournalDetail` теперь в `journal`, `TaskControl` в `tasks`, `StatsSnapshot` в `stats`, `ResourceCleanup` в `cleanup`; в `lifecycle` остался только мост `RuntimeResources`. Дальше: сократить `PnLibraryImpl` и зависимости `plugin`.
- Составить очередь патчей для `next`. Подход: новое ядро, старый API адаптерами, API 2 рядом; свои плагины переводить по одному, простые первыми.
- Поставить `@ConfigSecret` на пароли и токены в конфигах плагинов.
- После `0.0.1-dev.7`: поднять версию библиотеки в плагинах, перенести оставшиеся команды на API библиотеки.
- `dev.6` на GitHub отмечен как Pre-release, объявить обычным релизом.
