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
