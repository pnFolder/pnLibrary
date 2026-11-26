package org.pnfolder.pnlibrary.core.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.pnfolder.pnlibrary.core.diagnostics.config.ConfigurationDocumentCodec;

class ConfigurationDocumentCodecTest {
    private final ConfigurationDocumentCodec codec = new ConfigurationDocumentCodec();

    @ParameterizedTest(name = "parses {0}")
    @MethodSource("documents")
    void parsesSupportedConfigurationFormats(String path, String source, String expectedKey, Object expectedValue) {
        Map<String, Object> result = codec.parse(path, source.getBytes(StandardCharsets.UTF_8));

        assertEquals(expectedValue, result.get(expectedKey));
    }

    @ParameterizedTest(name = "renders {0}")
    @MethodSource("renderedDocuments")
    void rendersSupportedConfigurationFormats(String path, String expectedFragment) {
        String rendered = codec.render(path, Collections.singletonMap("enabled", true));

        assertTrue(rendered.contains(expectedFragment), rendered);
    }

    static Stream<Arguments> documents() {
        return Stream.of(
            Arguments.of("config.yml", "enabled: true\n", "enabled", true),
            Arguments.of("config.json", "{\"enabled\":true}", "enabled", true),
            Arguments.of("config.properties", "enabled=yes\n", "enabled", "yes"),
            Arguments.of("config.toml", "[server]\nenabled = \"yes\"\n", "server.enabled", "yes"),
            Arguments.of("config.conf", "enabled: yes\n", "enabled", "yes"));
    }

    static Stream<Arguments> renderedDocuments() {
        return Stream.of(
            Arguments.of("config.yml", "enabled: true"),
            Arguments.of("config.json", "\"enabled\": true"),
            Arguments.of("config.properties", "enabled=true"),
            Arguments.of("config.toml", "enabled = true"),
            Arguments.of("config.conf", "enabled = true"));
    }
}
