# Единая система наблюдаемости

`PnLibrary.observability` — единственная точка входа для диагностики, событий и файлов поддержки.
Старые `diagnostics` и `activity` сохранены как совместимые алиасы, но используют тот же журнал и тот же жизненный цикл.

## Быстрый диагностический снимок

```kotlin
val event = library.observability.diagnostic(
    files = listOf(serverLog, githubResponse, pluginConfig),
    metadata = mapOf("operation" to "update-check"),
    pluginId = "acceptance",
    source = "Updater",
)
```

Библиотека сама определяет типы файлов, сохраняет их, связывает с событием и включает в бинарный диагностический отчёт.
Пользовательская конфигурация для журнала не требуется.

## Расширенная регистрация диагностики

```kotlin
library.observability.status("acceptance", "updater", "DEGRADED")
library.observability.record(
    plugin = "acceptance",
    level = DiagnosticLevel.ERROR,
    component = "Updater",
    code = "GITHUB_TIMEOUT",
    message = "GitHub не ответил вовремя",
)
```

Оба вызова попадают в одну систему отчётности и могут быть выгружены через `createDiagnosticReport`.
