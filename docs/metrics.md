# Метрики

`MetricsService` открывает сессию метрик для плагина-владельца: `open(owner, configurations)` возвращает `PluginMetrics`. Каждая включённая конфигурация (`MetricsProviderConfiguration`) добавляет одного провайдера (по умолчанию доступен bStats, `MetricsProvider.BSTATS`).

- Если ни одна конфигурация не включена или у провайдера нет учётных данных, `open` бросает `IllegalArgumentException`.
- Поставщики значений вычисляются платформой, возможно в фоне: они должны быть потокобезопасными, не блокировать и не иметь побочных эффектов.
- Закрытие сессии безопасно повторять.
- `openErrorReporter` открывает необязательную отправку ошибок для той же конфигурации.

Для плагина удобнее `ModuleContext.metrics` (`MetricsController`) и `MetricsBuilder`.

## bStats на нескольких платформах

bStats хранит отдельный проект для каждой платформы, поэтому у плагина, который работает на Bukkit, BungeeCord и Velocity, ID разные. `MetricsBuilder` принимает их в любой комбинации:

```kotlin
metrics.bStats(PlatformType.BUKKIT, 35046)          // по одной платформе
metrics.bStats(mapOf(PlatformType.VELOCITY to 35047, PlatformType.BUNGEECORD to 35048))
metrics.bStats(35000)                                // запасной ID для платформ без своего
```

Библиотека сама выбирает ID запущенной платформы. Если для неё нет ни своего, ни запасного ID, `open` бросает `IllegalArgumentException`.
