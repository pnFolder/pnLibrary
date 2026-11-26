package org.pnfolder.pnlibrary.core.runtime.details;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration;
import org.pnfolder.pnlibrary.api.updates.release.ReleaseSummary;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;

/** Summarizes update registrations for the diagnostics report. */
public final class RuntimeUpdateSummary {
    private RuntimeUpdateSummary() {
    }

    public static @NotNull Map<String, Object> of(@NotNull List<UpdateRegistration> registrations) {
        Tally tally = new Tally();
        for (UpdateRegistration registration : registrations) {
            tally.add(registration.getSnapshot());
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("count", registrations.size());
        summary.put("byChannel", tally.byChannel);
        summary.put("byState", tally.byState);
        summary.put("availableCount", tally.available);
        summary.put("javaIncompatibleCount", tally.javaIncompatible);
        summary.put("releaseCountByProduct", tally.releaseCountByProduct);
        summary.put("releaseCountByChannel", tally.releaseCountByChannel);
        addAges(summary, tally.latestByChannel);
        summary.put("messages", tally.messages);
        return summary;
    }

    private static void addAges(Map<String, Object> summary, Map<String, Instant> latestByChannel) {
        Map<String, Object> latest = new LinkedHashMap<>();
        Map<String, Object> ageDays = new LinkedHashMap<>();
        int stale = 0;
        Instant now = Instant.now();
        for (Map.Entry<String, Instant> entry : latestByChannel.entrySet()) {
            long days = Duration.between(entry.getValue(), now).toDays();
            latest.put(entry.getKey(), entry.getValue().toString());
            ageDays.put(entry.getKey(), Math.max(days, 0L));
            stale += days >= 30 ? 1 : 0;
        }
        summary.put("latestPublishedAtByChannel", latest);
        summary.put("releaseAgeDaysByChannel", ageDays);
        summary.put("staleChannelCount", stale);
    }

    /** The counts of one pass over the registrations. */
    private static final class Tally {
        private final Map<String, Instant> latestByChannel = new LinkedHashMap<>();
        private final Map<String, Integer> byChannel = new LinkedHashMap<>();
        private final Map<String, Integer> byState = new LinkedHashMap<>();
        private final Map<String, Integer> releaseCountByProduct = new LinkedHashMap<>();
        private final Map<String, Integer> releaseCountByChannel = new LinkedHashMap<>();
        private final Map<String, Integer> messages = new LinkedHashMap<>();
        private int available;
        private int javaIncompatible;

        private void add(UpdateSnapshot snapshot) {
            count(byChannel, snapshot.getChannel().name());
            count(byState, snapshot.getState().name());
            available += snapshot.getLatestVersion() != null ? 1 : 0;
            String message = snapshot.getMessage();
            boolean javaMessage = message != null && message.toLowerCase(Locale.ROOT).contains("java");
            javaIncompatible += snapshot.getState().name().toLowerCase(Locale.ROOT).contains("java") || javaMessage ? 1 : 0;
            releaseCountByProduct.put(snapshot.getProduct(), snapshot.getAvailableReleases().size());
            if (message != null) {
                count(messages, message.length() > 256 ? message.substring(0, 256) : message);
            }
            for (ReleaseSummary release : snapshot.getAvailableReleases()) {
                count(releaseCountByChannel, release.getChannel().name());
                keepLatest(release);
            }
        }

        private void keepLatest(ReleaseSummary release) {
            Instant published = release.getPublishedAt();
            String channel = release.getChannel().name();
            Instant known = latestByChannel.get(channel);
            if (published != null && (known == null || published.isAfter(known))) {
                latestByChannel.put(channel, published);
            }
        }

        private static void count(Map<String, Integer> counts, String key) {
            Integer current = counts.get(key);
            counts.put(key, current == null ? 1 : current + 1);
        }
    }
}
