package io.github.skydynamic.quickbackupmulti.schedule;

import io.github.skydynamic.quickbackupmulti.QuickbackupmultiReforged;

public interface IModSchedule {
    /** Returned by {@link #getNextExecuteTime()} when the schedule will not fire again. */
    long NO_NEXT_EXECUTE_TIME = -1L;

    String getName();

    boolean startSchedule();
    void stopSchedule();

    boolean isRunning();

    long getNextExecuteTime();

    boolean resetTimer();

    IModSchedule setExecutor(Runnable executor);

    void execute();

    /** The next execute time for logging, or {@code "unknown"} if the schedule will not fire again. */
    default String formatNextExecuteTime() {
        long nextExecuteTime = getNextExecuteTime();
        return nextExecuteTime == NO_NEXT_EXECUTE_TIME
            ? "unknown"
            : QuickbackupmultiReforged.formatTimestamp(nextExecuteTime);
    }
}
