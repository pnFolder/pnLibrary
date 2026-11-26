package org.pnfolder.pnlibrary.config.yaml.schema;

import org.pnfolder.pnlibrary.config.yaml.text.YamlText;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

/**
 * Inserts schema comments into block-style YAML without changing scalar values.
 *
 * <p>Metadata is selected by indentation and field name. Nested schemas are active
 * only while the source remains inside their parent indentation, preventing equal
 * keys in unrelated sections from inheriting the wrong documentation.
 */
public final class YamlSchemaDecorator {
    private static final int YAML_INDENT = 2;

    private YamlSchemaDecorator() {
    }

    /** Returns {@code source} with comments from {@code root} and exactly one trailing newline. */
    @NotNull
    public static String decorate(@NotNull String source, @NotNull Map<String, YamlFieldMetadata> root) {
        List<String> result = new ArrayList<>();
        Map<Integer, Map<String, YamlFieldMetadata>> schemasByIndent = new HashMap<>();
        schemasByIndent.put(0, root);
        for (String line : YamlText.lines(source)) {
            int indent = YamlText.indentOf(line);
            schemasByIndent.keySet().removeIf(level -> level > indent);
            String key = YamlText.trimChars(substringBefore(YamlText.trimStart(line), ':'), '"', '\'');
            Map<String, YamlFieldMetadata> schema = schemasByIndent.get(indent);
            YamlFieldMetadata metadata = schema == null ? null : schema.get(key);
            if (metadata != null) {
                addComments(result, metadata, indent);
                if (!metadata.getChildren().isEmpty()) {
                    schemasByIndent.put(indent + YAML_INDENT, metadata.getChildren());
                } else {
                    schemasByIndent.remove(indent + YAML_INDENT);
                }
            }
            result.add(line);
        }
        return YamlText.trimEnd(String.join("\n", result)) + "\n";
    }

    private static void addComments(List<String> result, YamlFieldMetadata metadata, int indent) {
        if (metadata.isSeparateWithBlankLine() && !result.isEmpty()
                && !YamlText.isBlank(result.get(result.size() - 1))) {
            result.add("");
        }
        for (String comment : metadata.getComments()) {
            result.add(YamlText.spaces(indent) + commentLine(comment));
        }
    }

    private static String substringBefore(String text, char delimiter) {
        int index = text.indexOf(delimiter);
        return index < 0 ? text : text.substring(0, index);
    }

    /** Renders {@code lines} as YAML comments, writing a blank line as a bare {@code #} without trailing space. */
    @NotNull
    public static String commentLines(@NotNull String[] lines, @NotNull String indent) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < lines.length; index++) {
            if (index > 0) {
                text.append('\n');
            }
            text.append(indent).append(commentLine(lines[index]));
        }
        return text.toString();
    }

    private static String commentLine(String text) {
        return YamlText.isBlank(text) ? "#" : "# " + text;
    }
}
