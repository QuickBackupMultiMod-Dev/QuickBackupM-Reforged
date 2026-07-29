package io.github.skydynamic.quickbackupmulti.cli;

import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Options shared by every subcommand: how to locate the database/storage and which world (collection) to act on.
 * Mixed into each subcommand via {@code @Mixin}.
 */
public class CommonOptions {
    @Option(
        names = {"-s", "--storage-path"},
        description = "Path to the QuickBackupMulti storage directory (the folder containing QuickBackupMulti.mv.db). Takes precedence over --config."
    )
    String storagePath;

    @Option(
        names = {"-c", "--config"},
        description = "Path to the mod's QuickBackupMulti.json config; its 'storagePath' is used when --storage-path is not given."
    )
    Path config;

    @Option(
        names = {"-w", "--world"},
        description = "World/level id of the backups (singleplayer / integrated worlds)."
    )
    String world;

    @Option(
        names = {"--server"},
        description = "Act on dedicated-server backups (collection 'server')."
    )
    boolean server;

    /**
     * Resolve the configured options into a connected {@link BackupTarget}. Caller is responsible for closing it.
     * Requires exactly one of {@code --server} / {@code --world}.
     */
    BackupTarget open() throws IOException {
        Path resolved = resolveStoragePath();

        if (server == (world != null)) {
            throw new IllegalArgumentException("Specify exactly one of --server or --world <name>.");
        }

        String collectionName = server ? "server" : world;
        String levelId = server ? "" : world;

        return BackupTarget.open(resolved, collectionName, levelId);
    }

    /** Resolve the storage directory from {@code --storage-path} (preferred) or {@code --config}. */
    Path resolveStoragePath() throws IOException {
        String resolvedStoragePath = storagePath;
        if (resolvedStoragePath == null || resolvedStoragePath.isBlank()) {
            if (config == null) {
                throw new IllegalArgumentException("Either --storage-path or --config must be provided.");
            }
            resolvedStoragePath = CliConfig.readStoragePath(config);
        }
        return Path.of(resolvedStoragePath);
    }

    /** Whether the user pinned a specific collection on the command line (so no interactive pick is needed). */
    boolean hasExplicitCollection() {
        return server || world != null;
    }

    /** The collection explicitly named on the command line, or {@code null} if none was given. */
    CollectionInfo explicitCollection() {
        if (!hasExplicitCollection()) {
            return null;
        }
        if (server == (world != null)) {
            throw new IllegalArgumentException("Specify at most one of --server or --world <name>.");
        }
        return server
            ? new CollectionInfo("server", "", true, 0)
            : new CollectionInfo(world, world, false, 0);
    }
}
