package io.github.skydynamic.quickbakcupmulti.schedule;

public interface IModSchedule {
    String getName();

    boolean startSchedule();
    void stopSchedule();

    boolean isRunning();

    long getNextExecuteTime();

    IModSchedule setExcutor(Runnable executor);
}
