# Аудит Velocity — 2026-10-16

Аудит охватывает исходники `platforms/velocity/api`, `platforms/velocity/runtime`,
их bootstrap в core, используемые SPI команд, задач, владельцев и логов, а также
FastStats bridge и конфигурацию сборки дистрибутива. Это аудит исходников с
автоматическими тестами; запуск реального proxy с клиентами в эту проверку не входит.

## Найденные проблемы и исправления

| Проблема до изменения | Последствие | Исправление |
| --- | --- | --- |
| GenericPlatformCommandAdapter принимал owner, но NativeCommandRegistrar его не получал | В Velocity нативным владельцем команд сторонних плагинов становилась pnLibrary | Owner передаётся через SPI до CommandMeta.plugin(owner), нативная регистрация владельца проверяется |
| PlatformTaskRequest не содержал owner | Нативный scheduler получал pnLibrary вместо владельца TaskScope | Core передаёт владельца scope в request; Velocity использует его в buildTask |
| Проверка закрытия задачи выполнялась только до nativeSchedule | Задача, созданная одновременно с close, могла остаться активной | После добавления handle повторно проверяется закрытие; запоздавшая задача отменяется |
| Ошибка отмены одного handle прерывала весь close | Остальные задачи оставались активными | Все отмены выполняются, ошибки агрегируются, handle удаляется в finally |
| ENTITY-запрос выполнялся через обычный proxy scheduler | API обещал контекст, которого у Velocity нет | ENTITY явно отклоняется |
| whenServerReady наследовал пустую реализацию | Проверки зависимостей и reconciliation обновлений не получали сигнал готовности | Callback'и ждут markReady после запуска runtime и регистрации Velocity API; остановка очищает очередь |
| executeGlobal обходил task adapter | Внутренние задачи не попадали в учёт и отмену | Внутренняя отправка использует тот же task adapter с владельцем pnLibrary |
| ProxyUpdateCommand создавался в Velocity и Bungee adapter | Платформенные адаптеры знали конкретную функцию core | Общая proxy-команда регистрируется в PnLibraryBootstrap и освобождается командным сервисом |
| Native callbacks теряли введённый alias | CommandContext.invokedAlias всегда содержал основное имя | SPI переносит alias; Velocity передаёт Invocation.alias(), Bukkit — label |
| Удаление Velocity-команды выполнялось только по имени | Cleanup не выражал владение всем набором алиасов | Используется unregister(CommandMeta) |
| Конфликты проверялись только внутри pnLibrary | Возможна перезапись команды чужого плагина | Перед регистрацией проверяются native name и aliases |
| Command adapter хранил неиспользуемый plugin и дважды ссылку на registrar | Конструктор создавал ложное впечатление зависимостей | Неиспользуемый plugin удалён, registrar хранится один раз |
| При каждом command callback создавался VelocityAudienceAdapter | Лишние объекты и две точки настройки audience | Общий audience adapter создаётся один раз и передаётся командам |
| ownerMetadata перебирал все плагины; conversion повторялся в snapshot | Дублирование и линейный поиск владельца | VelocityPluginRegistry использует fromInstance и общий conversion |
| remotePolicyContext принимал неиспользуемый PluginMetadata на всех платформах | Лишний аргумент в глобальном SPI | Аргумент удалён из SPI, core и трёх платформ; нативный descriptor остаётся источником истины |
| Проверка remote owner выполнялась дважды, её local variable не использовалась | Дублирование lookup и разные ошибки | Проверка выполняется в factory через VelocityPluginRegistry.require |
| Ошибка запуска или ready могла оставить ресурсы адаптера | Неполная очистка | Entry point закрывает host либо adapter и сохраняет исходную ошибку |
| Ссылка runtimeHost сбрасывалась после close | При ошибке close оставалась ссылка на закрытый host | Ссылка снимается перед остановкой |
| Повторное initialize могло создать лишнюю обвязку | Неявная повторная инициализация | Повторный запуск явно отклоняется |
| UncaughtExceptionLogObserver не заменял callback и мог пропустить previous handler при ошибке | Устаревший observer и потерянная стандартная обработка исключения | Callback обновляется; predecessor захвачен отдельно и вызывается в finally; при отсутствии predecessor печатается исходное исключение |
| bStats shutdown обращался к null metricsBase после ошибки чтения конфигурации | Ошибка остановки неработавшей метрики | shutdown проверяет наличие metricsBase |
| Диагностика несколько раз читала live-коллекцию игроков | Разные размеры и распределения в одной секции | Секция players использует одну скопированную коллекцию |
| Нижний индекс percentile скрывал высокую задержку при малом числе игроков | Для ping 10/500 p95 получался 10, health не замечал проблему | Используется nearest-rank p95; для этого примера результат 500 |

Изменения NativeCommandRegistrar применены к Bukkit/Bungee, иначе общий SPI не
компилировался бы. Их команды также получают фактического владельца. Полная
переработка планировщиков этих двух платформ в этот аудит не входит.

## Проверка зависимостей конструкторов

