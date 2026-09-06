package ru.privatenull.pnlibrary.api

/**
 * Parsed `/pndebug` command invocation.
 *
 * @property target  Plugin name or `"all"`.
 * @property configs Whether configuration files should be collected.
 * @property logs    Whether recent warning/error logs should be collected.
 * @property local   Skip upload; save to local file only.
 */
data class DebugRequest(
    val target: String,
    val configs: Boolean,
    val logs: Boolean,
    val local: Boolean,
) {
    companion object {
        private val VALID_TARGET = Regex("[A-Za-z0-9_.-]{1,80}")

        /**
         * Parses command arguments into a [DebugRequest].
         *
         * @param args     Raw argument array from the command handler.
         * @param prefixed If `true`, the first argument must be `"debug"` and is consumed.
         * @throws IllegalArgumentException on unrecognised arguments or duplicate flags.
         */
        @JvmStatic
        @JvmOverloads
        fun parse(args: Array<String>, prefixed: Boolean = false): DebugRequest {
            var offset = 0
            if (prefixed) {
                require(args.isNotEmpty() && args[0].equals("debug", ignoreCase = true)) { "usage" }
                offset = 1
            }
            var target = "all"
            var targetChosen = false
            val flags = mutableSetOf<String>()
            val knownFlags = setOf("--config", "--logs", "--local", "--full")
            for (i in offset until args.size) {
                val v = args[i]
                if (v.startsWith("--")) {
                    val f = v.lowercase()
                    require(f in knownFlags && flags.add(f)) { "usage" }
                } else {
                    require(!targetChosen && VALID_TARGET.matches(v)) { "usage" }
                    target = v
                    targetChosen = true
                }
            }
            return DebugRequest(
                target  = target,
                configs = "--config" in flags || "--full" in flags,
                logs    = "--logs"   in flags || "--full" in flags,
                local   = "--local"  in flags,
            )
        }
    }
}
