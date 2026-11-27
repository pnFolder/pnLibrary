package org.pnfolder.pnlibrary.updates.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Locale;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateConfigurationBackup;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.api.version.ApiVersionRange;
import org.pnfolder.pnlibrary.api.version.PnLibraryApi;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;
import org.pnfolder.pnlibrary.updates.engine.net.ArtifactSpecification;
import org.pnfolder.pnlibrary.updates.engine.net.ArtifactVerifier;
import org.pnfolder.pnlibrary.updates.engine.transaction.TransactionArtifact;
import org.pnfolder.pnlibrary.updates.engine.transaction.journal.TransactionJournal;
import org.pnfolder.pnlibrary.updates.engine.transaction.TransactionResult;
import org.pnfolder.pnlibrary.updates.engine.transaction.TransactionState;
import org.pnfolder.pnlibrary.updates.engine.transaction.UpdateTransaction;
import org.pnfolder.pnlibrary.updates.history.configuration.ConfigurationSnapshots;
import org.pnfolder.pnlibrary.updates.history.events.UpdateEvent;
import org.pnfolder.pnlibrary.updates.history.events.UpdateEventChange;
import org.pnfolder.pnlibrary.updates.history.events.UpdateEventJournal;
import org.pnfolder.pnlibrary.updates.paths.LibraryPaths;

/**
 * Opt-in real platform acceptance. Only existing server software is copied; no production data/configs.
 * The synthetic release changes version metadata, not library bytecode. Catalog downloads and
 * resolver policy are covered separately; this exercises real classloading, commands, restart
 * reconciliation, the packaged offline installer and explicit configuration rollback.
 */
class NativeProxyUpdateTest {
    @Test
    @Tag("velocity")
    void velocityAppliesAndRollsBackItsOwnLibraryWithDurableConsoleAttribution() throws Exception {
        cycle(true, false);
    }

    @Test
    @Tag("bungee")
    void bungeeAppliesAndRollsBackItsOwnLibraryWithDurableConsoleAttribution() throws Exception {
        cycle(false, false);
    }

    @Test
    @Tag("paper")
    void paperAppliesAndRollsBackItsOwnLibraryWithDurableConsoleAttribution() throws Exception {
        cycle(false, true);
    }

    private void cycle(boolean velocity, boolean bukkit) throws Exception {
        String label = bukkit ? System.getProperty("pnlibrary.native.bukkitPlatform", "paper") : velocity ? "velocity" : "bungee";
        Path fixture = Paths.get(required("fixtures")).resolve(label + "-" + UUID.randomUUID());
        Path plugins = fixture.resolve("plugins");
        Files.createDirectories(plugins);
        Path data = plugins.resolve(velocity ? "pnlibrary" : "pnLibrary");
        Files.createDirectories(data);
        Path distribution = Paths.get(required("distribution"));
        String originalVersion = required("version");
        String targetVersion = "99.0.0-dev.1";
        String suffix = bukkit ? "bukkit-java8" : velocity ? "velocity-java17" : "bungeecord-java8";
        Path original = distribution.resolve("pnLibrary-" + originalVersion + "-" + suffix + ".jar");
        Path installed = plugins.resolve("pnLibrary.jar");
        Files.copy(original, installed);
        Path serverSource = Paths.get(required(bukkit ? "paperServerJar" : velocity ? "velocityServerJar" : "bungeeServerJar"));
        Files.copy(serverSource, fixture.resolve("server.jar"));
        int port;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = socket.getLocalPort();
        }
        if (bukkit) {
            configurePaper(fixture, serverSource, port);
        } else {
            configure(fixture, velocity, port);
        }
        Path metrics = plugins.resolve("bStats");
        Files.createDirectories(metrics);
        write(metrics.resolve("config.yml"), "enabled: false\n");
        Path faststats = plugins.resolve("faststats");
        Files.createDirectories(faststats);
        write(faststats.resolve("config.properties"), "configVersion=2\nenabled=false\n");
        Path configuration = data.resolve("updates.yml");
        String oldConfiguration = "updates:\n  enabled: false\n  library:\n    channel: stable\n    automatic-download: false\n# fixture configuration v1\n";
        write(configuration, oldConfiguration);
        Instant startedAt = Instant.now();

