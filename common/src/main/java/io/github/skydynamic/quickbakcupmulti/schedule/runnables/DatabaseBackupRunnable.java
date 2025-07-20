package io.github.skydynamic.quickbakcupmulti.schedule.runnables;

import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class DatabaseBackupRunnable implements Runnable {
    @Override
    public void run() {
        Path storagePath = Path.of(QuickbakcupmultiReforged.getModConfig().getStoragePath());
        File databaseFile = storagePath.resolve("QuickBakcupMulti.mv.db").toFile();
        File databaseBackupPath = storagePath.resolve("databaseBackup").toFile();
        if (!databaseBackupPath.exists()) {
            databaseBackupPath.mkdirs();
        }

        if (databaseFile.exists()) {
            try {
                FileUtils.copyFile(databaseFile, FileUtils.getFile(databaseBackupPath, "database-" + System.currentTimeMillis() + ".mv.db"));
            } catch (IOException e) {
                QuickbakcupmultiReforged.logger.error("Database Backup Failed", e);
            }
        } else {
            QuickbakcupmultiReforged.logger.error("Database File Not Found");
        }
    }
}
