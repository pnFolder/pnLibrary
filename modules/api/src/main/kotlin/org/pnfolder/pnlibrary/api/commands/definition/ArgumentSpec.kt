package org.pnfolder.pnlibrary.api.commands.definition

/**
 * What a platform needs to know about an [ArgumentType] to validate and suggest it natively, such as the
 * Brigadier tree of a modern Paper server, before the portable parser runs.
 *
 * @property kind the shape of the accepted token
 * @property minimum lowest accepted number for numeric kinds, or `null` for no bound
 * @property maximum highest accepted number for numeric kinds, or `null` for no bound
 * @property choices the accepted values of a [Kind.CHOICE], also offered as suggestions
 */
class ArgumentSpec private constructor(
    val kind: Kind,
    val minimum: Double?,
    val maximum: Double?,
    choices: List<String>,
) {
    /** The shape of a token. */
    enum class Kind { TEXT, INTEGER, LONG, DECIMAL, BOOLEAN, CHOICE, GREEDY }

    val choices: List<String> = choices.toList()

    /** Specs of the built-in argument shapes. */
    companion object {
        /** Any single word. */
        @JvmField
        val TEXT: ArgumentSpec = ArgumentSpec(Kind.TEXT, null, null, emptyList())

        /** Every remaining word of the line, joined by single spaces. */
        @JvmField
        val GREEDY: ArgumentSpec = ArgumentSpec(Kind.GREEDY, null, null, emptyList())

        /** A true/false token. */
        @JvmField
        val BOOLEAN: ArgumentSpec = ArgumentSpec(Kind.BOOLEAN, null, null, emptyList())

        /** A whole number between [minimum] and [maximum]. */
        @JvmStatic
        @JvmOverloads
        fun integer(minimum: Int = Int.MIN_VALUE, maximum: Int = Int.MAX_VALUE): ArgumentSpec =
            ArgumentSpec(Kind.INTEGER, minimum.toDouble(), maximum.toDouble(), emptyList())

        /** A 64-bit whole number between [minimum] and [maximum]. */
        @JvmStatic
        @JvmOverloads
        fun long(minimum: Long = Long.MIN_VALUE, maximum: Long = Long.MAX_VALUE): ArgumentSpec =
            ArgumentSpec(Kind.LONG, minimum.toDouble(), maximum.toDouble(), emptyList())

        /** A decimal number between [minimum] and [maximum]. */
        @JvmStatic
        @JvmOverloads
        fun decimal(minimum: Double = -Double.MAX_VALUE, maximum: Double = Double.MAX_VALUE): ArgumentSpec =
            ArgumentSpec(Kind.DECIMAL, minimum, maximum, emptyList())

        /** One of [choices]. */
        @JvmStatic
        fun choice(choices: List<String>): ArgumentSpec = ArgumentSpec(Kind.CHOICE, null, null, choices)
    }
}
