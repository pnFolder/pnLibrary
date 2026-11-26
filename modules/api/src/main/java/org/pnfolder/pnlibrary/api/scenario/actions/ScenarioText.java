package org.pnfolder.pnlibrary.api.scenario.actions;

import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.audience.OnlinePlayer;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapter;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService;
import org.pnfolder.pnlibrary.api.scenario.expression.Expression;
import org.pnfolder.pnlibrary.api.scenario.expression.ExpressionException;
import org.pnfolder.pnlibrary.api.scenario.requirements.RequirementContext;
import org.pnfolder.pnlibrary.common.lang.Failures;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads values and fills in the placeholders of texts for one scenario run; see {@link ActionContext#format}. */
final class ScenarioText {
    private static final String EXTERNAL_ADAPTER = "placeholderapi";
    private static final String PAPI_PREFIX = "papi:";
    /** {@code {value-or-placeholder}} and {@code %external%}, each with its own capture group. */
    private static final Pattern TOKEN = Pattern.compile("\\{(?![?:/])([^{}]+)\\}|%([A-Za-z0-9_.:-]+)%");

    private final RequirementContext context;
    private final OnlinePlayer player;
    private final PnLogger logger;
    private final PlaceholderService placeholders;
    private final Map<String, Object> values;

    ScenarioText(RequirementContext context, OnlinePlayer player, PnLogger logger, PlaceholderService placeholders,
                 Map<String, Object> values) {
        this.context = context;
        this.player = player;
        this.logger = logger;
        this.placeholders = placeholders;
        this.values = values;
    }

    String format(String text) {
        Matcher matcher = TOKEN.matcher(text);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            Object value = Failures.runCatchingNonFatal(() -> valueOf(matcher))
                    .onFailure(error -> logger.error("Placeholder " + matcher.group() + " failed; it is left as written", error))
                    .getOrNull();
            String replacement = value != null ? Expression.format(value) : matcher.group();
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    String text(String expression) {
        Object value = Failures.runCatchingNonFatal(() -> read(expression))
                .onFailure(error -> logger.error("Placeholder in '" + expression + "' failed; the text is left as written", error))
                .getOrNull();
        return value != null ? value.toString() : format(expression);
    }

    Object read(String expression) {
        String trimmed = expression.trim();
        Matcher whole = TOKEN.matcher(trimmed);
        if (whole.matches()) {
            return valueOf(whole);
        }
        if (TOKEN.matcher(trimmed).find()) {
            return format(trimmed);
        }
        return resolve(trimmed);
    }

    Object resolve(String reference) {
        String expression = reference.trim();
        if (expression.startsWith("{") && expression.endsWith("}")) {
            expression = expression.substring(1, expression.length() - 1);
        }
        // Text with tags such as `[<gray>pnTest</gray>]` is a message, not a name: asking placeholders for it only logs a warning.
        if (expression.indexOf('<') >= 0 || expression.indexOf('>') >= 0) {
            return null;
        }
        Object own = values.get(nameOf(expression));
        if (own != null) {
            return own;
        }
        return placeholders != null ? placeholder(expression) : null;
    }

    private Object valueOf(Matcher match) {
        String braced = match.group(1);
        if (braced != null && braced.startsWith("=")) {
            return calculate(braced.substring(1));
        }
        String name = braced != null ? nameOf(braced) : match.group(2);
        if (name.equals("player") || name.equals("player_name")) {
            return AudienceExtensions.name(player);
        }
        return braced != null ? braced(braced.trim()) : external(name);
    }

    private static String nameOf(String expression) {
        int bar = expression.indexOf('|');
        return (bar < 0 ? expression : expression.substring(0, bar)).trim();
    }

    /** {@code {= 2 * level}} in a text: the result of a calculation, see {@link Expression}. A mistake leaves the token as written. */
    private Object calculate(String source) {
        try {
            return Expression.format(Expression.parse(source).evaluate(context));
        } catch (ExpressionException error) {
            logger.warning("Calculation {=" + source + "} failed: " + error.getMessage());
            return null;
        }
    }

    /**
     * {@code {name}}, {@code {name|format}}, {@code {plugin/module:path}}, {@code {papi:name}}: this run's value
     * first, then a pnLibrary placeholder; {@code papi:} asks PlaceholderAPI. Without a placeholder service only
     * this run's values are read.
     */
    private Object braced(String expression) {
        String name = nameOf(expression);
        if (name.startsWith(PAPI_PREFIX)) {
            return external(name.substring(PAPI_PREFIX.length()));
        }
        return placeholders != null ? placeholder(expression) : values.get(name);
    }

    private Object placeholder(String expression) {
        return placeholders.resolve(expression, player.getUniqueId(), values).toCompletableFuture().join();
    }

    private Object external(String name) {
        PlaceholderAdapter adapter = placeholders != null ? placeholders.adapters().get(EXTERNAL_ADAPTER) : null;
        if (adapter == null || !adapter.isAvailable()) {
            return null;
        }
        return adapter.resolve(player.getUniqueId(), name);
    }
}
