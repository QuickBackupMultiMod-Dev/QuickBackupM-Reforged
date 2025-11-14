package io.github.skydynamic.quickbakcupmulti.schedule.impl;

import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import io.github.skydynamic.quickbakcupmulti.schedule.CronUtils;
import io.github.skydynamic.quickbakcupmulti.schedule.IModSchedule;
import io.github.skydynamic.quickbakcupmulti.schedule.ModJob;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import static io.github.skydynamic.quickbakcupmulti.schedule.CronUtils.buildTrigger;

public class ModSchedule implements IModSchedule {
    private String identity;

    private String crontab;
    private Integer interval;

    private Runnable executor;

    protected JobDetail jobDetail;
    protected Trigger trigger;
    protected Scheduler scheduler;

    // Quartz
    @SuppressWarnings("unused")
    public ModSchedule() {
    }

    public ModSchedule(String identity, Integer interval) {
        this.identity = identity;
        this.interval = interval;
    }

    public ModSchedule(String identity, String crontab) {
        this.identity = identity;
        this.crontab = crontab;
    }

    @Override
    public String getName() {
        return identity;
    }

    @Override
    public boolean startSchedule() {
        jobDetail = JobBuilder
            .newJob(ModJob.class)
            .withIdentity(identity)
            .build();
        StdSchedulerFactory sf = new StdSchedulerFactory();

        if (crontab != null && !crontab.isEmpty()) {
            trigger = buildTrigger(identity, CronUtils.ScheduleMode.CRONTAB, crontab);
        } else if (interval != null && interval > 0) {
            trigger = buildTrigger(identity, CronUtils.ScheduleMode.INTERVAL, interval);
        } else {
            return false;
        }

        if (trigger == null) {
            return false;
        }

        try {
            scheduler = sf.getScheduler();
            scheduler.scheduleJob(jobDetail, trigger);
            scheduler.start();
            return true;
        } catch (SchedulerException e) {
            QuickbakcupmultiReforged.logger.error("Failed to get scheduler", e);
            return false;
        }
    }

    @Override
    public void stopSchedule() {
        try {
            scheduler.shutdown(true);
        } catch (SchedulerException e) {
            QuickbakcupmultiReforged.logger.error("Failed to stop scheduler", e);
        }
    }

    @Override
    public ModSchedule setExecutor(Runnable executor) {
        this.executor = executor;
        return this;
    }

    @Override
    public boolean isRunning() {
        try {
            return scheduler.isStarted();
        } catch (SchedulerException e) {
            return false;
        }
    }

    @Override
    public long getNextExecuteTime() {
        return trigger.getNextFireTime().getTime();
    }

    @Override
    public boolean resetTimer() {
        if (scheduler != null) {
            stopSchedule();
            return startSchedule();
        }
        return false;
    }

    public void execute() {
        QuickbakcupmultiReforged.logger.info("Schedule {} execute in {}", identity, QuickbakcupmultiReforged.formatTimestamp(System.currentTimeMillis()));
        executor.run();
        QuickbakcupmultiReforged.logger.info(
            "Schedule {} execute done, next execute time: {}",
            identity,
            QuickbakcupmultiReforged.formatTimestamp(trigger.getNextFireTime().getTime())
        );
    }
}
