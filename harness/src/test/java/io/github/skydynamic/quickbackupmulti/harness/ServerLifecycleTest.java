package io.github.skydynamic.quickbackupmulti.harness;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The full backup lifecycle on a dedicated server, for every Minecraft release in the branch's declared
 * support range and on both loaders.
 *
 * <p>Booting at all is already a meaningful assertion: {@code quickbackupmulti.mixins.json} declares
 * {@code "required": true} with {@code injectors.defaultRequire: 1}, so a mixin whose target moved in a
 * newer Minecraft version aborts startup instead of silently disabling a feature. That makes these tests
 * a direct check on version drift rather than only on the mod's logic.
 */
class ServerLifecycleTest extends FunctionalTestBase {

    @ParameterizedTest(name = "{0} / {1} — boots with mixins applied")
    @MethodSource("serverMatrix")
    void modLoads(String mc, String loader) {
        runner.run(mc, loader, "server", "boot", () -> {
            try (ServerRun run = ServerRun.provision(config, mc, loader, "boot",
                ModConfigFile.deterministic())) {
                run.boot();

                // The mod logs its own startup through a "QuickBackupMulti" logger; seeing nothing from it
                // means the entrypoint never ran even though the server came up.
                assertTrue(run.server().sawLine("QuickBackupMulti"),
                    "The server booted but the mod never logged anything, so its entrypoint did not run");

                // A mixin that fails to apply is fatal with defaultRequire=1, so reaching this point
                // already proves it applied — but an *error* logged by the mixin subsystem still means
                // something silently degraded.
                assertFalse(run.server().sawLine("Mixin apply failed"),
                    "A mixin failed to apply on Minecraft " + mc);

                run.stop();
            }
        });
    }

    @ParameterizedTest(name = "{0} / {1} — make, list, restore round trip")
    @MethodSource("serverMatrix")
    void fullLifecycle(String mc, String loader) {
        runner.run(mc, loader, "server", "lifecycle", () -> {
            try (ServerRun run = ServerRun.provision(config, mc, loader, "lifecycle",
                ModConfigFile.deterministic())) {
                run.boot();

                // --- make -------------------------------------------------------------------------
                run.makeBackup("before");

                BackupStore store = run.store();
                assertTrue(store.exists(), "The mod did not create its H2 database");
                assertTrue(store.hasBackup("before"),
                    "/qb make did not record a 'before' row; storage_info holds "
                        + store.backups().stream().map(BackupStore.Backup::name).toList());

                List<String> blobs = store.blobHashes();
                assertFalse(blobs.isEmpty(),
                    "The backup recorded no content-addressed blobs, so nothing was actually copied");
                assertFalse(store.filesIn("before").isEmpty(),
                    "The backup recorded no file paths");

                // A full backup is made alongside the first incremental one, since none exists yet.
                assertFalse(store.fullBackupDirs().isEmpty(),
                    "No full backup was created to serve as a baseline for later increments");

                List<String> capturedFiles = store.filesIn("before");
                List<String> actualFiles = ServerRun.relativeFiles(run.worldDir());
                assertEquals(capturedFiles, actualFiles,
                    "The backup did not record the exact set of files the world actually held");

                // --- change the world so a restore has something to undo ---------------------------
                // A dedicated server restore overlays the backed-up files onto the live world without
                // deleting it first (unlike the client path, which calls deleteLevel() before restoring).
                // So changing an *existing* file rather than adding a new one is what proves the restore
                // actually ran: a new file would survive, but a modification is reverted.
                Path levelDat = run.worldDir().resolve("level.dat");
                assertTrue(Files.exists(levelDat), "No level.dat to modify");
                long originalSize = Files.size(levelDat);
                Files.write(levelDat, new byte[]{0, 1, 2, 3, 4}, java.nio.file.StandardOpenOption.APPEND);
                assertNotEquals(originalSize, Files.size(levelDat), "Append did not change level.dat");

                // --- list -------------------------------------------------------------------------
                run.server().send("qb list");
                // The list output itself is localised and colour-coded, so assert on the index the mod
                // would resolve rather than on the rendered text.
                assertEquals(1, store.indexOf("before"),
                    "'before' should be the first (and only) listed backup");

                // --- restore ----------------------------------------------------------------------
                // With autoRestartMode=DISABLE the server exits after restoring, which is the signal the
                // restore ran to completion.
                assertEquals(0, run.restoreAndAwaitExit("before"),
                    "The server did not exit cleanly after restoring");

                assertEquals(originalSize, Files.size(levelDat),
                    "The restore did not revert level.dat to its backed-up size, so the world was not "
                        + "actually rolled back");

                // Booting again proves the restored world is loadable, which is the part a user cares
                // about and the part a half-written restore breaks.
                run.boot();
                run.stop();
            }
        });
    }

