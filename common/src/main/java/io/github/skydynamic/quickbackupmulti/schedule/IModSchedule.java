package io.github.skydynamic.quickbackupmulti.schedule;

public interface IModSchedule {
    String getName();

    boolean startSchedule();
    void stopSchedule();

    boolean isRunning();

    long getNextExecuteTime();

    boolean resetTimer();

    IModSchedule setExecutor(Runnable executor);

    void execute();
}
