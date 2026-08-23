package io.github.skydynamic.quickbackupmulti.harness;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The integrated server: boots to the main menu, opens a world, drives {@code /qb} commands through the
 * chat box the way a player would, and — since the client-side restore path turned out not to need any
 * GUI interaction ({@link io.github.skydynamic.quickbackupmulti.restore.ClientRestoreDelegate} runs
 * fully automatically once armed) — exercises a full client-driven restore too.
 *
 * <p>Commands go through {@link ClientCommandChannel}: normally the mod's own stdin channel, which needs
 * no window focus and so works on a headless CI runner, falling back to {@link ClientInput}'s
 * {@code java.awt.Robot} keystrokes when a build does not offer one. Either way it is the client's real
 * command path — unlike a dedicated server, the game reads no commands from stdin by itself.
 *
 * <p>The code under test is {@code common}, loaded identically by Fabric and NeoForge, so only Fabric
 * clients are tested — the NeoForge client has <em>loader</em> differences (different entrypoint wiring,
 * different restart mechanics) but the mod's own client logic is the same.
 */
class ClientLifecycleTest extends FunctionalTestBase {
    private static final String LEVEL_ID = "qbm-harness-test";

    /**
     * Boots the Fabric client far enough to prove the mod loaded into it.
     *
     * <p>Getting this far means Mixin applied, the mod's client entrypoint ran, and the resource and
     * language files loaded without error. That is already a stronger version-drift signal than a compile,
     * because a client mixin targeting a method that moved aborts startup with {@code required: true}.
     *
     * <p>It stops short of asserting the main menu was reached, because vanilla logs nothing when the
     * loading overlay hands over to a screen — there is no marker to wait for. The two world scenarios
     * below are what cover that: {@code --quickPlaySingleplayer} only takes effect once the client has
     * worked through its initial screen chain, so anything blocking the menu fails them.
     */
    @ParameterizedTest(name = "{0} / Fabric client — boots with the mod loaded")
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
                assertFalse(run.client().sawLine("Mixin apply failed"),
                    "A client mixin failed to apply on Minecraft " + mc);
                // Startup carries on asynchronously past the boot marker, so a client that dies while
                // finishing up would otherwise go unnoticed.
                assertTrue(run.client().isAlive(),
                    "The client exited during startup instead of settling into its game loop");
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
                    // the world finishing loading; block until the player is actually in it before typing.
                    assertTrue(run.client().sawLine("Starting integrated minecraft server"),
                        "The integrated server never started");
                    run.awaitWorldReady(Duration.ofMinutes(3));

                    // Making one backup is what proves the world-load path ran: the client only points the
                    // mod at a database and a blob directory when a world is opened, so a working
                    // /qb make covers both. It also proves a real player-typed chat command reaches the
                    // mod's command dispatcher.
                    run.commands().sendCommand("qb make clientprobe");
                    run.client().awaitLine("Make Backup thread close", Duration.ofMinutes(5));

                    // Asserted after the backup rather than straight after boot: the database file itself
                    // only has to exist once something has been written to it.
                    BackupStore store = run.store(LEVEL_ID);
                    assertTrue(store.exists(),
                        "The mod did not create its H2 database at the top-level storage path");
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
                    run.awaitWorldReady(Duration.ofMinutes(3));

                    ClientCommandChannel input = run.commands();
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

                    // Every file the backup captured has to be back, but the world is live again by now:
                    // clientAutoReJoinWorld reopens it, and the integrated server writes fresh entities/
                    // and poi/ region files as it loads chunks. Asserting an exact file set here would be
                    // asserting on world simulation rather than on the restore, and would pass or fail
                    // depending on how far the rejoin had got — the same reason the dedicated-server
                    // lifecycle test compares this way round too. The sentinel above is what proves
                    // nothing merely survived untouched.
                    List<String> restoredFiles = ServerRun.relativeFiles(worldDir);
                    List<String> missing = expectedFiles.stream()
                        .filter(f -> !restoredFiles.contains(f)).toList();
                    assertTrue(missing.isEmpty(),
                        "The restore did not put back files the backup recorded: " + missing);
                    assertTrue(restoredFiles.contains("level.dat"),
                        "The restored world has no level.dat, so it is not loadable");

                    run.quit();
                }
            }
        });
    }
}