    /**
     * Regression test for the nested-world bug: every restore used to leave behind an extra
     * {@code world/world}, because the level directory was passed where the <em>saves</em> directory was
     * expected and acquiring the directory lock created it.
     */
    @ParameterizedTest(name = "{0} / {1} — restore does not nest world/world (#56)")
    @MethodSource("serverMatrix")
    void restoreDoesNotNestWorldDirectory(String mc, String loader) {
        runner.run(mc, loader, "server", "nested-world", () -> {
            // DEFAULT restarts in-process on Fabric, which is exactly the path that re-opened the storage
            // source and nested the directory. On NeoForge DEFAULT spawns a detached JVM instead, so the
            // in-process restart cannot be observed and DISABLE plus an explicit reboot is used.
            boolean inProcessRestart = "fabric".equals(loader);
            ModConfigFile modConfig = ModConfigFile.deterministic()
                .with("autoRestartMode", inProcessRestart ? "DEFAULT" : "DISABLE");

            try (ServerRun run = ServerRun.provision(config, mc, loader, "nested-world", modConfig)) {
                run.boot();
                run.makeBackup("nest-check");

                if (inProcessRestart) {
                    run.restoreAndAwaitRestart("nest-check");
                } else {
                    run.restoreAndAwaitExit("nest-check");
                    run.boot();
                }

                Path nested = run.worldDir().resolve("world");
                assertFalse(Files.exists(nested),
                    "A restore created a nested world directory at " + nested
                        + "; the level directory was passed where the saves directory was expected");

                // Restoring the same backup a second time is what made the old bug compound
                // (world/world/world), so prove the second pass is clean too.
                if (inProcessRestart) {
                    run.restoreAndAwaitRestart("nest-check");
                } else {
                    run.restoreAndAwaitExit("nest-check");
                    run.boot();
                }

                assertFalse(Files.exists(nested),
                    "A second restore created a nested world directory at " + nested);

                run.stop();
            }
        });
    }

    /**
     * Regression test for the full-backup rotation: deleting only the directory left the
     * {@code storage_info} row behind, so the same already-deleted backup was picked as "oldest" every
     * time and the count never fell below the limit.
     */
    @ParameterizedTest(name = "{0} / {1} — full backup rotation converges (#55)")
    @MethodSource("serverMatrix")
    void fullBackupRotationConverges(String mc, String loader) {
        runner.run(mc, loader, "server", "full-rotation", () -> {
            // A full backup after every incremental one, keeping two: the rotation then has to run
            // repeatedly within a single short scenario.
            ModConfigFile modConfig = ModConfigFile.deterministic().withFullBackups(1, 2);

            try (ServerRun run = ServerRun.provision(config, mc, loader, "full-rotation", modConfig)) {
                run.boot();

                for (int i = 1; i <= 5; i++) {
                    run.makeBackup("rot" + i);
                }

                // Pruning runs asynchronously after each full backup, so asserting immediately after the
                // 5th makeBackup races the rotation. 5 full backups with a limit of 2 must prune exactly 3.
                run.server().awaitOccurrences("Delete oldest full backup:", 3, Duration.ofMinutes(2));

                BackupStore store = run.store();
                List<BackupStore.Backup> fulls = store.fullBackups();
                List<String> dirs = store.fullBackupDirs();

                assertTrue(fulls.size() <= 2,
                    "The rotation kept " + fulls.size() + " full backups in the database with a limit of 2 "
                        + "(" + fulls.stream().map(BackupStore.Backup::name).toList() + "); "
                        + "orphaned rows make the same backup look like the oldest forever");
                assertTrue(dirs.size() <= 2,
                    "The rotation left " + dirs.size() + " full backup directories with a limit of 2: " + dirs);
                // The two have to agree: a row without a directory is exactly the state that broke the
                // rotation, and a directory without a row leaks disk space.
                assertEquals(fulls.size(), dirs.size(),
                    "The database has " + fulls.size() + " full backups but there are " + dirs.size()
                        + " directories on disk; rows and directories must be pruned together");

                run.stop();
            }
        });
    }

