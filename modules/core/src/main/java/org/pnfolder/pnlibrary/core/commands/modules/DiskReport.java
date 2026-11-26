package org.pnfolder.pnlibrary.core.commands.modules;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** The disk of the {@code plugins} folder; fields are named as they are written on the link. */
public final class DiskReport {
    /** A report of nothing, for a server whose disk is not known yet. */
    public static final DiskReport EMPTY = new DiskReport(Collections.emptyList(), 0, -1, -1);

    private final List<DiskRow> rows;
    private final long registeredLibraries;
    private final long free;
    private final long capacity;

    /**
     * @param rows one row per plugin
     * @param registeredLibraries size of the jars of libraries that plugins and modules registered through pnLibrary
     * @param free free space on the disk, or -1 when unknown
     * @param capacity size of the disk, or -1 when unknown
     */
    public DiskReport(@NotNull List<DiskRow> rows, long registeredLibraries, long free, long capacity) {
        this.rows = Objects.requireNonNull(rows, "rows");
        this.registeredLibraries = registeredLibraries;
        this.free = free;
        this.capacity = capacity;
    }

    @NotNull
    public List<DiskRow> getRows() {
        return rows;
    }

    public long getRegisteredLibraries() {
        return registeredLibraries;
    }

    public long getFree() {
        return free;
    }

    public long getCapacity() {
        return capacity;
    }

    public long getTotal() {
        long total = 0;
        for (DiskRow row : rows) {
            total += row.getTotal();
        }
        return total;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DiskReport)) {
            return false;
        }
        DiskReport that = (DiskReport) other;
        return registeredLibraries == that.registeredLibraries && free == that.free && capacity == that.capacity
                && rows.equals(that.rows);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rows, registeredLibraries, free, capacity);
    }

    @Override
    public String toString() {
        return "DiskReport(rows=" + rows + ", registeredLibraries=" + registeredLibraries + ", free=" + free
                + ", capacity=" + capacity + ")";
    }
}
