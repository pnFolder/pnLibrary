package org.pnfolder.pnlibrary.api.diagnostics

import java.util.Locale

/**
 * Parsed `/pn debug` command invocation.
 *
 * @property target  Plugin name or `"all"`.
 * @property configs Whether configuration files should be collected; always `true` for a command.
 * @property logs    Whether recent logs and the observation journal should be collected; always `true` for a command.
 * @property local   Skip upload; save to local file only.
 */
data class DebugRequest(
    val target: String,
    val configs: Boolean,
    val logs: Boolean,
    val local: Boolean,
) {
    /** Parser for validated command-line diagnostic requests. */
    companion object {
        private val VALID_TARGET = Regex("[A-Za-z0-9_.-]{1,80}")

        /**
         * Parses command arguments into a [DebugRequest]. The report always carries logs and configurations; the only
         * flag is `--local`, which saves the file instead of uploading it.
         *
         * @param args     Raw argument array from the command handler.
         * @param prefixed If `true`, the first argument must be `"debug"` and is consumed.
         * @throws IllegalArgumentException on unrecognised arguments or duplicate flags.
         */
        @JvmStatic
        @JvmOverloads
        fun parse(args: Array<out String>, prefixed: Boolean = false): DebugRequest {
            var offset = 0
            if (prefixed) {
                require(args.isNotEmpty() && args[0].equals("debug", ignoreCase = true)) { "Invalid debug command usage" }
                offset = 1
            }
            var target = "all"
            var targetChosen = false
            val flags = mutableSetOf<String>()
            val knownFlags = setOf("--local")
            for (i in offset until args.size) {
                val v = args[i]
                if (v.startsWith("--")) {
                    val f = v.lowercase(Locale.ROOT)
                    require(f in knownFlags && flags.add(f)) { "Invalid debug command usage" }
                } else {
                    require(!targetChosen && VALID_TARGET.matches(v)) { "Invalid debug command usage" }
                    target = v
                    targetChosen = true
                }
            }
            return DebugRequest(
                target  = target,
                configs = true,
                logs    = true,
                local   = "--local" in flags,
            )
        }
    }
}
