package ru.privatenull.pnlibrary.core.config.yaml

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class YamlSchemaDecoratorTest {
    @Test
    fun `decorates nested fields at their matching indentation`() {
        val schema = mapOf(
            "server" to YamlFieldMetadata(
                comments = listOf("Server settings."),
                separateWithBlankLine = false,
                children = mapOf(
                    "port" to YamlFieldMetadata(
                        comments = listOf("Listening port."),
                        separateWithBlankLine = false,
                        children = emptyMap(),
                    ),
                ),
            ),
            "port" to YamlFieldMetadata(
                comments = listOf("Unrelated root port."),
                separateWithBlankLine = true,
                children = emptyMap(),
            ),
        )

        val result = YamlSchemaDecorator.decorate(
            "server:\n  port: 25565\nport: 8080\n",
            schema,
        )

        assertEquals(
            "# Server settings.\nserver:\n  # Listening port.\n  port: 25565\n\n# Unrelated root port.\nport: 8080\n",
            result,
        )
    }
}
