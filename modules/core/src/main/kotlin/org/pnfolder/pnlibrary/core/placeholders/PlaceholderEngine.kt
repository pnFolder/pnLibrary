package org.pnfolder.pnlibrary.core.placeholders

import org.pnfolder.pnlibrary.api.placeholders.ArgumentConverter
import org.pnfolder.pnlibrary.api.placeholders.ArgumentInput
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapter
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapterRegistry
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderInfo
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderValue
import org.pnfolder.pnlibrary.api.placeholders.RouteInfo
import org.pnfolder.pnlibrary.api.placeholders.RouteKind
import org.pnfolder.pnlibrary.api.placeholders.RoutePart
import org.pnfolder.pnlibrary.api.placeholders.ViewerInfo
import org.pnfolder.pnlibrary.api.placeholders.Visibility
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * The placeholder system of the library. It keeps the tree of placeholders, reads addresses, applies formats, fills
 * texts and shows the placeholders that ask for it to PlaceholderAPI through [PlaceholderAdapter]s.
 *
 * Every module works through its own [scope], which releases what the module registered when the module closes.
 *
 * @param onProblem receives a readable line for every problem that is not an exception of the caller, such as an
 *   unknown format in an address
 */
class PlaceholderEngine(private val onProblem: (String) -> Unit = {}) : PlaceholderAdapterRegistry {
    internal class Entry(val owner: String, val root: PhNode, val papi: String?)

    private class ConverterEntry(val owner: String, val visibility: Visibility, val converter: ArgumentConverter<Any>)

    private class Match(val node: PhNode, val path: List<PhNode>)

    private val groups = ConcurrentHashMap<String, CopyOnWriteArrayList<Entry>>()
    private val adapters = ConcurrentHashMap<String, PlaceholderAdapter>()
    private val publications = ConcurrentHashMap<String, AutoCloseable>()
    private val problems = ConcurrentHashMap.newKeySet<String>()
    private val converters = ConcurrentHashMap<Class<*>, CopyOnWriteArrayList<ConverterEntry>>()
    internal val formats = FormatRegistry(::report).also(BuiltInFormats::register)

    /** The view of one module. [owner] is `plugin/module`; with [papiEnabled] off, nothing of the module is shown to PlaceholderAPI. */
    fun scope(owner: String, papiEnabled: Boolean = true): PlaceholderService = ModuleScope(owner, papiEnabled)

    /** Tells the administrator once about [message]. */
    fun report(message: String) {
        if (problems.size < MAXIMUM_REPORTED && problems.add(message)) onProblem(message)
    }

    // --- adapters -------------------------------------------------------------------------------------------

    override fun register(adapter: PlaceholderAdapter): AutoCloseable {
        val key = adapter.id.lowercase(Locale.ROOT)
        require(adapters.putIfAbsent(key, adapter) == null) { "Placeholder adapter ${adapter.id} is already registered" }
        reconcile()
        return AutoCloseable {
            if (adapters.remove(key, adapter)) {
                publications.keys.toList().filter { it.startsWith("$key/") }.forEach { publications.remove(it)?.let(::closeQuietly) }
                if (adapter is AutoCloseable) closeQuietly(adapter)
            }
        }
    }

    override fun get(id: String): PlaceholderAdapter? = adapters[id.lowercase(Locale.ROOT)]

    override fun all(): List<PlaceholderAdapter> = adapters.values.sortedBy(PlaceholderAdapter::id)

    /** Shows every prefix that asks for it to every adapter that is there, and withdraws the prefixes nobody asks for. */
    @Synchronized
    private fun reconcile() {
        val wanted = groups.values.flatten().mapNotNull { it.papi }.toSet()
        for ((id, adapter) in adapters) {
            for (prefix in wanted) {
                val key = "$id/$prefix"
                if (publications.containsKey(key) || !adapter.available) continue
                try {
                    publications[key] = adapter.publish(prefix) { playerId, params, wait -> answer(prefix, playerId, params, wait) }
                } catch (failure: Exception) {
                    report("Could not show '$prefix' to ${adapter.id}: ${failure.message ?: failure.javaClass.simpleName}")
                }
            }
        }
        publications.keys.filter { it.substringAfter('/') !in wanted }.forEach { publications.remove(it)?.let(::closeQuietly) }
    }

    private fun closeQuietly(handle: AutoCloseable) {
        try {
            handle.close()
        } catch (failure: Exception) {
            report("Closing failed: ${failure.message ?: failure.javaClass.simpleName}")
        }
    }

