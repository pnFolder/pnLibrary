# Диагностика

Плагин может отдавать в pnLibrary снимок своего состояния: он попадает в отчёт `DiagnosticReport` и в архив поддержки.

## Регистрация

```kotlin
val registration = PnLibraryProvider.get().diagnostics.register(
    "pnMarket",
    DiagnosticContainer.builder("auction").snapshot(Supplier { mapOf("lots" to 12) }).build(),
)
// registration.close() убирает отчёт плагина
```

```java
DiagnosticRegistration reg = PnLibraryProvider.get().getDiagnostics().register("pnMarket",
    DiagnosticContainer.builder("auction").snapshot(() -> Map.of("lots", 12)).build());
```

## Правила

- Имя плагина не зависит от регистра.
- Поставщики вызываются параллельно и из любых потоков: они должны быть потокобезопасными и быстрыми. Файлы и сеть в них не читают.
- Уровень подробности задаёт `DiagnosticLevel`, запрос на отладку — `DebugRequest`.
- Закройте `DiagnosticRegistration` при выключении плагина.

## Логгер модуля и чёрный ящик

`ModuleContext.logger` (`PnLogger`) пишет в консоль и в «чёрный ящик»: буфер диагностики, журнал `journal/` и, для ошибки
с исключением, отчёт об ошибках. Короткие формы (`info`, `success`, `warning`, `error`) пишут везде.

| Вызов | Консоль | Чёрный ящик |
|---|---|---|
| `info(msg)`, `success(msg)`, `warning(msg)`, `error(msg)` | да | да |
| `warning(msg, ex)`, `error(msg, ex)` | да | да, со стеком |
| `info(msg, false)`, `warning(msg, false)`, `error(msg, ex, false)` | да | нет |
| `debug(msg)` | нет | да |
| `log(level, msg, ex, record)` | да (кроме DEBUG) | да, если `record` истинно |

Последний аргумент `record` по умолчанию истинный. Передайте `false`, когда строка шумная или некритичная и в отчёте
поддержки ей делать нечего (например, сообщение при каждом тике или ожидаемый отказ игрока). `debug` заменяет
`Level.FINE`: деталь попадает в отчёт, а консоль остаётся чистой.
