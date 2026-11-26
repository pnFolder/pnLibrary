package org.pnfolder.pnlibrary.core.menu.data.question;

import org.pnfolder.pnlibrary.core.menu.data.remote.MenuWire;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.core.exceptions.Throwables;
import org.pnfolder.pnlibrary.core.link.remote.DataCall;

/** One question another server asked, with the way to answer it. */
public final class MenuQuestion {
    final DataCall call;
    final JsonObject args;
    final UpdateActor actor;
    private final BiConsumer<Boolean, String> reply;

    public MenuQuestion(DataCall call, JsonObject args, UpdateActor actor, BiConsumer<Boolean, String> reply) {
        this.call = call;
        this.args = args;
        this.actor = actor;
        this.reply = reply;
    }

    String method() {
        return call.getMethod();
    }

    String text(String name) {
        JsonElement value = args.get(name);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    String textOrEmpty(String name) {
        String value = text(name);
        return value == null ? "" : value;
    }

    int number(String name, int fallback) {
        JsonElement value = args.get(name);
        return value == null ? fallback : value.getAsInt();
    }

    boolean flag(String name) {
        JsonElement value = args.get(name);
        return value != null && value.getAsBoolean();
    }

    Integer optionalNumber(String name) {
        JsonElement value = args.get(name);
        return value == null || value.isJsonNull() ? null : value.getAsInt();
    }

    Long optionalLong(String name) {
        JsonElement value = args.get(name);
        return value == null || value.isJsonNull() ? null : value.getAsLong();
    }

    void give(Object value) {
        reply.accept(true, MenuWire.gson().toJson(value));
    }

    void fail(String message) {
        reply.accept(false, message);
    }

    /** Answers with what the stage produces, or with its failure. */
    void finish(CompletionStage<?> stage) {
        stage.whenComplete((value, error) -> {
            if (error != null) {
                reply.accept(false, Throwables.rootMessage(error));
            } else {
                reply.accept(true, MenuWire.gson().toJson(value));
            }
        });
    }
}
