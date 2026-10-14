package ru.privatenull.pnlibrary.core.runtime

import java.nio.file.Path
import java.nio.file.Paths

/** Resolves the host-owned data directory without leaking reflection into the composition root. */
internal object PlatformDataFolderResolver {
    fun resolve(owner: Any, hint: Path?): Path = hint ?: runCatching {
        val method = owner.javaClass.getMethod("getDataFolder")
        (method.invoke(owner) as java.io.File).toPath()
    }.getOrElse {
        Paths.get("plugins", owner.javaClass.simpleName)
    }
}
