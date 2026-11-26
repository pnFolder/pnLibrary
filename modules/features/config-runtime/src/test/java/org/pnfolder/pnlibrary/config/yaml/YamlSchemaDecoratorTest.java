package org.pnfolder.pnlibrary.config.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class YamlSchemaDecoratorTest {
    @Test
    void decoratesNestedFieldsAtTheirMatchingIndentation() {
        Map<String, YamlFieldMetadata> nested = new LinkedHashMap<String, YamlFieldMetadata>();
        nested.put("port", new YamlFieldMetadata(Collections.singletonList("Listening port."), false,
                Collections.<String, YamlFieldMetadata>emptyMap()));
        Map<String, YamlFieldMetadata> schema = new LinkedHashMap<String, YamlFieldMetadata>();
        schema.put("server", new YamlFieldMetadata(Collections.singletonList("Server settings."), false, nested));
        schema.put("port", new YamlFieldMetadata(Collections.singletonList("Unrelated root port."), true,
                Collections.<String, YamlFieldMetadata>emptyMap()));

        String result = YamlSchemaDecorator.decorate("server:\n  port: 25565\nport: 8080\n", schema);

        assertEquals(
                "# Server settings.\nserver:\n  # Listening port.\n  port: 25565\n\n# Unrelated root port.\nport: 8080\n",
                result);
    }
}
