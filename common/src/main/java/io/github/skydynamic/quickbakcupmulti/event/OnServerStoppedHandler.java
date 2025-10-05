package io.github.skydynamic.quickbakcupmulti.event;

import io.github.skydynamic.quickbakcupmulti.ModEnvType;
import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import io.github.skydynamic.quickbakcupmulti.restart.RestoreMarker;
import io.github.skydynamic.quickbakcupmulti.restart.ServerRestart;
import io.github.skydynamic.quickbakcupmulti.schedule.ScheduleManager;
import io.github.skydynamic.quickbakcupmulti.utils.BackupManager;

public class OnServerStoppedHandler {
    public static void handle() {
        ScheduleManager.clearAllSchedule();
        if (QuickbakcupmultiReforged.getModContainer().isRestoringBackup()) {
            if (QuickbakcupmultiReforged.getModContainer().getEnvType() == ModEnvType.SERVER) {
                BackupManager.makeTempBackup();
                QuickbakcupmultiReforged.getModContainer().setRestoringBackup(false);
                switch (QuickbakcupmultiReforged.getModConfig().getAutoRestartMode()) {
                    case DISABLE -> {
                        BackupManager.restoreBackup(QuickbakcupmultiReforged.getModContainer().getCurrentSelectionBackup());
                        RestoreMarker.delete();
                    }
                    case DEFAULT -> {
                        if (RestoreMarker.exists()) {
                            ServerRestart.restartServer();
                        } else {
                            QuickbakcupmultiReforged.logger.warn("Restore marker missing, fallback to in-process restore.");
                            BackupManager.restoreBackup(QuickbakcupmultiReforged.getModContainer().getCurrentSelectionBackup());
                        }
                    }
                    case MCSM -> new Thread(() -> System.exit(1)).start();
                }
            }
        } else {
            QuickbakcupmultiReforged.getDatabase().closeDatabase();
        }
    }
}
