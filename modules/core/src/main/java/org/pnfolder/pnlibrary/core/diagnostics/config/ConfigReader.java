package org.pnfolder.pnlibrary.core.diagnostics.config;

import java.util.Objects;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticConfiguration;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.core.diagnostics.redaction.DiagnosticRedactor;

/**
 * Reads plugin configuration files for support reports, with the values each report must hide.
 *
 * <p>File confinement is done by {@link SecureConfigReader}, syntax by {@link ConfigurationDocumentCodec}, and
 * the hiding rules by {@link ConfigRedaction}, built from the global settings and the file's own declaration.
 */
public final class ConfigReader {
    /** Largest configuration file a report accepts, in bytes. */
    public static final long MAX_FILE_SIZE_BYTES = 1_048_576L;

    private static final String UNPARSABLE_WITH_RULES = "[file cannot be parsed and has values that must stay hidden]";

    private final Path dataFolder;
    private final PnLibraryConfig globalConfig;
    private final DiagnosticRedactor redactor = new DiagnosticRedactor();
    private final SecureConfigReader secureFileReader = new SecureConfigReader(MAX_FILE_SIZE_BYTES);
    private final ConfigurationDocumentCodec documentCodec = new ConfigurationDocumentCodec(redactor);

    /** Creates a reader with the default global settings; {@code dataFolder} is the default root of relative paths. */
    public ConfigReader(@NotNull Path dataFolder) {
        this(dataFolder, new PnLibraryConfig());
    }

    /** Creates a reader that also applies the process-wide exclusions and patterns of {@code globalConfig}. */
    public ConfigReader(@NotNull Path dataFolder, @NotNull PnLibraryConfig globalConfig) {
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
        this.globalConfig = globalConfig;
    }

    /** Reads a file for an encrypted report from the default root; see {@link #readProtectedFile(DiagnosticConfiguration, Path)}. */
    @NotNull
    public ExactFile readProtectedFile(@NotNull DiagnosticConfiguration configSpec) {
        return readProtectedFile(configSpec, dataFolder);
    }

    /**
     * Reads a file for an encrypted report: the original bytes, comments included, unless the file holds a value
     * that was asked to be hidden; then it is rendered again with those values hidden.
     */
    @NotNull
    public ExactFile readProtectedFile(@NotNull DiagnosticConfiguration configSpec, @NotNull Path rootDirectory) {
        String path = configSpec.getPath();
        SecureConfigReader.Result read = secureFileReader.read(rootDirectory, path);
        byte[] content = read.getContent();
        if (content == null) {
            return new ExactFile(path, null, read.getError());
        }
        ConfigRedaction redaction = redaction(configSpec);
        Map<String, Object> data = documentCodec.isStructured(path) ? parseQuietly(path, content) : null;
        if (data == null) {
            // Values of a format that cannot be parsed cannot be hidden one by one, so the file is only sent when nothing has to be.
            return redaction.hasRequests() ? new ExactFile(path, null, UNPARSABLE_WITH_RULES) : new ExactFile(path, content, null);
        }
        Map<String, Object> hidden = redaction.requested(data);
        if (hidden.equals(data)) {
            return new ExactFile(path, content, null);
        }
        return new ExactFile(path, documentCodec.render(path, hidden).getBytes(StandardCharsets.UTF_8), null);
    }

    /** Reads a file for a plaintext report from the default root; see {@link #readRedactedFile(DiagnosticConfiguration, Path)}. */
    @NotNull
    public RedactedFile readRedactedFile(@NotNull DiagnosticConfiguration configSpec) {
        return readRedactedFile(configSpec, dataFolder);
    }

    /** Reads, validates and redacts a configuration while preserving its original file format. */
    @NotNull
    @SuppressWarnings("unchecked")
    public RedactedFile readRedactedFile(@NotNull DiagnosticConfiguration configSpec, @NotNull Path rootDirectory) {
        Map<String, Object> result = readAndRedact(configSpec, rootDirectory);
        Object error = result.get("error");
        if (error != null) {
            return new RedactedFile(configSpec.getPath(), "", error.toString());
        }
        Object data = result.get("data");
        Map<String, Object> tree = data instanceof Map ? (Map<String, Object>) data : Collections.emptyMap();
        return new RedactedFile(configSpec.getPath(), documentCodec.render(configSpec.getPath(), tree), null);
    }

