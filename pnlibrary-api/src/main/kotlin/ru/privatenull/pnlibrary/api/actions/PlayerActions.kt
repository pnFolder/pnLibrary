package ru.privatenull.pnlibrary.api.actions

import java.util.UUID
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import net.kyori.adventure.text.Component
import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.util.function.Consumer

/** Supported platform-independent actions loaded directly from YAML. */
enum class PlayerActionType { MESSAGE, TITLE, ACTION_BAR, KICK, TELEPORT, SOUND, PLAYER_COMMAND }

/** One configuration-friendly action. Only fields relevant to [type] are used. */
class PlayerAction @JvmOverloads constructor(
    /** Handler key: a built-in name such as `message`, or a custom namespaced key. */
    var type: String = "message",
    var text: String? = null,
    var title: String? = null,
    var subtitle: String? = null,
    var fadeIn: Int = 10,
    var stay: Int = 70,
    var fadeOut: Int = 20,
    var world: String? = null,
    var x: Double = 0.0,
    var y: Double = 0.0,
    var z: Double = 0.0,
    var yaw: Float = 0f,
    var pitch: Float = 0f,
    var sound: String? = null,
    var volume: Float = 1f,
    var soundPitch: Float = 1f,
    var command: String? = null,
    /** Arbitrary configuration values consumed by custom handlers. */
    var arguments: MutableMap<String, Any?> = linkedMapOf(),
    /** Optional object passed to a handler by code; it is not required to come from YAML. */
    @field:ru.privatenull.pnlibrary.api.config.ConfigIgnore var payload: Any? = null,
) {
    fun text(value: String?) = apply { text = value }
    fun title(value: String?, subtitle: String? = null) = apply {
        title = value
        this.subtitle = subtitle
    }
    fun sound(value: String?, volume: Float = 1f, pitch: Float = 1f) = apply {
        sound = value
        this.volume = volume
        soundPitch = pitch
    }
    fun argument(name: String, value: Any?) = apply { arguments[name] = value }
    fun payload(value: Any?) = apply { payload = value }

    companion object {
        @JvmStatic fun of(type: String) = PlayerAction(type)
    }
}

/** Ordered action scenario represented in YAML as an object containing `actions`. */
class PlayerActionSequence @JvmOverloads constructor(
    var actions: MutableList<PlayerAction> = mutableListOf(),
) {
    fun add(action: PlayerAction) = apply { actions += action }

    companion object {
        @JvmStatic fun of(vararg actions: PlayerAction) = PlayerActionSequence(actions.toMutableList())
    }
}

/** Executes configured actions for a player identified without native platform classes. */
interface PlayerActionService {
    fun execute(playerId: UUID, sequence: PlayerActionSequence)
    fun execute(playerId: UUID, sequence: PlayerActionSequence, placeholders: Map<String, Any?>)
    fun executeAsync(playerId: UUID, sequence: PlayerActionSequence, placeholders: Map<String, Any?> = emptyMap()): CompletionStage<ActionSequenceResult>
    fun register(handler: String, actionHandler: PlayerActionHandler): PlayerActionRegistration
    fun register(handler: String, access: PlayerActionAccess, actionHandler: PlayerActionHandler): PlayerActionRegistration
    fun register(handler: String, access: Consumer<PlayerActionAccess.Builder>, actionHandler: PlayerActionHandler): PlayerActionRegistration =
        register(handler, PlayerActionAccess.builder().also(access::accept).build(), actionHandler)
}

/** Controls which registered pnLibrary plugins may execute one handler. */
class PlayerActionAccess private constructor(
    val allLibraryPlugins: Boolean,
    val allowedPlugins: Set<PluginId>,
    val allowedPatterns: Set<String>,
    val deniedPlugins: Set<PluginId>,
) {
    fun allows(owner: PluginId, consumer: PluginId): Boolean {
        if (consumer in deniedPlugins) return false
        if (owner == consumer) return true
        if (allLibraryPlugins || consumer in allowedPlugins) return true
        return allowedPatterns.any { pattern ->
            Regex("^" + pattern.split('*').joinToString(".*", transform = Regex::escape) + "$", RegexOption.IGNORE_CASE)
                .matches(consumer.value)
        }
    }
    companion object {
        @JvmStatic fun ownerOnly() = Builder().build()
        @JvmStatic fun shared() = Builder().allowAllLibraryPlugins().build()
        @JvmStatic fun builder() = Builder()
    }
    class Builder {
        private var all = false
        private val allowed = linkedSetOf<PluginId>()
        private val patterns = linkedSetOf<String>()
        private val denied = linkedSetOf<PluginId>()
        fun allowAllLibraryPlugins() = apply { all = true }
        fun allow(vararg ids: String) = apply { ids.map(PluginId::of).forEach(allowed::add) }
        fun allowMatching(vararg patterns: String) = apply { this.patterns += patterns }
        fun deny(vararg ids: String) = apply { ids.map(PluginId::of).forEach(denied::add) }
        fun build() = PlayerActionAccess(all, allowed.toSet(), patterns.toSet(), denied.toSet())
    }
}

data class PlayerActionContext(
    val owner: PluginId,
    val playerId: UUID,
    val handler: String,
    val action: PlayerAction,
    val message: Component?,
    val title: Component?,
    val subtitle: Component?,
    val arguments: Map<String, Any?>,
    val payload: Any?,
    /** Executes child actions for the same player and with the same placeholder values. */
    val flow: PlayerActionFlow,
) {
    fun require(name: String): Any = arguments[name] ?: error("Action $handler requires argument '$name'")
    fun requireString(name: String): String = require(name).toString()
    fun requireInt(name: String): Int = (require(name) as? Number)?.toInt()
        ?: require(name).toString().toIntOrNull() ?: error("Action argument '$name' must be an integer")
    fun requireDouble(name: String): Double = (require(name) as? Number)?.toDouble()
        ?: require(name).toString().toDoubleOrNull() ?: error("Action argument '$name' must be a number")
}

/** Allows one action handler to compose other actions without blocking a server thread. */
interface PlayerActionFlow {
    fun execute(action: PlayerAction): CompletionStage<PlayerActionResult>
    fun execute(sequence: PlayerActionSequence): CompletionStage<ActionSequenceResult>
    fun after(delay: Duration): DelayedPlayerActionFlow
}

/** A deferred action flow scheduled through the owning plugin context. */
interface DelayedPlayerActionFlow {
    fun execute(action: PlayerAction): CompletionStage<PlayerActionResult>
    fun execute(sequence: PlayerActionSequence): CompletionStage<ActionSequenceResult>
}

data class PlayerActionResult(val successful: Boolean, val message: String? = null) {
    companion object {
        @JvmStatic fun success() = PlayerActionResult(true)
        @JvmStatic fun skipped(message: String) = PlayerActionResult(false, message)
    }
}

data class ActionSequenceResult(val actions: List<PlayerActionResult>) {
    val successful: Boolean get() = actions.all(PlayerActionResult::successful)
}

fun interface PlayerActionHandler {
    fun execute(context: PlayerActionContext): CompletionStage<PlayerActionResult>

    companion object {
        @JvmStatic fun immediate(handler: java.util.function.Function<PlayerActionContext, PlayerActionResult>) =
            PlayerActionHandler { CompletableFuture.completedFuture(handler.apply(it)) }
    }
}

interface PlayerActionRegistration : AutoCloseable {
    val owner: PluginId
    val handler: String
    val access: PlayerActionAccess
    val isActive: Boolean
}
