package io.github.skydynamic.quickbackupmulti.cli;

import io.github.skydynamic.increment.storage.lib.database.StorageInfo;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;

@Command(
    name = "qbm-cli",
    mixinStandardHelpOptions = true,
    version = "QuickBackupMulti CLI",
    description = "Export, delete and list QuickBackupMulti backups without launching Minecraft.%n"
        + "Run with no subcommand to browse and act on backups interactively.",
    subcommands = {QbmCli.ListCommand.class, QbmCli.ExportCommand.class, QbmCli.DeleteCommand.class}
)
public class QbmCli implements Callable<Integer> {

    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Mixin
    CommonOptions common;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new QbmCli()).execute(args);
        System.exit(exitCode);
    }

    /**
     * No subcommand: launch the interactive browser. Pick a collection (unless {@code --server}/{@code --world}
     * pins one), then pick a backup and an action, with {@code Esc} stepping back one screen at a time.
     */
    @Override
    public Integer call() {
        try {
            Path storagePath = common.resolveStoragePath();

            if (common.hasExplicitCollection()) {
                return browseAndAct(storagePath, "Select a world/collection", null, CollectionInfo::label,
                    common.explicitCollection(), false);
            }

            List<CollectionInfo> candidates = BackupTarget.discoverCollections(storagePath);
            if (candidates.isEmpty()) {
                System.out.println("No collections with backups found under: " + storagePath.toAbsolutePath());
                return 0;
            }
            if (candidates.size() == 1) {
                return browseAndAct(storagePath, "Select a world/collection", null, CollectionInfo::label,
                    candidates.get(0), false);
            }
            return browseAndAct(storagePath, "Select a world/collection", candidates, CollectionInfo::label,
                null, true);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    /**
     * Drive the collection → backup → action screens as a small back/forward stack.
     *
     * <p>{@code Esc} on the backup screen returns to the collection picker (only possible when
     * {@code collectionPickerAvailable} is {@code true} — i.e. a picker was actually shown, as opposed to a
     * pinned or auto-selected single collection). {@code Esc} on the action screen returns to the backup picker.
     * {@code Ctrl+C} exits the process immediately from any screen (handled inside {@link InteractiveMenu}).
     *
     * @param candidates               collections to offer when {@code collectionPickerAvailable} is true, else unused
     * @param pinnedCollection         the collection to browse when no picker is needed (explicit flag or the only candidate)
     * @param collectionPickerAvailable whether a real choice exists, i.e. whether Esc on the backup screen has somewhere to go back to
     */
    private static int browseAndAct(
        Path storagePath,
        String collectionTitle,
        List<CollectionInfo> candidates,
        Function<CollectionInfo, String> collectionLabeler,
        CollectionInfo pinnedCollection,
        boolean collectionPickerAvailable
    ) throws Exception {
        CollectionInfo collection = pinnedCollection;

        while (true) {
            if (collection == null) {
                collection = new InteractiveMenu<>(collectionTitle, candidates, collectionLabeler).prompt();
                if (collection == null) {
                    System.out.println("Cancelled.");
                    return 0;
                }
            }

            try (BackupTarget target = BackupTarget.open(storagePath, collection)) {
                List<StorageInfo> backups = target.listBackups().stream()
                    .sorted(Comparator.comparingLong(StorageInfo::getTimestamp).reversed())
                    .toList();
                if (backups.isEmpty()) {
                    System.out.println("No backups found in this collection.");
                    if (!collectionPickerAvailable) {
                        return 0;
                    }
                    collection = null;
                    continue;
                }

                StorageInfo backup = null;
                while (true) {
                    if (backup == null) {
                        backup = new InteractiveMenu<>(
                            "Select a backup (newest first) — collection: " + collection.displayName()
                                + "  (" + backups.size() + " backup" + (backups.size() == 1 ? "" : "s") + ")",
                            backups,
                            b -> String.format("%s  (%s)  %s", b.getName(), SDF.format(b.getTimestamp()),
                                b.getDesc() == null || b.getDesc().isBlank() ? "" : b.getDesc())
                        ).prompt();
                        if (backup == null) {
                            if (!collectionPickerAvailable) {
                                System.out.println("Cancelled.");
                                return 0;
                            }
                            break; // back to the collection picker
                        }
                    }

                    String action = new InteractiveMenu<>(
                        "Action for backup '" + backup.getName() + "'",
                        List.of("Delete", "Export folder", "Export zip"),
                        s -> s
                    ).prompt();
                    if (action == null) {
                        backup = null; // back to the backup picker
                        continue;
                    }

                    switch (action) {
                        case "Delete" -> {
                            target.delete(backup.getName());
                            System.out.println("Deleted backup '" + backup.getName() + "'.");
                        }
                        case "Export folder" -> {
                            Path result = exportBackup(target, backup.getName(), null, false);
                            System.out.println("Exported '" + backup.getName() + "' to: " + result.toAbsolutePath());
                        }
                        case "Export zip" -> {
                            Path result = exportBackup(target, backup.getName(), null, true);
                            System.out.println("Exported '" + backup.getName() + "' to: " + result.toAbsolutePath());
                        }
                    }
                    return 0;
                }
            }
            collection = null; // only reached via the "back to collection picker" break above
        }
    }

    /**
     * Reconstruct {@code name} to a folder, or a single zip archive when {@code zip} is set.
     *
     * @param out user-supplied destination, or {@code null} to use the default {@code <storagePath>/export/<name>}
     * @return the folder or zip file that was written
     */
    static Path exportBackup(BackupTarget target, String name, String out, boolean zip) throws Exception {
        List<MissingFileInfo> missingFiles;

        if (zip) {
            Path zipFile;
            if (out != null) {
                Path custom = Path.of(out);
                zipFile = out.toLowerCase().endsWith(".zip") ? custom : custom.resolve(name + ".zip");
            } else {
                zipFile = target.defaultExportDir(name).resolveSibling(name + ".zip");
            }
            Path tempDir = Files.createTempDirectory("qbm-export-");
            try {
                missingFiles = target.reconstructTo(name, tempDir);
                CliZipUtils.zipDirectory(tempDir, zipFile);
            } finally {
                org.apache.commons.io.FileUtils.deleteDirectory(tempDir.toFile());
            }
            printMissingFiles(missingFiles);
            return zipFile;
        } else {
            Path outDir = out != null ? Path.of(out) : target.defaultExportDir(name);
            missingFiles = target.reconstructTo(name, outDir);
            printMissingFiles(missingFiles);
            return outDir;
        }
    }

    private static void printMissingFiles(List<MissingFileInfo> missingFiles) {
        if (!missingFiles.isEmpty()) {
            System.err.println("\nWarning: " + missingFiles.size() + " file(s) could not be found:");
            for (MissingFileInfo missing : missingFiles) {
                System.err.println("  - File: " + missing.fileName());
                System.err.println("    Hash: " + missing.fileHash());
            }
        }
    }

    @Command(
        name = "list",
        description = "Browse the backups in a world/collection interactively.%n"
            + "--server takes precedence over --world. With neither given, singleplayer mode kicks in: "
            + "pick a save under --savePath/-S (validated by the presence of level.dat)."
    )
    static class ListCommand implements Callable<Integer> {
        @Mixin
        CommonOptions common;

        @Option(
            names = {"-S", "--savePath"},
            description = "Path to the Minecraft 'saves' directory, used to pick a world in singleplayer mode "
                + "(when neither --server nor --world is given)."
        )
        Path savePath;

        @Override
        public Integer call() {
            try {
                Path storagePath = common.resolveStoragePath();

                if (common.server) {
                    return browseAndAct(storagePath, "Select a save", null, CollectionInfo::displayName,
                        new CollectionInfo("server", "", true, 0), false);
                }
                if (common.world != null) {
                    return browseAndAct(storagePath, "Select a save", null, CollectionInfo::displayName,
                        new CollectionInfo(common.world, common.world, false, 0), false);
                }

                if (savePath == null) {
                    throw new IllegalArgumentException(
                        "Neither --server nor --world given: singleplayer mode requires --savePath/-S (the 'saves' directory)."
                    );
                }
                List<String> saves = BackupTarget.discoverSaveFolders(savePath);
                if (saves.isEmpty()) {
                    System.out.println("No valid saves (containing level.dat) found under: " + savePath.toAbsolutePath());
                    return 0;
                }
                List<CollectionInfo> candidates = saves.stream()
                    .map(name -> new CollectionInfo(name, name, false, 0))
                    .toList();
                if (candidates.size() == 1) {
                    return browseAndAct(storagePath, "Select a save", null, CollectionInfo::displayName,
                        candidates.get(0), false);
                }
                return browseAndAct(storagePath, "Select a save", candidates, CollectionInfo::displayName,
                    null, true);
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "export", description = "Export (reconstruct) a backup to a directory or zip archive.")
    static class ExportCommand implements Callable<Integer> {
        @Mixin
        CommonOptions common;

        @Parameters(index = "0", description = "Name of the backup to export.")
        String name;

        @Option(names = {"-o", "--out"}, description = "Output directory (folder mode) or zip file/dir (zip mode). Default: <storagePath>/export/<name>.")
        String out;

        @Option(names = {"--zip"}, description = "Export as a single .zip archive instead of a folder.")
        boolean zip;

        @Override
        public Integer call() {
            try (BackupTarget target = common.open()) {
                if (!target.exists(name)) {
                    System.err.println("Error: backup '" + name + "' not found.");
                    return 1;
                }

                if (zip) {
                    Path zipFile = exportBackup(target, name, out, true);
                    System.out.println("Exported '" + name + "' to: " + zipFile.toAbsolutePath());
                } else {
                    Path outDir = exportBackup(target, name, out, false);
                    System.out.println("Exported '" + name + "' to: " + outDir.toAbsolutePath());
                }
                return 0;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "delete", description = "Delete a backup (frees blobs no longer referenced by other backups).")
    static class DeleteCommand implements Callable<Integer> {
        @Mixin
        CommonOptions common;

        @Parameters(index = "0", description = "Name of the backup to delete.")
        String name;

        @Override
        public Integer call() {
            try (BackupTarget target = common.open()) {
                if (!target.exists(name)) {
                    System.err.println("Error: backup '" + name + "' not found.");
                    return 1;
                }
                target.delete(name);
                System.out.println("Deleted backup '" + name + "'.");
                return 0;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }
}
