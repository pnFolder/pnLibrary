package org.pnfolder.pnlibrary.api.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** Like Paper: a file holds one type and is named after it, so the project tree reads as a list of types. */
class OneTypePerFileTest {
    private val api: Path = generateSequence(Paths.get("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("settings.gradle.kts")) }
        .resolve("modules/api/src/main/kotlin/org/pnfolder/pnlibrary/api")

    private val declaration = Regex(
        "^(?:@\\w[^\\n]*\\n)*(?:(?:public|internal|private|sealed|data|enum|annotation|fun|open|abstract|value|inline)\\s+)*" +
            "(?:interface|class|object|typealias)\\s+(\\w+)",
        RegexOption.MULTILINE,
    )

    /** Internal helpers that live beside the one type that uses them. */
    private val helpers = setOf("ExpressionParser.kt")

    @Test
    fun `every file has at most one top-level type and carries its name`() {
        val offenders = Files.walk(api).use { paths ->
            paths.filter { it.toString().endsWith(".kt") && it.fileName.toString() !in helpers }.toList()
        }.mapNotNull { file ->
            val types = declaration.findAll(Files.readString(file)).map { it.groupValues[1] }.toList()
            val name = file.fileName.toString().removeSuffix(".kt")
            if (types.size > 1 || (types.size == 1 && types[0] != name)) "${api.relativize(file)}: $types" else null
        }
        assertTrue(offenders.isEmpty(), "Split these files, one type each, named after it:\n${offenders.joinToString("\n")}")
    }
}
