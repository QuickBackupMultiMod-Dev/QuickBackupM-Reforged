package io.github.skydynamic.quickbakcupmulti.schedule.impl;

import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import io.github.skydynamic.quickbakcupmulti.schedule.CronUtils;
import io.github.skydynamic.quickbakcupmulti.schedule.IModSchedule;
import io.github.skydynamic.quickbakcupmulti.schedule.quartz.ModJobFactory;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import static io.github.skydynamic.quickbakcupmulti.schedule.CronUtils.buildTrigger;

public class ModSchedule implements IModSchedule, Job {
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
        jobDetail = JobBuilder.newJob(this.getClass()).withIdentity(identity).build();
        StdSchedulerFactory sf = new StdSchedulerFactory();

        if (crontab != null && !crontab.isEmpty() && interval == null) {
            trigger = buildTrigger(identity, CronUtils.ScheduleMode.CRONTAB, crontab);
        } else if (interval != null && interval > 0 && crontab == null) {
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
            scheduler.setJobFactory(new ModJobFactory(this));
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
    public ModSchedule setExcutor(Runnable executor) {
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

    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
        QuickbakcupmultiReforged.logger.info("Schedule {} execute in {}", identity, QuickbakcupmultiReforged.formatTimestamp(System.currentTimeMillis()));
        executor.run();
        QuickbakcupmultiReforged.logger.info(
            "Schedule {} execute done, next execute time: {}",
            identity,
            QuickbakcupmultiReforged.formatTimestamp(jobExecutionContext.getTrigger().getNextFireTime().getTime())
        );
    }
}
