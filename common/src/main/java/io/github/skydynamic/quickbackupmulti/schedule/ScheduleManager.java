package io.github.skydynamic.quickbackupmulti.schedule;

import io.github.skydynamic.quickbackupmulti.QuickbackupmultiReforged;
import io.github.skydynamic.quickbackupmulti.schedule.impl.ModSchedule;

public class ScheduleManager {
    private static void registerSchedule(ModSchedule schedule) {
        if (QuickbackupmultiReforged.getModContainer().getSchedules().contains(schedule)) {
            QuickbackupmultiReforged.logger.warn("Schedule already exists: {}", schedule.getName());
            return;
        }
        QuickbackupmultiReforged.getModContainer().getSchedules().add(schedule);
        QuickbackupmultiReforged.logger.info("Register schedule: {}", schedule.getName());
    }

    public static void registerSchedule(String name, String crontab, Runnable executor) {
        ModSchedule schedule = new ModSchedule(name, crontab).setExecutor(executor);
        registerSchedule(schedule);
    }

    public static void registerSchedule(String name, int interval, Runnable executor) {
        ModSchedule schedule = new ModSchedule(name, interval).setExecutor(executor);
        registerSchedule(schedule);
    }

    public static void startAllSchedule() {
        for (IModSchedule schedule : QuickbackupmultiReforged.getModContainer().getSchedules()) {
            if (!schedule.startSchedule()) {
                QuickbackupmultiReforged.logger.warn("Failed to start schedule: {}", schedule.getName());
            } else {
                QuickbackupmultiReforged.logger.info("Start schedule: {}, next execute time: {}",
                    schedule.getName(),
                    schedule.formatNextExecuteTime()
                );
            }
        }
    }

    /**
     * Unschedules every job without touching the shared scheduler, so nothing fires again.
     *
     * <p>Split out from {@link #stopAllSchedule()} for the shutdown path: the scheduler must keep its
     * worker pool until any job already in flight has finished, because shutting it down waits for that
     * job while the job is waiting on the server thread that is doing the shutting down.
     */
    public static void stopAllScheduleJobs() {
        for (IModSchedule schedule : QuickbackupmultiReforged.getModContainer().getSchedules()) {
            if (schedule.isRunning()) {
                schedule.stopSchedule();
                QuickbackupmultiReforged.logger.info("Stop schedule: {}", schedule.getName());
            }
        }
    }

    public static void stopAllSchedule() {
        stopAllScheduleJobs();
        // Every schedule shares one Quartz scheduler and its threads are not daemons, so it has to be
        // shut down explicitly once all the jobs are gone or the JVM will not exit.
        ModSchedule.shutdownSharedScheduler();
    }

    public static void clearAllSchedule() {
        stopAllSchedule();
        QuickbackupmultiReforged.getModContainer().getSchedules().clear();
    }

    public static boolean resetTimer(String name) {
        for (IModSchedule schedule : QuickbackupmultiReforged.getModContainer().getSchedules()) {
            if (schedule.getName().equals(name) && schedule.resetTimer()) {
                QuickbackupmultiReforged.logger.info("Reset timer: {}", name);
                return true;
            }
        }
        return false;
    }
}
