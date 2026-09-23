package ru.privatenull.pnlibrary.console;

/** Destination for rendered console lines. */
@FunctionalInterface
public interface ConsoleSink {
    void send(String line);
}
