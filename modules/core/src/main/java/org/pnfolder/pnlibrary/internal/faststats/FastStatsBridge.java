package org.pnfolder.pnlibrary.internal.faststats;

import dev.faststats.ErrorTracker;
import dev.faststats.data.Metric;
import java.util.concurrent.Callable;

/** Thin static access to the FastStats metric and error-tracker factories used by pnLibrary. */
public final class FastStatsBridge {
    private FastStatsBridge() { }

    public static Metric<?> string(String id, Callable<String> value) { return Metric.string(id, value); }

    public static Metric<?> number(String id, Callable<Number> value) { return Metric.number(id, value); }

    public static Metric<?> numberMap(String id, Callable<java.util.Map<String, ? extends Number>> value) { return Metric.numberMap(id, value); }

    public static Metric<?> object(String id, Callable<com.google.gson.JsonObject> value) { return Metric.object(id, value); }

    public static ErrorTracker errorTracker() { return ErrorTracker.contextAware(); }
}
