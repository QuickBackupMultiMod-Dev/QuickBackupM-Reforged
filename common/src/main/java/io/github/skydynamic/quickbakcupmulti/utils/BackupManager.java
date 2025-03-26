package io.github.skydynamic.quickbakcupmulti.utils;

import io.github.skydynamic.increment.storage.lib.database.StorageInfo;
import io.github.skydynamic.increment.storage.lib.util.IndexUtil;
import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.apache.commons.io.filefilter.NotFileFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static io.github.skydynamic.quickbakcupmulti.translate.Translate.tr;

public class BackupManager {
    private static final Logger logger = LoggerFactory.getLogger("Qbm-BackupManager");
    private static final IOFileFilter folderFilter = new NotFileFilter(new NameFileFilter(QuickbakcupmultiReforged.getModConfig().getIgnoredFolders()));
    private static final IOFileFilter fileFilter = new NotFileFilter(new NameFileFilter(QuickbakcupmultiReforged.getModConfig().getIgnoredFiles()));

    public static Path getBackupPath() {
        Path path = Path.of(QuickbakcupmultiReforged.getModConfig().getStoragePath());
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                QuickbakcupmultiReforged.logger.error("Create backup path error: {}", e.getMessage());
            }
        }
        return path;
    }

    private static void getBackupExists(File file, Consumer<String> consumer) {
        if (file.isDirectory() && QuickbakcupmultiReforged.getStorager().storageExists(file.getName())) {
            consumer.accept(file.getName());
        }
    }

    private static List<String> getBackupExistsWithList(Path path) {
        List<String> backupsDirList = new ArrayList<>();
        for (File file : Objects.requireNonNull(path.toFile().listFiles())) {
            getBackupExists(file, backupsDirList::add);
        }
        return backupsDirList;
    }

    public static List<String> getBackupsList() {
        return getBackupExistsWithList(getBackupPath());
    }

    public static void makeBackup(CommandSourceStack commandSource, String name, String desc) {
        if (QuickbakcupmultiReforged.getStorager().storageExists(name)) {
            commandSource.sendSystemMessage(Component.nullToEmpty(tr("quickbackupmulti.make.fail_exists")));
            return;
        }
        long startTime = System.currentTimeMillis();
        try {
            commandSource.sendSystemMessage(Component.nullToEmpty(tr("quickbackupmulti.make.start")));
            MinecraftServer server = commandSource.getServer();
            server.executeIfPossible(() -> server.saveEverything(true, true, true));
            for (ServerLevel serverLevel : server.getAllLevels()) {
                if (serverLevel == null || serverLevel.noSave) continue;
                serverLevel.noSave = true;
            }

            StorageInfo storageInfo = new StorageInfo(name, desc, System.currentTimeMillis(), true, new ArrayList<>());

            QuickbakcupmultiReforged.getStorager()
                .incrementalStorage(
                    storageInfo, QuickbakcupmultiReforged.getModContainer().getCurrentSavePath(),
                    BackupManager.getBackupPath().resolve(name),
                    fileFilter, folderFilter
                );

            long endTime = System.currentTimeMillis();
            double intervalTime = (endTime - startTime) / 1000.0;
            commandSource.sendSystemMessage(Component.nullToEmpty(tr("quickbackupmulti.make.success", intervalTime)));

            // TODO: Schedule Backup

            for (ServerLevel serverLevel : server.getAllLevels()) {
                if (serverLevel == null || !serverLevel.noSave) continue;
                serverLevel.noSave = false;
            }
        } catch (Exception e) {
            logger.error("Make Backup Failed", e);
            commandSource.sendSystemMessage(Component.nullToEmpty(tr("quickbackupmulti.make.fail",  e.toString())));
            try {
                FileUtils.forceDeleteOnExit(getBackupPath().resolve(name).toFile());
            } catch (IOException ex) {
                logger.error("Delete Backup Failed", ex);
            }
        }
    }

    public static boolean deleteBackup(CommandSourceStack commandSource, String name) {
        if (QuickbakcupmultiReforged.getStorager().storageExists(name)) {
            try {
                IndexUtil.reIndex(name, "");
                QuickbakcupmultiReforged.getStorager().deleteStorage(name);
                FileUtils.deleteDirectory(getBackupPath().resolve(name).toFile());
                return true;
            } catch (IOException e) {
                logger.error("Delete Backup Failed", e);
                return false;
            }
        } else return false;
    }

    public static void restoreBackup(String name) {
        File targetBackupSlot = getBackupPath().resolve(name).toFile();
        Path savePath = QuickbakcupmultiReforged.getModContainer().getCurrentSavePath();
        try {
            for (File file : FileUtils.listFiles(savePath.toFile(), fileFilter, folderFilter)) {
                if (file.equals(savePath.toFile())) continue;
                FileUtils.forceDelete(file);
            }

            FileUtils.copyDirectory(targetBackupSlot, savePath.toFile());
            IndexUtil.copyIndexFile(
                name,
                Path.of(QuickbakcupmultiReforged.getModConfig().getStoragePath()).resolve(""),
                savePath.toFile()
            );
        } catch (IOException e) {
            logger.error("Restore Failed", e);
        }
    }
}
