package org.pnfolder.pnlibrary.core.placeholders;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapter;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapterRegistry;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService;
import org.pnfolder.pnlibrary.api.placeholders.info.PlaceholderInfo;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;
import org.pnfolder.pnlibrary.api.placeholders.reply.PublishedAnswers;

/**
 * The placeholder system of the library. It keeps the tree of placeholders, reads addresses, applies formats, fills
 * texts and shows the placeholders that ask for it to PlaceholderAPI through {@link PlaceholderAdapter}s.
 *
 * <p>Every module works through its own {@link #scope(String) scope}, which releases what the module registered when
 * the module closes.
 */
public final class PlaceholderEngine implements PlaceholderAdapterRegistry {
    private static final int MAXIMUM_REPORTED = 500;

    private final Consumer<String> onProblem;
    private final Set<String> problems = ConcurrentHashMap.newKeySet();
    private final PlaceholderGroups groups = new PlaceholderGroups(this::report);
    private final AdapterPublications adapters = new AdapterPublications(this::publishedAnswers, groups::publishedPrefixes, this::report);
    private final FormatRegistry formats = new FormatRegistry(this::report);
    private final ConverterRegistry converters = new ConverterRegistry(this::report);
    private final PlaceholderEvaluation evaluation = new PlaceholderEvaluation(formats, this::report);
    private final PlaceholderResolution resolution = new PlaceholderResolution(groups, adapters, evaluation, this::report);
    private final TextRendering rendering = new TextRendering(
            (address, viewer, values, consumer) -> resolveIn(address, viewer, values, null, consumer), adapters::renderOutside);

    /** An engine that ignores the problems it finds. */
    public PlaceholderEngine() {
        this(problem -> { });
    }

    /** {@code onProblem} receives a readable line for every problem that is not an exception of the caller, such as an unknown format. */
    public PlaceholderEngine(@NotNull Consumer<String> onProblem) {
        this.onProblem = onProblem;
        BuiltInFormats.register(formats);
    }

    /** The view of one module. {@code owner} is {@code plugin/module}. */
    public @NotNull PlaceholderService scope(@NotNull String owner) {
        return scope(owner, true);
    }

    /** Like {@link #scope(String)}; with {@code papiEnabled} off, nothing of the module is shown to PlaceholderAPI. */
    public @NotNull PlaceholderService scope(@NotNull String owner, boolean papiEnabled) {
        return new ModuleScope(this, owner, papiEnabled);
    }

    /** Tells the administrator once about {@code message}. */
    public void report(@NotNull String message) {
        if (problems.size() < MAXIMUM_REPORTED && problems.add(message)) {
            onProblem.accept(message);
        }
    }

    /** The formats, built-in ones included. */
    public @NotNull FormatRegistry getFormats() {
        return formats;
    }

    ConverterRegistry getConverters() {
        return converters;
    }

    @Override
    public @NotNull AutoCloseable register(@NotNull PlaceholderAdapter adapter) {
        return adapters.register(adapter);
    }

    @Override
    public @Nullable PlaceholderAdapter get(@NotNull String id) {
        return adapters.get(id);
    }

    @Override
    public @NotNull List<PlaceholderAdapter> all() {
        return adapters.all();
    }

    /** What PlaceholderAPI asks for {@code %prefix_params%}: {@code _} separates the parts, {@code ~5F} writes a real underscore. */
    private PublishedAnswers publishedAnswers(final String prefix) {
        return (playerId, params, wait) -> {
            String address = params.replace('_', '.');
            ViewerInfo viewer = new ViewerInfo(playerId, ViewerInfo.DEFAULT_LANGUAGE, true);
            CompletableFuture<Object> future = resolveIn(address, viewer, Collections.<String, Object>emptyMap(), prefix, null)
                    .toCompletableFuture();
            Object value = null;
            try {
                value = wait <= 0 ? future.getNow(null) : future.get(wait, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
                // No answer in time or a failed placeholder: PlaceholderAPI shows its own text for no value.
            }
            return value == null ? null : value.toString();
        };
    }

    /** The value of {@code address}, formats applied, or {@code null}. */
    public @NotNull CompletionStage<Object> resolve(@NotNull String address) {
        return resolve(address, ViewerInfo.none());
    }

    /** Like {@link #resolve(String)} for one viewer. */
    public @NotNull CompletionStage<Object> resolve(@NotNull String address, @NotNull ViewerInfo viewer) {
        return resolve(address, viewer, Collections.<String, Object>emptyMap());
    }

    /** Like {@link #resolve(String, ViewerInfo)}; the {@code values} of the caller win over registered placeholders. */
    public @NotNull CompletionStage<Object> resolve(@NotNull String address, @NotNull ViewerInfo viewer, @NotNull Map<String, Object> values) {
        return resolveIn(address, viewer, values, null, null);
    }

    /** Like {@link #resolve(String)}, but hidden placeholders answer too: for the {@code test} command of an operator. */
    public @NotNull CompletionStage<Object> resolveAll(@NotNull String address) {
        return resolveAll(address, ViewerInfo.none());
    }

    /** Like {@link #resolveAll(String)} for one viewer. */
    public @NotNull CompletionStage<Object> resolveAll(@NotNull String address, @NotNull ViewerInfo viewer) {
        return resolveIn(address, viewer, Collections.<String, Object>emptyMap(), null, Ownership.EVERYTHING);
    }

    CompletionStage<String> renderAs(String text, ViewerInfo viewer, Map<String, Object> values, String consumer) {
        return rendering.render(text, viewer, values, consumer);
    }

    CompletionStage<Object> resolveIn(String address, ViewerInfo viewer, Map<String, Object> values, String prefix, String consumer) {
        return resolution.resolve(address, viewer, values, prefix, consumer);
    }

    /** {@code text} with every {@code {address}} replaced; then the outside systems fill their own {@code %tokens%}. */
    public @NotNull CompletionStage<String> render(@NotNull String text) {
        return render(text, ViewerInfo.none());
    }

    /** Like {@link #render(String)} for one viewer. */
    public @NotNull CompletionStage<String> render(@NotNull String text, @NotNull ViewerInfo viewer) {
        return render(text, viewer, Collections.<String, Object>emptyMap());
    }

    /** Like {@link #render(String, ViewerInfo)}; the {@code values} of the caller win over registered placeholders. */
    public @NotNull CompletionStage<String> render(@NotNull String text, @NotNull ViewerInfo viewer, @NotNull Map<String, Object> values) {
        return rendering.render(text, viewer, values, null);
    }

    /** Whether {@code address} names a registered placeholder; nothing is evaluated. */
    public boolean contains(@NotNull String address) {
        return containsFor(address, null);
    }

    boolean containsFor(String address, String consumer) {
        return resolution.contains(address, consumer);
    }

    /** Every placeholder, sorted by address. */
    public @NotNull List<PlaceholderInfo> list() {
        return groups.list();
    }

    /** The names of every format, built-in ones included. */
    public @NotNull List<String> formatNames() {
        return formats.names();
    }

    void add(PlaceholderEntry entry) {
        groups.add(entry);
        adapters.reconcile();
    }

    void remove(PlaceholderEntry entry) {
        groups.remove(entry);
        adapters.reconcile();
    }

    void removeOwner(String owner) {
        groups.removeOwner(owner);
        formats.removeOwner(owner);
        converters.removeOwner(owner);
        adapters.reconcile();
    }
}
