# pnLibrary Compatibility Guard for Bukkit

This module is embedded into the consuming plugin and does not use pnLibrary runtime classes. It is useful when the user has disabled automatic updates: the plugin can still refuse to start if the installed library is missing, disabled, or too old.

```kotlin
dependencies {
    implementation("io.github.pnfolder:pnlibrary-compatibility-bukkit:<version>")
}

tasks.shadowJar {
    relocate("ru.privatenull.pnlibrary.compatibility", "your.plugin.libs.pnlibrary.compatibility")
    relocate("ru.privatenull.pnlibrary.console", "your.plugin.libs.pnlibrary.console")
}
```

```java
@Override
public void onEnable() {
    if (!PnLibraryCompatibility.require(this, "2.2.0")) {
        return;
    }
    // Safe to use pnLibrary here.
}
```

For a different managed library:

```java
PnLibraryCompatibility.require(this,
    CompatibilityOptions.builder("2.2.0")
        .pluginName("pnLibrary")
        .repository("pnFolder", "pnLibrary")
        .build());
```
