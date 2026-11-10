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

## Отчёт `/pn debug`

Отчёт зашифрован, расшифровывает его поддержка (`tools/support/decode_pnsupport.py`). В `plugins/pnLibrary/config.yml`:
`privacy` (по умолчанию `false`) скрывает имена, UUID, адреса и пинг игроков; `excluded-paths` — значения в файлах
конфигов плагинов, которые не попадут в отчёт (путь `ключ.подключ` для всех файлов или `плагин/файл.yml:ключ` для
одного). Поля, помеченные `@ConfigSecret`, скрыты без записи здесь, см. [configuration.md](configuration.md).

## Логгер модуля и чёрный ящик

`ModuleContext.logger` (`PnLogger`) пишет в консоль и в «чёрный ящик»: буфер диагностики, журнал `journal/` и, для ошибки
с исключением, отчёт об ошибках. Короткие формы (`info`, `success`, `warning`, `error`, `critical`) пишут везде. `critical` — сбой, после которого плагин не работает; в консоли строка идёт с пометкой `[CRITICAL]`, успех — с `[OK]`.

| Вызов | Консоль | Чёрный ящик |
|---|---|---|
| `info(msg)`, `success(msg)`, `warning(msg)`, `error(msg)` | да | да |
| `info(msg, ex)`, `warning(msg, ex)`, `error(msg, ex)` | да | да, со стеком |
| `info(msg, false)`, `warning(msg, false)`, `error(msg, ex, false)` | да | нет |
| `debug(msg)` | нет | да |
| `debug(msg, ex)` | нет | да, со стеком |
| `log(level, msg, ex, record)` | да (кроме DEBUG) | да, если `record` истинно |

Последний аргумент `record` по умолчанию истинный. Передайте `false`, когда строка шумная или некритичная и в отчёте
поддержки ей делать нечего (например, сообщение при каждом тике или ожидаемый отказ игрока). `debug` заменяет
`Level.FINE`: деталь попадает в отчёт, а консоль остаётся чистой.

Уровни (`LogLevel`): `DEBUG` (только в отчёт), `INFO`, `SUCCESS`, `WARNING`, `ERROR`, `CRITICAL`. Записи видно в `/pn events` (фильтр по уровню, карточка со стеком, повторы сводятся в одну строку), в `/pn gui` и в отчёте поддержки. Внутри самой библиотеки (конфиги, обновления, загрузки, интеграции) пишет тот же логгер, поэтому её сбои попадают в тот же чёрный ящик.

## Сообщить о проблеме

Плагин записывает проблему в журнал наблюдений: что случилось (`message`), где (`plugin`, `source`), детали (`data`) и, если есть, исключение.

```kotlin
val observability = PnLibraryProvider.get().observability
observability.capture {
    plugin("pnMarket")
    source("auction/expire")
    level(ObservationLevel.ERROR)
    message("Лот не возвращён владельцу")
    data("lot" to lotId, "owner" to ownerId)
    keepFor(Duration.ofDays(365))
}
observability.failure(error) { plugin("pnMarket"); source("auction/save") } // уровень ERROR, исключение записано
```

Срок хранения задаёт автор в коде через `keepFor(...)` (до 3650 дней), у каждой записи свой; игрок и админ его не меняют.
Без `keepFor` срок зависит от уровня: `TRACE` 6 часов, `INFO` 7 дней, `NOTICE` и `WARNING` 30 дней, `ERROR` 90 дней,
`CRITICAL` без срока. Указанный срок заменяет срок уровня и в большую, и в меньшую сторону. Всего хранится не больше 10 000 записей.

В отчёте поддержки (`/pn debug`) записи лежат в `observability/observations.jsonl`, по строке на запись:
`timeUtc` (когда случилось), `expiresUtc` (когда будет удалена, если срок задан), `plugin`, `source`, `level`, `message`,
`data`, `errorType`. Сводка по плагинам, местам и уровням — в `observability/analytics.json`
(`byPlugin`, `bySource`, `byLevel`, последние записи с `timestampUtc`). Записи `PnLogger` и `diagnostics.record` попадают туда же.
