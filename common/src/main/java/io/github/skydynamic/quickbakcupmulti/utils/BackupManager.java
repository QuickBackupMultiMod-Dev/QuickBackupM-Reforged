package io.github.skydynamic.quickbakcupmulti.utils;

import io.github.skydynamic.increment.storage.lib.database.Database;
import io.github.skydynamic.increment.storage.lib.database.DatabaseTables;
import io.github.skydynamic.increment.storage.lib.database.StorageInfo;
import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import io.github.skydynamic.quickbakcupmulti.database.DatabaseManager;
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
import java.util.*;
import java.util.function.Consumer;

import static io.github.skydynamic.quickbakcupmulti.translate.Translate.tr;

public class BackupManager {
    private static final Logger logger = LoggerFactory.getLogger("Qbm-BackupManager");
    private static final IOFileFilter folderFilter = new NotFileFilter(new NameFileFilter(QuickbakcupmultiReforged.getModConfig().getIgnoredFolders()));
    private static final IOFileFilter fileFilter = new NotFileFilter(new NameFileFilter(QuickbakcupmultiReforged.getModConfig().getIgnoredFiles()));

    public static Path getBackupPath() {
        Path path = Path.of(QuickbakcupmultiReforged.getModConfig().getStoragePath()).resolve(QuickbakcupmultiReforged.getModContainer().getLevelId());
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
        if (file.isDirectory() && QuickbakcupmultiReforged.getDatabase().storageExists(file.getName())) {
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
        if (QuickbakcupmultiReforged.getDatabase().storageExists(name)) {
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

            QuickbakcupmultiReforged.getManager().incrementalStorage(
                name,
                desc,
                QuickbakcupmultiReforged.getModContainer().getCurrentSavePath().toFile(),
                fileFilter,
                folderFilter
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
        }
    }

    public static boolean deleteBackup(CommandSourceStack commandSource, String name) {
        if (QuickbakcupmultiReforged.getDatabase().storageExists(name)) {
            QuickbakcupmultiReforged.getManager().deleteStorage(name);
            return true;
        } else {
            return false;
        }
    }

    public static void restoreBackup(String name) {
        Map<String, String> hashMap = QuickbakcupmultiReforged.getDatabase().getFileHashMap(name);
        Path savePath = QuickbakcupmultiReforged.getModContainer().getCurrentSavePath();
        try {
            for (Map.Entry<String, String> entry : hashMap.entrySet()) {
                String fileHash = entry.getKey();
                String fileName = entry.getValue();
                String hashStart = fileHash.substring(0, 2);
                File hashFile = getBackupPath().resolve("blogs/" + hashStart + "/" + fileHash).toFile();
                File targetDir = savePath.resolve(fileName).toFile();
                FileUtils.copyFile(hashFile, targetDir);
            }
        } catch (IOException e) {
            logger.error("Restore Failed", e);
        }
    }

    public static void deleteWorld(String worldName) {
        try {
            FileUtils.deleteDirectory(getBackupPath().toFile());
            DatabaseManager databaseManager = new DatabaseManager(
                "QuickBakcupMulti",
                QuickbakcupmultiReforged.getModConfig().getStoragePath(),
                UUID.nameUUIDFromBytes(worldName.getBytes())
            );
            Database database = new Database(databaseManager);
            List<StorageInfo> storageInfoList = database.getAllStorageInfo();
            for (StorageInfo storageInfo : storageInfoList) {
                database.deleteTableValue(storageInfo.getName(), DatabaseTables.FILE_HASH);
                database.deleteTableValue(storageInfo.getName(), DatabaseTables.STORAGE_INFO);
            }
        } catch (IOException e) {
            QuickbakcupmultiReforged.logger.error("Delete Failed", e);
        }
    }
}
