package io.github.skydynamic.quickbakcupmulti.event;

import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import io.github.skydynamic.quickbakcupmulti.schedule.ScheduleManager;
import io.github.skydynamic.quickbakcupmulti.utils.BackupManager;

public class OnServerStoppedHandler {
    public static void handle() {
        if (QuickbakcupmultiReforged.getModContainer().isRestoringBackup()) {
            BackupManager.restoreBackup(QuickbakcupmultiReforged.getModContainer().getCurrentSelectionBackup());
            QuickbakcupmultiReforged.getModContainer().setRestoringBackup(false);
            switch (QuickbakcupmultiReforged.getModConfig().getAutoRestartMode()) {
                case DISABLE -> {
                }
                case DEFAULT -> QuickbakcupmultiReforged.getServerManager().startServer();
                case MCSM -> new Thread(() -> System.exit(1)).start();
            }
        } else {
            QuickbakcupmultiReforged.getDatabase().closeDatabase();
            ScheduleManager.clearAllSchedule();
        }
    }
}
