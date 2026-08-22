package ru.privatenull.pnlibrary.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PluginRuntimeTest {

    @Test
    void readsRepositoryFromPluginWebsite() {
        assertEquals("Dy6HiLa/pnCases",
                PluginRuntime.repository("pnCases", "https://github.com/Dy6HiLa/pnCases"));
        assertEquals("owner/project",
                PluginRuntime.repository("ignored", "https://github.com/owner/project/releases/latest"));
    }

    @Test
    void fallsBackToSharedGithubOwner() {
        assertEquals("Dy6HiLa/pnChat", PluginRuntime.repository("pnChat", null));
    }

    @Test
    void choosesDeclaredAdminPermission() {
        assertEquals("pnrelic.admin", PluginRuntime.notificationPermission(
                "pnRelics", List.of("pnrelic.use", "pnrelic.admin")));
    }

    @Test
    void prefersConventionalAdminPermission() {
        assertEquals("pnsouls.admin", PluginRuntime.notificationPermission(
                "pnSouls", List.of("pnsouls.update", "pnsouls.admin")));
    }
}