    /** Returns the redacted tree used by report serialization from the default root. */
    @NotNull
    public Map<String, Object> readAndRedact(@NotNull DiagnosticConfiguration configSpec) {
        return readAndRedact(configSpec, dataFolder);
    }

    /**
     * Returns the redacted neutral tree used by report serialization.
     *
     * <p>The result always contains {@code path}. On failure it additionally contains {@code error}; on success it
     * contains {@code data}.
     */
    @NotNull
    public Map<String, Object> readAndRedact(@NotNull DiagnosticConfiguration configSpec, @NotNull Path rootDirectory) {
        String path = configSpec.getPath();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", path);
        SecureConfigReader.Result read = secureFileReader.read(rootDirectory, path);
        byte[] content = read.getContent();
        if (content == null) {
            result.put("error", read.getError());
            return result;
        }
        Map<String, Object> data;
        try {
            data = documentCodec.parse(path, content);
        } catch (Exception e) {
            result.put("error", "Parsing failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            return result;
        }
        result.put("data", redaction(configSpec).redact(data));
        return result;
    }

    @Nullable
    private Map<String, Object> parseQuietly(String path, byte[] content) {
        // A file that cannot be parsed is handled by the caller, which refuses it when something must stay hidden.
        return Failures.runCatchingNonFatal(() -> documentCodec.parse(path, content)).getOrNull();
    }

    private ConfigRedaction redaction(DiagnosticConfiguration configSpec) {
        return new ConfigRedaction(
            new HashSet<>(configSpec.getExcludedPaths()),
            ConfigRedaction.compile(concat(globalConfig.getSecretKeyPatterns(), configSpec.getSecretKeyPatterns())),
            ConfigRedaction.compile(concat(globalConfig.getRedactValuePatterns(), configSpec.getValuePatterns())),
            redactor);
    }

    private static List<String> concat(List<String> first, List<String> second) {
        List<String> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    /** Text configuration prepared for a plaintext local report. */
    public static final class RedactedFile {
        private final String path;
        private final String content;
        private final String error;

        /** Creates the outcome for {@code path}: the redacted {@code content}, or an {@code error}. */
        public RedactedFile(@NotNull String path, @NotNull String content, @Nullable String error) {
            this.path = path;
            this.content = content;
            this.error = error;
        }

        /** The declared path. */
        @NotNull
        public String getPath() {
            return path;
        }

        /** The redacted text; empty when there is an error. */
        @NotNull
        public String getContent() {
            return content;
        }

        /** Why the file could not be read, or {@code null}. */
        @Nullable
        public String getError() {
            return error;
        }
    }

    /** Configuration bytes for an encrypted report. */
    public static final class ExactFile {
        private final String path;
        private final byte[] content;
        private final String error;

        /** Creates the outcome for {@code path}: the {@code content} to send, or an {@code error}. */
        public ExactFile(@NotNull String path, @Nullable byte[] content, @Nullable String error) {
            this.path = path;
            this.content = content;
            this.error = error;
        }

        /** The declared path. */
        @NotNull
        public String getPath() {
            return path;
        }

        /** The bytes to send, or {@code null} when there is an error. */
        @Nullable
        public byte[] getContent() {
            return content;
        }

        /** Why the file could not be read, or {@code null}. */
        @Nullable
        public String getError() {
            return error;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof ExactFile)) {
                return false;
            }
            ExactFile that = (ExactFile) other;
            return path.equals(that.path) && Arrays.equals(content, that.content)
                && (error == null ? that.error == null : error.equals(that.error));
        }

        @Override
        public int hashCode() {
            return 31 * (31 * path.hashCode() + Arrays.hashCode(content)) + (error == null ? 0 : error.hashCode());
        }

        @Override
        public String toString() {
            return "ExactFile(path=" + path + ", error=" + error + ")";
        }
    }
}
