# Настройки runtime pnLibrary

Файл `plugins/pnLibrary/config.yml` создаётся при первом запуске. pnLibrary
проверяет типы, диапазоны и названия параметров до запуска сервисов. При ошибке
исправьте указанное поле и перезапустите сервер.

| Параметр | По умолчанию | Назначение |
|---|---:|---|
| `upload` | `true` | Загружать отчёт, если не передан `--local` |
| `upload-mode` | `encrypted-mclogs` | `encrypted-mclogs`, `encrypted`, `mclogs` или `disabled` |
| `allow-plaintext` | `false` | Обязательное явное согласие для режима `mclogs` |
| `configs` | `true` | Разрешить сбор объявленных конфигураций |
| `logs` | `true` | Разрешить сбор журнала pnLibrary |
| `log-records` | `200` | Число последних warning/error, от 1 до 2000 |
| `cooldown-seconds` | `10` | Пауза между командами одного отправителя на Bukkit |
| `keep-reports` | `10` | Число локальных отчётов, от 1 до 1000 |
| `max-report-bytes` | `8388608` | Лимит открытого JSON до шифрования |
| `delete-after-days` | `90` | Когда удалить загруженный отчёт; `0` отключает удаление |

`upload-endpoint` и `upload-public-base` используются только собственным backend
в режиме `encrypted`. `upload-public-key` позволяет заменить встроенный публичный
ключ, а `upload-key-id` указывает идентификатор ключа для поддержки.

Дополнительная защита данных задаётся списками:

```yaml
excluded-paths:
  - storage.internalPool
secret-key-patterns:
  - '(?i).*(password|token|secret|webhook).*'
redact-value-patterns:
  - 'license-[A-Za-z0-9-]+'
```

Обычный `mclogs` передаёт открытый текст и поэтому требует одновременно
`upload-mode: mclogs` и `allow-plaintext: true`. Рекомендуемый режим —
`encrypted-mclogs`: mclo.gs получает только зашифрованный конверт.

Если сеть недоступна, готовый локальный отчёт не удаляется. Команда показывает
путь к нему и отдельное сообщение об ошибке загрузки.
