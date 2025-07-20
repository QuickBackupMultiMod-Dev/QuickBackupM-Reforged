package io.github.skydynamic.quickbakcupmulti.schedule.quartz;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

public class DisableQuartzInfoLogger {
    public static void disable() {
        Configurator.setLevel("org.quartz", Level.ERROR);
    }
}