        String command = bukkit ? "pn updates" : "pn update";
        try (Server server = boot(fixture, velocity, "initial", bukkit)) {
            server.command(command + " list", "pnlibrary · " + originalVersion);
            server.command(command + " history", "История pnLibrary");
            server.command(command + " backups", "Резервные наборы pnLibrary");
        }
        Path release = fixture.resolve("release.jar");
        syntheticRelease(original, release, originalVersion, targetVersion);
        ProductId product = ProductId.of("pnlibrary");
        ApiVersionRange api = new ApiVersionRange(PnLibraryApi.VERSION, PnLibraryApi.VERSION);
        UUID planId = UUID.randomUUID();
        ConfigurationSnapshots configs = new ConfigurationSnapshots(new LibraryPaths(data).getConfigurations(), plugins);
        UUID configId = configs.capture(planId, Collections.singletonMap(product,
            new UpdateConfigurationBackup(data, Collections.singletonList("updates.yml"))));
        UpdateTransaction transactions = new UpdateTransaction(new LibraryPaths(data).getTransactions(),
            new ArtifactVerifier(128L * 1024 * 1024), plugins);
        TransactionResult saved = transactions.prepareForRestart(Collections.singletonList(new TransactionArtifact(
            new ArtifactSpecification(product, SemanticVersion.parse(targetVersion), api, "release.jar",
                Files.size(release), digest(release)),
            release, plugins.resolve("update/pnLibrary.jar"), installed, originalVersion, api, installed)),
            planId, PnLibraryApi.VERSION, configId);
        assertEquals(digest(original), digest(installed), "Staging must not replace a loaded/installed JAR");
        runUpdater(distribution, originalVersion, data, fixture.resolve("install.log"));
        assertEquals(digest(release), digest(installed));

        try (Server server = boot(fixture, velocity, "updated", bukkit)) {
            server.command(command + " list", "pnlibrary · " + targetVersion);
            await("Health confirmation", fixture.resolve("updated.log"),
                () -> TransactionJournal.load(saved.getJournal()).getState() == TransactionState.COMMITTED);
            server.command(command + " history", "применено после перезапуска");
            server.command(command + " backups", "Резервные наборы pnLibrary");
            String transactionId = saved.getJournal().getParent().getFileName().toString();
            server.command(command + " rollback " + transactionId + " configs confirm",
                "Набор " + transactionId + " подготовлен");
        }
        assertTrue(TransactionJournal.load(saved.getJournal()).getRestoreConfigurations());
        write(configuration, oldConfiguration.replace("v1", "v2"));
        runUpdater(distribution, originalVersion, data, fixture.resolve("rollback.log"));
        assertEquals(digest(original), digest(installed));
        assertEquals(oldConfiguration, read(configuration));

