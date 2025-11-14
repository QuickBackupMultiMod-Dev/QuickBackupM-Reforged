package io.github.skydynamic.quickbakcupmulti.schedule;

import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import io.github.skydynamic.quickbakcupmulti.schedule.impl.ModSchedule;

public class ScheduleManager {
    private static void registerSchedule(ModSchedule schedule) {
        if (QuickbakcupmultiReforged.getModContainer().getSchedules().contains(schedule)) {
            QuickbakcupmultiReforged.logger.warn("Schedule already exists: {}", schedule.getName());
            return;
        }
        QuickbakcupmultiReforged.getModContainer().getSchedules().add(schedule);
        QuickbakcupmultiReforged.logger.info("Register schedule: {}", schedule.getName());
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
        for (IModSchedule schedule : QuickbakcupmultiReforged.getModContainer().getSchedules()) {
            if (!schedule.startSchedule()) {
                QuickbakcupmultiReforged.logger.warn("Failed to start schedule: {}", schedule.getName());
            } else {
                QuickbakcupmultiReforged.logger.info("Start schedule: {}, next execute time: {}",
                    schedule.getName(),
                    QuickbakcupmultiReforged.formatTimestamp(schedule.getNextExecuteTime())
                );
            }
        }
    }

    public static void stopAllSchedule() {
        for (IModSchedule schedule : QuickbakcupmultiReforged.getModContainer().getSchedules()) {
            if (schedule.isRunning()) {
                schedule.stopSchedule();
                QuickbakcupmultiReforged.logger.info("Stop schedule: {}", schedule.getName());
            }
        }
    }

    public static void clearAllSchedule() {
        stopAllSchedule();
        QuickbakcupmultiReforged.getModContainer().getSchedules().clear();
    }

    public static boolean resetTimer(String name) {
        for (IModSchedule schedule : QuickbakcupmultiReforged.getModContainer().getSchedules()) {
            if (schedule.getName().equals(name) && schedule.resetTimer()) {
                QuickbakcupmultiReforged.logger.info("Reset timer: {}", name);
                return true;
            }
        }
        return false;
    }
}
