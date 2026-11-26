package org.pnfolder.pnlibrary.core.updates.catalog;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNodeBuilder;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.updates.history.UpdateHistoryEntry;
import org.pnfolder.pnlibrary.common.time.DisplayZone;
import org.pnfolder.pnlibrary.core.exceptions.Throwables;

/** The {@code history} literal of the update catalog: a page of what was updated, for every product or for one. */
final class UpdateCatalogHistoryCommands {
    private final PnLibrary library;
    private final String viewPermission;
    private final Predicate<CommandContext> ready;
    private final Function<CommandContext, UpdateCatalogRoutes> routesFor;
    private final Function<CommandContext, Integer> requestedPage;
    private final Consumer<Runnable> deliver;

    UpdateCatalogHistoryCommands(PnLibrary library, @Nullable String viewPermission, Predicate<CommandContext> ready,
                                 Function<CommandContext, UpdateCatalogRoutes> routesFor,
                                 Function<CommandContext, Integer> requestedPage, Consumer<Runnable> deliver) {
        this.library = library;
        this.viewPermission = viewPermission;
        this.ready = ready;
        this.routesFor = routesFor;
        this.requestedPage = requestedPage;
        this.deliver = deliver;
    }

    void install(CommandNodeBuilder history) {
        if (viewPermission != null) {
            history.permission(viewPermission);
        }
        history.executes(context -> showHistory(context, null));
        history.argument("page", ArgumentType.integer(), page -> page.executes(context -> showHistory(context, null)));
        history.literal("plugin", node -> node.argument("product", ArgumentType.string(), product -> {
            product.suggests(context -> UpdateCatalogSupport.productNames(library));
            product.executes(context -> showHistory(context, context.<String>get("product")));
            product.argument("page", ArgumentType.integer(), page ->
                page.executes(context -> showHistory(context, context.<String>get("product"))));
        }));
    }

    private void showHistory(CommandContext context, @Nullable String product) {
        if (!ready.test(context)) {
            return;
        }
        Integer page = requestedPage.apply(context);
        if (page == null) {
            return;
        }
        CompletableFuture<List<UpdateHistoryEntry>> entries = library.getUpdates().getHistory().page(page, product).toCompletableFuture();
        CompletableFuture<Integer> total = library.getUpdates().getHistory().count(product).toCompletableFuture();
        CompletableFuture.allOf(entries, total).whenComplete((done, failure) -> deliver.accept(() -> {
            if (failure != null) {
                context.getSender().sendMessage(CatalogErrors.line(historyFailure(failure, product)));
            } else {
                int pages = Math.max(1, (total.join() + UpdateCatalogView.PAGE_SIZE - 1) / UpdateCatalogView.PAGE_SIZE);
                send(context, UpdateCatalogView.history(entries.join(), page, routesFor.apply(context), pages, product,
                    Instant.now(), DisplayZone.getZone()));
            }
        }));
    }

    private static String historyFailure(Throwable failure, String product) {
        if (product != null && Throwables.rootCause(failure) instanceof IllegalArgumentException) {
            return "Некорректное имя плагина: " + product + ".";
        }
        return "Не удалось прочитать историю обновлений.";
    }

    private static void send(CommandContext context, List<Component> lines) {
        for (Component line : lines) {
            context.getSender().sendMessage(line);
        }
    }
}
