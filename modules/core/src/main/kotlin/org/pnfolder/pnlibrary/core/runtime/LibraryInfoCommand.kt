package org.pnfolder.pnlibrary.core.runtime

import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.command
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.version.PnLibraryApi
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.chat.ChatReader
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity

/**
 * `/pninfo`: one command on every platform that shows the installed pnLibrary version,
 * the platform it runs on and a few facts that help a support request.
 *
 * It needs no permission: nothing in the output is secret.
 */
internal fun libraryInfoCommand(
    library: PnLibrary,
    identity: PlatformIdentity,
    version: () -> String,
): CommandDefinition = command("pninfo") {
    consoleBypassesPermission()
    executes { context ->
        val kit = ChatKit(ChatReader.of(context.sender))
        val rows = listOf(
            kit.header("pnLibrary"),
            kit.field("Версия", kit.text(version(), ChatPalette.TEXT)),
            kit.field("Платформа", kit.text("${identity.implementationName} (${identity.id})", ChatPalette.TEXT)),
            kit.field("Версия API", kit.text("${PnLibraryApi.VERSION}", ChatPalette.TEXT)),
            kit.field("Java", kit.text(System.getProperty("java.version") ?: "неизвестно", ChatPalette.TEXT)),
            kit.field("Плагинов подключено", kit.text("${library.plugins.all().count()}", ChatPalette.TEXT)),
        )
        rows.forEach(context.sender::send)
    }
}
