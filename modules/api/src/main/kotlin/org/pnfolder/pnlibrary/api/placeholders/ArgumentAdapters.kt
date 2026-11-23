package org.pnfolder.pnlibrary.api.placeholders

import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * How text from an address becomes a value of [T].
 *
 * @property label short word for messages, such as `int` or `player`
 * @property parse the conversion; `null` when the text does not fit
 * @property choices allowed words when the type has a fixed list
 * @property me what `me` means for the viewer, or `null` when it means nothing
 */
class ArgumentAdapter<T : Any>(
    override val label: String,
    val choices: List<String> = emptyList(),
    val me: ((ViewerInfo) -> T?)? = null,
    val parse: (String) -> T?,
) : ArgumentConverter<T> {
    override fun read(input: ArgumentInput): T? = parse(input.text)

    override fun suggest(input: ArgumentInput): List<String> = choices.filter { it.startsWith(input.text, ignoreCase = true) }

    override fun meOf(viewer: ViewerInfo): T? = me?.let { provider -> provider(viewer) }
}

/**
 * The converters of argument types for the whole server: the built-in ones and the types of the library. A module
 * should use `placeholders.converter(...)` instead: that one belongs to the module and goes away with it.
 */
object ArgumentAdapters {
    private val adapters = ConcurrentHashMap<Class<*>, ArgumentConverter<*>>()

    init {
        register(Int::class.javaObjectType, ArgumentAdapter("int") { it.toIntOrNull() })
        register(Long::class.javaObjectType, ArgumentAdapter("long") { it.toLongOrNull() })
        register(Double::class.javaObjectType, ArgumentAdapter("number") { it.toDoubleOrNull()?.takeIf(Double::isFinite) })
        register(String::class.java, ArgumentAdapter("text") { it })
        register(Boolean::class.javaObjectType, ArgumentAdapter("yes/no") { text ->
            when (text.lowercase()) {
                "true", "yes", "1", "on", "да", "вкл" -> true
                "false", "no", "0", "off", "нет", "выкл" -> false
                else -> null
            }
        })
        register(UUID::class.java, ArgumentAdapter("uuid") { runCatching { UUID.fromString(it) }.getOrNull() })
        register(Duration::class.java, ArgumentAdapter("duration, like 90s, 15m, 1h30m or 2d", parse = ::parseDuration))
        register(
            PlayerRef::class.java,
            ArgumentAdapter(
                label = "player",
                me = { viewer -> viewer.playerId?.let { PlayerRef(it.toString()) } },
                parse = { text -> text.takeIf { it.matches(PLAYER_NAME) }?.let(::PlayerRef) },
            ),
        )
    }

    /** Adds or replaces the converter of [type]: the one used for it when the parameter names none. */
    @JvmStatic
    fun <T : Any> register(type: Class<T>, converter: ArgumentConverter<T>) {
        adapters[type] = converter
    }

    /** Kotlin form of [register]: `ArgumentAdapters.register<Clan>("clan") { name -> clans.byName(name) }`. */
    inline fun <reified T : Any> register(
        label: String,
        choices: List<String> = emptyList(),
        noinline me: ((ViewerInfo) -> T?)? = null,
        noinline parse: (String) -> T?,
    ) = register(T::class.java, ArgumentAdapter(label, choices, me, parse))

    /** Removes the adapter of [type]. Built-in types come back as they were only after a restart. */
    @JvmStatic
    fun unregister(type: Class<*>) {
        adapters.remove(type)
    }

    /** The converter of [type], enums included, or `null` when the type is unknown. */
    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    fun <T : Any> find(type: Class<T>): ArgumentConverter<T>? {
        adapters[type]?.let { return it as ArgumentConverter<T> }
        if (type.isEnum) return enumAdapter(type)
        return null
    }

    /** The types that have an adapter, for the administrator's overview. */
    @JvmStatic
    fun types(): List<String> = adapters.keys.map { it.simpleName }.sorted()

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> enumAdapter(type: Class<T>): ArgumentAdapter<T> {
        val constants = type.enumConstants.map { it as Enum<*> }
        return ArgumentAdapter(
            label = "one of the words",
            choices = constants.map { it.name.lowercase() },
        ) { text -> constants.firstOrNull { it.name.equals(text, ignoreCase = true) } as T? }
    }

    private val PLAYER_NAME = Regex("[A-Za-z0-9_.-]{1,36}")
    private val DURATION = Regex("(?:(\\d+)d)?(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?", RegexOption.IGNORE_CASE)

    private fun parseDuration(text: String): Duration? {
        val match = DURATION.matchEntire(text) ?: return null
        val parts = match.groupValues.drop(1)
        if (parts.all { it.isEmpty() }) return null
        val (days, hours, minutes, seconds) = parts.map { it.toLongOrNull() ?: 0L }
        return Duration.ofDays(days).plusHours(hours).plusMinutes(minutes).plusSeconds(seconds)
    }
}
