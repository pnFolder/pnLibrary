# Bukkit-предметы и визуальные сущности

Инструменты находятся в `pnlibrary-bukkit-api`. Плагин подключает API как
`compileOnly`; платформенный JAR pnLibrary предоставляет реализацию на сервере.

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    compileOnly("io.github.pnfolder:pnlibrary-bukkit-api:2.2.0-beta.2")
}
```

## ItemFactory

```yaml
icon:
  material: DIAMOND
  amount: 2
  name: "&bНаграда"
  lore:
    - "&7Нажмите, чтобы получить"
  enchantments:
    unbreaking: 3
```

```java
ItemStack icon = ItemFactory.fromSection(config.getConfigurationSection("icon"));
ItemFactory.writeItem(config, "icon", icon);
ItemFactory.writeExactItem(config, "exact-icon", icon);
```

`writeItem` создаёт читаемую конфигурацию. `writeExactItem` дополнительно сохраняет
`item_data`, чтобы не потерять нестандартные метаданные предмета.

## ItemStackCodec

```java
String stored = ItemStackCodec.encode(item);
ItemStack restored = ItemStackCodec.decode(stored);
```

Декодировать следует только данные сервера или доверенного плагина: payload содержит
полностью сериализованный Bukkit-объект.

## HeadUtil

```java
ItemStack head = HeadUtil.create(textureHash, "&6Профиль игрока");
String validTexture = HeadUtil.normalizeTexture(configuredTexture);
```

Поддерживаются hash, полный `textures.minecraft.net` URL и Base64 JSON. На новых
версиях используется публичный PlayerProfile API, на старых применяется совместимый
reflection fallback.

## VisualEntity

```java
VisualEntity reward = VisualEntity.item(location, item);
reward.setScale(0.6f);
reward.teleport(nextLocation);
reward.setRotation(yaw, pitch);

// Обязательно при завершении анимации.
reward.remove();
```

Также доступны `VisualEntity.block(...)` и `VisualEntity.text(...)`. Логическая
позиция не содержит внутренний armor-stand offset, поэтому один и тот же `Location`
можно безопасно передавать в `teleport` на каждом кадре анимации.
