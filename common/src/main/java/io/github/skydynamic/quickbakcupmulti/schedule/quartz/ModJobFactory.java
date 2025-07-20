package io.github.skydynamic.quickbakcupmulti.schedule.quartz;

import org.quartz.Job;
import org.quartz.Scheduler;
import org.quartz.simpl.SimpleJobFactory;
import org.quartz.spi.TriggerFiredBundle;

public class ModJobFactory extends SimpleJobFactory {
    private final Job jobInstance;

    public ModJobFactory(Job jobInstance) {
        this.jobInstance = jobInstance;
    }

    @Override
    public Job newJob(TriggerFiredBundle bundle, Scheduler scheduler){
        return jobInstance;
    }
}
