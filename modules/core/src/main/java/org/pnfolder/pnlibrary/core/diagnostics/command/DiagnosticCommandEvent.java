package org.pnfolder.pnlibrary.core.diagnostics.command;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticReport;

/** State emitted while executing a diagnostic command. */
public abstract class DiagnosticCommandEvent {
    private DiagnosticCommandEvent() {
    }

    /** Command arguments could not be parsed. */
    public static final class InvalidUsage extends DiagnosticCommandEvent {
        /** The only instance. */
        public static final InvalidUsage INSTANCE = new InvalidUsage();

        private InvalidUsage() {
        }

        @Override
        public String toString() {
            return "InvalidUsage";
        }
    }

    /** The requester must wait before another report. */
    public static final class CoolingDown extends DiagnosticCommandEvent {
        private final long seconds;

        /** Creates the event for {@code seconds} whole seconds remaining in the cooldown. */
        public CoolingDown(long seconds) {
            this.seconds = seconds;
        }

        /** Whole seconds remaining in the cooldown. */
        public long getSeconds() {
            return seconds;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof CoolingDown && ((CoolingDown) other).seconds == seconds;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(seconds);
        }

        @Override
        public String toString() {
            return "CoolingDown(seconds=" + seconds + ")";
        }
    }

    /** Report collection has started. */
    public static final class Started extends DiagnosticCommandEvent {
        private final String target;

        /** Creates the event for the normalized plugin {@code target} or {@code all}. */
        public Started(@NotNull String target) {
            this.target = target;
        }

        /** The normalized plugin target or {@code all}. */
        @NotNull
        public String getTarget() {
            return target;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Started && ((Started) other).target.equals(target);
        }

        @Override
        public int hashCode() {
            return target.hashCode();
        }

        @Override
        public String toString() {
            return "Started(target=" + target + ")";
        }
    }

    /** Report collection completed successfully. */
    public static final class Completed extends DiagnosticCommandEvent {
        private final DiagnosticReport report;

        /** Creates the event for the generated local or uploaded {@code report}. */
        public Completed(@NotNull DiagnosticReport report) {
            this.report = report;
        }

        /** The generated local or uploaded diagnostic report. */
        @NotNull
        public DiagnosticReport getReport() {
            return report;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Completed && Objects.equals(((Completed) other).report, report);
        }

        @Override
        public int hashCode() {
            return report.hashCode();
        }

        @Override
        public String toString() {
            return "Completed(report=" + report + ")";
        }
    }

    /** Report collection failed. */
    public static final class Failed extends DiagnosticCommandEvent {
        private final String message;

        /** Creates the event with a sanitized {@code message} safe to show to the command sender. */
        public Failed(@NotNull String message) {
            this.message = message;
        }

        /** Sanitized detail safe to display to the command sender. */
        @NotNull
        public String getMessage() {
            return message;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Failed && ((Failed) other).message.equals(message);
        }

        @Override
        public int hashCode() {
            return message.hashCode();
        }

        @Override
        public String toString() {
            return "Failed(message=" + message + ")";
        }
    }
}
