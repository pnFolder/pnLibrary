package org.pnfolder.pnlibrary.core.placeholders;

import org.pnfolder.pnlibrary.core.placeholders.scan.PhPart;
import org.pnfolder.pnlibrary.core.placeholders.scan.PhNode;
import org.pnfolder.pnlibrary.core.placeholders.scan.Scanner;
import org.pnfolder.pnlibrary.core.placeholders.format.FormatDef;
import org.pnfolder.pnlibrary.core.placeholders.format.AnnotatedFormats;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapterRegistry;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderValue;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentConverter;
import org.pnfolder.pnlibrary.api.placeholders.info.FormatInfo;
import org.pnfolder.pnlibrary.api.placeholders.info.PlaceholderInfo;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;
import org.pnfolder.pnlibrary.api.registry.Registry;
import org.pnfolder.pnlibrary.api.registry.RegistryEntry;
import org.pnfolder.pnlibrary.api.registry.Visibility;
import org.pnfolder.pnlibrary.core.registry.ListRegistry;

/**
 * The view of one module on the placeholder system: what it registers belongs to {@code owner} ({@code plugin/module})
 * and is released by {@link #close()}. With PlaceholderAPI off, nothing of the module is shown to it.
 */
final class ModuleScope implements PlaceholderService {
    private static final Pattern PREFIX = Pattern.compile("[a-z0-9-]{2,32}");

    private final PlaceholderEngine engine;
    private final String owner;
    private final boolean papiEnabled;

    ModuleScope(PlaceholderEngine engine, String owner, boolean papiEnabled) {
        this.engine = engine;
        this.owner = owner;
        this.papiEnabled = papiEnabled;
    }

    private Scanner scanner() {
        return new Scanner(type -> engine.getConverters().find(type, owner));
    }

    @Override
    public void registerPublished(@Nullable String papi, @NotNull Object... holders) {
        checkPrefix(papi);
        for (PhNode root : scanner().scan(Arrays.asList(holders))) {
            engine.add(new PlaceholderEntry(owner, root, papiEnabled ? papi : null));
        }
    }

    @Override
    public @NotNull AutoCloseable value(
            @NotNull String address,
            @NotNull String description,
            @Nullable String papi,
            @NotNull Visibility visibility,
            @NotNull final PlaceholderValue resolve) {
        checkPrefix(papi);
        List<PhNode> nodes = wordsOf(address);
        PhNode last = nodes.get(nodes.size() - 1);
        last.setDescription(description);
        last.setValue(call -> {
            Object result = resolve.get(call.getViewer());
            if (result instanceof CompletionStage) {
                return ((CompletionStage<?>) result).thenApply(inner -> (Object) inner);
            }
            return CompletableFuture.completedFuture(result);
        });
        last.setVisibility(visibility);
        final PlaceholderEntry entry = new PlaceholderEntry(owner, nodes.get(0), papiEnabled ? papi : null);
        engine.add(entry);
        return new AutoCloseable() {
            @Override
            public void close() {
                engine.remove(entry);
            }
        };
    }

    private static List<PhNode> wordsOf(String address) {
        String body = address.trim();
        if (body.startsWith("{")) {
            body = body.substring(1);
        }
        if (body.endsWith("}")) {
            body = body.substring(0, body.length() - 1);
        }
        List<PhNode> nodes = new ArrayList<>();
        for (String word : body.split("\\.", -1)) {
            nodes.add(new PhNode(PhPart.word(PhPart.checkWord(word.toLowerCase(Locale.ROOT))), Object.class));
        }
        PhNode.link(nodes);
        return nodes;
    }

    @Override
    public void formats(@NotNull Object... holders) {
        AnnotatedFormats.register(engine.getFormats(), owner, Arrays.asList(holders), type -> engine.getConverters().find(type, owner));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> @NotNull AutoCloseable converter(@NotNull Class<T> type, @NotNull ArgumentConverter<T> converter) {
        return engine.getConverters().add(owner, type, converter);
    }

    @Override
    public @NotNull CompletionStage<Object> resolve(@NotNull String address, @NotNull ViewerInfo viewer, @NotNull Map<String, Object> values) {
        return engine.resolveIn(address, viewer, values, null, owner);
    }

    @Override
    public @NotNull CompletionStage<String> render(@NotNull String text, @NotNull ViewerInfo viewer, @NotNull Map<String, Object> values) {
        return engine.renderAs(text, viewer, values, owner);
    }

    @Override
    public boolean contains(@NotNull String address) {
        return engine.containsFor(address, owner);
    }

    @Override
    public @NotNull List<PlaceholderInfo> list() {
        return engine.list();
    }

    @Override
    public @NotNull Registry<PlaceholderInfo> registry() {
        return new ListRegistry<>("placeholders", () -> {
            List<RegistryEntry<PlaceholderInfo>> entries = new ArrayList<>();
            for (PlaceholderInfo info : list()) {
                entries.add(RegistryEntries.placeholder(info));
            }
            return entries;
        });
    }

    @Override
    public @NotNull Registry<FormatInfo> formatRegistry() {
        return new ListRegistry<>("formats", () -> {
            List<FormatDef> formats = engine.getFormats().all();
            formats.sort((a, b) -> a.getName().compareTo(b.getName()));
            List<RegistryEntry<FormatInfo>> entries = new ArrayList<>();
            for (FormatDef format : formats) {
                entries.add(RegistryEntries.format(format));
            }
            return entries;
        });
    }

    @Override
    public @NotNull PlaceholderAdapterRegistry adapters() {
        return engine;
    }

    @Override
    public void close() {
        engine.removeOwner(owner);
    }

    private static void checkPrefix(@Nullable String papi) {
        if (papi != null && !PREFIX.matcher(papi).matches()) {
            throw new IllegalArgumentException("PlaceholderAPI prefix '" + papi
                    + "' must be 2-32 characters of a-z, 0-9 and '-' (no '_': PlaceholderAPI splits on it)");
        }
    }
}
