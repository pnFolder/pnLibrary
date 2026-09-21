package ru.privatenull.pnlibrary.demo

import ru.privatenull.pnlibrary.api.downloads.DownloadDestination
import ru.privatenull.pnlibrary.api.plugin.PluginBuilder
import java.nio.file.Path

object DemoDeclarations {
    fun configure(builder: PluginBuilder, dataDirectory: Path) {
        builder.updates("pnFolder", "pnLibrary") { updates ->
            updates.component("pndemo").supportedApi(1, 1).automaticDownload(false)
                .artifact("(?i)^pnLibrary-demo-bukkit-.*\\.jar$", minimumJava = 8)
        }
        builder.downloads(dataDirectory) { downloads ->
            downloads.file("demo-documentation") { file ->
                file.url("https://example.org/pnLibrary-demo.txt")
                    .destination(DownloadDestination.CACHE, "demo-documentation.txt")
                    .required(false).automaticDownload(false)
            }
        }
    }
}
