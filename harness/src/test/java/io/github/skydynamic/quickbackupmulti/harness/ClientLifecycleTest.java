package io.github.skydynamic.quickbackupmulti.harness;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The integrated server: boots to the main menu, opens a world, drives {@code /qb} commands through the
 * chat box the way a player would, and — since the client-side restore path turned out not to need any
 * GUI interaction ({@link io.github.skydynamic.quickbackupmulti.restore.ClientRestoreDelegate} runs
 * fully automatically once armed) — exercises a full client-driven restore too.
 *
 * <p>Commands are typed via {@link ClientInput}, which injects real keyboard events with
 * {@code java.awt.Robot}. That is the only in-band command channel a client has: unlike a dedicated
 * server, the game does not read commands from stdin.
 *
 * <p>The code under test is {@code common}, loaded identically by Fabric and NeoForge, so only Fabric
 * clients are tested — the NeoForge client has <em>loader</em> differences (different entrypoint wiring,
 * different restart mechanics) but the mod's own client logic is the same.
 */
class ClientLifecycleTest extends FunctionalTestBase {
    private static final String LEVEL_ID = "qbm-harness-test";

    /**
     * Boots the Fabric client to the main menu.
     *
     * <p>Reaching the menu means Mixin applied, the mod's client entrypoint ran, and the resource packs
     * and language files loaded without error. That is already a stronger version-drift signal than a
     * compile, because a mixin targeting a method that moved aborts the client with {@code required: true}.
     */
    @ParameterizedTest(name = "{0} / Fabric client — boots to menu")
    @MethodSource("clientMatrix")
    void clientBootsToMenu(String mc) {
        if (!config.displayAvailable()) {
            runner.skip(mc, "fabric", "client", "menu",
                "No display available (DISPLAY is unset and this is not Windows/macOS)");
        }
        runner.run(mc, "fabric", "client", "menu", () -> {
            try (ClientRun run = ClientRun.provision(config, mc, "menu",
                ModConfigFile.deterministic())) {
                run.bootToMenu();
                assertTrue(run.client().sawLine("QuickBackupMulti"),
                    "The mod's client entrypoint did not log anything, so it may not have run");
                run.quit();
            }
        });
    }

    /**
     * Creates a test world and verifies the integrated server starts with the mod loaded.
     *
     * <p>{@code --quickPlaySingleplayer} resumes an existing save; it does not create one. So a save is
     * generated once with a throwaway dedicated server (the world-generation path the server scenarios
     * already cover) and installed into the client's {@code saves/} rather than reimplementing "create a
     * new world" through the client's GUI.
     *
     * <p>The per-world backup store should appear under {@code <storagePath>/<levelId>}, not at the top
     * level: the client keeps one shared database and per-world blob directories, while a dedicated server
     * has both in one place. This is the integration point that would break if
     * {@code QuickbackupmultiReforged.setNewDataBase} stopped appending the level id on the client.
     */
    @ParameterizedTest(name = "{0} / Fabric client — creates world and initialises store")
    @MethodSource("clientMatrix")
    void clientCreatesWorldAndInitialisesStore(String mc) {
        if (!config.displayAvailable()) {
            runner.skip(mc, "fabric", "client", "world",
                "No display available (DISPLAY is unset and this is not Windows/macOS)");
        }
        runner.run(mc, "fabric", "client", "world", () -> {
            try (ServerRun seed = ServerRun.provision(config, mc, "fabric", "client-world-seed",
                ModConfigFile.deterministic())) {
                seed.boot();
                seed.stop();

                try (ClientRun run = ClientRun.provision(config, mc, "world",
                    ModConfigFile.deterministic())) {
                    run.installSave(seed.worldDir(), LEVEL_ID);
                    run.bootIntoWorld(LEVEL_ID);

                    // bootIntoWorld only waits for the integrated server to start, which is earlier than
                    // it finishing loading; a plain sawLine would race that, so block for it instead.
                    assertTrue(run.client().sawLine("Starting integrated minecraft server"),
                        "The integrated server never started");
                    run.client().awaitLine("Done (", Duration.ofMinutes(3));

                    // The store's database lives at the top level, but blobs go under a per-world subdirectory.
                    BackupStore store = run.store(LEVEL_ID);
                    assertTrue(store.exists(),
                        "The mod did not create its H2 database at the top-level storage path");

                    // Making one backup proves the blob directory is in the right place and the mod can
                    // write to it, and proves a real player-typed chat command reaches the mod's command
                    // dispatcher.
                    new ClientInput().sendCommand("qb make clientprobe");
                    run.client().awaitLine("Make Backup thread close", Duration.ofMinutes(5));

                    assertTrue(store.hasBackup("clientprobe"),
                        "The backup did not record a row in storage_info");
                    assertFalse(store.blobHashes().isEmpty(),
                        "The backup recorded no blobs, so the per-world blob directory may be in the wrong place");

                    run.quit();
                }
            }
        });
    }

