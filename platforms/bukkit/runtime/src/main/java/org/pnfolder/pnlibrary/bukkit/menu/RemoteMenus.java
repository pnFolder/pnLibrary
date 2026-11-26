package org.pnfolder.pnlibrary.bukkit.menu;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.pnfolder.pnlibrary.bukkit.tasks.ServerScheduler;
import org.pnfolder.pnlibrary.common.lang.Attempt;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.core.menu.data.ViewerInfo;

/**
 * Runs a chat command for a viewer from another server: the buttons of a remote menu end up here.
 *
 * <p>The command runs as a console that holds only the viewer's permissions and collects what it prints.
 */
public final class RemoteMenus {
    private static final long RUN_SECONDS = 15L;

    private RemoteMenus() {
    }

    /** Runs {@code command} for {@code viewer} on this server, on the server thread, and returns what it printed. */
    public static List<Component> run(ServerScheduler scheduler, ViewerInfo viewer, String command) {
        CompletableFuture<List<Component>> printed = new CompletableFuture<>();
        scheduler.execute(() -> {
            Attempt<List<Component>> result = Failures.runCatchingNonFatal(() -> execute(viewer, command));
            if (result.isSuccess()) {
                printed.complete(result.getOrNull());
            } else {
                printed.completeExceptionally(result.exceptionOrNull());
            }
        });
        try {
            return printed.get(RUN_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException error) {
            // The caller sees the same failure the wait produced; the interface it implements allows no checked ones.
            throw RemoteMenus.<RuntimeException>sneaky(error);
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> E sneaky(Throwable error) throws E {
        throw (E) error;
    }

    private static boolean holds(ViewerInfo viewer, String permission) {
        Set<String> permissions = viewer.getPermissions();
        return permissions.contains(permission)
            || permissions.contains("pnlibrary.admin") && permission.startsWith("pnlibrary.");
    }

    private static List<Component> execute(ViewerInfo viewer, String command) {
        List<Component> lines = new ArrayList<>();
        CommandSender sender = (CommandSender) Proxy.newProxyInstance(
            ConsoleCommandSender.class.getClassLoader(), new Class<?>[] {ConsoleCommandSender.class},
            handler(viewer, lines));
        if (!Bukkit.dispatchCommand(sender, command)) {
            lines.add(Component.text("Команда не найдена: /" + command));
        }
        return lines;
    }

    private static InvocationHandler handler(ViewerInfo viewer, List<Component> lines) {
        return (proxy, method, args) -> {
            switch (method.getName()) {
                case "getName":
                    return viewer.getName();
                case "hasPermission":
                    return args != null && args.length > 0 && args[0] instanceof String && holds(viewer, (String) args[0]);
                case "sendMessage":
                case "sendRawMessage":
                    if (args != null) {
                        for (Object argument : args) {
                            collect(argument, lines);
                        }
                    }
                    return null;
                case "equals":
                    return args != null && args.length > 0 && proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "toString":
                    return "RemoteSender(" + viewer.getName() + ")";
                default:
                    return defaultFor(method.getReturnType());
            }
        };
    }

    private static Object defaultFor(Class<?> type) {
        if (type == Boolean.TYPE) {
            return false;
        }
        return type == Integer.TYPE ? (Object) 0 : null;
    }

    private static void collect(Object value, List<Component> lines) {
        if (value instanceof Component) {
            lines.add((Component) value);
        } else if (value instanceof String) {
            lines.add(Component.text((String) value));
        } else if (value instanceof Object[]) {
            for (Object element : (Object[]) value) {
                collect(element, lines);
            }
        }
    }
}
