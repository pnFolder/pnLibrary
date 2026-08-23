/**
 * Простое структурированное логирование Bukkit/Paper-плагина.
 *
 * <pre>{@code
 * runtime.log().info("Загрузка конфигурации");
 * runtime.log().success("Конфигурация загружена");
 * runtime.log().warn("Vault не найден");
 * runtime.log().error("Не удалось подключиться к базе", exception);
 *
 * runtime.mBox("Инициализация модулей")
 *         .ok("Конфигурация", "Файл загружен")
 *         .ok("Команды", "Зарегистрировано: 5")
 *         .skip("Discord", "Интеграция отключена")
 *         .fail("База данных", exception)
 *         .show();
 * }</pre>
 *
 * <p>Тот же логгер доступен до создания runtime через
 * {@code identity.log()} и {@code identity.mBox(...)}.</p>
 */
package ru.privatenull.pnlibrary.logging;
