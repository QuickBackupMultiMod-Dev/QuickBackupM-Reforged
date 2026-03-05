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
                    QuickbackupmultiReforged.formatTimestamp(schedule.getNextExecuteTime())
                );
            }
        }
    }

    public static void stopAllSchedule() {
        for (IModSchedule schedule : QuickbackupmultiReforged.getModContainer().getSchedules()) {
            if (schedule.isRunning()) {
                schedule.stopSchedule();
                QuickbackupmultiReforged.logger.info("Stop schedule: {}", schedule.getName());
            }
        }
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
