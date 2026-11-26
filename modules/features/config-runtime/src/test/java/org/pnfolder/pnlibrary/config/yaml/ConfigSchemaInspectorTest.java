package org.pnfolder.pnlibrary.config.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.config.ConfigComment;
import org.pnfolder.pnlibrary.api.config.ConfigNamingStrategy;
import org.pnfolder.pnlibrary.api.config.ConfigNewLine;
import org.pnfolder.pnlibrary.api.config.ConfigNotBlank;
import org.pnfolder.pnlibrary.api.config.ConfigPattern;
import org.pnfolder.pnlibrary.api.config.ConfigProblem;
import org.pnfolder.pnlibrary.api.config.ConfigRange;
import org.pnfolder.pnlibrary.api.config.ConfigRequired;
import org.pnfolder.pnlibrary.api.config.ConfigSerializer;

class ConfigSchemaInspectorTest {
    private final ConfigObjectIntrospector introspector = new ConfigObjectIntrospector(ConfigNamingStrategy.KEBAB_CASE);
    private final ConfigSchemaInspector inspector = new ConfigSchemaInspector(introspector,
            new ConfigSerializerResolver(Collections.<Class<?>, ConfigSerializer<?>>emptyMap()));

    @Test
    void findsRequiredPathsInsideNestedConfigurationObjects() {
        assertEquals(new LinkedHashSet<String>(Arrays.asList("name", "connection.host")), inspector.requiredPaths(RootConfig.class));
    }

    @Test
    void reportsValidationFailuresWithCompleteYamlPaths() {
        RootConfig invalid = new RootConfig();
        invalid.name = " ";
        invalid.connection.port = 70_000;
        invalid.connection.host = "INVALID HOST";

        List<ConfigProblem> problems = inspector.validate(invalid, RootConfig.class);

        List<String> paths = new ArrayList<String>();
        for (ConfigProblem problem : problems) {
            paths.add(problem.getPath());
        }
        assertEquals(Arrays.asList("name", "connection.host", "connection.port"), paths);
    }

    @Test
    void buildsCommentsAndLayoutMetadataForNestedFields() {
        Map<String, YamlFieldMetadata> metadata = inspector.metadata(RootConfig.class, new RootConfig());

        assertEquals(Collections.singletonList("Connection settings."), metadata.get("connection").getComments());
        assertTrue(metadata.get("connection").isSeparateWithBlankLine());
        assertTrue(metadata.get("connection").getChildren().containsKey("host"));
    }

    private static final class RootConfig {
        @ConfigRequired
        @ConfigNotBlank
        String name = "server";

        @ConfigComment("Connection settings.")
        @ConfigNewLine
        ConnectionConfig connection = new ConnectionConfig();
    }

    private static final class ConnectionConfig {
        @ConfigRequired
        @ConfigPattern("[a-z.]+")
        String host = "localhost";

        @ConfigRange(min = 1.0, max = 65_535.0)
        int port = 25565;
    }
}
