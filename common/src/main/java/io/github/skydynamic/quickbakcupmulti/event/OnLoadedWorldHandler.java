package io.github.skydynamic.quickbakcupmulti.event;

import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import io.github.skydynamic.quickbakcupmulti.config.DatabaseConfig;
import io.github.skydynamic.quickbakcupmulti.schedule.ScheduleManager;
import io.github.skydynamic.quickbakcupmulti.schedule.runnables.DatabaseBackupRunnable;

public class OnLoadedWorldHandler {
    public static void handler() {
        // Database Schedule backup
        DatabaseConfig.BackupConfig databaseBackupConfig = QuickbakcupmultiReforged.getModConfig().getDatabaseConfig().getBackupConfig();
        Runnable databaseBackupRunnable = new DatabaseBackupRunnable();
        if (databaseBackupConfig.enabled) {
            if (databaseBackupConfig.interval != null) {
                ScheduleManager.registerSchedule("databaseSchedule", databaseBackupConfig.interval, databaseBackupConfig.jitter, databaseBackupRunnable);
            } else if (databaseBackupConfig.crontab != null) {
                ScheduleManager.registerSchedule("databaseSchedule", databaseBackupConfig.crontab, databaseBackupConfig.jitter, databaseBackupRunnable);
            }
        }

        ScheduleManager.startAllSchedule();
    }
}
