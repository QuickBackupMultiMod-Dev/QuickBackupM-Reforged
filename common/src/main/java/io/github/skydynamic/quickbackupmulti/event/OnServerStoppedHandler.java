package io.github.skydynamic.quickbackupmulti.event;

import io.github.skydynamic.quickbackupmulti.ModEnvType;
import io.github.skydynamic.quickbackupmulti.QuickbackupmultiReforged;
import io.github.skydynamic.quickbackupmulti.schedule.ScheduleManager;
import io.github.skydynamic.quickbackupmulti.utils.BackupManager;

public class OnServerStoppedHandler {
    public static void handle() {
        ScheduleManager.clearAllSchedule();
        if (QuickbackupmultiReforged.getModContainer().isRestoringBackup()) {
            if (QuickbackupmultiReforged.getModContainer().getEnvType() == ModEnvType.SERVER) {
                BackupManager.makeTempBackup();
                boolean restoreResult = BackupManager.restoreBackup(QuickbackupmultiReforged.getModContainer().getCurrentSelectionBackup());
                if (!restoreResult) {
                    QuickbackupmultiReforged.logger.warn("Restore failed, try to restore from temp backup");
                    boolean restoreTempResult = BackupManager.restoreBackup("restore_temp");
                    if (!restoreTempResult) {
                        QuickbackupmultiReforged.logger.error("Restore from temp backup failed, something may be wrong with the backup files.");
                    } else {
                        QuickbackupmultiReforged.logger.info("Restore from temp backup success.");
                    }
                }

                QuickbackupmultiReforged.getModContainer().setRestoringBackup(false);
                QuickbackupmultiReforged.getModContainer().setAfterRestarting(true);
                switch (QuickbackupmultiReforged.getModConfig().getAutoRestartMode()) {
                    case DISABLE -> {
                    }
                    case DEFAULT -> QuickbackupmultiReforged.getServerManager().startServer();
                    case MCSM -> new Thread(() -> System.exit(1)).start();
                }
            }
        } else {
            QuickbackupmultiReforged.getDatabase().closeDatabase();
        }
    }
}
