package org.pnfolder.pnlibrary.core

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Keeps the top-level packages of `core` layered: a package may depend on others, but no chain of imports
 * may lead back to where it started. Without this, shared helpers drift into a hub package and every
 * feature ends up importing the hub that wires it.
 */
class CorePackageCyclesTest {
    private val root: Path = Paths.get("src/main/kotlin/org/pnfolder/pnlibrary/core")
    private val importPattern = Regex("""^import org\.pnfolder\.pnlibrary\.core\.(\w+)\.""")

    private fun graph(): Map<String, Set<String>> {
        val edges = mutableMapOf<String, MutableSet<String>>()
        Files.walk(root).use { stream ->
            stream.filter { it.toString().endsWith(".kt") }.forEach { file ->
                val relative = root.relativize(file)
                val owner = if (relative.nameCount > 1) relative.getName(0).toString() else return@forEach
                Files.readAllLines(file).forEach { line ->
                    val target = importPattern.find(line)?.groupValues?.get(1)
                    if (target != null && target != owner) edges.getOrPut(owner) { mutableSetOf() } += target
                }
            }
        }
        return edges
    }

    private fun findCycle(graph: Map<String, Set<String>>): List<String>? {
        fun visit(start: String, node: String, path: List<String>, seen: Set<String>): List<String>? {
            for (next in graph[node].orEmpty().sorted()) {
                if (next == start) return path + start
                if (next !in seen) visit(start, next, path + next, seen + next)?.let { return it }
            }
            return null
        }
        return graph.keys.sorted().firstNotNullOfOrNull { visit(it, it, listOf(it), setOf(it)) }
    }

    @Test
    fun `core packages do not depend on each other in a cycle`() {
        assertTrue(Files.isDirectory(root), "Expected to run from the core module directory")
        val cycle = findCycle(graph())
        assertTrue(cycle == null, "Package cycle in core: ${cycle?.joinToString(" -> ")}")
    }
}
