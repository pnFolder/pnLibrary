package org.pnfolder.pnlibrary.api.actions

/** Finds the closest valid spelling for a mistyped key, tag or type so errors can say what was meant. */
internal object Suggestions {
    /** Returns the option most like [word], or `null` when nothing is close enough to be a typo. */
    fun closest(word: String, options: Collection<String>): String? {
        val wanted = simplify(word)
        var best: String? = null
        var bestDistance = Int.MAX_VALUE
        for (option in options) {
            val distance = distance(wanted, simplify(option))
            if (distance < bestDistance) {
                best = option
                bestDistance = distance
            }
        }
        return best?.takeIf { bestDistance <= maxOf(2, wanted.length / 3) }
    }

    /** Returns ` Did you mean 'x'?` for the closest option, or an empty string. */
    fun hint(word: String, options: Collection<String>): String =
        closest(word, options)?.let { " Did you mean '$it'?" }.orEmpty()

    private fun simplify(text: String): String = text.lowercase().filter { it != '_' && it != '-' && it != ' ' }

    private fun distance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, substitution)
            }
            previous = current
        }
        return previous[b.length]
    }
}