    /**
     * Drives a full client-side restore: {@code /qb make}, then {@code /qb restore} + {@code /qb confirm}
     * through the chat box, and verifies the world was actually rebuilt rather than merely reporting
     * success.
     *
     * <p>Unlike the dedicated-server path, {@link io.github.skydynamic.quickbackupmulti.restore.ClientRestoreDelegate}
     * needs no further interaction once armed: {@code RestoreScreen} is a progress display, not a
     * confirmation gate, so sending the two chat commands is the whole test. {@code clientAutoReJoinWorld}
     * is turned on so a finished restore reboots the integrated server, giving a second, log-observable
     * "Starting integrated minecraft server version" to wait for instead of guessing at a sleep.
     */
    @ParameterizedTest(name = "{0} / Fabric client — restores a backup (client-driven)")
    @MethodSource("clientMatrix")
    void clientRestoresBackup(String mc) {
        if (!config.displayAvailable()) {
            runner.skip(mc, "fabric", "client", "restore",
                "No display available (DISPLAY is unset and this is not Windows/macOS)");
        }
        runner.run(mc, "fabric", "client", "restore", () -> {
            try (ServerRun seed = ServerRun.provision(config, mc, "fabric", "client-restore-seed",
                ModConfigFile.deterministic())) {
                seed.boot();
                seed.stop();

                ModConfigFile modConfig = ModConfigFile.deterministic()
                    .with("clientAutoReJoinWorld", true);
                try (ClientRun run = ClientRun.provision(config, mc, "restore", modConfig)) {
                    run.installSave(seed.worldDir(), LEVEL_ID);
                    run.bootIntoWorld(LEVEL_ID);
                    run.client().awaitLine("Done (", Duration.ofMinutes(3));

                    ClientInput input = new ClientInput();
                    input.sendCommand("qb make clientprobe");
                    run.client().awaitLine("Make Backup thread close", Duration.ofMinutes(5));

                    BackupStore store = run.store(LEVEL_ID);
                    assertTrue(store.hasBackup("clientprobe"), "The client backup was not recorded");
                    List<String> expectedFiles = store.filesIn("clientprobe");
                    assertFalse(expectedFiles.isEmpty(), "The backup recorded no files");

                    // A file the backup does not know about: it has to disappear once the client actually
                    // deletes and rebuilds the world (ClientRestoreDelegate.deleteWorld()), rather than the
                    // restore silently no-op'ing onto the still-live world.
                    Path worldDir = run.savesDir().resolve(LEVEL_ID);
                    Path sentinel = worldDir.resolve("qbm-harness-sentinel.txt");
                    Files.writeString(sentinel, "harness");
                    assertTrue(Files.exists(sentinel), "Failed to plant the sentinel file");

                    int restoresBefore = run.client().occurrences("Make a temp backup success.");
                    int rejoinsBefore = run.client().occurrences("Starting integrated minecraft server version");

                    // 1-based index: this is the only backup this world has ever had, so it is index 1.
                    input.sendCommand("qb restore 1");
                    input.sendCommand("qb confirm");

                    // "Make a temp backup success." is logged before the reconstruct runs, so this only
                    // proves the restore started — not that it finished, which is what the rejoin waits for.
                    run.client().awaitOccurrences("Make a temp backup success.", restoresBefore + 1,
                        Duration.ofMinutes(3));
                    run.client().awaitOccurrences("Starting integrated minecraft server version",
                        rejoinsBefore + 1, Duration.ofMinutes(2));

                    assertFalse(Files.exists(sentinel),
                        "The sentinel file survived the restore, so the world was not actually deleted "
                            + "and rebuilt");
                    Path nested = worldDir.resolve(LEVEL_ID);
                    assertFalse(Files.exists(nested),
                        "A client restore created a nested world directory at " + nested);
                    assertEquals(expectedFiles, ServerRun.relativeFiles(worldDir),
                        "The restored world's files do not match what the backup recorded");

                    run.quit();
                }
            }
        });
    }
}
