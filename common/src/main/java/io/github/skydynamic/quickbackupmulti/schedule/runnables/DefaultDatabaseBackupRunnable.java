package io.github.skydynamic.quickbackupmulti.schedule.runnables;

import io.github.skydynamic.quickbackupmulti.QuickbackupmultiReforged;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class DefaultDatabaseBackupRunnable implements Runnable {
    @Override
    public void run() {
        Path storagePath = Path.of(QuickbackupmultiReforged.getModConfig().getStoragePath());
        File databaseFile = storagePath.resolve("QuickBackupMulti.mv.db").toFile();
        File databaseBackupPath = storagePath.resolve(QuickbackupmultiReforged.getModContainer().getLevelId()).resolve("databaseBackup").toFile();
        if (!databaseBackupPath.exists()) {
            databaseBackupPath.mkdirs();
        }

        if (databaseFile.exists()) {
            try {
                FileUtils.copyFile(databaseFile, FileUtils.getFile(databaseBackupPath, "database-" + System.currentTimeMillis() + ".mv.db"));
            } catch (IOException e) {
                QuickbackupmultiReforged.logger.error("Database Backup Failed", e);
            }
        } else {
            QuickbackupmultiReforged.logger.error("Database File Not Found");
        }
    }
}
