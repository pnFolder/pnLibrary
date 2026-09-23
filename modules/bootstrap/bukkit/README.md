# pnLibrary Bootstrap for Bukkit

Small bootstrap library intended to be shaded into a Bukkit/Paper plugin. It has no runtime dependency on pnLibrary and can therefore install pnLibrary before the consuming plugin accesses its API.

```kotlin
dependencies {
    implementation("io.github.pnfolder:pnlibrary-bootstrap-bukkit:<version>")
}

tasks.shadowJar {
    relocate(
        "ru.privatenull.pnlibrary.bootstrap",
        "your.plugin.libs.pnlibrary.bootstrap",
    )
    relocate("com.google.gson", "your.plugin.libs.gson")
}
```

Call the bootstrap before accessing pnLibrary:

```java
@Override
public void onLoad() {
    if (!PnLibraryBootstrap.ensureInstalled(this, "2.2.0")) {
        getServer().getPluginManager().disablePlugin(this);
    }
}
```

Do not declare pnLibrary as a hard `depend` in `plugin.yml`: Bukkit would refuse to load the consuming plugin before the bootstrap can run. Use `softdepend: [pnLibrary]` and keep all pnLibrary-linked classes out of the bootstrap call path until installation succeeds.

The installer accepts only HTTPS GitHub release assets with a declared size and SHA-256 digest, verifies the downloaded size and digest, validates `plugin.yml`, and places the JAR into the server's `plugins` directory using an atomic move when supported.
