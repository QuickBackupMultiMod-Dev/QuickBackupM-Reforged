package io.github.skydynamic.quickbackupmulti.cli;

import io.github.skydynamic.increment.storage.lib.manager.IDatabaseManager;

import java.util.UUID;

/**
 * Minimal {@link IDatabaseManager} for the CLI. Mirrors the mod's
 * {@code io.github.skydynamic.quickbackupmulti.database.DatabaseManager}: the database file lives at
 * {@code <databasePath>/<fileName>.mv.db} and each world/collection is namespaced by {@code collectionUuid}.
 */
public class CliDatabaseManager implements IDatabaseManager {
    private String fileName = "QuickBackupMulti";
    private String databasePath;
    private UUID collectionUuid;

    public CliDatabaseManager(String databasePath, UUID collectionUuid) {
        this.databasePath = databasePath;
        this.collectionUuid = collectionUuid;
    }

    @Override
    public void setFileName(String name) {
        this.fileName = name;
    }

    @Override
    public void setDatabasePath(String path) {
        this.databasePath = path;
    }

    @Override
    public void setCollectionUuid(UUID uuid) {
        this.collectionUuid = uuid;
    }

    @Override
    public String getFileName() {
        return fileName;
    }

    @Override
    public String getDatabasePath() {
        return databasePath;
    }

    @Override
    public UUID getCollectionUuid() {
        return collectionUuid;
    }
}
