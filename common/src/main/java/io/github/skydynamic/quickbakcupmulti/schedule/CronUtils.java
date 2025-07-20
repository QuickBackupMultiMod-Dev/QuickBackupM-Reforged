package io.github.skydynamic.quickbakcupmulti.schedule;

import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import io.github.skydynamic.quickbakcupmulti.utils.JitterUtils;
import org.quartz.*;

import java.text.ParseException;
import java.util.Date;

public class CronUtils {
    public enum ScheduleMode {
        INTERVAL(Integer.class),
        CRONTAB(String.class);

        private final Class<?> type;

        ScheduleMode(Class<?> type) {
            this.type = type;
        }
    }

    public static <T> Trigger buildTrigger(String name, ScheduleMode mode, T value, String jitter) {
        IllegalArgumentException buildException = new IllegalArgumentException("Schedule mode %s requires value of type %s, but got %s (value: %s)"
                                .formatted(mode.name(), mode.type.getName(), value.getClass().getName(), value));
        int jitterSeconds = JitterUtils.parseAndRandom(jitter);
        switch (mode) {
            case INTERVAL -> {
                if (value instanceof Integer v) {
                    return TriggerBuilder.newTrigger()
                        .withIdentity(name)
                        .withSchedule(SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(v).repeatForever())
                        .startAt(new Date(System.currentTimeMillis() + v * 1000L + jitterSeconds * 1000L))
                        .usingJobData("jitter", jitterSeconds)
                        .build();
                } else {
                    QuickbakcupmultiReforged.logger.error("Failed to build schedule trigger", buildException);
                }
            }
            case CRONTAB -> {
                if (value instanceof String v) {
                    if (!cronIsValid(v)) {
                        QuickbakcupmultiReforged.logger.error("Failed to build schedule trigger, CronExpression {} is invalid", v);
                        return null;
                    }
                    return TriggerBuilder.newTrigger()
                        .withIdentity(name)
                        .withSchedule(CronScheduleBuilder.cronSchedule(v))
                        .startAt(new Date(getNextExecutionTime(v).getTime() + jitterSeconds * 1000L))
                        .usingJobData("jitter", jitterSeconds)
                        .build();
                } else {
                    QuickbakcupmultiReforged.logger.error("Failed to build schedule trigger", buildException);
                }
            }
        }
        return null;
    }

    public static Date getNextExecutionTime(String cronExpress) {
        try {
            CronExpression cronExpression = new CronExpression(cronExpress);
            return cronExpression.getNextValidTimeAfter(new Date());
        } catch (ParseException e) {
            return new Date();
        }
    }

    public static boolean cronIsValid(String cronExpression) {
        try {
            new CronExpression(cronExpression);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
