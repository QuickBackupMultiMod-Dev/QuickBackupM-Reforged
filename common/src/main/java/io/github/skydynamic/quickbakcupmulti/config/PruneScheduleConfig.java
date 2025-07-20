package io.github.skydynamic.quickbakcupmulti.config;

import lombok.Getter;

@SuppressWarnings("FieldMayBeFinal")
@Getter
public class PruneScheduleConfig extends ScheduleConfig {
    private String timezoneOverride = null;
    private PbsConfig regularBackup = new PbsConfig();
}
