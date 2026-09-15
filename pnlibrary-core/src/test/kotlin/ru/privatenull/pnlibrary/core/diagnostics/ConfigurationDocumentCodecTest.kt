package ru.privatenull.pnlibrary.core.diagnostics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class ConfigurationDocumentCodecTest {
    private val codec = ConfigurationDocumentCodec()

    @ParameterizedTest(name = "parses {0}")
    @MethodSource("documents")
    fun `parses supported configuration formats`(
        path: String,
        source: String,
        expectedKey: String,
        expectedValue: Any,
    ) {
        val result = codec.parse(path, source.toByteArray())

        assertEquals(expectedValue, result[expectedKey])
    }

    @ParameterizedTest(name = "renders {0}")
    @MethodSource("renderedDocuments")
    fun `renders supported configuration formats`(
        path: String,
        expectedFragment: String,
    ) {
        val rendered = codec.render(path, mapOf("enabled" to true))

        assertTrue(rendered.contains(expectedFragment), rendered)
    }

    companion object {
        @JvmStatic
        fun documents(): Stream<Arguments> = Stream.of(
            Arguments.of("config.yml", "enabled: true\n", "enabled", true),
            Arguments.of("config.json", "{\"enabled\":true}", "enabled", true),
            Arguments.of("config.properties", "enabled=yes\n", "enabled", "yes"),
            Arguments.of("config.toml", "[server]\nenabled = \"yes\"\n", "server.enabled", "yes"),
            Arguments.of("config.conf", "enabled: yes\n", "enabled", "yes"),
        )

        @JvmStatic
        fun renderedDocuments(): Stream<Arguments> = Stream.of(
            Arguments.of("config.yml", "enabled: true"),
            Arguments.of("config.json", "\"enabled\": true"),
            Arguments.of("config.properties", "enabled=true"),
            Arguments.of("config.toml", "enabled = true"),
            Arguments.of("config.conf", "enabled = true"),
        )
    }
}
