package io.github.skydynamic.quickbackupmulti.event;

import io.github.skydynamic.quickbackupmulti.schedule.ScheduleManager;

/**
 * Runs while the server is shutting down but before {@link OnServerStoppedHandler}.
 *
 * <p>Schedules have to stop firing here rather than in the stopped handler. A schedule runs on a Quartz
 * worker thread and a backup calls back onto the server thread to save the world; once the server has
 * left its tick loop nothing drains that queue again, so a job that fires during shutdown waits for a
 * task that will never run. Quartz worker threads are not daemons, so a single job stuck that way is
 * enough to keep the JVM alive forever — the server looks like it ignored {@code stop} and has to be
 * killed.
 *
 * <p>Only the jobs are removed; the shared scheduler itself is shut down later by
 * {@link OnServerStoppedHandler}. Shutting the pool down here would mean waiting for an in-flight job
 * from the very thread that job needs, which is a deadlock.
 */
public class OnServerStoppingHandler {
    public static void handle() {
        ScheduleManager.stopAllScheduleJobs();
    }
}
