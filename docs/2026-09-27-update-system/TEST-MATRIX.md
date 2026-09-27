# Матрица проверки системы обновлений

Дата проверки: 2026-09-27  
Ветка: `feat/pnlibrary-architecture-pnupdate`

## Автоматические проверки

| Сценарий | Проверка | Результат |
|---|---|---|
| Ручная команда не использует старый GitHub-кэш | `ReleaseCatalogueClientTest`, `UpdateOrchestratorTest` | PASS |
| Текущая версия и доступное обновление различаются корректно | `UpdateResolverTest`, `BukkitControlCommandTest` | PASS |
| Пауза плагина ограничена семью днями | `UpdateConfigurationTest` | PASS |
| Отключённая автозагрузка не отключает проверку pnLibrary | `UpdateConfigurationTest` | PASS |
| Несовместимый API плагина блокирует общий план | `UpdateResolverTest` | PASS |
| Java, платформа и API участвуют в выборе JAR | `UpdateResolverTest`, `ReleaseCatalogueClientTest` | PASS |
| Причина блокировки сохраняется после рестарта | `UpdateStateStoreTest` | PASS |
| Ошибка активации не вызывает автоматический откат | `UpdateTransactionTest` | PASS |
| Подготовленный набор остаётся ожидающим до полной готовности сервера | `UpdateTransactionTest` | PASS |
| Неудачная транзакция без резервных копий не вытесняет рабочую точку отката | `UpdateTransactionTest` | PASS |
| Статус «подготовлено» показывается только плагинам из текущего плана | `BukkitControlCommandTest` | PASS |
| Ручной откат восстанавливает установленный JAR через `plugins/update` | `UpdateTransactionTest` | PASS |
| Ошибка консольного оформления не оставляет загрузку навечно незавершённой | `DirectDownloadManagerTest` | PASS |
| Внешняя зависимость помещается в `plugins/update` | `DirectDownloadManagerTest` | PASS |
| Команды статуса и отката присутствуют, rollback-токен одноразовый | `BukkitControlCommandTest`, `UpdateConfirmationTokensTest` | PASS |

Полный прогон:

```powershell
.\gradlew.bat :modules:api:test :modules:features:update:test :modules:core:test :platforms:bukkit:runtime:test
```

Результат: `BUILD SUCCESSFUL`.

## Проверка сборки

Команда:

```powershell
.\gradlew.bat :examples:acceptance-bukkit:assembleAcceptanceKit
```

Каталог `build/acceptance-bukkit` синхронизируется при каждой сборке и содержит ровно два актуальных файла:

- `pnLibrary-2.2.0-beta.2-bukkit-java8.jar`;
- `pnLibrary-acceptance-2.2.0-beta.2-bukkit-java8.jar`.

В каждом JAR найден ровно один автоматически сгенерированный `META-INF/pnlibrary/component.json`. Ручных копий metadata в `src/main/resources` нет.

## Конфигурация администратора

Файл `plugins/pnLibrary/updates.yml`:

```yaml
updates:
  enabled: true
  library:
    automatic-download: false
  downloads:
    automatic: false
  plugins:
    enabled: true
    automatic-download: false
    plugins:
      acceptance:
        enabled: true
        automatic-download: false
        pause-until: null
```

- pnLibrary проверяется всегда;
- `automatic-download` разрешает только загрузку;
- `plugins.plugins.<id>` относится к плагину, а не к внутреннему компоненту;
- интервал проверки отсутствует в YAML и остаётся частью кода библиотеки;
- максимальная пауза — семь дней.

## Ручная проверка на Paper

1. Поместить оба JAR из `build/acceptance-bukkit` в `plugins` и полностью запустить сервер.
2. Выполнить `/pn update-status acceptance` — получить конкретное состояние и причину.
3. Выполнить `/pn update acceptance` — команда обязана сделать свежий запрос GitHub.
4. При найденном плане убедиться, что JAR появился в `plugins/update`.
5. Полностью перезапустить сервер. Только после события полной готовности сервера система проверит фактически загруженные версии и подтвердит успех либо сохранит ошибку с доступным ручным откатом.
6. Для проверки отката выполнить `/pn update-rollback`, подтвердить кнопку и снова полностью перезапустить сервер.
7. Для blocker-сценария опубликовать библиотеку с новым API без совместимого acceptance: статус должен назвать `acceptance` и требуемый API, а загрузка плана не должна начаться.

Реальный Paper-процесс не запускается unit-тестами Gradle, поэтому этот последний раздел остаётся серверной приёмкой; все используемые им resolver, transaction, state и command-контракты покрыты автоматическими тестами выше.
