package org.pnfolder.pnlibrary.core.text

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage

/**
 * MiniMessage when the server's Adventure can run it, otherwise [MiniMessageLite]. Availability is checked once;
 * only linkage errors count as "too old", any other failure is a real bug and propagates.
 */
internal class MiniText(private val factory: () -> MiniMessage = MiniMessage::miniMessage) {
    private var checked = false
    private var mini: MiniMessage? = null

    fun deserialize(input: String, warn: (String) -> Unit): Component =
        available(warn)?.deserialize(input) ?: MiniMessageLite.deserialize(input)

    fun serialize(component: Component, warn: (String) -> Unit, fallback: (Component) -> String): String =
        available(warn)?.serialize(component) ?: fallback(component)

    @Synchronized
    private fun available(warn: (String) -> Unit): MiniMessage? {
        if (checked) return mini
        checked = true
        mini = try {
            factory().also { it.deserialize("<red>x</red>") }
        } catch (error: LinkageError) {
            warn("Adventure сервера слишком старый для MiniMessage, включён упрощённый разбор (${error.javaClass.simpleName}: ${error.message})")
            null
        }
        return mini
    }
}
