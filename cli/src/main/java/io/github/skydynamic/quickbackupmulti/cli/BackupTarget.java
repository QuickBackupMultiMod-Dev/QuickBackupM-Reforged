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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Information about a missing file discovered during reconstruction.
 *
 * @param fileHash the blob's hash identifier (used to locate it in the store)
 * @param fileName the backup's logical path for this file (what would have been restored)
 */
record MissingFileInfo(String fileHash, String fileName) {}

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

    public static BackupTarget open(Path storagePath, CollectionInfo collection) {
        return open(storagePath, collection.collectionName(), collection.levelId());
    }

    /** Folder names under the storage root that are not world collections. */
    private static final Set<String> RESERVED_FOLDERS = Set.of("export", "blogs", "blogs_temp", "full");

    /** Folders whose presence marks a directory as a QBM collection root. */
    private static final Set<String> COLLECTION_MARKERS = Set.of("blogs", "blogs_temp", "full");

    /**
     * Discover the collections that actually hold backups under {@code storagePath}.
     *
     * <p>Mirrors the mod's on-disk layout: each singleplayer/integrated world stores its blobs under
     * {@code <storagePath>/<levelId>/{blogs,blogs_temp,full}} (so the folder name is the plaintext level id and the
     * collection name), while the dedicated-server collection ({@code "server"}) stores them directly under
     * {@code <storagePath>/{blogs,blogs_temp,full}}. Collections with zero incremental backups are omitted.
     *
     * <p>Results are sorted by backup count (descending) then by label, with the server collection listed first.
     */
    public static List<CollectionInfo> discoverCollections(Path storagePath) throws IOException {
        List<CollectionInfo> collections = new ArrayList<>();

        // Dedicated-server collection: blob folders sit directly under the storage root.
        if (hasCollectionMarker(storagePath)) {
            int count = countBackups(storagePath, "server", "");
            if (count > 0) {
                collections.add(new CollectionInfo("server", "", true, count));
            }
        }

        // World collections: one sub-folder per level id.
        if (Files.isDirectory(storagePath)) {
            try (Stream<Path> entries = Files.list(storagePath)) {
                List<Path> worldDirs = entries
                    .filter(Files::isDirectory)
                    .filter(p -> !RESERVED_FOLDERS.contains(p.getFileName().toString()))
                    .filter(BackupTarget::hasCollectionMarker)
                    .toList();
                for (Path dir : worldDirs) {
                    String levelId = dir.getFileName().toString();
                    int count = countBackups(storagePath, levelId, levelId);
                    if (count > 0) {
                        collections.add(new CollectionInfo(levelId, levelId, false, count));
                    }
                }
            }
        }

        collections.sort(
            Comparator.comparing(CollectionInfo::server).reversed()
                .thenComparing(Comparator.comparingInt(CollectionInfo::backupCount).reversed())
                .thenComparing(CollectionInfo::levelId)
        );
        return collections;
    }

    private static boolean hasCollectionMarker(Path dir) {
        for (String marker : COLLECTION_MARKERS) {
            if (Files.isDirectory(dir.resolve(marker))) {
                return true;
            }
        }
        return false;
    }

    private static int countBackups(Path storagePath, String collectionName, String levelId) {
        try (BackupTarget target = open(storagePath, collectionName, levelId)) {
            return target.listBackups().size();
        }
    }

    /**
     * Discover valid singleplayer world saves under a Minecraft {@code saves} directory, for the {@code list}
     * command's singleplayer-mode picker. A folder is considered a valid save if it directly contains a
     * {@code level.dat} file. Unlike {@link #discoverCollections}, this scans the actual saves directory rather
     * than the backup storage directory, and does not require the world to already have backups.
     *
     * @return valid world/level ids, sorted alphabetically
     */
    public static List<String> discoverSaveFolders(Path savePath) throws IOException {
        if (!Files.isDirectory(savePath)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(savePath)) {
            return entries
                .filter(Files::isDirectory)
                .filter(p -> Files.isRegularFile(p.resolve("level.dat")))
                .map(p -> p.getFileName().toString())
                .sorted()
                .toList();
        }
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
     *
     * @return list of files that could not be found in the blob store
     */
    public List<MissingFileInfo> reconstructTo(String name, Path targetRoot) throws IOException {
        Map<String, String> hashMap = database.getFileHashMap(name);
        Files.createDirectories(targetRoot);
        List<MissingFileInfo> missingFiles = new ArrayList<>();

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

            if (!blob.exists()) {
                missingFiles.add(new MissingFileInfo(fileHash, fileName));
                continue;
            }

            FileUtils.copyFile(blob, targetRoot.resolve(fileName).toFile());
        }

        return missingFiles;
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