    /**
     * Regression test for schedules interfering with one another: resetting the backup timer used to
     * tear down the shared Quartz scheduler and take the unrelated schedules with it.
     */
    @ParameterizedTest(name = "{0} / {1} — resetting one schedule leaves others running (#54)")
    @MethodSource("serverMatrix")
    void resettingOneScheduleLeavesOthersRunning(String mc, String loader) {
        runner.run(mc, loader, "server", "schedules", () -> {
            // Two schedules on one shared scheduler, and resetTimerOnBackup so /qb make resets the first.
            // Use short intervals so at least one fires during the scenario, proving it stayed alive.
            ModConfigFile modConfig = ModConfigFile.deterministic()
                .withScheduleBackup("10s", true)
                .withDatabaseSchedule("10s");

            try (ServerRun run = ServerRun.provision(config, mc, loader, "schedules", modConfig)) {
                run.boot();

                // Both must have started; these messages are logged in English regardless of the
                // configured language.
                assertTrue(run.server().sawLine("Start schedule: scheduleBackup"),
                    "The backup schedule never started");
                assertTrue(run.server().sawLine("Start schedule: databaseSchedule"),
                    "The database schedule never started");

                run.makeBackup("schedule-probe");
                run.server().awaitLine("Reset timer for scheduleBackup", Duration.ofMinutes(2));

                // The reset must not have stopped anything. "Stop schedule:" is only logged when a
                // schedule is actually torn down, which at this point in the run would mean the shared
                // scheduler was shut down under the other job.
                assertFalse(run.server().sawLine("Stop schedule: databaseSchedule"),
                    "Resetting the backup timer stopped the unrelated database schedule");
                assertFalse(run.server().sawLine("Failed to start schedule"),
                    "A schedule failed to start after the timer reset");

                // Wait for at least one schedule to fire, proving the scheduler stayed alive.
                run.server().awaitAny(Duration.ofMinutes(1),
                    "Schedule scheduleBackup execute done",
                    "Schedule databaseSchedule execute done");

                run.stop();
            }
        });
    }

    /** Export writes a standalone copy, which is the other consumer of the reconstruct path. */
    @ParameterizedTest(name = "{0} / {1} — export and delete")
    @MethodSource("serverMatrix")
    void exportAndDelete(String mc, String loader) {
        runner.run(mc, loader, "server", "export-delete", () -> {
            try (ServerRun run = ServerRun.provision(config, mc, loader, "export-delete",
                ModConfigFile.deterministic())) {
                run.boot();
                run.makeBackup("exportable");

                BackupStore store = run.store();
                List<String> expectedFiles = store.filesIn("exportable");
                assertFalse(expectedFiles.isEmpty(), "The backup recorded no files to export");

                run.server().send("qb export \"exportable\"");
                // The default export (no "zip" literal) reconstructs into <storagePath>/export/<name>/
                // as plain files, on a background thread. Waiting for the directory to merely become
                // non-empty would pass on a partially-written export, so wait for the reconstructed file
                // set to actually match what the backup recorded instead.
                Path exportedDir = run.storageDir().resolve("export").resolve("exportable");
                waitUntil(() -> ServerRun.relativeFiles(exportedDir).equals(expectedFiles),
                    Duration.ofMinutes(5),
                    "Exported files under " + exportedDir + " never matched the backup's recorded file "
                        + "set " + expectedFiles);

                run.server().send("qb delete \"exportable\"");
                waitUntil(() -> !run.store().hasBackup("exportable"), Duration.ofMinutes(3),
                    "/qb delete did not remove the 'exportable' row from storage_info");

                run.stop();
            }
        });
    }

    private interface Condition {
        boolean test() throws Exception;
    }

    private static void waitUntil(Condition condition, Duration timeout, String failure)
        throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                if (condition.test()) {
                    return;
                }
            } catch (Exception e) {
                // Still settling: the database may be mid-write.
            }
            Thread.sleep(500);
        }
        throw new AssertionError(failure + " (waited " + timeout.toSeconds() + "s)");
    }
}
