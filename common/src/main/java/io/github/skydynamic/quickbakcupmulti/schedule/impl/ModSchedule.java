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
    private String jitter;

    private Runnable executor;

    protected JobDetail jobDetail;
    protected Trigger trigger;
    protected Scheduler scheduler;

    // Quartz
    @SuppressWarnings("unused")
    public ModSchedule() {
    }

    public ModSchedule(String identity, Integer interval, String jitter) {
        this.identity = identity;
        this.interval = interval;
        this.jitter = jitter;
    }

    public ModSchedule(String identity, String crontab, String jitter) {
        this.identity = identity;
        this.crontab = crontab;
        this.jitter = jitter;
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
            trigger = buildTrigger(identity, CronUtils.ScheduleMode.CRONTAB, crontab, jitter);
        } else if (interval != null && interval > 0 && crontab == null) {
            trigger = buildTrigger(identity, CronUtils.ScheduleMode.INTERVAL, interval, jitter);
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
        JobDataMap dataMap = trigger.getJobDataMap();
        int jitter = dataMap.getInt("jitter");
        return trigger.getNextFireTime().getTime() + jitter * 1000L;
    }

    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
        JobDataMap dataMap = jobExecutionContext.getMergedJobDataMap();
        int jitter = dataMap.getInt("jitter");

        try {
            Thread.sleep(jitter * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        QuickbakcupmultiReforged.logger.info("Schedule {} execute in {}", identity, QuickbakcupmultiReforged.formatTimestamp(System.currentTimeMillis()));
        executor.run();
        QuickbakcupmultiReforged.logger.info(
            "Schedule {} execute done, next execute time: {}",
            identity,
            QuickbakcupmultiReforged.formatTimestamp(jobExecutionContext.getTrigger().getNextFireTime().getTime() + jitter * 1000L)
        );
    }
}
