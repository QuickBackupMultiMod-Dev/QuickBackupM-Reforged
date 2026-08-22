package io.github.skydynamic.quickbackupmulti.harness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Reads the mod's backup store from outside the game: the H2 metadata plus the blob and full-backup
 * directories.
 *
 * <p>Asserting here rather than on chat output is deliberate. The mod's user-facing messages come from
 * language files and are formatted with colour codes, so matching them tests the translation, not the
 * backup. What actually matters is that a row exists, that the blobs it points at exist, and that the
 * rotation left the right directories behind.
 *
 * <p>The schema mirrors {@code incremental-storage-lib} 1.3.0: tables {@code storage_info},
 * {@code file_hash} and {@code file_hash_reference}, each scoped by a {@code collection_uuid} of
 * {@code UUID.nameUUIDFromBytes(collectionName)} — {@code "server"} on a dedicated server, the level id
 * on a client.
 *
 * <p>The database and the blobs do not necessarily live in the same directory.
 * {@code QuickbackupmultiReforged.setNewDataBase} points {@code DatabaseManager} at the configured
 * {@code storagePath} but hands {@code StorageManager} a copy of the config whose {@code storagePath}
 * has {@code /<levelId>} appended on a client. So one client keeps a single shared database at the top
 * level and per-world blob directories underneath it, while a dedicated server has both in one place.
 */
public final class BackupStore {
    /**
     * Description the library writes on its internal temp storage, which
     * {@code Database.getAllStorageInfo} filters out. Matching that filter keeps the harness's idea of
     * "the backups a user can see" identical to the mod's.
     */
    private static final String TEMP_STORAGE_DESC = "a5ff1c641758cc02744172a50e577bbe06c2a1c5";

    private final Path databaseDir;
    private final Path blobDir;
    private final UUID collectionUuid;

    /**
     * @param databaseDir where {@code QuickBackupMulti.mv.db} lives — the configured {@code storagePath}
     * @param blobDir where {@code blogs/}, {@code full/} and {@code blogs_temp/} live: the same directory
     *                on a dedicated server, {@code <storagePath>/<levelId>} on a client
     * @param collectionName {@code "server"} for a dedicated server, the level id for a client world
     */
    public BackupStore(Path databaseDir, Path blobDir, String collectionName) {
        this.databaseDir = databaseDir;
        this.blobDir = blobDir;
        this.collectionUuid = UUID.nameUUIDFromBytes(collectionName.getBytes());
    }

    /** A store whose database and blobs share one directory, as on a dedicated server. */
    public BackupStore(Path storageDir, String collectionName) {
        this(storageDir, storageDir, collectionName);
    }

    /** One row of {@code storage_info}. */
    public record Backup(String name, String desc, long timestamp, boolean incremental) {
    }

    /** The H2 database file the mod writes. */
    public Path databaseFile() {
        return databaseDir.resolve("QuickBackupMulti.mv.db");
    }

    public boolean exists() {
        return Files.exists(databaseFile());
    }

    /**
     * Every backup the mod would show a user, oldest first.
     *
     * <p>Read-only, and against a copy: H2 takes an exclusive file lock, so opening the live database
     * while the server holds it would fail. Copying first means assertions can also run while the
     * server is still up.
     */
    public List<Backup> backups() {
        List<Backup> all = query();
        return all.stream()
            .filter(b -> !TEMP_STORAGE_DESC.equals(b.desc()))
            .sorted(Comparator.comparingLong(Backup::timestamp))
            .toList();
    }

    /** Just the incremental backups, which is what {@code /qb list} numbers. */
    public List<Backup> incrementalBackups() {
        return backups().stream().filter(Backup::incremental).toList();
    }

    /** Just the full backups, which drive the rotation. */
    public List<Backup> fullBackups() {
        return backups().stream().filter(b -> !b.incremental()).toList();
    }

    public boolean hasBackup(String name) {
        return backups().stream().anyMatch(b -> b.name().equals(name));
    }

