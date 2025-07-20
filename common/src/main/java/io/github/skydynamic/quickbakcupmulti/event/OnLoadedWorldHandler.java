package io.github.skydynamic.quickbakcupmulti.event;

import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import io.github.skydynamic.quickbakcupmulti.config.ScheduleBackupConfig;
import io.github.skydynamic.quickbakcupmulti.config.ScheduleConfig;
import io.github.skydynamic.quickbakcupmulti.schedule.ScheduleManager;
import io.github.skydynamic.quickbakcupmulti.schedule.runnables.DefaultDatabaseBackupRunnable;
import io.github.skydynamic.quickbakcupmulti.schedule.runnables.DefaultScheduleBackupRunnable;

public class OnLoadedWorldHandler {
    public static void handler() {
        // Database Schedule backup
        ScheduleConfig databaseBackupConfig = QuickbakcupmultiReforged.getModConfig().getDatabaseConfig().getBackup();
        Runnable databaseBackupRunnable = new DefaultDatabaseBackupRunnable();
        if (databaseBackupConfig.enabled) {
            if (databaseBackupConfig.interval != null) {
                ScheduleManager.registerSchedule("databaseSchedule", databaseBackupConfig.interval, databaseBackupConfig.jitter, databaseBackupRunnable);
            } else if (databaseBackupConfig.crontab != null) {
                ScheduleManager.registerSchedule("databaseSchedule", databaseBackupConfig.crontab, databaseBackupConfig.jitter, databaseBackupRunnable);
            }
        }

        // Schedule backup
        ScheduleBackupConfig scheduleBackupConfig = QuickbakcupmultiReforged.getModConfig().getScheduleBackupConfig();
        Runnable scheduleBackupRunnable = new DefaultScheduleBackupRunnable();
        if (scheduleBackupConfig.enabled) {
            if (scheduleBackupConfig.interval != null) {
                ScheduleManager.registerSchedule("scheduleBackup", scheduleBackupConfig.interval, scheduleBackupConfig.jitter, scheduleBackupRunnable);
            } else if (scheduleBackupConfig.crontab != null) {
                ScheduleManager.registerSchedule("scheduleBackup", scheduleBackupConfig.crontab, scheduleBackupConfig.jitter, scheduleBackupRunnable);
            }
        }


        ScheduleManager.startAllSchedule();
    }
}
