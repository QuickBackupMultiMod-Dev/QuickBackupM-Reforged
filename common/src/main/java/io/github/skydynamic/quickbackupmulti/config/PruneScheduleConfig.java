package io.github.skydynamic.quickbackupmulti.config;

import lombok.Getter;

@SuppressWarnings("FieldMayBeFinal")
@Getter
public class PruneScheduleConfig extends ScheduleConfig {
    public String timezoneOverride = null;
    public PbsConfig regularBackup = new PbsConfig();
    public ScheduleConfig temporaryBackup = new ScheduleConfig();
}
