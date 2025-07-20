package io.github.skydynamic.quickbakcupmulti.config;

import lombok.Getter;

@SuppressWarnings("FieldMayBeFinal")
@Getter
public class DatabaseConfig {
    private ScheduleConfig backup = new ScheduleConfig();

}
