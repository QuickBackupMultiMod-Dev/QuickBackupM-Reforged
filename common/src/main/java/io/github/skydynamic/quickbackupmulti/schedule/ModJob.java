package io.github.skydynamic.quickbackupmulti.schedule;

import io.github.skydynamic.quickbackupmulti.QuickbackupmultiReforged;
import org.quartz.Job;
import org.quartz.JobExecutionContext;

public class ModJob implements Job {
    @Override
    public void execute(JobExecutionContext context) {
        String jobIdentity = context.getJobDetail().getKey().getName();
       QuickbackupmultiReforged.getModContainer().getSchedule(jobIdentity).ifPresent(ModJob::executeSchedule);
    }

    public static void executeSchedule(IModSchedule schedule) {
        schedule.execute();
    }
}
