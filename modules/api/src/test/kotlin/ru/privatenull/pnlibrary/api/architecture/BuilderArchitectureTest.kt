package ru.privatenull.pnlibrary.api.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.commands.CommandDefinition
import ru.privatenull.pnlibrary.api.config.ConfigMigrationPlan
import ru.privatenull.pnlibrary.api.config.ConfigOptions
import ru.privatenull.pnlibrary.api.config.ConfigTypeAccess
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticConfiguration
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticContainer
import ru.privatenull.pnlibrary.api.downloads.FileDownloads
import ru.privatenull.pnlibrary.api.plugin.RemotePolicy
import ru.privatenull.pnlibrary.api.remote.RemotePolicyContext
import ru.privatenull.pnlibrary.api.tasks.TaskQuery
import ru.privatenull.pnlibrary.api.tasks.TaskSpec
import ru.privatenull.pnlibrary.api.updates.ExternalPluginDependency
import ru.privatenull.pnlibrary.api.updates.PluginUpdateRequest
import ru.privatenull.pnlibrary.api.updates.ProductDescriptor
import java.lang.reflect.Modifier

class BuilderArchitectureTest {
    @Test
    fun `public value declarations expose static builder entry points`() {
        val entryPoints = listOf(
            Triple(CommandDefinition::class.java, arrayOf(String::class.java), "command"),
            Triple(ConfigMigrationPlan::class.java, arrayOf(String::class.java), "config migration"),
            Triple(ConfigOptions::class.java, emptyArray(), "config options"),
            Triple(ConfigTypeAccess::class.java, emptyArray(), "config access"),
            Triple(DiagnosticConfiguration::class.java, arrayOf(String::class.java), "diagnostic configuration"),
            Triple(DiagnosticContainer::class.java, arrayOf(String::class.java), "diagnostic container"),
            Triple(FileDownloads::class.java, emptyArray(), "file downloads"),
            Triple(RemotePolicy::class.java, emptyArray(), "remote policy"),
            Triple(RemotePolicyContext::class.java, emptyArray(), "remote policy context"),
            Triple(TaskQuery::class.java, emptyArray(), "task query"),
            Triple(TaskSpec::class.java, emptyArray(), "task spec"),
            Triple(PluginUpdateRequest::class.java, emptyArray(), "update request"),
            Triple(ExternalPluginDependency::class.java, arrayOf(String::class.java, String::class.java), "external dependency"),
            Triple(ProductDescriptor::class.java, arrayOf(String::class.java, String::class.java), "product descriptor"),
        )

        entryPoints.forEach { (type, parameters, label) ->
            val method = type.getMethod("builder", *parameters)
            assertTrue(Modifier.isStatic(method.modifiers), "$label builder must be static for Java")
        }
    }
}
