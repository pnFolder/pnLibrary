package org.pnfolder.pnlibrary.core.runtime.details;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;

/** Renders update registrations as maps for the diagnostics report. */
public final class RuntimeUpdateDetails {
    private RuntimeUpdateDetails() {
    }

    public static @NotNull Map<String, Object> registration(@NotNull UpdateRegistration value) {
        org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot snapshot = value.getSnapshot();
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        details.put("product", snapshot.getProduct());
        details.put("repository", value.getRepository());
        details.put("currentVersion", snapshot.getCurrentVersion());
        details.put("latestVersion", snapshot.getLatestVersion());
        details.put("channel", snapshot.getChannel().name());
        details.put("state", snapshot.getState().name());
        details.put("currentJava", snapshot.getCurrentJava());
        details.put("requiredJava", snapshot.getRequiredJava());
        details.put("automaticDownload", snapshot.getAutomaticDownload());
        details.put("releaseUrl", snapshot.getReleaseUrl());
        details.put("message", snapshot.getMessage());
        List<Object> releases = new ArrayList<Object>();
        for (org.pnfolder.pnlibrary.api.updates.release.ReleaseSummary release : snapshot.getAvailableReleases()) {
            Map<String, Object> entry = new LinkedHashMap<String, Object>();
            entry.put("version", release.getVersion());
            entry.put("channel", release.getChannel().name());
            entry.put("publishedAt", release.getPublishedAt() == null ? null : release.getPublishedAt().toString());
            releases.add(entry);
        }
        details.put("availableReleases", releases);
        return details;
    }

    public static @NotNull Map<String, Object> of(@NotNull UpdatePlanSnapshot snapshot) {
        List<Object> blockers = new ArrayList<Object>();
        for (Object blocker : snapshot.getBlockers()) {
            blockers.add(blocker.getClass().getSimpleName());
        }
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        details.put("id", snapshot.getId().toString());
        details.put("revision", snapshot.getRevision());
        details.put("state", snapshot.getState().name());
        details.put("message", snapshot.getMessage());
        details.put("blockers", blockers);
        details.put("targetApi", snapshot.getPlan() == null ? null : snapshot.getPlan().getTargetApi());
        details.put("changeCount", snapshot.getPlan() == null ? null : snapshot.getPlan().getChanges().size());
        details.put("selectedReleaseCount", snapshot.getPlan() == null ? null : snapshot.getPlan().getSelected().size());
        return details;
    }
}
