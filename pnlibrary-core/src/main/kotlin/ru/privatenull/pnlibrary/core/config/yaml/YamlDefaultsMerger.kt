package ru.privatenull.pnlibrary.core.config.yaml

/** Рекурсивное слияние YAML с сохранением комментариев для [CodeFirstYaml]. */
internal object YamlDefaultsMerger {
    data class Result(val content: String, val addedPaths: List<String>) {
        val changed: Boolean get() = addedPaths.isNotEmpty()
    }

    fun merge(existing: String, defaults: String): Result {
        val target = existing.lines().dropLastWhile(String::isEmpty).toMutableList()
        val source = defaults.lines().dropLastWhile(String::isEmpty)
        val added = mutableListOf<String>()
        collect(source, 0, source.size, 0, emptyList()).forEach { candidate ->
            if (find(target, candidate.path) != null) return@forEach
            val parent = candidate.path.dropLast(1)
            val insertion = if (parent.isEmpty()) target.size else find(target, parent)?.end ?: return@forEach
            val block = source.subList(candidate.start, candidate.end)
            if (insertion == target.size && target.lastOrNull()?.isNotBlank() == true) target.add("")
            val index = if (insertion == target.size - 1 && target.lastOrNull()?.isEmpty() == true) target.size else insertion
            target.addAll(index, block)
            added += candidate.path.joinToString(".")
        }
        return Result(target.joinToString("\n").trimEnd() + "\n", added)
    }

    fun paths(yaml: String): List<String> = collect(yaml.lines(), 0, yaml.lines().size, 0, emptyList())
        .map { it.path.joinToString(".") }

    private fun find(lines: List<String>, path: List<String>): Block? {
        var start = 0; var end = lines.size; var indent = 0; var found: Block? = null
        path.forEach { key ->
            found = blocks(lines, start, end, indent).firstOrNull { it.key == key } ?: return null
            start = found!!.keyLine + 1; end = found!!.end; indent += 2
        }
        return found
    }

    private fun collect(lines: List<String>, start: Int, end: Int, indent: Int, parent: List<String>): List<Block> {
        val direct = blocks(lines, start, end, indent)
        return direct.flatMap { block ->
            val pathBlock = block.copy(path = parent + block.key)
            listOf(pathBlock) + collect(lines, block.keyLine + 1, block.end, indent + 2, pathBlock.path)
        }
    }

    private fun blocks(lines: List<String>, start: Int, end: Int, indent: Int): List<Block> {
        val keys = (start until end).mapNotNull { index -> key(lines[index], indent)?.let { index to it } }
        return keys.mapIndexed { position, (keyLine, key) ->
            val decoratedStart = decoratedStart(lines, keyLine, start)
            val next = keys.getOrNull(position + 1)?.first?.let { decoratedStart(lines, it, keyLine + 1) } ?: end
            Block(key, decoratedStart, keyLine, next, emptyList())
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
        if (line.takeWhile { it == ' ' }.length != indent) return null
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith('#') || trimmed.startsWith('-')) return null
        val colon = trimmed.indexOf(':')
        if (colon <= 0) return null
        return trimmed.substring(0, colon).trim().trim('"', '\'').takeIf { it.isNotEmpty() }
    }

    private data class Block(
        val key: String,
        val start: Int,
        val keyLine: Int,
        val end: Int,
        val path: List<String>,
    )
}
