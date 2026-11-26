package org.pnfolder.pnlibrary.core.link.remote;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.updates.release.ProductChange;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.core.exceptions.Throwables;

/**
 * Carries out the update controls another server asked for, on this server.
 *
 * <p>It does only what {@link RemoteAction} lists, through the same update service the commands use, and it records
 * who asked in the update history. A request id is accepted once, so a recorded request cannot be played again.
 */
public final class RemoteExecutor {
    private static final Duration REMEMBER = Duration.ofMinutes(10);

    private final PnLibrary library;
    private final Supplier<Instant> clock;
    private final ConcurrentHashMap<UUID, Instant> seen = new ConcurrentHashMap<UUID, Instant>();

    public RemoteExecutor(@NotNull PnLibrary library) {
        this(library, Instant::now);
    }

    public RemoteExecutor(@NotNull PnLibrary library, @NotNull Supplier<Instant> clock) {
        this.library = library;
        this.clock = clock;
    }

    /** Runs {@code request} and passes the answer to {@code reply}, possibly from another thread. */
    public void run(@NotNull RemoteRequest request, @NotNull Consumer<RemoteResult> reply) {
        Instant now = clock.get();
        seen.values().removeIf(at -> Duration.between(at, now).compareTo(REMEMBER) > 0);
        if (seen.putIfAbsent(request.getId(), now) != null) {
            reply.accept(result(request, false, "Этот запрос уже выполнялся."));
            return;
        }
        String origin = request.getOrigin().isEmpty() ? "сервер" : request.getOrigin();
        UpdateActor actor = new UpdateActor(UpdateActor.Kind.INTEGRATION, "remote:" + request.getOrigin(),
            request.getActor() + " (" + origin + ")");
        String product = request.getProduct() == null ? "" : request.getProduct();
        try {
            dispatch(request, product, actor, reply);
        } catch (Exception error) {
            library.getLogging().logger(library.getOwner(), "link")
                .error("Remote action " + request.getAction() + " from " + request.getOrigin() + " failed", error);
            reply.accept(result(request, false, Throwables.rootMessage(error)));
        }
    }

    private void dispatch(RemoteRequest request, String product, UpdateActor actor, Consumer<RemoteResult> reply) {
        switch (request.getAction()) {
            case CHECK:
                check(request, actor, reply);
                break;
            case UPDATE:
                update(request, product, actor, reply);
                break;
            default:
                automatic(request, product, actor, reply);
                break;
        }
    }

    private void check(RemoteRequest request, UpdateActor actor, Consumer<RemoteResult> reply) {
        library.getUpdates().getPlans().check(actor).whenComplete((plan, error) -> {
            if (error != null) {
                reply.accept(result(request, false, "Проверка не удалась: " + Throwables.rootMessage(error)));
                return;
            }
            int count = plan.getPlan() == null ? 0 : plan.getPlan().getChanges().size();
            reply.accept(result(request, true, count == 0 ? "Всё актуально." : "Найдено обновлений: " + count + "."));
        });
    }

    private void automatic(RemoteRequest request, String product, UpdateActor actor, Consumer<RemoteResult> reply) {
        boolean enabled = request.getAction() == RemoteAction.AUTO_ON;
        library.getUpdates().getSettings().automaticDownload(product, enabled, actor).whenComplete((ignored, error) -> {
            if (error != null) {
                reply.accept(result(request, false, "Не удалось изменить: " + Throwables.rootMessage(error)));
            } else {
                reply.accept(result(request, true,
                    "Автозагрузка " + product + ": " + (enabled ? "включена" : "выключена") + "."));
            }
        });
    }

    private void update(RemoteRequest request, String product, UpdateActor actor, Consumer<RemoteResult> reply) {
        if (library.getUpdates().getProducts().get(product) == null) {
            reply.accept(result(request, false, "Плагин " + product + " не подключён к обновлениям pnLibrary."));
            return;
        }
        library.getUpdates().getPlans().check(actor).whenComplete((plan, error) -> {
            if (error != null) {
                reply.accept(result(request, false, "Проверка не удалась: " + Throwables.rootMessage(error)));
                return;
            }
            ProductChange change = changeOf(plan, product);
            if (plan.getState() != UpdateState.UPDATE_AVAILABLE || change == null) {
                reply.accept(result(request, true, "Для " + product + " нет обновления."));
                return;
            }
            stage(request, product, actor, plan, change, reply);
        });
    }

    private void stage(RemoteRequest request, String product, UpdateActor actor, UpdatePlanSnapshot plan,
                       ProductChange change, Consumer<RemoteResult> reply) {
        CompletionStage<UpdatePlanSnapshot> staged = library.getUpdates().getPlans().stage(plan.getId(), actor);
        staged.whenComplete((ignored, error) -> {
            if (error != null) {
                reply.accept(result(request, false, "Не удалось подготовить: " + Throwables.rootMessage(error)));
            } else {
                reply.accept(result(request, true, "Подготовлено: " + product + " v" + change.getFrom() + " » v"
                    + change.getTo() + ". Нужен перезапуск."));
            }
        });
    }

    private static ProductChange changeOf(UpdatePlanSnapshot plan, String product) {
        if (plan.getPlan() == null) {
            return null;
        }
        for (ProductChange change : plan.getPlan().getChanges()) {
            if (change.getProduct().getValue().equalsIgnoreCase(product)) {
                return change;
            }
        }
        return null;
    }

    private RemoteResult result(RemoteRequest request, boolean ok, String text) {
        return new RemoteResult(request.getId(), request.getOrigin(), ok, text, clock.get());
    }
}
