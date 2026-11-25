package org.pnfolder.pnlibrary.core.commands.deps

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.Mockito.withSettings
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandSender
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.core.commands.engine.CommandRoute
import org.pnfolder.pnlibrary.core.commands.engine.CommandTreeRouter
import org.pnfolder.pnlibrary.core.downloads.dependencies.MissingDependency
import org.pnfolder.pnlibrary.core.downloads.dependencies.MissingGroup
import org.pnfolder.pnlibrary.core.downloads.DownloadService
import org.pnfolder.pnlibrary.core.lifecycle.RuntimeResources
import java.net.URI

class DepsCommandTest {
    private val downloads = mock(DownloadService::class.java)
    private val library = mock(PnLibrary::class.java, withSettings().extraInterfaces(RuntimeResources::class.java))
    private val sent = mutableListOf<String>()
    private val sender = object : CommandSender {
        override fun pointers() = org.pnfolder.pnlibrary.api.audience.senderPointers("Console") { permission -> true }
        override fun sendMessage(message: Component) {
            sent += PlainTextComponentSerializer.plainText().serialize(message)
        }
    }
    private val definition = DepsCommand(library).definition()

    init {
        `when`((library as RuntimeResources).dependencyDownloads()).thenReturn(downloads)
        `when`(downloads.automatic()).thenReturn(false)
    }

    private fun run(vararg words: String): String {
        sent.clear()
        val route = CommandTreeRouter().route(definition, CommandContext(sender, words.toList(), "deps", words.joinToString(" ")))
        (route as CommandRoute.Executable).node.execution.execute(route.context).toCompletableFuture().join()
        return sent.joinToString("\n")
    }

    private fun group(registered: Boolean) = MissingGroup(
        "Shop", "a1b2c3",
        listOf(
            MissingDependency(
                "Vault", "1.7.0", URI("https://example.org/vault"), listOf(URI("https://example.org/Vault.jar")),
                title = "Экономика", description = "Нужна для денег.", required = true,
            ),
            MissingDependency("Optional", "2.0.0", null, emptyList(), required = false),
        ),
        if (registered) mock(org.pnfolder.pnlibrary.api.downloads.DownloadRegistration::class.java) else null,
    )

    @Test
    fun `nothing missing is said plainly`() {
        `when`(downloads.missingGroups()).thenReturn(emptyList())

        val text = run()

        assertTrue(text.contains("выключена"), text)
        assertTrue(text.contains("Всё нужное установлено"), text)
    }

    @Test
    fun `a missing group shows title, why, required or not, links and the install command`() {
        `when`(downloads.missingGroups()).thenReturn(listOf(group(registered = true)))

        val text = run()

        assertTrue(text.contains("Shop просит"), text)
        assertTrue(text.contains("Экономика (Vault) 1.7.0+ | обязательный"), text)
        assertTrue(text.contains("Нужна для денег."), text)
        assertTrue(text.contains("https://example.org/Vault.jar"), text)
        assertTrue(text.contains("Optional 2.0.0+ | необязательный"), text)
        assertTrue(text.contains("/pn deps install a1b2c3"), text)
    }

    @Test
    fun `a group without a direct link tells to download by hand`() {
        `when`(downloads.missingGroups()).thenReturn(listOf(group(registered = false)))

        assertTrue(run().contains("Скачайте по ссылкам"))
    }

    @Test
    fun `auto on and off save the choice`() {
        run("auto", "on")
        run("auto", "off")

        verify(downloads).setAutomatic(true)
        verify(downloads).setAutomatic(false)
    }

    @Test
    fun `an unknown code is refused`() {
        `when`(downloads.installGroup("zzz")).thenReturn(null)

        assertTrue(run("install", "zzz").contains("Такого кода нет"))
    }

    @Test
    fun `the command is named deps and has install and auto`() {
        assertEquals("deps", definition.name)
        assertTrue(definition.root.children.map { it.name }.containsAll(listOf("install", "auto")))
    }
}
