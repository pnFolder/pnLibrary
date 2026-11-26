package org.pnfolder.pnlibrary.updates.service;

import org.pnfolder.pnlibrary.updates.service.registration.UpdateRegistrationStore;
import org.pnfolder.pnlibrary.updates.service.registration.RegisteredUpdate;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.api.updates.UpdateSettings;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.common.time.DisplayZone;
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging;
import org.pnfolder.pnlibrary.updates.engine.FreezeStore;
import org.pnfolder.pnlibrary.updates.install.UpdateGraphResolver;
import org.pnfolder.pnlibrary.updates.policy.UpdatePolicyOverrides;

/** The choices an administrator makes per plugin: channel, automatic download, Java build and holding updates back. */
final class SettingsChanges implements UpdateSettings {
    private static final DateTimeFormatter HOLD_FORMAT = DateTimeFormatter.ofPattern("d MMM HH:mm", new Locale("ru"));

    /** One change to a registration; returns what to write to the history, or null when nothing changed. */
    private interface Change {
        @Nullable
        String apply(RegisteredUpdate registration);
    }

    private final Object owner;
    private final PlatformLogging logging;
    private final UpdateRegistrationStore registrations;
    private final UpdatePolicyOverrides overrides;
    private final UpdateGraphResolver graphResolver;
    private final FreezeStore freezes;
    private final UpdateOrchestratorRuntime runtime;
    private final BooleanSupplier closed;

    SettingsChanges(Object owner, PlatformLogging logging, UpdateRegistrationStore registrations,
                    UpdatePolicyOverrides overrides, UpdateGraphResolver graphResolver, FreezeStore freezes,
                    UpdateOrchestratorRuntime runtime, BooleanSupplier closed) {
        this.owner = owner;
        this.logging = logging;
        this.registrations = registrations;
        this.overrides = overrides;
        this.graphResolver = graphResolver;
        this.freezes = freezes;
        this.runtime = runtime;
        this.closed = closed;
    }

    @NotNull
    @Override
    public CompletionStage<UpdateSnapshot> channel(@NotNull String product, @Nullable UpdateChannel channel,
                                                   @NotNull UpdateActor actor) {
        return change(product, actor, true, registration -> {
            String before = name(registration.getSnapshot().getChannel());
            if (Objects.equals(overrides.channel(registration.getProduct()), channel)) {
                return null;
            }
            overrides.setChannel(registration.getProduct(), channel);
            if (channel == null) {
                return "выбор канала снят: " + before + " → " + name(graphResolver.channelFor(registration))
                        + ", канал определяет плагин";
            }
            return "канал: " + before + " → " + name(channel);
        });
    }

    @NotNull
    @Override
    public CompletionStage<UpdateSnapshot> javaBuild(@NotNull String product, @Nullable Integer java,
                                                     @NotNull UpdateActor actor) {
        return change(product, actor, false, registration -> {
            List<Integer> available = registration.getSnapshot().getJavaBuilds();
            if (java != null && !available.isEmpty() && !available.contains(java)) {
                throw new IllegalArgumentException("Для " + registration.getProduct() + " нет сборки под Java " + java
                        + ". Есть: " + joined(available));
            }
            Integer before = overrides.javaBuild(registration.getProduct());
            if (Objects.equals(before, java)) {
                return null;
            }
            overrides.setJavaBuild(registration.getProduct(), java);
            if (java == null) {
                return "сборка Java: выбор снят, подбирается подходящая";
            }
            return "сборка Java: " + (before == null ? "подбор" : before) + " → " + java;
        });
    }

    @NotNull
    @Override
    public CompletionStage<UpdateSnapshot> hold(@NotNull String product, @NotNull Duration duration,
                                                @NotNull UpdateActor actor) {
        return change(product, actor, true, registration -> {
            Instant until = freezes.freeze(registration.getDescriptor().getId(), duration);
            return "обновления удержаны до " + HOLD_FORMAT.format(until.atZone(DisplayZone.getZone()));
        });
    }

    @NotNull
    @Override
    public CompletionStage<UpdateSnapshot> release(@NotNull String product, @NotNull UpdateActor actor) {
        return change(product, actor, true, registration ->
                freezes.clear(registration.getDescriptor().getId()) ? "удержание обновлений снято" : null);
    }

    @Nullable
    @Override
    public Instant heldUntil(@NotNull String product) {
        RegisteredUpdate registration = registrations.find(product);
        return registration == null ? null : freezes.active().get(registration.getDescriptor().getId());
    }

    /** Applies one administrator setting, records it with its author and refreshes what readers see. */
    private CompletionStage<UpdateSnapshot> change(String product, UpdateActor actor, boolean recheck, Change change) {
        CompletableFuture<UpdateSnapshot> result = new CompletableFuture<>();
        try {
            if (closed.getAsBoolean()) {
                throw new IllegalStateException("Update service is closed");
            }
            RegisteredUpdate registration = registrations.find(product);
            if (registration == null) {
                throw new IllegalArgumentException("Плагин " + product + " не зарегистрирован в pnLibrary");
            }
            String description = change.apply(registration);
            registration.refreshPolicy();
            if (description != null) {
                runtime.recordSettingsChange(registration, description, actor);
                // The setting is already persisted, so a failed recheck is reported but does not fail the change.
                if (recheck) {
                    Failures.runCatchingNonFatal(() -> runtime.getOrchestrator().checkNow(actor)).onFailure(error ->
                            logging.log(owner, LogLevel.ERROR, "Update recheck after a settings change failed", error));
                }
            }
            result.complete(registration.getSnapshot());
        } catch (Exception error) {
            result.completeExceptionally(error);
        }
        return result;
    }

    private static String name(UpdateChannel channel) {
        return channel.name().toLowerCase(Locale.ROOT);
    }

    private static String joined(List<Integer> values) {
        List<String> parts = new ArrayList<>();
        for (Integer value : values) {
            parts.add(String.valueOf(value));
        }
        return String.join(", ", parts);
    }
}
