package org.pnfolder.pnlibrary.api.placeholders.tree


/** The argument types a placeholder branch can take. */
object ArgTypes {
    val int: ArgType<Int> = object : ArgType<Int>("int") {
        override fun parse(text: String) = text.toIntOrNull()
    }
    val number: ArgType<Double> = object : ArgType<Double>("number") {
        override fun parse(text: String) = text.toDoubleOrNull()?.takeIf { it.isFinite() }
    }
    val text: ArgType<String> = object : ArgType<String>("text") {
        override fun parse(text: String) = text.takeIf { it.isNotEmpty() }
    }
    val bool: ArgType<Boolean> = object : ArgType<Boolean>("bool") {
        override fun parse(text: String) = when (text.lowercase()) {
            "true", "yes", "1" -> true
            "false", "no", "0" -> false
            else -> null
        }
    }
    val player: ArgType<PlayerArg> = object : ArgType<PlayerArg>("player") {
        override fun parse(text: String) = text.takeIf { it.matches(PLAYER_NAME) }?.let(::PlayerArg)
    }

    /** An integer inside [range]. */
    fun int(range: IntRange): ArgType<Int> = object : ArgType<Int>("int ${range.first}..${range.last}") {
        override fun parse(text: String) = text.toIntOrNull()?.takeIf { it in range }
    }

    /** One of [values], compared without regard to case; the stored spelling is returned. */
    fun choice(vararg values: String): ArgType<String> = object : ArgType<String>("choice") {
        override val choices = values.toList()
        override fun parse(text: String) = values.firstOrNull { it.equals(text, ignoreCase = true) }
    }

    private val PLAYER_NAME = Regex("[A-Za-z0-9_.-]{1,36}")
}
