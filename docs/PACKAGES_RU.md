# Структура pnLibrary и переход на beta.4

Пакеты соответствуют папкам исходников. Общие возможности не требуют Bukkit, BungeeCord или Velocity API.

| Модуль / пакет | Назначение |
|---|---|
| `pnlibrary-api` / `api.runtime` | PnLibrary, провайдер, результат запуска, настройки runtime |
| `api.integration` | Общий конструктор интеграции плагина |
| `api.diagnostics` | Контейнеры, конфигурации и регистрация диагностики |
| `api.logging`, `api.metrics`, `api.tasks`, `api.updates` | Контракты соответствующих сервисов |
| `api.platform` | Контракт платформенного адаптера |
| `api.version` | Семантические версии плагинов и диапазоны |
| `api.version.minecraft` | Enum MinecraftVersion, разбор строк, сравнение и диапазоны |
| `api.config` | ManagedConfig, ConfigGroup, кодек и валидация |
| `pnlibrary-core` / `core.config.yaml` | CodeFirstYaml и слияние новых ключей YAML |
| `core.runtime`, `core.tasks`, `core.logging`, `core.metrics`, `core.updates` | Реализации общих сервисов |
| `core.diagnostics`, `core.security`, `core.upload` | Сбор, шифрование и загрузка отчётов |
| `pnlibrary-bukkit` / `bukkit.compat` | Определение текущей версии Bukkit и его возможностей |

## Сравнение версий на любой платформе

```kotlin
import ru.privatenull.pnlibrary.api.version.minecraft.MinecraftVersion

val version = MinecraftVersion.parse("1.21.11")
val supported = MinecraftVersion.V1_12_2..MinecraftVersion.V26_2
if (version in supported) {
    // Версия входит в диапазон.
}
```

```java
import ru.privatenull.pnlibrary.api.version.minecraft.MinecraftVersion;

MinecraftVersion version = MinecraftVersion.parse("1.21.11");
boolean supported = version.isBetween(MinecraftVersion.V1_12_2, MinecraftVersion.V26_2);
```

Bukkit/Paper/Folia: текущая версия доступна через
`ru.privatenull.pnlibrary.bukkit.compat.BukkitMinecraftVersion.current()`
или `ServerCapabilities.minecraftVersion`.

У прокси нет единственной версии Minecraft-ядра: версия клиента и версия подключённого backend могут отличаться.
Передайте строку нужной версии в общий `MinecraftVersion.parse(...)`.
Версию самого прокси нельзя подставлять вместо версии Minecraft.
Неизвестная enum-версия возвращает `UNKNOWN` и не попадает в диапазон.

## Конфигурации на любой платформе

После локальной публикации модулей (`publishToMavenLocal`) подключите:

```kotlin
repositories { mavenLocal() }
dependencies {
    compileOnly("ru.privatenull:pnlibrary-api:2.0.0-beta.4")
    compileOnly("ru.privatenull:pnlibrary-core:2.0.0-beta.4") { isTransitive = false }
}
```

Это локальный Maven-репозиторий; наличие GitHub Release не публикует Maven-координаты автоматически.
Установленный runtime pnLibrary предоставляет классы на сервере.

```kotlin
import ru.privatenull.pnlibrary.api.config.*
import ru.privatenull.pnlibrary.core.config.yaml.CodeFirstYaml

val config: ManagedConfig<Settings> = CodeFirstYaml(
    file, Settings(), codec, java.util.logging.Logger.getLogger("MyPlugin"),
    ConfigValueValidator { value ->
        if (value.database.port in 1..65535) emptyList()
        else listOf(ConfigProblem("database.port", "должен быть от 1 до 65535"))
    },
)
val settings = config.loadValue()
config.save(settings)
config.reload()
config.unload()
```

`Settings`, `file` и `codec: ConfigCodec<Settings>` задаёт ваш плагин.
Новые ключи добавляются при загрузке. Проверки и работа с файлами одинаковы на всех платформах.
Bukkit `FileConfiguration` обслуживается отдельным Bukkit-специфичным `AtomicYamlStore`.

## Миграция

В beta.4 изменились публичные JVM-пакеты. Пересоберите интеграции с новыми импортами
и обновите pnLibrary вместе с зависимыми плагинами при остановленном ядре.
Старые двоичные плагины автоматически совместимыми не становятся.
PnClans в этой рабочей копии уже использует новые пакеты.

Загрузчик отсутствующей библиотеки должен состоять из классов самого плагина:
обращаться к API pnLibrary до её установки нельзя, включая сравнение версий.