| Класс | Решение и основание |
| --- | --- |
| PnLibraryVelocityPlugin | ProxyServer, Logger, Metrics.Factory, Path используются при создании runtime; сохранены |
| VelocityPlatformAdapter | Plugin нужен для внутренних задач и observer; proxy — для native API; metricsFactory/dataFolder передаются в runtime; logger используется logHandler и больше не объявлен отдельным полем |
| VelocityCommandAdapter | Только registrar и функция преобразования audience; native constructor получает proxy и готовый audience adapter |
| DefaultVelocityNativeCommandRegistrar | Только proxy; владелец приходит в register, а не зашивается в constructor |
| VelocityTaskAdapter | Native constructor принимает только proxy; владелец приходит в запросе; функциональный constructor служит проверяемой границей native scheduling |
| VelocityAudienceAdapter | Proxy нужен для console, поиска и списка игроков |
| VelocityLibraryPlayer | Нативный Player нужен для каждой операции; дополнительные абстракции не требуются |
| VelocityDiagnosticDetailsCollector | Proxy нужен для backend, dependencies и ping |
| VelocityPluginRegistry | Proxy нужен для plugin manager lookup и inventory |
| VelocityPlatformImpl | Proxy реализует существующий публичный native API |
| VelocityMetricsFactory | Все четыре аргумента используются: Metrics.Factory для bStats, остальные — для FastStats |
| Metrics.Factory / Metrics | Injected proxy/logger/path используются для native метрик и конфигурации; это адаптированная поставляемая реализация bStats |
| FastStatsVelocityProvider.Session | Owner/proxy/logger/path/token используются для VelocityContext; проверка Java 21 перед созданием provider сохранена |

Параметры ProxyInitializeEvent и ProxyShutdownEvent оставлены: они задают тип события
для Velocity Subscribe, даже если payload не читается. Для этого случая добавлен
адресный Suppress, а не удалён необходимый параметр метода.

## Границы после изменения

- Entry point отвечает за native события, создание host, сигнал готовности и остановку.
- Core запускает общие функции pnLibrary, включая proxy update command.
- Platform adapter соединяет native возможности и управляет готовностью/остановкой.
- Command/task adapters получают владельца каждой операции через SPI.
- Plugin registry отвечает за разрешение native owner и преобразование descriptor.
- Diagnostics, audiences и metrics сохраняют самостоятельные предметные задачи.

Generic `NativeCommandRegistrar<S>` сохранён: он действительно различает нативные
CommandSource разных платформ. Generic-параметры owner/proxy на всём PlatformAdapter
не добавлялись: публичный межплатформенный runtime хранит владельца как Any.
Вместо распространения этих типов через весь core нативная граница проверяет
регистрацию владельца и передаёт его без подмены в Velocity API.

Публичный VelocityPlatform по-прежнему предоставляет ProxyServer. После аудита
регистрация команд и задач consumer-плагина переведена исключительно на ModuleContext:
из PnLibrary удалены прямые task/command services и runtime helpers. Это намеренный
breaking change, описанный в API-MIGRATION.md. Изменяются также SPI: NativeCommandRegistrar,
PlatformTaskRequest и PlatformOwnerControl; собственные сторонние реализации этих
SPI потребуют перекомпиляции/миграции.

## Что не следует обещать

- GLOBAL/ASYNC/executeReply на Velocity используют proxy scheduler и не означают
  Bukkit main thread. ENTITY scheduling недоступен.
- UncaughtExceptionLogObserver наблюдает JVM default uncaught handler, а не весь
  SLF4J-поток. Его owner — владелец диагностического bridge, а не доказанный виновник
  исключения. Ошибки, перехваченные scheduler, этим механизмом не наблюдаются.
- Готовность означает завершение инициализации pnLibrary и публикацию native API;
  она не гарантирует завершение startup всех остальных плагинов.
- Support snapshot читает живой proxy; все секции не являются атомарной транзакцией.
- Проверка конфликтов команд исключает уже существующее имя на момент проверки,
  но не является общей блокировкой с регистрациями других плагинов.
- Bungee native Command не сообщает фактически введённый alias; там передаётся
  canonical name. Velocity и Bukkit сохраняют реальный alias.
- FastStats требует Java 21; основной Velocity runtime остаётся Java 17.

## Проверка

Регрессионные тесты покрывают native owner команд и задач, aliases и native cleanup,
конфликт с чужой командой, неизвестного владельца, передачу TaskScope.owner из core,
завершение one-shot, создание задачи во время shutdown, ошибки отмены, запрет ENTITY,
очередь readiness, sensitive backend addresses и chaining uncaught handler.

Финальные команды проверки:

```text
gradlew.bat :modules:runtime-spi:apiDump
gradlew.bat check
```

check проверяет API baseline, тесты модулей, компиляцию примеров и сборку
дистрибутивов. Сетевые отправки bStats/FastStats и поведение реального
Velocity proxy под нагрузкой требуют отдельного интеграционного запуска.

Результат: общий check прошёл (141 задача). После последних изменений p95 и
явных audience-делегатов повторно прошёл `platforms:velocity:runtime:test`:
15 тестов, без ошибок и пропусков. `git diff --check` также прошёл.
