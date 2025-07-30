package io.github.skydynamic.quickbakcupmulti.event;

import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import io.github.skydynamic.quickbakcupmulti.config.PruneScheduleConfig;
import io.github.skydynamic.quickbakcupmulti.config.ScheduleBackupConfig;
import io.github.skydynamic.quickbakcupmulti.config.ScheduleConfig;
import io.github.skydynamic.quickbakcupmulti.schedule.ScheduleManager;
import io.github.skydynamic.quickbakcupmulti.schedule.runnables.DefaultDatabaseBackupRunnable;
import io.github.skydynamic.quickbakcupmulti.schedule.runnables.DefaultPruneRunnable;
import io.github.skydynamic.quickbakcupmulti.schedule.runnables.DefaultScheduleBackupRunnable;
import io.github.skydynamic.quickbakcupmulti.utils.DurationUtils;

public class OnLoadedWorldHandler {
    public static void handler() {
        // Database Schedule backup
        ScheduleConfig databaseBackupConfig = QuickbakcupmultiReforged.getModConfig().getDatabaseConfig().getBackup();
        if (databaseBackupConfig.enabled) {
            Runnable databaseBackupRunnable = new DefaultDatabaseBackupRunnable();
            if (databaseBackupConfig.interval != null) {
                int interval = (int) DurationUtils.parseDurationToSeconds(databaseBackupConfig.interval);
                ScheduleManager.registerSchedule("databaseSchedule", interval, databaseBackupRunnable);
            } else if (databaseBackupConfig.crontab != null) {
                ScheduleManager.registerSchedule("databaseSchedule", databaseBackupConfig.crontab, databaseBackupRunnable);
            }
        }

        // Schedule backup
        ScheduleBackupConfig scheduleBackupConfig = QuickbakcupmultiReforged.getModConfig().getScheduleBackupConfig();
        if (scheduleBackupConfig.enabled) {
            Runnable scheduleBackupRunnable = new DefaultScheduleBackupRunnable();
            if (scheduleBackupConfig.interval != null) {
                int interval = (int) DurationUtils.parseDurationToSeconds(scheduleBackupConfig.interval);
                ScheduleManager.registerSchedule("scheduleBackup", databaseBackupConfig.interval, scheduleBackupRunnable);
            } else if (scheduleBackupConfig.crontab != null) {
                ScheduleManager.registerSchedule("scheduleBackup", scheduleBackupConfig.crontab, scheduleBackupRunnable);
            }
        }

        // Prune schedule
        PruneScheduleConfig pruneScheduleConfig = QuickbakcupmultiReforged.getModConfig().getPruneScheduleConfig();
        if (pruneScheduleConfig.enabled) {
            Runnable pruneRunnable = DefaultPruneRunnable.PRUNE_REGULAR_BACKUP_RUNNABLE;
            if (pruneScheduleConfig.interval != null) {
                int interval = (int) DurationUtils.parseDurationToSeconds(pruneScheduleConfig.interval);
                ScheduleManager.registerSchedule("pruneSchedule", interval, pruneRunnable);
            } else if (pruneScheduleConfig.crontab != null) {
                ScheduleManager.registerSchedule("pruneSchedule", pruneScheduleConfig.crontab, pruneRunnable);
            }

            // Prune temporary backup
            if (pruneScheduleConfig.getTemporaryBackup().enabled) {
                Runnable pruneTemporaryBackupRunnable = DefaultPruneRunnable.PRUNE_TEMPORARY_BACKUP_RUNNABLE;
                if (pruneScheduleConfig.getTemporaryBackup().interval != null) {
                    int interval = (int) DurationUtils.parseDurationToSeconds(pruneScheduleConfig.getTemporaryBackup().interval);
                    ScheduleManager.registerSchedule(
                        "pruneTemporaryBackupSchedule",
                        interval,
                        pruneTemporaryBackupRunnable);
                } else if (pruneScheduleConfig.getTemporaryBackup().crontab != null) {
                    ScheduleManager.registerSchedule(
                        "pruneTemporaryBackupSchedule",
                        pruneScheduleConfig.getTemporaryBackup().crontab,
                        pruneTemporaryBackupRunnable);
                }
            }
        }

        ScheduleManager.startAllSchedule();
    }
}
