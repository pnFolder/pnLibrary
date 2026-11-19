package org.pnfolder.pnlibrary.api.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition
import org.pnfolder.pnlibrary.api.config.ConfigOptions
import org.pnfolder.pnlibrary.api.plugin.PluginAccess
import org.pnfolder.pnlibrary.api.dependencies.external.ExternalPluginDependency
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticConfiguration
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticContainer
import org.pnfolder.pnlibrary.api.downloads.FileDownloads
import org.pnfolder.pnlibrary.api.remote.RemotePolicyContext
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings
import org.pnfolder.pnlibrary.api.tasks.info.TaskQuery
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec
import org.pnfolder.pnlibrary.api.updates.request.PluginUpdateRequest
import org.pnfolder.pnlibrary.api.updates.product.ProductDescriptor
import java.lang.reflect.Modifier

class BuilderArchitectureTest {
    @Test
    fun `public value declarations expose static builder entry points`() {
        val entryPoints = listOf(
            Triple(CommandDefinition::class.java, arrayOf(String::class.java), "command"),
            Triple(ConfigOptions::class.java, emptyArray(), "config options"),
            Triple(PluginAccess::class.java, emptyArray(), "plugin access"),
            Triple(DiagnosticConfiguration::class.java, arrayOf(String::class.java), "diagnostic configuration"),
            Triple(DiagnosticContainer::class.java, arrayOf(String::class.java), "diagnostic container"),
            Triple(FileDownloads::class.java, emptyArray(), "file downloads"),
            Triple(RemotePolicySettings::class.java, emptyArray(), "remote policy"),
            Triple(RemotePolicyContext::class.java, emptyArray(), "remote policy context"),
            Triple(TaskQuery::class.java, emptyArray(), "task query"),
            Triple(TaskSpec::class.java, emptyArray(), "task spec"),
            Triple(PluginUpdateRequest::class.java, emptyArray(), "update request"),
            Triple(ExternalPluginDependency::class.java, arrayOf(String::class.java, String::class.java), "external dependency"),
            Triple(ProductDescriptor::class.java, emptyArray(), "product descriptor"),
        )

        entryPoints.forEach { (type, parameters, label) ->
            val method = type.getMethod("builder", *parameters)
            assertTrue(Modifier.isStatic(method.modifiers), "$label builder must be static for Java")
        }
    }
}
