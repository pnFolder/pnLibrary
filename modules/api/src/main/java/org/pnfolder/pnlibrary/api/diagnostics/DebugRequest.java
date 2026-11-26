package org.pnfolder.pnlibrary.api.diagnostics;

import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Parsed {@code /pn debug} command invocation. */
public final class DebugRequest {
    private static final Pattern VALID_TARGET = Pattern.compile("[A-Za-z0-9_.-]{1,80}");
    private static final String USAGE = "Invalid debug command usage";

    private final String target;
    private final boolean configs;
    private final boolean logs;
    private final boolean local;

    /**
     * Creates a request.
     *
     * @param target plugin name or {@code "all"}
     * @param configs whether configuration files should be collected; always {@code true} for a command
     * @param logs whether recent logs and the observation journal should be collected; always {@code true} for a command
     * @param local skip upload; save to local file only
     */
    public DebugRequest(@NotNull String target, boolean configs, boolean logs, boolean local) {
        this.target = Objects.requireNonNull(target);
        this.configs = configs;
        this.logs = logs;
        this.local = local;
    }

    /** Plugin name or {@code "all"}. */
    @NotNull
    public String getTarget() {
        return target;
    }

    /** Whether configuration files should be collected. */
    public boolean isConfigs() {
        return configs;
    }

    /** Whether recent logs and the observation journal should be collected. */
    public boolean isLogs() {
        return logs;
    }

    /** Whether the report is only saved to a local file, not uploaded. */
    public boolean isLocal() {
        return local;
    }

    /**
     * Parses command arguments that carry no leading {@code debug}; see {@link #parse(String[], boolean)}.
     *
     * @throws IllegalArgumentException on unrecognised arguments or duplicate flags
     */
    @NotNull
    public static DebugRequest parse(@NotNull String[] args) {
        return parse(args, false);
    }

    /**
     * Parses command arguments into a {@link DebugRequest}. The report always carries logs and configurations; the only
     * flag is {@code --local}, which saves the file instead of uploading it.
     *
     * @param args raw argument array from the command handler
     * @param prefixed if {@code true}, the first argument must be {@code "debug"} and is consumed
     * @throws IllegalArgumentException on unrecognised arguments or duplicate flags
     */
    @NotNull
    public static DebugRequest parse(@NotNull String[] args, boolean prefixed) {
        int offset = 0;
        if (prefixed) {
            require(args.length > 0 && args[0].equalsIgnoreCase("debug"));
            offset = 1;
        }
        String target = "all";
        boolean targetChosen = false;
        Set<String> flags = new HashSet<>();
        for (int i = offset; i < args.length; i++) {
            String value = args[i];
            if (value.startsWith("--")) {
                String flag = value.toLowerCase(Locale.ROOT);
                require(flag.equals("--local") && flags.add(flag));
            } else {
                require(!targetChosen && VALID_TARGET.matcher(value).matches());
                target = value;
                targetChosen = true;
            }
        }
        return new DebugRequest(target, true, true, flags.contains("--local"));
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw new IllegalArgumentException(USAGE);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DebugRequest)) {
            return false;
        }
        DebugRequest that = (DebugRequest) other;
        return configs == that.configs && logs == that.logs && local == that.local && target.equals(that.target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(target, configs, logs, local);
    }

    @NotNull
    @Override
    public String toString() {
        return "DebugRequest(target=" + target + ", configs=" + configs + ", logs=" + logs + ", local=" + local + ")";
    }
}
