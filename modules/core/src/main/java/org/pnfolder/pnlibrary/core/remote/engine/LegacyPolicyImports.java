package org.pnfolder.pnlibrary.core.remote.engine;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

/**
 * Policies written before {@code api.remote} was split into {@code context} and {@code result} still import the old
 * flat package; their imports are rewritten before compiling so published policies keep working.
 */
final class LegacyPolicyImports {
    private static final String OLD = "org.pnfolder.pnlibrary.api.remote";
    private static final Pattern MOVED = Pattern.compile(Pattern.quote(OLD)
        + "\\.(RemotePolicyContext|ServerInfo|PlatformInfo|ProductInfo|RemotePolicyResult|RemotePolicyExplanation|DenyAction|PolicyErrorAction)\\b");
    private static final Pattern WILDCARD = Pattern.compile("import\\s+" + Pattern.quote(OLD) + "\\.\\*\\s*;");

    private LegacyPolicyImports() {
    }

    static @NotNull String rewrite(@NotNull String source) {
        Matcher named = MOVED.matcher(source);
        StringBuffer result = new StringBuffer();
        while (named.find()) {
            String name = named.group(1);
            String folder = name.startsWith("RemotePolicyContext") || name.endsWith("Info") ? "context" : "result";
            named.appendReplacement(result, Matcher.quoteReplacement(OLD + "." + folder + "." + name));
        }
        named.appendTail(result);
        return WILDCARD.matcher(result.toString()).replaceAll(Matcher.quoteReplacement(
            "import " + OLD + ".*;\nimport " + OLD + ".context.*;\nimport " + OLD + ".result.*;"));
    }
}
