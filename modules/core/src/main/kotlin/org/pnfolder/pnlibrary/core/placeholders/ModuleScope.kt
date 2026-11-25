package org.pnfolder.pnlibrary.core.placeholders

import org.pnfolder.pnlibrary.api.placeholders.info.FormatInfo
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentConverter
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapterRegistry
import org.pnfolder.pnlibrary.api.placeholders.info.PlaceholderInfo
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderValue
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo
import org.pnfolder.pnlibrary.api.registry.Registry
import org.pnfolder.pnlibrary.api.registry.Visibility
import org.pnfolder.pnlibrary.core.registry.ListRegistry
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * The view of one module on the placeholder system: what it registers belongs to [owner] (`plugin/module`) and is
 * released by [close]. With [papiEnabled] off, nothing of the module is shown to PlaceholderAPI.
 */
internal class ModuleScope(
    private val engine: PlaceholderEngine,
    private val owner: String,
    private val papiEnabled: Boolean,
) : PlaceholderService {
    private fun scanner() = Scanner { type -> engine.converters.find(type, owner) }

    override fun register(vararg holders: Any, papi: String?) {
        checkPrefix(papi)
        scanner().scan(holders.toList()).forEach { engine.add(PlaceholderEngine.Entry(owner, it, papi.takeIf { papiEnabled })) }
    }

    override fun value(address: String, description: String, papi: String?, visibility: Visibility, resolve: PlaceholderValue): AutoCloseable {
        checkPrefix(papi)
        val words = address.trim().removePrefix("{").removeSuffix("}").split('.').map { checkWord(it.lowercase()) }
        val nodes = words.map { PhNode(PhPart.Word(it), Any::class.java) }
        nodes.zipWithNext().forEach { (parent, child) -> parent.children += child }
        nodes.last().also {
            it.description = description
            it.value = { call ->
                when (val result = resolve.get(call.viewer)) {
                    is CompletionStage<*> -> result.thenApply { inner -> inner }
                    else -> CompletableFuture.completedFuture(result)
                }
            }
        }
        nodes.last().visibility = visibility
        val entry = PlaceholderEngine.Entry(owner, nodes.first(), papi.takeIf { papiEnabled })
        engine.add(entry)
        return AutoCloseable { engine.remove(entry) }
    }

    override fun formats(vararg holders: Any) {
        AnnotatedFormats.register(engine.formats, owner, holders.toList()) { type -> engine.converters.find(type, owner) }
    }

    override fun <T : Any> converter(type: Class<T>, converter: ArgumentConverter<T>): AutoCloseable =
        engine.converters.add(owner, type, converter)

    override fun resolve(address: String, viewer: ViewerInfo, values: Map<String, Any?>) =
        engine.resolveIn(address, viewer, values, null, owner)

    override fun render(text: String, viewer: ViewerInfo, values: Map<String, Any?>) = engine.renderAs(text, viewer, values, owner)

    override fun contains(address: String) = engine.containsFor(address, owner)

    override fun list() = engine.list()

    override fun registry(): Registry<PlaceholderInfo> = ListRegistry("placeholders") { list().map { it.asEntry() } }

    override fun formatRegistry(): Registry<FormatInfo> = ListRegistry("formats") { engine.formats.all().sortedBy { it.name }.map { it.asEntry() } }

    override fun adapters(): PlaceholderAdapterRegistry = engine

    override fun close() = engine.removeOwner(owner)

    private fun checkPrefix(papi: String?) {
        require(papi == null || papi.matches(PREFIX)) {
            "PlaceholderAPI prefix '$papi' must be 2-32 characters of a-z, 0-9 and '-' (no '_': PlaceholderAPI splits on it)"
        }
    }

    private companion object {
        val PREFIX = Regex("[a-z0-9-]{2,32}")
    }
}
