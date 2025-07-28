package io.github.skydynamic.quickbakcupmulti.config;

import lombok.Getter;

@SuppressWarnings("FieldMayBeFinal")
@Getter
public class PruneScheduleConfig extends ScheduleConfig {
    public String timezoneOverride = null;
    public PbsConfig regularBackup = new PbsConfig();
}
