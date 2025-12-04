package io.github.skydynamic.quickbakcupmulti.event;

import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import io.github.skydynamic.quickbakcupmulti.config.ModConfig;
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
        registerSchedules();

        ScheduleManager.startAllSchedule();

        QuickbakcupmultiReforged.getModContainer().setAfterRestarting(false);
    }

    private static void registerSchedules() {
        ModConfig config = QuickbakcupmultiReforged.getModConfig();

         // Database Schedule backup
        ScheduleConfig databaseBackupConfig = config.getDatabaseConfig().getBackup();
        if (databaseBackupConfig.enabled) {
            Runnable databaseBackupRunnable = new DefaultDatabaseBackupRunnable();
            if (databaseBackupConfig.crontab != null) {
                ScheduleManager.registerSchedule("databaseSchedule", databaseBackupConfig.crontab, databaseBackupRunnable);
            } else if (databaseBackupConfig.interval != null) {
                int interval = (int) DurationUtils.parseDurationToSeconds(databaseBackupConfig.interval);
                ScheduleManager.registerSchedule("databaseSchedule", interval, databaseBackupRunnable);
            }
        }

        // Schedule backup
        ScheduleBackupConfig scheduleBackupConfig = config.getScheduleBackupConfig();
        if (scheduleBackupConfig.enabled) {
            Runnable scheduleBackupRunnable = new DefaultScheduleBackupRunnable();
            if (scheduleBackupConfig.crontab != null) {
                ScheduleManager.registerSchedule("scheduleBackup", scheduleBackupConfig.crontab, scheduleBackupRunnable);
            } else if (scheduleBackupConfig.interval != null) {
                int interval = (int) DurationUtils.parseDurationToSeconds(scheduleBackupConfig.interval);
                ScheduleManager.registerSchedule("scheduleBackup", interval, scheduleBackupRunnable);
            }
        }

        // Prune schedule
        PruneScheduleConfig pruneScheduleConfig = config.getPruneScheduleConfig();
        if (pruneScheduleConfig.enabled) {
            if (pruneScheduleConfig.regularBackup.enabled) {
                Runnable pruneRunnable = DefaultPruneRunnable.PRUNE_REGULAR_BACKUP_RUNNABLE;
                if (pruneScheduleConfig.crontab != null) {
                    ScheduleManager.registerSchedule("pruneSchedule", pruneScheduleConfig.crontab, pruneRunnable);
                } else if (pruneScheduleConfig.interval != null) {
                    int interval = (int) DurationUtils.parseDurationToSeconds(pruneScheduleConfig.interval);
                    ScheduleManager.registerSchedule("pruneSchedule", interval, pruneRunnable);
                }
            }

            // Prune temporary backup
            if (pruneScheduleConfig.getTemporaryBackup().enabled) {
                Runnable pruneTemporaryBackupRunnable = DefaultPruneRunnable.PRUNE_TEMPORARY_BACKUP_RUNNABLE;
                if (pruneScheduleConfig.getTemporaryBackup().crontab != null) {
                    ScheduleManager.registerSchedule(
                        "pruneTemporaryBackupSchedule",
                        pruneScheduleConfig.getTemporaryBackup().crontab,
                        pruneTemporaryBackupRunnable);
                } else if (pruneScheduleConfig.getTemporaryBackup().interval != null) {
                    int interval = (int) DurationUtils.parseDurationToSeconds(pruneScheduleConfig.getTemporaryBackup().interval);
                    ScheduleManager.registerSchedule(
                        "pruneTemporaryBackupSchedule",
                        interval,
                        pruneTemporaryBackupRunnable);
                }
            }
        }
    }
}