    /** What PlaceholderAPI asks for `%prefix_params%`: `_` separates the parts, `~5F` writes a real underscore. */
    private fun answer(prefix: String, playerId: UUID?, params: String, wait: Long): String? {
        val address = params.replace('_', '.')
        val future = resolveIn(address, ViewerInfo(playerId, viaPapi = true), emptyMap(), prefix, null).toCompletableFuture()
        val value = try {
            if (wait <= 0) future.getNow(null) else future.get(wait, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            null
        }
        return value?.toString()
    }

    // --- reading --------------------------------------------------------------------------------------------

    /** The value of [address], formats applied, or `null`. */
    fun resolve(address: String, viewer: ViewerInfo = ViewerInfo.none(), values: Map<String, Any?> = emptyMap()): CompletionStage<Any?> =
        resolveIn(address, viewer, values, null, null)

    /** Like [resolve], but hidden placeholders answer too: for the `test` command of an operator. */
    fun resolveAll(address: String, viewer: ViewerInfo = ViewerInfo.none()): CompletionStage<Any?> =
        resolveIn(address, viewer, emptyMap(), null, EVERYTHING)

    /** The entries of a group, the nearest to [consumer] first. A placeholder inside may still be closed to it, see [reachable]. */
    private fun visibleEntries(group: String, consumer: String?): List<Entry> =
        groups[group].orEmpty().sortedBy { nearness(it.owner, Visibility.ALL, consumer) ?: Int.MAX_VALUE }

    /** Whether [consumer] may use the placeholder at [node] of an entry of [owner]. A `null` consumer is the outside world. */
    private fun reachable(owner: String, node: PhNode, consumer: String?) = nearness(owner, node.visibility, consumer) != null

    private fun resolveIn(address: String, viewer: ViewerInfo, values: Map<String, Any?>, prefix: String?, consumer: String?): CompletionStage<Any?> {
        if (prefix == null) {
            val trimmed = address.trim()
            val body = if (trimmed.startsWith("{") && trimmed.endsWith("}")) trimmed.substring(1, trimmed.length - 1) else trimmed
            if (Addresses.depth(body) > Addresses.MAX_NESTING) {
                report("Placeholder {${body.take(40)}...} holds more than ${Addresses.MAX_NESTING} placeholders inside each other")
                return CompletableFuture.completedFuture(null)
            }
            val segments = Addresses.innerParts(body)
            if (segments != null) {
                return expandInner(segments, viewer, values, consumer).thenCompose { filled ->
                    if (filled == null) CompletableFuture.completedFuture(null) else resolveIn(filled, viewer, values, null, consumer)
                }
            }
        }
        val parsed = Addresses.parse(address) ?: return CompletableFuture.completedFuture(null)
        val path = parsed.words.joinToString(".")
        if (prefix == null && values.isNotEmpty() && parsed.owner == null) {
            val own = values[path] ?: values[address.removePrefix("{").removeSuffix("}").substringBefore('|').trim()]
            if (own != null) return CompletableFuture.completedFuture(applyFormats(own, null, parsed.formats, viewer, address, null, consumer))
        }
        var hidden: Pair<Entry, PhNode>? = null
        for (entry in visibleEntries(parsed.words.first().lowercase(), consumer)) {
            if (prefix != null && entry.papi != prefix) continue
            if (!ownerFits(parsed.owner, entry.owner, consumer)) continue
            val match = find(entry.root, parsed.words, 1, listOf(entry.root)) ?: continue
            if (!reachable(entry.owner, match.node, consumer)) {
                if (hidden == null) hidden = entry to match.node
                continue
            }
            return evaluate(match, parsed, viewer, address, consumer)
        }
        if (consumer != null && hidden != null) {
            report("Placeholder {$path} of ${hidden.first.owner} is not visible to $consumer (visibility: ${hidden.second.visibility.name.lowercase()})")
        }
        if (prefix != null) return CompletableFuture.completedFuture(null)
        val outside = fromAdapters(viewer.playerId, path)
        if (outside == null) report("Placeholder {$path} is not registered")
        return CompletableFuture.completedFuture(outside)
    }

    /**
     * Fills the `{inner}` placeholders of an address, as in `{clan.of.{player.name}.level}`: each one is read first and its
     * value, quoted when needed, takes its place. `null` when one of them gives no value.
     */
    private fun expandInner(segments: List<Pair<Boolean, String>>, viewer: ViewerInfo, values: Map<String, Any?>, consumer: String?): CompletionStage<String?> {
        val parts = segments.map { (inner, text) ->
            if (!inner) {
                CompletableFuture.completedFuture<String?>(text)
            } else {
                resolveIn(text, viewer, values, null, consumer).toCompletableFuture()
                    .handle { value, _ -> value?.toString()?.let(Addresses::quote) }
            }
        }
        return CompletableFuture.allOf(*parts.toTypedArray()).thenApply {
            val done = parts.map { it.join() }
            if (done.any { it == null }) null else done.joinToString("")
        }
    }

    private fun fromAdapters(playerId: UUID?, path: String): Any? {
        for (adapter in all()) {
            if (!adapter.available) continue
            try {
                adapter.resolve(playerId, path)?.let { return it }
            } catch (failure: Exception) {
                report("Placeholder {$path} failed in ${adapter.id}: ${failure.message ?: failure.javaClass.simpleName}")
            }
        }
        return null
    }

    /** [text] with every `{address}` replaced; then the outside systems fill their own `%tokens%`. */
    fun render(text: String, viewer: ViewerInfo = ViewerInfo.none(), values: Map<String, Any?> = emptyMap()): CompletionStage<String> =
        renderAs(text, viewer, values, null)

    private fun renderAs(text: String, viewer: ViewerInfo, values: Map<String, Any?>, consumer: String?): CompletionStage<String> {
        val pieces = mutableListOf<CompletableFuture<String>>()
        var from = 0
        while (true) {
            val open = text.indexOf('{', from)
            val close = if (open < 0) -1 else matchingClose(text, open)
            if (open < 0 || close < 0) break
            if (open > from) pieces += CompletableFuture.completedFuture(text.substring(from, open))
            val original = text.substring(open, close + 1)
            pieces += resolveIn(original, viewer, values, null, consumer).toCompletableFuture().handle { value, _ -> value?.toString() ?: original }
            from = close + 1
        }
        if (from < text.length) pieces += CompletableFuture.completedFuture(text.substring(from))
        return CompletableFuture.allOf(*pieces.toTypedArray())
            .thenApply { pieces.joinToString("") { it.join() } }
            .thenApply { filled -> renderOutside(filled, viewer.playerId) }
    }

    /** The `}` that closes the `{` at [open], with `{}` pairs inside skipped; `-1` when it is not closed. */
    private fun matchingClose(text: String, open: Int): Int {
        var depth = 0
        for (index in open until text.length) {
            when (text[index]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return index
            }
        }
        return -1
    }

    private fun renderOutside(text: String, playerId: UUID?): String {
        if ('%' !in text) return text
        return all().filter { it.available }.fold(text) { current, adapter ->
            try {
                adapter.render(playerId, current)
            } catch (failure: Exception) {
                report("${adapter.id} failed to render a text: ${failure.message ?: failure.javaClass.simpleName}")
                current
            }
        }
    }

    /** Whether [address] names a registered placeholder; nothing is evaluated. */
    fun contains(address: String): Boolean = containsFor(address, null)

    private fun containsFor(address: String, consumer: String?): Boolean {
        val parsed = Addresses.parse(address) ?: return false
        return visibleEntries(parsed.words.first().lowercase(), consumer)
            .filter { ownerFits(parsed.owner, it.owner, consumer) }
            .any { entry -> find(entry.root, parsed.words, 1, listOf(entry.root))?.let { reachable(entry.owner, it.node, consumer) } == true }
    }

    /** Every placeholder, sorted by address. */
    fun list(): List<PlaceholderInfo> {
        val found = mutableListOf<PlaceholderInfo>()
        for (entries in groups.values) for (entry in entries) collect(entry, entry.root, emptyList(), found)
        return found.sortedBy { it.address }
    }

    /** The names of every format, built-in ones included. */
    fun formatNames(): List<String> = formats.names()

    private fun collect(entry: Entry, node: PhNode, parent: List<String>, into: MutableList<PlaceholderInfo>) {
        val here = parent + when (val part = node.part) {
            is PhPart.Word -> part.name
            is PhPart.Slot -> "{${part.name}}"
        }
        if (node.value != null) {
            val published = entry.papi?.takeIf { node.visibility == Visibility.ALL }?.let { listOf(it + "_" + here.joinToString("_")) }.orEmpty()
            into += PlaceholderInfo(here.joinToString("."), node.description, entry.owner, node.valueType.simpleName, node.format, published, node.visibility)
        }
        node.children.forEach { collect(entry, it, here, into) }
    }

    private fun find(node: PhNode, words: List<String>, index: Int, path: List<PhNode>): Match? {
        if (index == words.size) return if (node.value != null) Match(node, path) else null
        val word = words[index]
        for (child in node.children) {
            val part = child.part
            if (part is PhPart.Word && part.name.equals(word, ignoreCase = true)) {
                find(child, words, index + 1, path + child)?.let { return it }
            }
        }
        for (child in node.children) {
            val part = child.part
            if (part is PhPart.Slot) find(child, words, index + 1, path + child)?.let { return it }
        }
        return null
    }

    private fun routeOf(match: Match, address: ParsedAddress): RouteInfo {
        // The field is the last word; arguments after it (`stats.kills.Alex`) belong to it.
        val lastWord = match.path.indexOfLast { it.part is PhPart.Word }
        val parts = match.path.mapIndexed { index, node ->
            val text = address.words[index]
            when (val part = node.part) {
                is PhPart.Slot -> RoutePart(RouteKind.ARGUMENT, text, part.name)
                is PhPart.Word -> when (index) {
                    0 -> RoutePart(RouteKind.GROUP, text, part.name)
                    lastWord -> RoutePart(RouteKind.FIELD, text, part.name)
                    else -> RoutePart(RouteKind.BRANCH, text, part.name)
                }
            }
        } + address.formats.map { RoutePart(RouteKind.FORMAT, it.name, it.name, it.arguments) }
        return RouteInfo(parts)
    }

    /** Reads the arguments of the path one after another, left to right; each converter sees the ones before it. */
    private fun readSlots(match: Match, address: ParsedAddress, route: RouteInfo, viewer: ViewerInfo): CompletionStage<Map<String, ParsedArgument>> {
        var chain: CompletionStage<Map<String, ParsedArgument>> = CompletableFuture.completedFuture(emptyMap())
        var position = 0
        match.path.forEachIndexed { index, node ->
            val part = node.part as? PhPart.Slot ?: return@forEachIndexed
            val text = address.words[index]
            val here = position++
            chain = chain.thenCompose { done ->
                val input = ArgumentInput(text, viewer, route, part.name, here, done.values.map { it.argument })
                part.type.convert(input).thenApply { done + (part.name to it) }
            }
        }
        return chain
    }

    private fun evaluate(match: Match, address: ParsedAddress, viewer: ViewerInfo, original: String, consumer: String?): CompletionStage<Any?> {
        val route = routeOf(match, address)
        return readSlots(match, address, route, viewer).thenCompose { slots ->
            val stage = try {
                checkNotNull(match.node.value)(Call(viewer, slots, route))
            } catch (failure: Throwable) {
                report("Placeholder $original failed: ${failure.message ?: failure.javaClass.simpleName}")
                return@thenCompose CompletableFuture.completedFuture<Any?>(null)
            }
            stage.handle { value, failure ->
                if (failure != null) {
                    report("Placeholder $original failed: ${failure.cause?.message ?: failure.message ?: failure.javaClass.simpleName}")
                    null
                } else if (value is Formatted) {
                    applyFormats(value.value, match.node, address.formats, viewer, original, value.format, consumer)
                } else {
                    applyFormats(value, match.node, address.formats, viewer, original, null, consumer)
                }
            }
        }
    }

    private fun applyFormats(
        value: Any?,
        node: PhNode?,
        written: List<FormatCall>,
        viewer: ViewerInfo,
        original: String,
        own: String?,
        consumer: String?,
    ): Any? {
        val explicit = written.filter { it.name != "raw" }
        val chain = when {
            explicit.isNotEmpty() -> explicit
            written.isNotEmpty() -> emptyList()
            own != null -> listOf(FormatCall(own, emptyList()))
            node?.format != null -> listOf(FormatCall(checkNotNull(node.format), emptyList()))
            else -> emptyList()
        }
        if (value == null) return chain.firstOrNull { it.name == "default" }?.arguments?.joinToString(",")
        var current: Any = value
        for (call in chain) {
            val format = formats.find(call.name, current, consumer, call.owner)
            if (format == null) {
                report("Placeholder $original: no format '${call.name}' fits ${current.javaClass.simpleName}")
                continue
            }
            current = try {
                format.apply(current, call.arguments, viewer) ?: return null
            } catch (failure: Exception) {
                report("Placeholder $original: format '${call.name}' failed: ${failure.message ?: failure.javaClass.simpleName}")
                return null
            }
        }
        return current
    }

    // --- registering ----------------------------------------------------------------------------------------

    private fun addresses(node: PhNode, parent: String = ""): Map<String, Visibility> {
        val here = parent + (if (parent.isEmpty()) "" else ".") + when (val part = node.part) {
            is PhPart.Word -> part.name
            is PhPart.Slot -> "{}"
        }
        val found = LinkedHashMap<String, Visibility>()
        if (node.value != null) found[here] = node.visibility
        node.children.forEach { found += addresses(it, here) }
        return found
    }

    private fun add(entry: Entry) {
        val name = (entry.root.part as PhPart.Word).name
        val mine = addresses(entry.root)
        groups[name].orEmpty().filter { it.owner != entry.owner }.forEach { other ->
            val theirs = addresses(other.root)
            val same = mine.keys.filter { address ->
                val there = theirs[address] ?: return@filter false
                nearness(other.owner, there, entry.owner) != null || nearness(entry.owner, mine.getValue(address), other.owner) != null
            }
            if (same.isNotEmpty()) {
                val more = if (same.size > 1) " and ${same.size - 1} more" else ""
                report("${entry.owner} and ${other.owner} both have {${same.first()}}$more; the nearer one answers")
            }
        }
        groups.computeIfAbsent(name) { CopyOnWriteArrayList() }.add(entry)
        reconcile()
    }

    private fun remove(entry: Entry) {
        val name = (entry.root.part as PhPart.Word).name
        groups[name]?.remove(entry)
        groups.entries.removeIf { it.value.isEmpty() }
        reconcile()
    }

    private fun removeOwner(owner: String) {
        groups.values.forEach { list -> list.removeIf { it.owner == owner } }
        groups.entries.removeIf { it.value.isEmpty() }
        formats.removeOwner(owner)
        converters.values.forEach { list -> list.removeIf { it.owner == owner } }
        reconcile()
    }

    private fun converterFor(type: Class<*>, consumer: String?): ArgumentConverter<Any>? = converters[type].orEmpty()
        .mapNotNull { entry -> nearness(entry.owner, entry.visibility, consumer)?.let { it to entry.converter } }
        .minByOrNull { it.first }?.second

    private fun checkPrefix(papi: String?) {
        require(papi == null || papi.matches(PREFIX)) {
            "PlaceholderAPI prefix '$papi' must be 2-32 characters of a-z, 0-9 and '-' (no '_': PlaceholderAPI splits on it)"
        }
    }

    private inner class ModuleScope(private val owner: String, private val papiEnabled: Boolean) : PlaceholderService {
        private fun scanner() = Scanner { type -> converterFor(type, owner) }

        override fun register(vararg holders: Any, papi: String?) {
            checkPrefix(papi)
            scanner().scan(holders.toList()).forEach { add(Entry(owner, it, papi.takeIf { papiEnabled })) }
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
            val entry = Entry(owner, nodes.first(), papi.takeIf { papiEnabled })
            add(entry)
            return AutoCloseable { remove(entry) }
        }

        override fun formats(vararg holders: Any) {
            AnnotatedFormats.register(this@PlaceholderEngine.formats, owner, holders.toList()) { type -> converterFor(type, owner) }
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> converter(type: Class<T>, converter: ArgumentConverter<T>): AutoCloseable {
            val entry = ConverterEntry(owner, Visibility.ALL, converter as ArgumentConverter<Any>)
            val list = converters.computeIfAbsent(type) { CopyOnWriteArrayList() }
            list.firstOrNull { it.owner != owner }
                ?.let { report("$owner and ${it.owner} both read ${type.simpleName}; the nearer one is used") }
            list.add(entry)
            return AutoCloseable { list.remove(entry) }
        }

        override fun resolve(address: String, viewer: ViewerInfo, values: Map<String, Any?>) =
            resolveIn(address, viewer, values, null, owner)

        override fun render(text: String, viewer: ViewerInfo, values: Map<String, Any?>) = renderAs(text, viewer, values, owner)

        override fun contains(address: String) = containsFor(address, owner)

        override fun list() = this@PlaceholderEngine.list()

        override fun adapters(): PlaceholderAdapterRegistry = this@PlaceholderEngine

        override fun close() = removeOwner(owner)
    }

    private companion object {
        val PREFIX = Regex("[a-z0-9-]{2,32}")
        const val MAXIMUM_REPORTED = 500
    }
}
