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
import java.util.List;
import java.util.concurrent.Callable;

@Command(
    name = "qbm-cli",
    mixinStandardHelpOptions = true,
    version = "QuickBackupMulti CLI",
    description = "Export, delete and list QuickBackupMulti backups without launching Minecraft.",
    subcommands = {QbmCli.ListCommand.class, QbmCli.ExportCommand.class, QbmCli.DeleteCommand.class}
)
public class QbmCli implements Runnable {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new QbmCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        // No subcommand given: show usage.
        CommandLine.usage(this, System.out);
    }

    @Command(name = "list", description = "List the backups in a world/collection.")
    static class ListCommand implements Callable<Integer> {
        @Mixin
        CommonOptions common;

        @Override
        public Integer call() {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            try (BackupTarget target = common.open()) {
                List<StorageInfo> backups = target.listBackups();
                if (backups.isEmpty()) {
                    System.out.println("No backups found.");
                    return 0;
                }
                int index = 1;
                for (StorageInfo info : backups) {
                    System.out.printf(
                        "[%d] %s  (%s)  %s%n",
                        index++,
                        info.getName(),
                        sdf.format(info.getTimestamp()),
                        info.getDesc() == null || info.getDesc().isBlank() ? "" : info.getDesc()
                    );
                }
                return 0;
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
                    Path zipFile;
                    if (out != null) {
                        Path custom = Path.of(out);
                        zipFile = out.toLowerCase().endsWith(".zip") ? custom : custom.resolve(name + ".zip");
                    } else {
                        zipFile = target.defaultExportDir(name).resolveSibling(name + ".zip");
                    }
                    Path tempDir = Files.createTempDirectory("qbm-export-");
                    try {
                        target.reconstructTo(name, tempDir);
                        CliZipUtils.zipDirectory(tempDir, zipFile);
                    } finally {
                        org.apache.commons.io.FileUtils.deleteDirectory(tempDir.toFile());
                    }
                    System.out.println("Exported '" + name + "' to: " + zipFile.toAbsolutePath());
                } else {
                    Path outDir = out != null ? Path.of(out) : target.defaultExportDir(name);
                    target.reconstructTo(name, outDir);
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
