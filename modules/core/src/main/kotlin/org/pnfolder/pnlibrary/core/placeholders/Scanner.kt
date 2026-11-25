package org.pnfolder.pnlibrary.core.placeholders

import org.pnfolder.pnlibrary.api.placeholders.annotation.Argument
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentConverter
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue
import org.pnfolder.pnlibrary.api.placeholders.annotation.Group
import org.pnfolder.pnlibrary.api.placeholders.annotation.Hide
import org.pnfolder.pnlibrary.api.placeholders.annotation.Invalid
import org.pnfolder.pnlibrary.api.placeholders.NoValueException
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder
import org.pnfolder.pnlibrary.api.placeholders.reply.PlaceholderAnswer
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply
import org.pnfolder.pnlibrary.api.placeholders.reply.ReplyKind
import org.pnfolder.pnlibrary.api.placeholders.annotation.Route
import org.pnfolder.pnlibrary.api.placeholders.route.RouteInfo
import org.pnfolder.pnlibrary.api.placeholders.annotation.UseFormat
import org.pnfolder.pnlibrary.api.placeholders.annotation.Viewer
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo
import org.pnfolder.pnlibrary.api.registry.Visibility
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Type
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

private typealias Receiver = (Call) -> Any?

/** Turns classes marked with the placeholder annotations into the placeholder tree of the library. */
internal class Scanner(private val lookup: (Class<*>) -> ArgumentConverter<Any>? = { null }) {

    fun scan(holders: List<Any>): List<PhNode> {
        val roots = mutableListOf<PhNode>()
        for (holder in holders) {
            val type = holder.javaClass
            val name = type.getAnnotation(Placeholders::class.java)?.value
                ?: error("${type.name} must be marked with @Placeholders(\"group\")")
            val root = PhNode(PhPart.Word(checkWord(name)), Any::class.java)
            val rules = listOfNotNull(classRule(type))
            members(type, root.children, { holder }, emptySet(), rules, 0, null, type.getAnnotation(Placeholders::class.java).visibility)
            roots += root
        }
        return roots
    }

    private class FormatRule(val name: String, val type: Class<*>?)

    private fun classRule(type: Class<*>): FormatRule? = type.getAnnotation(UseFormat::class.java)?.let {
        FormatRule(it.value, it.type.java.takeIf { found -> found != Any::class.java })
    }

    private fun members(
        owner: Class<*>,
        into: MutableList<PhNode>,
        receiver: Receiver,
        usedNames: Set<String>,
        rules: List<FormatRule>,
        depth: Int,
        inherited: Placeholder?,
        limit: Visibility,
    ) {
        check(depth <= MAX_DEPTH) { "${owner.name}: placeholder objects are nested deeper than $MAX_DEPTH levels" }
        val names = mutableSetOf<String>()
        for (member in Members.collect(owner)) {
            check(names.add(member.name)) { "${owner.name}: two placeholders are called '${member.name}'" }
            into += memberPhNode(owner, member, receiver, usedNames, rules, depth, inherited, limit)
        }
    }

