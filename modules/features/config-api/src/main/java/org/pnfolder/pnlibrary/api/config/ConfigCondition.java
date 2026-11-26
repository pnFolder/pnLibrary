package org.pnfolder.pnlibrary.api.config;

/** One typed configuration condition. */
@FunctionalInterface
public interface ConfigCondition<T> {
    /** Returns {@code true} when the tested value is valid. */
    boolean test(T value);
}
