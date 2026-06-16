package io.github.skydynamic.quickbackupmulti.cli;

import io.github.skydynamic.increment.storage.lib.database.Database;
import io.github.skydynamic.increment.storage.lib.database.StorageInfo;
import io.github.skydynamic.increment.storage.lib.manager.IConfig;
import io.github.skydynamic.increment.storage.lib.utils.StorageManager;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A connected view of one world/collection of backups, reusing the same {@code incremental-storage-lib}
 * primitives the mod uses. Blob layout (mirrors {@code BackupManager}): blobs live under
 * {@code <storagePath>/<levelId>/blogs/<hash[0:2]>/<hash>} (or {@code blogs_temp/<hash>} for temp blobs),
 * while the H2 database is {@code <storagePath>/QuickBackupMulti.mv.db}, namespaced by collection UUID.
 */
public class BackupTarget implements AutoCloseable {
    private final Database database;
    private final Path storagePath;
    private final Path blobRoot;

    private BackupTarget(Database database, Path storagePath, Path blobRoot) {
        this.database = database;
        this.storagePath = storagePath;
        this.blobRoot = blobRoot;
    }

    public static BackupTarget open(Path storagePath, String collectionName, String levelId) {
        UUID collectionUuid = UUID.nameUUIDFromBytes(collectionName.getBytes());
        Database database = new Database(new CliDatabaseManager(storagePath.toString(), collectionUuid));
        Path blobRoot = storagePath.resolve(levelId);
        return new BackupTarget(database, storagePath, blobRoot);
    }

    /** Default export destination for a backup: {@code <storagePath>/export/<name>}, matching the in-game command. */
    public Path defaultExportDir(String name) {
        return storagePath.resolve("export").resolve(name);
    }

    public List<StorageInfo> listBackups() {
        return database.getAllStorageInfo().stream()
            .filter(StorageInfo::getUseIncrementalStorage)
            .toList();
    }

    public boolean exists(String name) {
        return database.storageExists(name);
    }

    /**
     * Reconstruct a backup's files from the deduplicated blob store into {@code targetRoot}.
     */
    public void reconstructTo(String name, Path targetRoot) throws IOException {
        Map<String, String> hashMap = database.getFileHashMap(name);
        Files.createDirectories(targetRoot);
        for (Map.Entry<String, String> entry : hashMap.entrySet()) {
            String fileHash = entry.getKey();
            String fileName = entry.getValue();
            File blob;
            if (fileHash.startsWith("blog_temp")) {
                blob = blobRoot.resolve("blogs_temp").resolve(fileHash).toFile();
            } else {
                String hashStart = fileHash.substring(0, 2);
                blob = blobRoot.resolve("blogs").resolve(hashStart).resolve(fileHash).toFile();
            }
            FileUtils.copyFile(blob, targetRoot.resolve(fileName).toFile());
        }
    }

    /**
     * Delete a backup, freeing any blobs no longer referenced by other backups (same semantics as the in-game
     * {@code /qb delete}, which calls {@link StorageManager#deleteStorage}).
     */
    public void delete(String name) {
        IConfig config = new IConfig() {
            @Override
            public String getStoragePath() {
                return blobRoot.toString();
            }
        };
        new StorageManager(database, config).deleteStorage(name);
    }

    @Override
    public void close() {
        database.closeDatabase();
    }
}
