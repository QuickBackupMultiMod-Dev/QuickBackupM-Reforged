package io.github.skydynamic.quickbakcupmulti.config;

import lombok.Getter;

@SuppressWarnings("FieldMayBeFinal")
@Getter
public class DatabaseConfig {
    private BackupConfig backupConfig = new BackupConfig();

    public static class BackupConfig {
        public boolean enabled;
        public Integer interval = 7200;
        public String crontab = null;
        public String jitter = "1m";

        @Override
        public String toString() {
            return "DatabaseBackupConfig{" +
                    "enabled=" + enabled +
                    ", interval=" + interval + "s" +
                    ", crontab='" + crontab + '\'' +
                    ", jitter='" + jitter + '\'' +
                    '}';
        }
    }
}
