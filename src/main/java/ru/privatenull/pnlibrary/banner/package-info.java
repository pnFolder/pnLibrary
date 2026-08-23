/**
 * Баннеры жизненного цикла плагина и безопасное обновление через GitHub Releases.
 *
 * <h2>Идентификатор плагина</h2>
 * <p>Идентификатор создаётся один раз в {@code onEnable()} и сохраняется в поле:</p>
 *
 * <pre>{@code
 * identity = new PluginBanner.Identity(this, "PnFolder")
 *         .github("owner", "repository")
 *         .bStats(12345)
 *         .autoDownloadUpdates(true)
 *         .showUpToDateMessage(false)
 *         .showUpdateErrors(true)
 *         .notifyAdministrators(true)
 *         .notificationPermission("myplugin.admin")
 *         .notifyOnlineAdministrators(true)
 *         .notifyAdministratorsOnJoin(true)
 *         .updateTimeout(Duration.ofSeconds(20))
 *         .updateCheckInterval(Duration.ofHours(6))
 *         .maxUpdateSizeMegabytes(150);
 * }</pre>
 *
 * <h2>Запуск</h2>
 * <pre>{@code
 * PluginBanner.Data startup = new PluginBanner.Data(identity)
 *         .ok("Конфигурация")
 *         .ok("Команды")
 *         .warn("Vault", "Экономика отключена");
 * PluginBanner.broadcastEnable(startup);
 * }</pre>
 *
 * <h2>Выключение</h2>
 * <pre>{@code
 * PluginBanner.Data shutdown = new PluginBanner.Data(identity)
 *         .ok("Задачи", "Все задачи остановлены")
 *         .ok("База данных", "Соединение закрыто");
 * PluginBanner.broadcastDisable(shutdown);
 * }</pre>
 *
 * <h2>Как применяется автообновление</h2>
 * <p>Новый JAR скачивается асинхронно, проверяется как архив плагина и помещается
 * в {@code plugins/update}. Работающий файл не удаляется. Сервер применяет
 * подготовленную версию при следующем штатном запуске.</p>
 *
 * <p>{@code updateAssetPattern(...)} является необязательной расширенной
 * настройкой и нужна только для Release с несколькими подходящими JAR.</p>
 */
package ru.privatenull.pnlibrary.banner;