    /** The 1-based index {@code /qb restore <n>} would resolve, or -1. */
    public int indexOf(String name) {
        List<Backup> list = incrementalBackups();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).name().equals(name)) return i + 1;
        }
        return -1;
    }

    /** The file paths a backup recorded, relative to the world directory. */
    public List<String> filesIn(String backupName) {
        String json = queryFileHashMap(backupName);
        if (json == null) return List.of();
        List<String> paths = new ArrayList<>();
        // The column holds a flat {"<hash>": "<relative path>"} object written by Gson. Reading the
        // values with a scan avoids adding a JSON dependency to the harness.
        var m = java.util.regex.Pattern
            .compile("\"[^\"]+\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
            .matcher(json);
        while (m.find()) {
            paths.add(m.group(1).replace("\\\\", "/").replace('\\', '/'));
        }
        return paths.stream().sorted().toList();
    }

    /** Content-addressed blobs on disk, as {@code blogs/<first two chars>/<hash>}. */
    public List<String> blobHashes() throws IOException {
        Path blogs = blobDir.resolve("blogs");
        if (!Files.isDirectory(blogs)) return List.of();
        try (var walk = Files.walk(blogs)) {
            return walk.filter(Files::isRegularFile)
                .map(p -> p.getFileName().toString())
                .sorted()
                .toList();
        }
    }

    /** Directories under {@code full/}, which is what the rotation adds to and prunes. */
    public List<String> fullBackupDirs() throws IOException {
        Path full = blobDir.resolve("full");
        if (!Files.isDirectory(full)) return List.of();
        try (var list = Files.list(full)) {
            return list.filter(Files::isDirectory)
                .map(p -> p.getFileName().toString())
                .sorted()
                .toList();
        }
    }

    /** Leftover temp blobs. A finished restore should not keep these around forever. */
    public List<String> tempBlobs() throws IOException {
        Path temp = blobDir.resolve("blogs_temp");
        if (!Files.isDirectory(temp)) return List.of();
        try (var list = Files.list(temp)) {
            return list.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    private List<Backup> query() {
        return withDatabase(conn -> {
            List<Backup> out = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT \"name\", \"desc\", \"timestamp\", \"use_incremental_storage\" "
                    + "FROM \"storage_info\" WHERE \"collection_uuid\" = ?")) {
                ps.setObject(1, collectionUuid);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new Backup(rs.getString(1), rs.getString(2), rs.getLong(3),
                            rs.getBoolean(4)));
                    }
                }
            }
            return out;
        });
    }

    private String queryFileHashMap(String backupName) {
        return withDatabase(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT \"file_hash_map\" FROM \"file_hash\" "
                    + "WHERE \"collection_uuid\" = ? AND \"name\" = ?")) {
                ps.setObject(1, collectionUuid);
                ps.setString(2, backupName);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            }
        });
    }

    private interface DbAction<T> {
        T run(Connection conn) throws SQLException;
    }

    private <T> T withDatabase(DbAction<T> action) {
        if (!exists()) {
            throw new HarnessException("No backup database at " + databaseFile()
                + " — the mod never initialised its storage on this version");
        }
        Path copy = null;
        try {
            // H2 locks the file exclusively; the server usually still holds it. A copy is read-only
            // anyway, so this cannot perturb what is being measured.
            copy = Files.createTempFile("qbm-store-", ".mv.db");
            Files.copy(databaseFile(), copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            String url = "jdbc:h2:file:" + copy.toAbsolutePath().toString()
                .replaceAll("\\.mv\\.db$", "") + ";ACCESS_MODE_DATA=r";
            try (Connection conn = DriverManager.getConnection(url, "", "")) {
                return action.run(conn);
            }
        } catch (SQLException e) {
            throw new HarnessException("Could not read the backup database at " + databaseFile(), e);
        } catch (IOException e) {
            throw new HarnessException("Could not copy the backup database for reading", e);
        } finally {
            if (copy != null) {
                try {
                    Files.deleteIfExists(copy);
                    Files.deleteIfExists(Path.of(copy.toString().replaceAll("\\.mv\\.db$", ".trace.db")));
                } catch (IOException e) {
                    // A temp file left in the OS temp directory is not worth failing a test over.
                }
            }
        }
    }
}
