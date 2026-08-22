package io.github.skydynamic.quickbackupmulti.utils;

import io.github.skydynamic.increment.storage.lib.database.Database;
import io.github.skydynamic.increment.storage.lib.database.DatabaseTables;
import io.github.skydynamic.increment.storage.lib.database.StorageInfo;
import io.github.skydynamic.quickbackupmulti.DatabaseCache;
import io.github.skydynamic.quickbackupmulti.QuickbackupmultiReforged;
import io.github.skydynamic.quickbackupmulti.database.DatabaseManager;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.github.skydynamic.quickbackupmulti.translate.Translate.tr;

public class BackupManager {
    private static final Logger logger = LoggerFactory.getLogger("Qbm-BackupManager");
    private static final IOFileFilter folderFilter = new NotFileFilter(new NameFileFilter(QuickbackupmultiReforged.getModConfig().getIgnoredFolders()));
    private static final IOFileFilter fileFilter = new NotFileFilter(new NameFileFilter(QuickbackupmultiReforged.getModConfig().getIgnoredFiles()));

    public static Path getBackupPath() {
        Path path = Path.of(QuickbackupmultiReforged.getModConfig().getStoragePath()).resolve(QuickbackupmultiReforged.getModContainer().getLevelId());
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                logger.error("Create backup path error: {}", e.getMessage());
            }
        }
        return path;
    }

    public static List<StorageInfo> getBackupsList() {
        List<StorageInfo> backupList;
        if (!QuickbackupmultiReforged.getModConfig().isCacheDatabase()) {
            backupList = QuickbackupmultiReforged.getDatabase()
                .getAllStorageInfo()
                .stream()
                .filter(StorageInfo::getUseIncrementalStorage)
                .toList();
        } else {
            backupList = DatabaseCache.getStorageInfoCaches();
        }
        return backupList;
    }

    public static List<StorageInfo> getSortedBackups() {
        return getBackupsList().stream()
            .sorted(Comparator.comparingLong(StorageInfo::getTimestamp))
            .toList();
    }

    public static StorageInfo getBackupByIndex(int index) {
        List<StorageInfo> backups = getSortedBackups();
        if (index < 1 || index > backups.size()) {
            return null;
        }
        return backups.get(index - 1);
    }

    public static int getBackupIndex(String name) {
        List<StorageInfo> backups = getSortedBackups();
        for (int i = 0; i < backups.size(); i++) {
            if (backups.get(i).getName().equals(name)) {
                return i + 1;
            }
        }
        return -1;
    }

    private static void makeFullBackup() {
        if (QuickbackupmultiReforged.getModConfig().getFullBackupInterval() == -1) {
            return;
        }

        if (!getBackupPath().resolve("full").toFile().exists()) {
            logger.info("Do not have a full backup, make a full backup for future use...");
            QuickbackupmultiReforged.getManager().fullStorage(
                "FullBackup-" + (QuickbackupmultiReforged.getModContainer().getLevelId().isEmpty() ? "Server" : QuickbackupmultiReforged.getModContainer().getLevelId()),
                "Full backup",
                QuickbackupmultiReforged.getModContainer().getCurrentSavePath().toFile(),
                fileFilter,
                folderFilter
            );
        } else {
            List<StorageInfo> storageInfoList = QuickbackupmultiReforged.getDatabase().getAllStorageInfo();
            List<StorageInfo> incrementalBackups = storageInfoList.stream().filter(StorageInfo::getUseIncrementalStorage).toList();
            List<StorageInfo> fullBackups = storageInfoList.stream().filter(it -> !it.getUseIncrementalStorage()).toList();
            if (!incrementalBackups.isEmpty() && incrementalBackups.size() % QuickbackupmultiReforged.getModConfig().getFullBackupInterval() == 0) {
                // Room has to be made for the full backup that is about to be created, so prune down
                // to saveFullBackupCount - 1. Every removal drops the database row as well: deleting
                // only the directory used to leave the record behind, so the same (already deleted)
                // backup was picked as "oldest" on every later run and the rotation never advanced.
                pruneFullBackups(fullBackups, QuickbackupmultiReforged.getModConfig().getSaveFullBackupCount() - 1);
                logger.info("Make a full backup for future use...");
                QuickbackupmultiReforged.getManager().fullStorage(
                    "FullBackup-" + (QuickbackupmultiReforged.getModContainer().getLevelId().isEmpty() ? "Server" : QuickbackupmultiReforged.getModContainer().getLevelId()),
                    "Full backup",
                    QuickbackupmultiReforged.getModContainer().getCurrentSavePath().toFile(),
                    fileFilter,
                    folderFilter
                );
            }
        }
    }

    /**
     * Drop the oldest full backups until at most {@code keepCount} remain.
     *
     * <p>A full backup lives in two places: the {@code full/<name>} directory and a
     * {@code STORAGE_INFO} row. Removing only the directory leaves the row behind, and since the
     * rotation reads its candidates from the database the orphaned row is selected as "oldest"
     * again on every later run — the deletion silently no-ops on the missing directory, the count
     * never falls below the limit, and full backups grow without bound. So always drop both.
     *
     * <p>{@link io.github.skydynamic.increment.storage.lib.utils.StorageManager#deleteStorage} is
     * deliberately not used here: it is written for incremental backups and starts by reading the
     * backup's {@code FILE_HASH} row, which a full backup never has, so it would throw.
     */
    static void pruneFullBackups(List<StorageInfo> fullBackups, int keepCount) {
        for (StorageInfo fullBackup : FullBackupRotation.selectExpired(fullBackups, keepCount)) {
            logger.info("Delete oldest full backup: {}", fullBackup.getName());
            try {
                FileUtils.deleteDirectory(getBackupPath().resolve("full").resolve(fullBackup.getName()).toFile());
            } catch (IOException e) {
                // Still drop the database row: leaving it behind is what made the rotation loop
                // forever, and a stale row is worse than a stale directory.
                logger.error("delete oldest full backup failed: ", e);
            }
            QuickbackupmultiReforged.getDatabase().deleteTableValue(fullBackup.getName(), DatabaseTables.STORAGE_INFO);
            QuickbackupmultiReforged.getDatabase().deleteTableValue(fullBackup.getName(), DatabaseTables.FILE_HASH);
        }
    }

    public static void makeBackup(CommandSourceStack commandSource, String name, String desc) {
        if (QuickbackupmultiReforged.getDatabase().storageExists(name)) {
            commandSource.sendSystemMessage(Component.nullToEmpty(tr("quickbackupmulti.make.fail_exists")));
            return;
        }
        long startTime = System.currentTimeMillis();
        try {
            commandSource.sendSystemMessage(Component.nullToEmpty(tr("quickbackupmulti.make.start")));
            MinecraftServer server = commandSource.getServer();
            server.executeBlocking(() -> {
                server.saveEverything(true, true, true);
                for (ServerLevel serverLevel : server.getAllLevels()) {
                    if (serverLevel == null || serverLevel.noSave) continue;
                    serverLevel.noSave = true;
                }
            });

            QuickbackupmultiReforged.getManager().incrementalStorage(
                name,
                desc,
                QuickbackupmultiReforged.getModContainer().getCurrentSavePath().toFile(),
                fileFilter,
                folderFilter
            );

            long endTime = System.currentTimeMillis();
            double intervalTime = (endTime - startTime) / 1000.0;
            commandSource.sendSystemMessage(Component.nullToEmpty(tr("quickbackupmulti.make.success", intervalTime)));

            server.executeBlocking(() -> {
                for (ServerLevel serverLevel : server.getAllLevels()) {
                    if (serverLevel == null || !serverLevel.noSave) continue;
                    serverLevel.noSave = false;
                }
            });
        } catch (Exception e) {
            logger.error("Make Backup Failed", e);
            commandSource.sendSystemMessage(Component.nullToEmpty(tr("quickbackupmulti.make.fail",  e.toString())));
        } finally {
            makeFullBackup();
        }
    }

    public static void makeTempBackup() {
        logger.info("Make a temp backup...");
        QuickbackupmultiReforged.getManager().incrementalStorageTemp(
            QuickbackupmultiReforged.getModContainer().getCurrentSavePath().toFile(), fileFilter, folderFilter
        );
        logger.info("Make a temp backup success.");
    }

    public static boolean deleteBackup(CommandSourceStack commandSource, String name) {
        if (QuickbackupmultiReforged.getDatabase().storageExists(name)) {
            QuickbackupmultiReforged.getManager().deleteStorage(name);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Reconstruct a backup's files from the hash-deduplicated blob store into {@code targetRoot}.
     * This is the shared logic behind both restore (target = live world) and export (target = arbitrary directory).
     */
    private static boolean reconstructBackup(String name, Path targetRoot, RestoreExtraRunnable extraRunnable) {
        Map<String, String> hashMap = QuickbackupmultiReforged.getDatabase().getFileHashMap(name);
        try {
            int index = 0;
            for (Map.Entry<String, String> entry : hashMap.entrySet()) {
                String fileHash = entry.getKey();
                String fileName = entry.getValue();
                File hashFile;
                if (fileHash.startsWith("blog_temp")) {
                    hashFile = getBackupPath().resolve("blogs_temp").resolve(fileHash).toFile();
                } else {
                    String hashStart = fileHash.substring(0, 2);
                    hashFile = getBackupPath().resolve("blogs").resolve(hashStart).resolve(fileHash).toFile();
                }
                File targetDir = targetRoot.resolve(fileName).toFile();
                FileUtils.copyFile(hashFile, targetDir);

                index++;

                if (extraRunnable != null) {
                    extraRunnable.execute(hashMap.size(), index);
                }
            }
            return true;
        } catch (IOException e) {
            logger.error("Reconstruct backup failed", e);
            return false;
        }
    }

    public static boolean restoreBackup(String name, RestoreExtraRunnable extraRunnable) {
        Path savePath = QuickbackupmultiReforged.getModContainer().getCurrentSavePath();
        return reconstructBackup(name, savePath, extraRunnable);
    }

    public static boolean restoreBackup(String name) {
        return restoreBackup(name, null);
    }

    /**
     * Export (reconstruct) a backup into {@code targetDir} as plain world files.
     *
     * @return {@code true} on success, {@code false} if the backup does not exist or reconstruction failed.
     */
    public static boolean exportBackup(String name, Path targetDir) {
        if (!QuickbackupmultiReforged.getDatabase().storageExists(name)) {
            return false;
        }
        try {
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            logger.error("Create export directory failed", e);
            return false;
        }
        return reconstructBackup(name, targetDir, null);
    }

    public static void deleteWorld(String worldName) {
        DatabaseManager databaseManager = new DatabaseManager(
            "QuickBackupMulti",
            QuickbackupmultiReforged.getModConfig().getStoragePath(),
            UUID.nameUUIDFromBytes(worldName.getBytes())
        );
        Database database = new Database(databaseManager);
        try {
            FileUtils.deleteDirectory(getBackupPath().toFile());
            List<StorageInfo> storageInfoList = database.getAllStorageInfo();
            for (StorageInfo storageInfo : storageInfoList) {
                database.deleteTableValue(storageInfo.getName(), DatabaseTables.FILE_HASH);
                database.deleteTableValue(storageInfo.getName(), DatabaseTables.STORAGE_INFO);
            }
        } catch (IOException e) {
            logger.error("Delete Failed", e);
        } finally {
            database.closeDatabase();
        }
    }

    @FunctionalInterface
    public interface RestoreExtraRunnable {
        void execute(int totalProgress, int currentProgress);
    }
}
