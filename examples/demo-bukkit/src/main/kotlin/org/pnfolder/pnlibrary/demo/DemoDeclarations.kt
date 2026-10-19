package org.pnfolder.pnlibrary.demo

import org.pnfolder.pnlibrary.api.downloads.DownloadDestination
import org.pnfolder.pnlibrary.api.plugin.PluginBuilder
import java.nio.file.Path

object DemoDeclarations {
    fun configure(builder: PluginBuilder, dataDirectory: Path) {
        builder.downloads(dataDirectory) { downloads ->
            downloads.file("demo-documentation") { file ->
                file.url("https://example.org/pnLibrary-demo.txt")
                    .destination(DownloadDestination.CACHE, "demo-documentation.txt")
                    .required(false).automaticDownload(false)
            }
        }
    }
}
