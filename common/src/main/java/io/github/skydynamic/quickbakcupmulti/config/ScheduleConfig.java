package io.github.skydynamic.quickbakcupmulti.config;

public class ScheduleConfig {
    public boolean enabled;
    public Integer interval = 7200;
    public String crontab = null;
    public String jitter = "1m";
}
