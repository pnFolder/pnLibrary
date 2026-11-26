package org.pnfolder.pnlibrary.core.menu.data;

import org.pnfolder.pnlibrary.core.menu.data.question.MenuQuestion;
import org.pnfolder.pnlibrary.core.menu.data.question.UpdateQuestions;
import org.pnfolder.pnlibrary.core.menu.data.question.SystemQuestions;
import org.pnfolder.pnlibrary.core.menu.data.question.NetworkQuestions;
import org.pnfolder.pnlibrary.core.menu.data.question.EventQuestions;
import java.util.Objects;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.core.link.remote.DataCall;
import org.pnfolder.pnlibrary.core.link.remote.DataHandler;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;
import org.pnfolder.pnlibrary.core.menu.style.MenuPermissions;

/**
 * Answers the questions another server asks about this one's library.
 *
 * <p>It only reads the facts and carries out the actions of {@link MenuData}; it never draws anything. The server of
 * the viewer builds the menu from the answers.
 *
 * <p>Actions that change something, and commands, are allowed only when this server itself knows the viewer and says
 * they hold the permission: what the asking server writes in the request is never taken as a right.
 */
public final class MenuDataServer implements DataHandler, AutoCloseable {
    private static final Map<String, String> REQUIRED = required();

    private final MenuData data;
    private final Executor executor;
    private final BiPredicate<String, String> holds;

    /**
     * @param data the facts of this server
     * @param executor where the questions are answered, away from the thread that received them
     * @param holds whether the player of that name, as this server sees them, has the permission; false for a player
     * this server does not know
     */
    public MenuDataServer(@NotNull MenuData data, @NotNull Executor executor, @NotNull BiPredicate<String, String> holds) {
        this.data = Objects.requireNonNull(data, "data");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.holds = Objects.requireNonNull(holds, "holds");
    }

    @Override
    public void close() {
        if (executor instanceof ExecutorService) {
            ((ExecutorService) executor).shutdown();
        }
    }

    @Override
    public void handle(@NotNull DataCall call, @NotNull BiConsumer<Boolean, String> reply) {
        executor.execute(() -> {
            try {
                answer(call, reply);
            } catch (Exception error) {
                reply.accept(false, error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName());
            }
        });
    }

    private void answer(DataCall call, BiConsumer<Boolean, String> reply) {
        UpdateActor actor = new UpdateActor(UpdateActor.Kind.INTEGRATION, "remote:" + call.getOrigin(),
            (call.getViewer().isEmpty() ? "игрок" : call.getViewer()) + " (" + call.getOrigin() + ")");
        MenuQuestion ask = new MenuQuestion(call, arguments(call), actor, reply);
        String needed = REQUIRED.get(call.getMethod());
        if (needed != null && !allowed(call.getViewer(), needed)) {
            reply.accept(false, "Не хватает права: " + needed);
            return;
        }
        if (UpdateQuestions.answer(data, ask) || SystemQuestions.answer(data, ask)
            || EventQuestions.answer(data, ask) || NetworkQuestions.answer(data, ask, holds)) {
            return;
        }
        reply.accept(false, "Неизвестный запрос: " + call.getMethod());
    }

    private static JsonObject arguments(DataCall call) {
        try {
            return new JsonParser().parse(call.getArgs()).getAsJsonObject();
        } catch (RuntimeException unreadable) {
            return new JsonObject();
        }
    }

    private boolean allowed(String viewer, String permission) {
        return holds.test(viewer, permission) || holds.test(viewer, MenuPermissions.ADMIN);
    }

    private static Map<String, String> required() {
        Map<String, String> needed = new HashMap<>();
        needed.put(MenuCalls.CHECK_ALL, MenuPermissions.UPDATES_DOWNLOAD);
        needed.put(MenuCalls.CHECK_ONE, MenuPermissions.UPDATES_DOWNLOAD);
        needed.put(MenuCalls.SET_CHANNEL, MenuConstants.SETTINGS);
        needed.put(MenuCalls.SET_HELD, MenuConstants.SETTINGS);
        needed.put(MenuCalls.ROLLBACK, MenuConstants.ROLLBACK);
        needed.put(MenuCalls.RESTORE_DATABASE, MenuConstants.ROLLBACK);
        needed.put(MenuCalls.EXTRACT_BACKUP, MenuConstants.ROLLBACK);
        needed.put(MenuCalls.RUN_TASK, MenuConstants.TASKS);
        needed.put(MenuCalls.CANCEL_TASK, MenuConstants.TASKS);
        needed.put(MenuCalls.MODULE_ACTION, MenuPermissions.MODULES);
        needed.put(MenuCalls.SET_TRACING, MenuConstants.BUS);
        needed.put(MenuCalls.CLEAR_TRACES, MenuConstants.BUS);
        needed.put(MenuCalls.EDIT_GROUP, MenuConstants.GROUPS_PERMISSION);
        needed.put(MenuCalls.CHANGE_SETTING, MenuPermissions.LIBRARY_SETTINGS);
        return needed;
    }
}