    private fun memberPhNode(
        owner: Class<*>,
        member: Member,
        receiver: Receiver,
        usedNames: Set<String>,
        rules: List<FormatRule>,
        depth: Int,
        inherited: Placeholder?,
        limit: Visibility,
    ): PhNode {
        val where = "${owner.simpleName}.${member.name}"
        require(Members.returnsReply(member)) { "$where: a placeholder must return Reply<T> or CompletableFuture<Reply<T>>" }
        val used = usedNames.toMutableSet()
        val plans = mutableListOf<ParameterPlan>()
        val segments = mutableListOf<PhPart>(PhPart.Word(checkWord(member.name)))
        for ((index, parameter) in member.parameters.withIndex()) {
            val type = parameter.type
            when {
                type == ViewerInfo::class.java -> {
                    require(parameter.isAnnotationPresent(Viewer::class.java)) { "$where: a ViewerInfo parameter needs @Viewer" }
                    plans += ParameterPlan.ViewerParameter
                }
                type == RouteInfo::class.java -> {
                    require(parameter.isAnnotationPresent(Route::class.java)) { "$where: a RouteInfo parameter needs @Route" }
                    plans += ParameterPlan.RouteParameter
                }
                type == ArgumentValue::class.java -> {
                    require(parameter.isAnnotationPresent(Argument::class.java)) { "$where: an ArgumentValue parameter needs @Argument" }
                    val spec = specFor(argumentTypeOf(parameter.parameterizedType, where), parameter.getAnnotation(Argument::class.java), where, lookup)
                    val name = pathName(parameter.getAnnotation(Argument::class.java)?.name, parameter, used, index)
                    plans += ParameterPlan.One(name, spec)
                    segments += PhPart.Slot(name, spec.pathType)
                }
                parameter.isAnnotationPresent(Group::class.java) -> {
                    val constructor = type.constructors.singleOrNull() ?: error("$where: ${type.simpleName} needs one public constructor")
                    val parts = constructor.parameters.mapIndexed { i, part ->
                        require(part.type == ArgumentValue::class.java) { "$where: ${type.simpleName} may only have ArgumentValue parameters" }
                        require(part.isAnnotationPresent(Argument::class.java)) { "$where: ${type.simpleName} parameters need @Argument" }
                        val options = part.getAnnotation(Argument::class.java)
                        val spec = specFor(argumentTypeOf(part.parameterizedType, where), options, where, lookup)
                        val name = pathName(options?.name, part, used, i)
                        segments += PhPart.Slot(name, spec.pathType)
                        name to spec
                    }
                    plans += ParameterPlan.Many(constructor, parts)
                }
                else -> error("$where: parameter ${index + 1} must be a ViewerInfo, a RouteInfo, an ArgumentValue<T> or a class with @Group")
            }
        }

        val branchType = Members.branchTypeOf(member)
        val nested = if (branchType == null) emptyList() else Members.collect(branchType)
        val isLeaf = nested.isEmpty()

        // Every method returns a plain value, an object, a Reply, or a CompletionStage of those; this makes one thing of it.
        fun settle(result: Any?): Any? = when (result) {
            is Reply<*> -> when (result.kind) {
                ReplyKind.NONE -> null
                ReplyKind.NEXT -> result.content
                ReplyKind.VALUE ->
                    if (isLeaf) wrapFormat(result.content, result.format) else throw PlaceholderAnswer(wrapFormat(result.content, result.format))
                ReplyKind.STOP -> throw PlaceholderAnswer(wrapFormat(result.content, result.format))
            }
            else -> result
        }

        fun run(source: Any, call: Call): Any? {
            val result = invoke(member, source, plans.map { it.value(call) }.toTypedArray())
            @Suppress("UNCHECKED_CAST")
            return if (result is CompletionStage<*>) (result as CompletionStage<Any?>).thenApply(::settle) else settle(result)
        }

        val step: Receiver = { call ->
            val source = receiver(call)
            when (source) {
                null -> null
                // The step before answers later: continue when it has. Every step below it waits the same way.
                is CompletionStage<*> -> source.thenCompose { value ->
                    if (value == null) {
                        CompletableFuture.completedFuture<Any?>(null)
                    } else {
                        val result = run(value, call)
                        @Suppress("UNCHECKED_CAST")
                        if (result is CompletionStage<*>) result as CompletionStage<Any?> else CompletableFuture.completedFuture(result)
                    }
                }
                else -> run(source, call)
            }
        }

        val nodes = segments.mapIndexed { i, segment ->
            PhNode(segment, if (i == segments.lastIndex && isLeaf) Members.leafType(member) else Any::class.java)
        }
        nodes.zipWithNext().forEach { (parent, child) -> parent.children += child }
        val last = nodes.last()
        // A placeholder is never wider than the class or the branch it lies in.
        val reach = if (member.ph.visibility.ordinal < limit.ordinal) member.ph.visibility else limit
        if (isLeaf) {
            last.visibility = reach
            makeLeaf(last, member, step, rules, inherited)
        } else {
            val inner = rules + listOfNotNull(classRule(checkNotNull(branchType)))
            val covers = if (member.ph.onInvalid != Invalid.KEEP) member.ph else inherited
            members(checkNotNull(branchType), last.children, step, used, inner, depth + 1, covers, reach)
        }
        return nodes.first()
    }

    private fun makeLeaf(node: PhNode, member: Member, step: Receiver, rules: List<FormatRule>, inherited: Placeholder?) {
        val valueType = Members.leafType(member)
        val fallback = fallbackFor(if (member.ph.onInvalid == Invalid.KEEP && inherited != null) inherited else member.ph, valueType)
        node.value = node@{ call ->
            val result = try {
                step(call)
            } catch (_: NoValueException) {
                null
            } catch (answered: PlaceholderAnswer) {
                return@node CompletableFuture.completedFuture(answered.value)
            }
            if (result is CompletionStage<*>) {
                @Suppress("UNCHECKED_CAST")
                (result as CompletionStage<Any?>).handle { value, failure ->
                    val answered = failure as? PlaceholderAnswer ?: failure?.cause as? PlaceholderAnswer
                    if (answered != null) return@handle answered.value
                    if (failure != null && failure !is NoValueException && failure.cause !is NoValueException) throw failure
                    value ?: fallback
                }
            } else {
                CompletableFuture.completedFuture(result ?: fallback)
            }
        }
        node.format = formatFor(member, valueType, rules)
        node.description = member.ph.value
    }

    private fun wrapFormat(content: Any?, format: String?): Any? = if (content != null && format != null) Formatted(content, format) else content

    private fun fallbackFor(ph: Placeholder, type: Class<*>): Any? = when {
        ph.onInvalid == Invalid.DEFAULT -> ph.default
        ph.onInvalid == Invalid.EMPTY -> ""
        else -> null
    }

    private fun formatFor(member: Member, type: Class<*>, rules: List<FormatRule>): String? {
        member.format?.let { return it.value }
        return rules.lastOrNull { it.type == null || boxed(it.type).isAssignableFrom(type) }?.name
            ?: if (java.time.Duration::class.java.isAssignableFrom(type)) "left" else null // a Duration reads as 1h 35m, not PT1H35M
    }

    private fun pathName(wanted: String?, parameter: java.lang.reflect.Parameter, used: MutableSet<String>, index: Int): String {
        val raw = wanted?.takeIf { it.isNotEmpty() } ?: if (parameter.isNamePresent) parameter.name else "arg${used.size + index + 1}"
        var name = raw.lowercase().replace(Regex("[^a-z0-9_-]"), "").ifEmpty { "arg" }
        var counter = 2
        while (!used.add(name)) name = "${raw.lowercase().replace(Regex("[^a-z0-9_-]"), "").ifEmpty { "arg" }}${counter++}"
        return name
    }

    private fun invoke(member: Member, receiver: Any, args: Array<Any?>): Any? = try {
        member.read(receiver, args)
    } catch (failure: InvocationTargetException) {
        val cause = failure.targetException
        if (cause is NoValueException) null else throw cause
    }

    private companion object {
        const val MAX_DEPTH = 8
    }
}
