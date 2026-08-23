/**
 * Проверка и безопасная подготовка обновлений плагина.
 *
 * <p>Пакет разделён по ответственности:</p>
 * <ul>
 *   <li>{@link ru.privatenull.pnlibrary.update.PluginUpdateService} управляет
 *       расписанием и состоянием;</li>
 *   <li>GitHub-клиент получает сведения о последнем Release;</li>
 *   <li>установщик проверяет JAR и помещает его в {@code plugins/update};</li>
 *   <li>listener уведомляет администраторов онлайн и при входе;</li>
 *   <li>SemVer-компаратор определяет, действительно ли версия новее.</li>
 * </ul>
 *
 * <p>Обычно пакет не требуется настраивать напрямую: параметры задаются через
 * {@code PluginBanner.Identity}, а сервис запускается методом
 * {@code PluginBanner.broadcastEnable(...)}.</p>
 */
package ru.privatenull.pnlibrary.update;