        try (Server server = boot(fixture, velocity, "restored", bukkit)) {
            server.command(command + " list", "pnlibrary · " + originalVersion);
            await("Rollback health confirmation", fixture.resolve("restored.log"),
                () -> TransactionJournal.load(saved.getJournal()).getRollbackHealthVerified());
            server.command(command + " history", "откат подтверждён после перезапуска");
        }
        List<UpdateEvent> events = new UpdateEventJournal(new LibraryPaths(data).getEvents()).read(1, 100, null);
        UpdateEvent applied = single(events, "HEALTH", planId);
        assertEquals(UpdateActor.Kind.SYSTEM, applied.getActor().getKind());
        assertEquals(Collections.singletonList(new UpdateEventChange("pnlibrary", originalVersion, targetVersion)),
            applied.getChanges());
        UpdateEvent requested = single(events, "ROLLBACK", planId);
        assertEquals(UpdateActor.Kind.CONSOLE, requested.getActor().getKind());
        assertFalse(requested.getActor().getId().trim().isEmpty());
        UpdateEvent restored = single(events, "HEALTH_ROLLBACK", planId);
        assertEquals(Collections.singletonList(new UpdateEventChange("pnlibrary", targetVersion, originalVersion)),
            restored.getChanges());
        assertFalse(applied.getOccurredAt().isBefore(startedAt));
        assertFalse(restored.getOccurredAt().isBefore(applied.getOccurredAt()));
        assertEquals(TransactionState.ROLLED_BACK, TransactionJournal.load(saved.getJournal()).getState());
        System.out.println("PASS real " + label + " install/restart/console rollback/config restore: " + fixture);
    }

    private static UpdateEvent single(List<UpdateEvent> events, String operation, UUID planId) {
        UpdateEvent found = null;
        for (UpdateEvent event : events) {
            if (event.getOperation().equals(operation) && event.getPlanId().equals(planId)) {
                if (found != null) {
                    throw new IllegalArgumentException("Collection contains more than one matching element.");
                }
                found = event;
            }
        }
        if (found == null) {
            throw new java.util.NoSuchElementException("Collection contains no element matching the predicate.");
        }
        return found;
    }

    private void configurePaper(Path fixture, Path server, int port) throws IOException {
        Path accepted = Paths.get(required("paperEulaFile"));
        if (!Pattern.compile("(?m)^eula=true\\s*$").matcher(read(accepted)).find()) {
            throw new IllegalArgumentException("An already accepted Minecraft EULA file must be explicitly supplied");
        }
        Files.copy(accepted, fixture.resolve("eula.txt"));
        // Copy software caches only; never worlds, player data, plugins or server configuration.
        for (String name : Arrays.asList("cache", "libraries", "versions")) {
            Path source = server.getParent().resolve(name);
            if (!Files.isDirectory(source)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(source)) {
                for (Path path : (Iterable<Path>) paths::iterator) {
                    if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar") && !Files.isSymbolicLink(path)) {
                        Path target = fixture.resolve(name).resolve(source.relativize(path).toString());
                        Files.createDirectories(target.getParent());
                        Files.copy(path, target);
                    }
                }
            }
        }
        write(fixture.resolve("server.properties"), String.join("\n",
            "server-ip=127.0.0.1",
            "server-port=" + port,
            "online-mode=true",
            "enable-query=false",
            "enable-rcon=false",
            "enable-status=false",
            "max-players=1",
            "level-name=verification",
            "level-type=minecraft:flat",
            "generator-settings={\"layers\":[{\"block\":\"minecraft:bedrock\",\"height\":1}],\"biome\":\"minecraft:plains\"}",
            "generate-structures=false",
            "view-distance=2",
            "simulation-distance=2",
            "spawn-chunk-radius=0"));
    }

    private void syntheticRelease(Path source, Path target, String before, String after) throws IOException {
        Set<String> descriptors = new HashSet<>(Arrays.asList("velocity-plugin.json", "bungee.yml", "plugin.yml",
            "META-INF/pnlibrary/component.json"));
        int replaced = 0;
        try (ZipFile zip = new ZipFile(source.toFile()); ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(target))) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                output.putNextEntry(new ZipEntry(entry.getName()));
                try (InputStream input = zip.getInputStream(entry)) {
                    if (descriptors.contains(entry.getName())) {
                        String text = new String(readAll(input), StandardCharsets.UTF_8);
                        if (text.contains(before)) {
                            replaced++;
                        }
                        output.write(text.replace(before, after).getBytes(StandardCharsets.UTF_8));
                    } else {
                        byte[] buffer = new byte[8192];
                        for (int count = input.read(buffer); count >= 0; count = input.read(buffer)) {
                            output.write(buffer, 0, count);
                        }
                    }
                }
                output.closeEntry();
            }
        }
        assertTrue(replaced >= 2, "Expected native and component version descriptors");
    }

    private void configure(Path fixture, boolean velocity, int port) throws IOException {
        String content = velocity ? String.join("\n",
            "config-version = \"2.8\"",
            "bind = \"127.0.0.1:" + port + "\"",
            "online-mode = true",
            "player-info-forwarding-mode = \"NONE\"",
            "forwarding-secret-file = \"forwarding.secret\"",
            "[servers]",
            "fixture = \"127.0.0.1:1\"",
            "try = [\"fixture\"]",
            "[forced-hosts]",
            "\"lobby.example.com\" = [\"fixture\"]",
            "\"factions.example.com\" = [\"fixture\"]",
            "\"minigames.example.com\" = [\"fixture\"]") : String.join("\n",
            "online_mode: true",
            "servers:",
            "  fixture:",
            "    address: 127.0.0.1:1",
            "    restricted: true",
            "listeners:",
            "- host: 127.0.0.1:" + port,
            "  query_enabled: false",
            "  priorities: [fixture]",
            "  force_default_server: true",
            "  forced_hosts: {}",
            "disabled_commands: []");
        write(fixture.resolve(velocity ? "velocity.toml" : "config.yml"), content);
        if (!velocity) {
            write(fixture.resolve("modules.yml"), "version: 2\nmodules: []\n");
        }
    }

    private Server boot(Path fixture, boolean velocity, String phase, boolean bukkit) throws Exception {
        Path log = fixture.resolve(phase + ".log");
        List<String> arguments = new ArrayList<>(Arrays.asList(
            System.getProperty("pnlibrary.native.javaExecutable", java()), "-Xms128M", "-Xmx1G", "-Dfile.encoding=UTF-8",
            "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-Dterminal.jline=false",
            "-Dterminal.ansi=false", "-jar", "server.jar"));
        if (bukkit) {
            arguments.add("--nogui");
        }
        Process process = new ProcessBuilder(arguments).directory(fixture.toFile())
            .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        Server server = new Server(process, log, bukkit ? "stop" : velocity ? "shutdown" : "end");
        try {
            String ready = velocity || bukkit ? "Done (" : "Listening on ";
            await("Platform readiness", log, () -> server.output().contains(ready));
            return server;
        } catch (Throwable error) {
            server.close();
            throw error;
        }
    }

    private final class Server implements AutoCloseable {
        private final Process process;
        private final Path log;
        private final String stop;

        Server(Process process, Path log, String stop) {
            this.process = process;
            this.log = log;
            this.stop = stop;
        }

        String output() throws IOException {
            return Files.exists(log) ? new String(Files.readAllBytes(log), StandardCharsets.UTF_8) : "";
        }

        void command(String command, String expected) throws Exception {
            int offset = output().length();
            process.getOutputStream().write((command + "\n").getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();
            await("Command " + command + " -> " + expected, log, () -> output().substring(Math.min(offset, output().length())).contains(expected));
        }

        @Override
        public void close() {
            if (!process.isAlive()) {
                return;
            }
            try {
                process.getOutputStream().write((stop + "\n").getBytes(StandardCharsets.UTF_8));
                process.getOutputStream().flush();
                assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Proxy failed to stop: " + log);
                assertEquals(0, process.exitValue(), outputQuietly());
            } catch (IOException | InterruptedException error) {
                throw new IllegalStateException(error);
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly();
                    try {
                        process.waitFor();
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        private String outputQuietly() {
            try {
                return output();
            } catch (IOException error) {
                return error.toString();
            }
        }
    }

    private void runUpdater(Path distribution, String version, Path data, Path log) throws Exception {
        Process process = new ProcessBuilder(java(), "-jar", distribution.resolve("pnLibrary-updater-" + version + ".jar").toString(),
            "--server-stopped", data.toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Offline updater timed out: " + log);
            assertEquals(0, process.exitValue(), read(log));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor();
            }
        }
    }

    /** A condition that is checked until it holds. */
    private interface Condition {
        boolean test() throws Exception;
    }

    private void await(String label, Path log, Condition condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
        while (System.nanoTime() < deadline) {
            if (condition.test()) {
                return;
            }
            Thread.sleep(100);
        }
        fail(label + " timed out; " + log + "\n" + (Files.exists(log) ? read(log) : "no log"));
    }

    private static String required(String name) {
        String value = System.getProperty("pnlibrary.native." + name);
        if (value == null) {
            throw new IllegalArgumentException("pnlibrary.native." + name + " is not set");
        }
        return value;
    }

    private static String java() {
        return Paths.get(System.getProperty("java.home"), "bin",
            System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
    }

    private static String digest(Path path) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536];
            for (int count = input.read(buffer); count >= 0; count = input.read(buffer)) {
                sha.update(buffer, 0, count);
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte value : sha.digest()) {
            hex.append(String.format(Locale.ROOT, "%02x", value));
        }
        return hex.toString();
    }

    private static void write(Path file, String text) throws IOException {
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int count = input.read(buffer); count >= 0; count = input.read(buffer)) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
