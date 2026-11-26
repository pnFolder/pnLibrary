package org.pnfolder.pnlibrary.core.diagnostics.config;

import java.util.Objects;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.core.diagnostics.redaction.DiagnosticRedactor;
import org.pnfolder.pnlibrary.core.diagnostics.redaction.Whitespace;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Parses and renders configuration documents supported by diagnostic reports.
 *
 * <p>The codec deliberately produces a neutral tree of maps, lists, scalar values, and {@code null}. It performs
 * no path validation or secret filtering; those concerns belong to {@link SecureConfigReader} and
 * {@link ConfigReader}, respectively. Unknown extensions are represented by a bounded {@code raw} value.
 */
public final class ConfigurationDocumentCodec {
    private static final List<String> STRUCTURED_EXTENSIONS =
        Arrays.asList(".yml", ".yaml", ".json", ".properties", ".toml", ".conf");
    private static final int MAX_RAW_CHARACTERS = 4_096;
    private static final int MAX_YAML_ALIASES = 50;
    private static final Pattern LINE_BREAK = Pattern.compile("\r\n|\n|\r");
    private static final Gson GSON = new Gson();
    private static final Gson PRETTY_JSON = new Gson().newBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final DiagnosticRedactor redactor;

    /** Creates a codec with the built-in redactor for unknown formats. */
    public ConfigurationDocumentCodec() {
        this(new DiagnosticRedactor());
    }

    /** Creates a codec that redacts the raw text of unknown formats with {@code redactor}. */
    public ConfigurationDocumentCodec(@NotNull DiagnosticRedactor redactor) {
        this.redactor = Objects.requireNonNull(redactor, "redactor");
    }

    /** Whether {@code path} has an extension that {@link #parse} turns into real keys rather than one {@code raw} text. */
    public boolean isStructured(@NotNull String path) {
        String lowercase = path.toLowerCase(Locale.ROOT);
        for (String extension : STRUCTURED_EXTENSIONS) {
            if (lowercase.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    /** Parses {@code content} according to the extension of {@code path}. */
    @NotNull
    public Map<String, Object> parse(@NotNull String path, @NotNull byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        String lowercase = path.toLowerCase(Locale.ROOT);
        if (lowercase.endsWith(".yml") || lowercase.endsWith(".yaml")) {
            return parseYaml(text);
        }
        if (lowercase.endsWith(".json")) {
            return objectToMap(GSON.fromJson(JsonParser.parseString(text), Object.class));
        }
        if (lowercase.endsWith(".properties")) {
            return parseProperties(text);
        }
        if (lowercase.endsWith(".toml")) {
            return parseToml(text);
        }
        if (lowercase.endsWith(".conf")) {
            return parseConf(text);
        }
        String raw = text.length() <= MAX_RAW_CHARACTERS ? text : text.substring(0, MAX_RAW_CHARACTERS);
        return Collections.singletonMap("raw", redactor.redact(raw));
    }

    /** Writes {@code data} in the format of {@code path}. */
    @NotNull
    public String render(@NotNull String path, @NotNull Map<String, Object> data) {
        String lowercase = path.toLowerCase(Locale.ROOT);
        if (lowercase.endsWith(".yml") || lowercase.endsWith(".yaml")) {
            return new Yaml().dump(data);
        }
        if (lowercase.endsWith(".json")) {
            return PRETTY_JSON.toJson(data) + "\n";
        }
        if (lowercase.endsWith(".properties")) {
            return renderAssignments(data, "=", false);
        }
        if (lowercase.endsWith(".toml") || lowercase.endsWith(".conf")) {
            return renderAssignments(data, " = ", true);
        }
        Object raw = data.get("raw");
        return raw == null ? "" : raw.toString();
    }

    private static Map<String, Object> parseYaml(String text) {
        LoaderOptions options = new LoaderOptions();
        options.setMaxAliasesForCollections(MAX_YAML_ALIASES);
        options.setAllowDuplicateKeys(false);
        return objectToMap(new Yaml(new SafeConstructor(options)).load(text));
    }

    private static Map<String, Object> parseProperties(String text) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(text));
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (String name : properties.stringPropertyNames()) {
            result.put(name, properties.getProperty(name));
        }
        return result;
    }

    private static Map<String, Object> parseToml(String text) {
        Map<String, Object> result = new LinkedHashMap<>();
        String section = "";
        for (String line : LINE_BREAK.split(text, -1)) {
            String trimmed = Whitespace.trim(line);
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) {
                continue;
            }
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                section = Whitespace.trim(trimmed.substring(1, trimmed.length() - 1));
                continue;
            }
            int separator = trimmed.indexOf('=');
            if (separator > 0) {
                String key = Whitespace.trim(trimmed.substring(0, separator));
                result.put(section.isEmpty() ? key : section + "." + key, unquote(trimmed.substring(separator + 1)));
            }
        }
        return result;
    }

    private static Map<String, Object> parseConf(String text) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String line : LINE_BREAK.split(text, -1)) {
            String trimmed = Whitespace.trim(line);
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
                continue;
            }
            int equals = trimmed.indexOf('=');
            int colon = trimmed.indexOf(':');
            int separator = equals > 0 ? equals : colon > 0 ? colon : -1;
            if (separator > 0) {
                result.put(Whitespace.trim(trimmed.substring(0, separator)), unquote(trimmed.substring(separator + 1)));
            }
        }
        return result;
    }

    private static String unquote(String value) {
        return removeSurrounding(removeSurrounding(Whitespace.trim(value), "\""), "'");
    }

    private static String removeSurrounding(String value, String quote) {
        boolean surrounded = value.length() >= 2 * quote.length() && value.startsWith(quote) && value.endsWith(quote);
        return surrounded ? value.substring(quote.length(), value.length() - quote.length()) : value;
    }

    private static Map<String, Object> objectToMap(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value == null) {
            return result;
        }
        if (!(value instanceof Map)) {
            result.put("value", transform(value));
            return result;
        }
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            if (entry.getKey() != null) {
                result.put(entry.getKey().toString(), transform(entry.getValue()));
            }
        }
        return result;
    }

    private static Object transform(Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map) {
            return objectToMap(value);
        }
        if (value instanceof List) {
            List<Object> items = new ArrayList<>();
            for (Object item : (List<?>) value) {
                items.add(transform(item));
            }
            return items;
        }
        return value.toString();
    }

    private static String renderAssignments(Map<String, Object> data, String separator, boolean toml) {
        StringBuilder text = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) {
                text.append('\n');
            }
            first = false;
            text.append(entry.getKey()).append(separator).append(toml ? tomlValue(entry.getValue()) : scalar(entry.getValue()));
        }
        return text.append('\n').toString();
    }

    private static String scalar(Object value) {
        return value == null ? "" : value.toString().replace("\\", "\\\\").replace("\n", "\\n");
    }

    private static String tomlValue(Object value) {
        if (value == null) {
            return "\"\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        return "\"" + value.toString().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
