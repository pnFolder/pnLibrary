package ru.privatenull.pnlibrary.console;

/** Destination for rendered console lines. */
@FunctionalInterface
public interface ConsoleSink {
    /** Sends one line.
     * @param line one fully rendered console line */
    void send(String line);
}
