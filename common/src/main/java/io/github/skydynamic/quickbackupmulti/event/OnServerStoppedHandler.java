package io.github.skydynamic.quickbackupmulti.event;

import io.github.skydynamic.quickbackupmulti.ModEnvType;
import io.github.skydynamic.quickbackupmulti.QuickbackupmultiReforged;
import io.github.skydynamic.quickbackupmulti.schedule.ScheduleManager;
import io.github.skydynamic.quickbackupmulti.utils.BackupManager;

public class OnServerStoppedHandler {
    public static void handle() {
        ScheduleManager.clearAllSchedule();

        if (!QuickbackupmultiReforged.getModContainer().isRestoringBackup()) {
            QuickbackupmultiReforged.getDatabase().closeDatabase();
            return;
        }

        // An integrated (client) server rolls back by reopening the world instead; see ClientRestoreDelegate.
        if (QuickbackupmultiReforged.getModContainer().getEnvType() != ModEnvType.SERVER) {
            return;
        }

        restoreSelection();

        QuickbackupmultiReforged.getModContainer().setRestoringBackup(false);
        QuickbackupmultiReforged.getModContainer().setAfterRestarting(true);

        // Release the H2 file lock before a replacement process opens the same database. Skipping this
        // used to work only because the restart happened inside this JVM.
        QuickbackupmultiReforged.getDatabase().closeDatabase();

        switch (QuickbackupmultiReforged.getModConfig().getAutoRestartMode()) {
            case DISABLE -> QuickbackupmultiReforged.logger.info(
                "Rollback finished; the server stays stopped (autoRestartMode=DISABLE).");
            // DEFAULT and SELF_RESTART are the same mechanism: launch a replacement process, then exit.
            case DEFAULT, SELF_RESTART -> QuickbackupmultiReforged.getServerManager().startServer();
            case MCSM -> new Thread(() -> System.exit(1)).start();
        }
    }

    /**
     * Applies the selected backup, falling back to the temp backup taken just before it.
     *
     * <p>Runs inside a server-lifecycle callback, so nothing may escape: an exception here turns a
     * rollback into a crash report that takes the process down before the restart can happen.
     */
    private static void restoreSelection() {
        try {
            BackupManager.makeTempBackup();
            boolean restoreResult = BackupManager.restoreBackup(
                QuickbackupmultiReforged.getModContainer().getCurrentSelectionBackup());
            if (restoreResult) {
                return;
            }
            QuickbackupmultiReforged.logger.warn("Restore failed, try to restore from temp backup");
            if (!BackupManager.restoreBackup("restore_temp")) {
                QuickbackupmultiReforged.logger.error(
                    "Restore from temp backup failed, something may be wrong with the backup files.");
            } else {
                QuickbackupmultiReforged.logger.info("Restore from temp backup success.");
            }
        } catch (Throwable t) {
            QuickbackupmultiReforged.logger.error("Rollback failed unexpectedly", t);
        }
    }
}
