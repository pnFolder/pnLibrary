package org.pnfolder.pnlibrary.config.yaml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.config.ConfigDocument;
import org.pnfolder.pnlibrary.api.config.ConfigSteps;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

/**
 * Brings an existing file to the version of its {@link ConfigSteps} and renders the result.
 *
 * <p>The file's marker says which version was applied last; only newer versions run. Files from a
 * newer plugin version are reported through {@link Result#isNewerThanCode()} and must not be rewritten.
 */
final class YamlMigrationEngine {
    private static final String VERSION_WARNING =
            "# !!! DO NOT EDIT OR REMOVE THIS LINE: pnLibrary uses it to upgrade this file between versions. !!!\n"
                    + "# !!! НЕ МЕНЯЙТЕ И НЕ УДАЛЯЙТЕ ЭТУ СТРОКУ: pnLibrary использует её для обновления файла между версиями. !!!\n";

    private final Yaml yaml = createYaml();

    private static Yaml createYaml() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setWidth(120);
        return new Yaml(new SafeConstructor(new LoaderOptions()), new Representer(options), options);
    }

    /** Outcome of {@link #migrate}. */
    static final class Result {
        private final String content;
        private final List<String> applied;
        private final boolean newerThanCode;

        /**
         * Creates a result.
         *
         * @param content migrated YAML with the version marker, or the input when {@code newerThanCode}
         * @param applied human-readable version labels, such as {@code v2}, in application order
         * @param newerThanCode {@code true} when the file was written by a newer version of the plugin
         */
        Result(@NotNull String content, @NotNull List<String> applied, boolean newerThanCode) {
            this.content = content;
            this.applied = applied;
            this.newerThanCode = newerThanCode;
        }

        @NotNull
        String getContent() {
            return content;
        }

        @NotNull
        List<String> getApplied() {
            return applied;
        }

        boolean isNewerThanCode() {
            return newerThanCode;
        }
    }

    /**
     * Applies every version of {@code steps} newer than the version stored in {@code content}.
     *
     * @throws IllegalStateException when a step fails or the version numbering is broken
     * @throws IllegalArgumentException when the YAML root is not an object
     */
    @NotNull
    Result migrate(@NotNull String content, @NotNull ConfigSteps<?> steps) {
        Object loaded = yaml.load(content);
        if (loaded == null) {
            loaded = Collections.emptyMap();
        }
        if (!(loaded instanceof Map)) {
            throw new IllegalArgumentException("Configuration root must be a YAML object");
        }
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) loaded).entrySet()) {
            root.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        ConfigDocument document = new ConfigDocument(root);
        int fileVersion = versionOf(document);
        if (fileVersion > steps.getLatestVersion()) {
            return new Result(content, Collections.<String>emptyList(), true);
        }
        List<Integer> applied = steps.applyTo(document, fileVersion);
        // The marker is written back by stampDefaults, together with its warning comment.
        document.remove(ConfigSteps.VERSION_KEY);
        Map<String, Object> body = document.toMap();
        String dumped = body.isEmpty() ? "" : YamlText.trimEnd(yaml.dump(body).replace("\r\n", "\n")) + "\n";
        List<String> labels = new ArrayList<String>();
        for (Integer version : applied) {
            labels.add("v" + version);
        }
        return new Result(stampDefaults(dumped, steps), labels, false);
    }

    private static int versionOf(ConfigDocument document) {
        Object marker = document.get(ConfigSteps.VERSION_KEY);
        if (marker == null) {
            return ConfigSteps.BASE_VERSION;
        }
        try {
            return Integer.parseInt(YamlText.trim(marker.toString()));
        } catch (NumberFormatException error) {
            return ConfigSteps.BASE_VERSION;
        }
    }

    /** Prepends the warning and the version marker of {@code steps} to {@code content}. */
    @NotNull
    String stampDefaults(@NotNull String content, @NotNull ConfigSteps<?> steps) {
        return VERSION_WARNING + ConfigSteps.VERSION_KEY + ": " + steps.getLatestVersion() + "\n" + content;
    }
}
