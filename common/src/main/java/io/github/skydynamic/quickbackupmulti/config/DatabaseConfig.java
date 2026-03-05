package io.github.skydynamic.quickbackupmulti.config;

import lombok.Getter;

@SuppressWarnings("FieldMayBeFinal")
@Getter
public class DatabaseConfig {
    public ScheduleConfig backup = new ScheduleConfig();
}
