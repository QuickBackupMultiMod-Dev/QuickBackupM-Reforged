package io.github.skydynamic.quickbackupmulti.schedule.impl;

import io.github.skydynamic.quickbackupmulti.QuickbackupmultiReforged;
import io.github.skydynamic.quickbackupmulti.schedule.CronUtils;
import io.github.skydynamic.quickbackupmulti.schedule.IModSchedule;
import io.github.skydynamic.quickbackupmulti.schedule.ModJob;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import static io.github.skydynamic.quickbackupmulti.schedule.CronUtils.buildTrigger;

public class ModSchedule implements IModSchedule {
    /** Whether any schedule has put the shared Quartz scheduler to use since it was last shut down. */
    private static volatile boolean sharedSchedulerInUse = false;

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
            // The scheduler is shared (see shutdownSharedScheduler), so a job registered under this
            // identity by an earlier run is still there and would make scheduleJob throw.
            scheduler.deleteJob(JobKey.jobKey(identity));
            scheduler.scheduleJob(jobDetail, trigger);
            scheduler.start();
            sharedSchedulerInUse = true;
            return true;
        } catch (SchedulerException e) {
            QuickbackupmultiReforged.logger.error("Failed to get scheduler", e);
            return false;
        }
    }

    @Override
    public void stopSchedule() {
        if (scheduler == null) {
            return;
        }
        try {
            // Only unschedule this job. The scheduler instance is shared by every ModSchedule, so
            // shutting it down here would silently stop all the other schedules as well.
            scheduler.deleteJob(JobKey.jobKey(identity));
        } catch (SchedulerException e) {
            QuickbackupmultiReforged.logger.error("Failed to stop schedule: {}", identity, e);
        }
    }

    /**
     * Shut down the process-wide Quartz scheduler.
     *
     * <p>{@link StdSchedulerFactory#getScheduler()} looks the scheduler up in a global repository by
     * name, so every {@code ModSchedule} shares one {@code DefaultQuartzScheduler} instance. This
     * therefore stops <em>all</em> schedules and must only be called once each job has been removed.
     * Quartz worker threads are not daemons, so it does need to be called on server stop, otherwise
     * the JVM would not exit.
     */
    public static void shutdownSharedScheduler() {
        // getScheduler() would build a scheduler and its thread pool if none existed yet. Every
        // schedule is disabled by default, so without this guard a plain server stop would spin one
        // up purely to shut it down again.
        if (!sharedSchedulerInUse) {
            return;
        }
        try {
            Scheduler sharedScheduler = new StdSchedulerFactory().getScheduler();
            if (!sharedScheduler.isShutdown()) {
                sharedScheduler.shutdown(true);
            }
            sharedSchedulerInUse = false;
        } catch (SchedulerException e) {
            QuickbackupmultiReforged.logger.error("Failed to stop scheduler", e);
        }
    }

    @Override
    public ModSchedule setExecutor(Runnable executor) {
        this.executor = executor;
        return this;
    }

    @Override
    public boolean isRunning() {
        if (scheduler == null) {
            return false;
        }
        try {
            return scheduler.isStarted()
                && !scheduler.isShutdown()
                && scheduler.checkExists(JobKey.jobKey(identity));
        } catch (SchedulerException e) {
            return false;
        }
    }

    @Override
    public long getNextExecuteTime() {
        // Quartz stores a *clone* of the trigger and only ever advances that copy, so the local
        // field's next fire time stays frozen at the value it was given when the job was scheduled.
        // Ask the scheduler for the live trigger instead.
        Trigger currentTrigger = null;
        if (scheduler != null) {
            try {
                currentTrigger = scheduler.getTrigger(TriggerKey.triggerKey(identity));
            } catch (SchedulerException e) {
                QuickbackupmultiReforged.logger.error("Failed to get trigger for schedule: {}", identity, e);
            }
        }
        if (currentTrigger == null) {
            currentTrigger = trigger;
        }

        if (currentTrigger == null || currentTrigger.getNextFireTime() == null) {
            return NO_NEXT_EXECUTE_TIME;
        }
        return currentTrigger.getNextFireTime().getTime();
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
        QuickbackupmultiReforged.logger.info("Schedule {} execute in {}", identity, QuickbackupmultiReforged.formatTimestamp(System.currentTimeMillis()));
        executor.run();
        QuickbackupmultiReforged.logger.info(
            "Schedule {} execute done, next execute time: {}",
            identity,
            formatNextExecuteTime()
        );
    }
}
