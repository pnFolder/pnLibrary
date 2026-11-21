package org.pnfolder.pnlibrary.config.yaml

import org.pnfolder.pnlibrary.api.config.CommentPolicy
import org.pnfolder.pnlibrary.api.config.MissingValuePolicy

/**
 * Adds keys and comments of the code defaults to an administrator's YAML line by line, so that every value,
 * comment and ordering the administrator wrote stays as it was. A YAML parser would lose the comments.
 *
 * Nested keys are found by the indentation the file actually uses: an administrator who re-indented a
 * section with four spaces must not get a second copy of every key in it.
 */
internal object YamlDefaultsMerger {
    /**
     * Outcome of [merge].
     *
     * @property content the merged YAML text
     * @property addedPaths dotted paths of the keys inserted from the defaults
     * @property addedComments dotted paths of the keys that received a default comment
     */
    data class Result(val content: String, val addedPaths: List<String>, val addedComments: List<String>) {
        val changed: Boolean get() = addedPaths.isNotEmpty() || addedComments.isNotEmpty()
    }

    /** Adds to [existing] the keys of [defaults] allowed by [values] and the comments allowed by [comments]. */
    fun merge(
        existing: String,
        defaults: String,
        values: MissingValuePolicy = MissingValuePolicy.ADD,
        comments: CommentPolicy = CommentPolicy.PRESERVE,
    ): Result {
        val target = existing.lines().dropLastWhile(String::isEmpty).toMutableList()
        val source = defaults.lines().dropLastWhile(String::isEmpty)
        val candidates = collect(source, 0, source.size, 0, emptyList())
        val added = if (values == MissingValuePolicy.ADD) addMissing(target, source, candidates) else emptyList()
        val commented = if (comments == CommentPolicy.ADD_MISSING) {
            addComments(target, source, candidates.filter { it.dotted !in added })
        } else {
            emptyList()
        }
        return Result(target.joinToString("\n").trimEnd() + "\n", added, commented)
    }

    /** Dotted paths of every key in [yaml], parents before their children. */
    fun paths(yaml: String): List<String> {
        val lines = yaml.lines()
        return collect(lines, 0, lines.size, 0, emptyList()).map { it.dotted }
    }

    private fun addMissing(target: MutableList<String>, source: List<String>, candidates: List<Block>): List<String> {
        val added = mutableListOf<String>()
        for (candidate in candidates) {
            if (find(target, candidate.path) != null) continue
            val place = insertionPlace(target, candidate.path.dropLast(1)) ?: continue
            if (place.line == target.size && target.lastOrNull()?.isNotBlank() == true) target.add("")
            val index = if (place.line == target.size - 1 && target.lastOrNull()?.isEmpty() == true) target.size else place.line
            target.addAll(index, reindent(source.subList(candidate.start, candidate.end), candidate.indent, place.indent))
            added += candidate.dotted
        }
        return added
    }

    private fun addComments(target: MutableList<String>, source: List<String>, candidates: List<Block>): List<String> {
        val commented = mutableListOf<String>()
        for (candidate in candidates) {
            val sourceComments = source.subList(candidate.start, candidate.keyLine).filter(::isComment)
            if (sourceComments.isEmpty()) continue
            val existing = find(target, candidate.path) ?: continue
            if (target.subList(existing.start, existing.keyLine).any(::isComment)) continue
            target.addAll(existing.keyLine, reindent(sourceComments, candidate.indent, existing.indent))
            commented += candidate.dotted
        }
        return commented
    }

    /** Where a new child of [parent] goes: the end of the parent's block, at the indentation its children use. */
    private fun insertionPlace(lines: List<String>, parent: List<String>): Place? {
        if (parent.isEmpty()) return Place(lines.size, 0)
        val block = find(lines, parent) ?: return null
        return Place(block.end, childIndent(lines, block) ?: (block.indent + 2))
    }

    private fun find(lines: List<String>, path: List<String>): Block? {
        var found: Block? = null
        for (key in path) {
            val parent = found
            found = if (parent == null) {
                blocks(lines, 0, lines.size, 0).firstOrNull { it.key == key }
            } else {
                val indent = childIndent(lines, parent) ?: return null
                blocks(lines, parent.keyLine + 1, parent.end, indent).firstOrNull { it.key == key }
            } ?: return null
        }
        return found
    }

    private fun collect(lines: List<String>, start: Int, end: Int, indent: Int, parent: List<String>): List<Block> =
        blocks(lines, start, end, indent).flatMap { block ->
            val located = block.copy(path = parent + block.key)
            val nested = childIndent(lines, block)
                ?.let { collect(lines, block.keyLine + 1, block.end, it, located.path) }
                .orEmpty()
            listOf(located) + nested
        }

    private fun childIndent(lines: List<String>, parent: Block): Int? =
        (parent.keyLine + 1 until parent.end)
            .map { lines[it] }
            .firstOrNull { it.isNotBlank() && !isComment(it) }
            ?.let(::indentOf)
            ?.takeIf { it > parent.indent }

    private fun blocks(lines: List<String>, start: Int, end: Int, indent: Int): List<Block> {
        val keys = (start until end).mapNotNull { index -> key(lines[index], indent)?.let { index to it } }
        return keys.mapIndexed { position, (keyLine, key) ->
            val decoratedStart = decoratedStart(lines, keyLine, start)
            val next = keys.getOrNull(position + 1)?.first?.let { decoratedStart(lines, it, keyLine + 1) } ?: end
            Block(key, indent, decoratedStart, keyLine, next, emptyList())
        }
    }

    private fun decoratedStart(lines: List<String>, keyLine: Int, lowerBound: Int): Int {
        var result = keyLine
        while (result > lowerBound) {
            val previous = lines[result - 1].trim()
            if (previous.isEmpty() || previous.startsWith('#')) result-- else break
        }
        return result
    }

    private fun key(line: String, indent: Int): String? {
        if (indentOf(line) != indent) return null
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith('#') || trimmed.startsWith('-')) return null
        val colon = trimmed.indexOf(':')
        if (colon <= 0) return null
        return trimmed.substring(0, colon).trim().trim('"', '\'').takeIf { it.isNotEmpty() }
    }

    private fun reindent(lines: List<String>, from: Int, to: Int): List<String> {
        if (from == to) return lines
        return lines.map { line -> if (line.isBlank()) line else " ".repeat(to) + line.drop(minOf(from, indentOf(line))) }
    }

    private fun indentOf(line: String) = line.takeWhile { it == ' ' }.length

    private fun isComment(line: String) = line.trimStart().startsWith('#')

    private class Place(val line: Int, val indent: Int)

    private data class Block(
        val key: String,
        val indent: Int,
        val start: Int,
        val keyLine: Int,
        val end: Int,
        val path: List<String>,
    ) {
        val dotted: String get() = path.joinToString(".")
    }
}
