package org.pnfolder.pnlibrary.internal.faststats;

import dev.faststats.ErrorTracker;
import dev.faststats.data.Metric;
import java.util.concurrent.Callable;

/** Thin static access to the FastStats metric and error-tracker factories used by pnLibrary. */
public final class FastStatsBridge {
    private FastStatsBridge() { }

    /**
     * Creates a text metric whose value is read on each submission.
     *
     * @param id metric identifier
     * @param value supplier of the current value
     * @return the metric
     */
    public static Metric<?> string(String id, Callable<String> value) { return Metric.string(id, value); }

    /**
     * Creates a numeric metric whose value is read on each submission.
     *
     * @param id metric identifier
     * @param value supplier of the current value
     * @return the metric
     */
    public static Metric<?> number(String id, Callable<Number> value) { return Metric.number(id, value); }

    /**
     * Creates a metric of named numbers whose values are read on each submission.
     *
     * @param id metric identifier
     * @param value supplier of the current values
     * @return the metric
     */
    public static Metric<?> numberMap(String id, Callable<java.util.Map<String, ? extends Number>> value) { return Metric.numberMap(id, value); }

    /**
     * Creates a metric of JSON objects whose value is read on each submission.
     *
     * @param id metric identifier
     * @param value supplier of the current value
     * @return the metric
     */
    public static Metric<?> object(String id, Callable<com.google.gson.JsonObject> value) { return Metric.object(id, value); }

    /**
     * Returns the context-aware error tracker that FastStats reports errors through.
     *
     * @return the shared error tracker
     */
    public static ErrorTracker errorTracker() { return ErrorTracker.contextAware(); }
}
