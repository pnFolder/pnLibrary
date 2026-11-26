package org.pnfolder.pnlibrary.core.menu;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.function.BiPredicate;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.core.commands.proxy.ProxyCommandNames;
import org.pnfolder.pnlibrary.core.commands.proxy.ProxyControlCommand;
import org.pnfolder.pnlibrary.core.link.Link;
import org.pnfolder.pnlibrary.core.menu.data.local.LocalMenuData;
import org.pnfolder.pnlibrary.core.menu.data.MenuDataServer;
import org.pnfolder.pnlibrary.core.menu.data.ViewerInfo;

/**
 * The questions about a proxy's library that game servers ask.
 *
 * <p>A proxy has no inventories and builds no menu. The server a player stands on builds the menu of the proxy from
 * these answers and asks the proxy to run the commands behind its buttons.
 */
public final class ProxyMenu {
    private ProxyMenu() {
    }

    /**
     * Makes the handler that answers game servers.
     *
     * @param library the proxy's library
     * @param link the proxy's link, which keeps the server groups
     * @param control the proxy's {@code /pn}, which runs the chat commands behind the buttons
     * @param describe the plugin behind a registration owner, or null
     * @param holds whether the player of that name, connected to this proxy, has the permission
     */
    public static @NotNull MenuDataServer server(@NotNull PnLibrary library, @NotNull Link link,
                                                 @NotNull ProxyControlCommand control,
                                                 @NotNull Function<Object, OwnerInfo> describe,
                                                 @NotNull BiPredicate<String, String> holds) {
        PluginDirectory directory = new PluginDirectory() {
            @Override
            public @Nullable OwnerInfo owner(@Nullable Object owner) {
                return describe.apply(owner);
            }

            @Override
            public @Nullable OwnerInfo ownerNamed(@NotNull String name) {
                for (PluginRegistration registration : library.getPlugins().all()) {
                    OwnerInfo info = describe.apply(registration.getOwner());
                    if (info != null && info.getName().equalsIgnoreCase(name)) {
                        return info;
                    }
                }
                return null;
            }

            @Override
            public @Nullable OwnerInfo ownerOf(@Nullable Class<?> type) {
                return null;
            }
        };
        ExecutorService executor = Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task, "pnlibrary-menu-data");
            thread.setDaemon(true);
            return thread;
        });
        LocalMenuData data = new LocalMenuData(library, directory, (viewer, line) -> run(control, viewer, line), link.groups());
        return new MenuDataServer(data, executor, holds);
    }

    private static List<Component> run(ProxyControlCommand control, ViewerInfo viewer, String line) {
        UUID viewerId = UUID.nameUUIDFromBytes(viewer.getName().getBytes(StandardCharsets.UTF_8));
        List<Component> printed;
        try {
            printed = control.run(viewerId, viewer.getName(), viewer.getPermissions()::contains, line);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(String.valueOf(error.getMessage()), error);
        } catch (ExecutionException | TimeoutException error) {
            throw new IllegalStateException(error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName(), error);
        }
        List<Component> rewritten = new java.util.ArrayList<>();
        for (Component component : printed) {
            rewritten.add(ProxyCommandNames.rewrite(component));
        }
        return rewritten;
    }
}
