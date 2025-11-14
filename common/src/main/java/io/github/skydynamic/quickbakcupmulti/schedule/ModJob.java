package io.github.skydynamic.quickbakcupmulti.schedule;

import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import org.quartz.Job;
import org.quartz.JobExecutionContext;

public class ModJob implements Job {
    @Override
    public void execute(JobExecutionContext context) {
        String jobIdentity = context.getJobDetail().getKey().getName();
       QuickbakcupmultiReforged.getModContainer().getSchedule(jobIdentity).ifPresent(ModJob::executeSchedule);
    }

    public static void executeSchedule(IModSchedule schedule) {
        schedule.execute();
    }
}
