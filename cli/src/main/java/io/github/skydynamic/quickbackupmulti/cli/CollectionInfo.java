package io.github.skydynamic.quickbackupmulti.cli;

/**
 * A discovered collection of backups on disk.
 *
 * <p>Because the H2 database namespaces every collection by a one-way hash
 * ({@code UUID.nameUUIDFromBytes(collectionName)}) and stores no plaintext name, collections cannot be
 * enumerated from the database. Instead they are discovered by scanning the storage directory: each world's
 * blob folder is named with the plaintext level id (mirroring the mod's {@code BackupManager.getBackupPath()}),
 * and the dedicated-server collection lives directly under the storage root.
 *
 * @param collectionName the value hashed into the collection UUID ({@code "server"} or the world/level id)
 * @param levelId        the sub-folder holding this collection's blobs ({@code ""} for the server collection)
 * @param server         whether this is the dedicated-server collection
 * @param backupCount     number of incremental backups this collection currently holds
 */
public record CollectionInfo(String collectionName, String levelId, boolean server, int backupCount) {

    /** Human-readable label shown in the interactive picker. */
    public String label() {
        return displayName() + "  (" + backupCount + " backup" + (backupCount == 1 ? "" : "s") + ")";
    }

    /** The collection's name without any backup-count suffix. */
    public String displayName() {
        return server ? "[server]" : levelId;
    }
}
