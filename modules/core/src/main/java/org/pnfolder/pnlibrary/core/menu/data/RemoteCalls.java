package org.pnfolder.pnlibrary.core.menu.data;

import com.google.gson.JsonObject;
import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.pnfolder.pnlibrary.core.link.remote.DataClient;
import org.pnfolder.pnlibrary.core.link.remote.DataException;

/**
 * The way questions reach another server: asked over the link, waited for, and for reads remembered for a few seconds,
 * because one screen asks for the same facts several times.
 */
abstract class RemoteCalls implements MenuData {
    private static final long WAIT_SECONDS = 25L;
    private static final long REMEMBER_NANOS = 3_000_000_000L;

    private final String target;
    private final DataClient client;
    private final ConcurrentHashMap<String, Remembered> remembered = new ConcurrentHashMap<>();

    RemoteCalls(String target, DataClient client) {
        this.target = target;
        this.client = client;
    }

    /** A remote response with the time it was received. */
    private static final class Remembered {
        final long at;
        final String json;

        Remembered(long at, String json) {
            this.at = at;
            this.json = json;
        }
    }

    /** Builds the arguments of a call from name and value pairs; a null value is left out. */
    static JsonObject args(Object... pairs) {
        JsonObject json = new JsonObject();
        for (int index = 0; index < pairs.length; index += 2) {
            String name = (String) pairs[index];
            Object value = pairs[index + 1];
            if (value instanceof String) {
                json.addProperty(name, (String) value);
            } else if (value instanceof Number) {
                json.addProperty(name, (Number) value);
            } else if (value instanceof Boolean) {
                json.addProperty(name, (Boolean) value);
            } else if (value != null) {
                json.addProperty(name, value.toString());
            }
        }
        return json;
    }

    <T> CompletionStage<T> later(final Supplier<T> work) {
        final CompletableFuture<T> result = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            try {
                result.complete(work.get());
            } catch (Throwable error) {
                result.completeExceptionally(error);
            }
        }, "pnlibrary-remote-menu");
        thread.setDaemon(true);
        thread.start();
        return result;
    }

    <T> T ask(String method, Type type) {
        return ask(method, type, new JsonObject(), "");
    }

    <T> T ask(String method, Type type, JsonObject args) {
        return ask(method, type, args, "");
    }

    <T> T ask(String method, Type type, JsonObject args, String viewer) {
        boolean reads = MenuCalls.READS.contains(method);
        String key = method + " " + args;
        long now = System.nanoTime();
        Remembered known = remembered.get(key);
        String json;
        if (reads && known != null && now - known.at < REMEMBER_NANOS) {
            json = known.json;
        } else {
            json = await(method, args, viewer);
            if (reads) {
                remembered.put(key, new Remembered(now, json));
            } else {
                remembered.clear();
            }
        }
        return MenuWire.gson().fromJson(json, type);
    }

    private String await(String method, JsonObject args, String viewer) {
        try {
            return client.call(target, viewer, method, args.toString()).get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof DataException) {
                throw (DataException) cause;
            }
            throw new DataException(cause != null && cause.getMessage() != null ? cause.getMessage() : error.getClass().getSimpleName());
        } catch (TimeoutException error) {
            throw new DataException("Сервер не ответил.");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new DataException("Сервер не ответил.");
        }
    }
}
