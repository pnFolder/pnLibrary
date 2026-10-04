# Наблюдаемость и диагностические отчёты

`PnLibrary.observability` — единая точка входа для событий, ошибок, состояния компонентов и файлов поддержки.
Старые свойства `diagnostics` и `activity` сохранены для совместимости с уже собранными плагинами, но их события попадают в тот же runtime и журнал.

## Что хранит система

- наблюдения в `plugins/pnLibrary/observability/observations.jsonl`;
- вложения в `plugins/pnLibrary/observability/attachments/`;
- метаданные вложений в постоянном `manifest.json`;
- последнее состояние каждого компонента в памяти процесса;
- критические записи бессрочно, остальные — согласно внутренней политике хранения.

Пути исходных файлов в журнал не записываются. Тип файла, размер и SHA-256 вычисляет библиотека. Исполняемые файлы и скрипты прикреплять нельзя.

## Kotlin

```kotlin
library.observability.capture {
    plugin("acceptance")
    source("update-check")
    message("Получен каталог релизов")
    level(ObservationLevel.NOTICE)
    data("releases" to releases.size, "channel" to channel)
    files(githubResponse, pluginConfig)
}

library.observability.failure(error) {
    plugin("acceptance")
    source("update-download")
    data("version" to targetVersion)
    files(serverLog)
}
```

Один вызов создаёт ровно одно наблюдение. Переданные файлы сохраняются как вложения этого наблюдения; MIME-тип указывать не нужно.

## Java

Для Java-кода можно собрать `ObservationRequest` и вызвать `record(request)` напрямую. Все модели
имеют обычные JVM-getter-методы.

```java
ObservationRequest request = new ObservationRequest(
    "acceptance",
    "update-download",
    "Update download failed",
    ObservationLevel.ERROR,
    Map.of("version", targetVersion),
    List.of(serverLog),
    error
);

Observation saved = library.getObservability().record(request);
```

## Как выбрать операцию

| Задача | Операция |
| --- | --- |
| Записать обычное событие | `capture { ... }` или `record(request)` |
| Записать исключение | `failure(error) { ... }` |
| Обновить текущее состояние компонента | `status(ComponentStatus(...))` |
| Прочитать историю | `recent(ObservationQuery(...))` |
| Собрать файл для поддержки | `createReport(ObservabilityReportRequest(...))` |

`status` не заменяет историю: он хранит только последнее состояние компонента. `capture` и
`failure` создают отдельные хронологические записи.

## Состояние компонента

```kotlin
library.observability.status(
    ComponentStatus(
        plugin = "acceptance",
        component = "database",
        state = "ready",
        detail = "Основное соединение доступно",
    )
)
```

Повторная запись той же пары `plugin + component` заменяет предыдущее состояние.

## Чтение истории

```kotlin
val failures = library.observability.recent(
    ObservationQuery(
        plugin = "acceptance",
        minimumLevel = ObservationLevel.ERROR,
        limit = 100,
    )
)
```

Фильтры можно сочетать: плагин, минимальная важность, начало и конец периода, максимальное количество записей.

## Отчёт

```kotlin
val report = library.observability.createReport(
    ObservabilityReportRequest(
        target = "acceptance",
        includeLogs = true,
        includeConfigurations = true,
    )
)
```

Отчёт получает один согласованный снимок журнала и вложений. Локальный бинарный файл сохраняется даже при ошибке удалённой загрузки.

## Карта реализации

- `api.observability` — публичные неизменяемые модели и DSL;
- `core.observability.ObservabilityRuntime` — единственная точка оркестрации;
- `ObservationJournal` — JSONL-журнал;
- `AttachmentStore` — байты и манифест вложений;
- `ComponentStatusRegistry` — актуальные состояния;
- `ObservabilityReportSnapshot` — источник данных для отчёта;
- `observability.report` — сборка архива, сохранение и публикация отчёта;
- `LegacyObservabilityAdapter` — адаптер старых API без собственного хранилища;
- `LegacyActivityMapper` — явное преобразование старых событий;
- `DiagnosticObservationBridge` — один диагностический инцидент превращает в одно наблюдение.

## Контракт хранения

1. Вызов API создаёт неизменяемое наблюдение с уникальным идентификатором.
2. Файлы копируются в хранилище вложений; исходный абсолютный путь не публикуется.
3. Запись журнала и манифест вложений становятся источником одного согласованного отчёта.
4. Обычная история очищается по внутренней политике хранения.
5. Критические события сохраняются независимо от обычного временного окна.

Настройки срока хранения и безопасности являются частью кода библиотеки, а не пользовательской
конфигурации сервера.
