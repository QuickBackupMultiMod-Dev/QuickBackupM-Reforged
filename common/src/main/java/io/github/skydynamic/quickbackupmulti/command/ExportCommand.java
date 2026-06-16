package io.github.skydynamic.quickbackupmulti.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.skydynamic.increment.storage.lib.database.StorageInfo;
import io.github.skydynamic.quickbackupmulti.QuickbackupmultiReforged;
import io.github.skydynamic.quickbackupmulti.utils.BackupManager;
import io.github.skydynamic.quickbackupmulti.utils.ZipUtils;
import io.github.skydynamic.quickbackupmulti.utils.permission.PermissionManager;
import io.github.skydynamic.quickbackupmulti.utils.permission.PermissionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.apache.commons.io.FileUtils;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import static io.github.skydynamic.quickbackupmulti.translate.Translate.tr;

public class ExportCommand {
    public static final LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal("export")
        .requires(it -> PermissionManager.hasPermission(it, 2, PermissionType.HELPER))
        .then(Commands.argument("name", StringArgumentType.string())
            .suggests(((context, builder) -> {
                for (StorageInfo info : BackupManager.getBackupsList()) {
                    if (info.getName().contains(builder.getRemaining())) {
                        builder.suggest(info.getName());
                    }
                }
                return builder.buildFuture();
            }))
            .executes(it ->
                export(it.getSource(), StringArgumentType.getString(it, "name"), null, false)
            )
            .then(Commands.literal("zip")
                .executes(it ->
                    export(it.getSource(), StringArgumentType.getString(it, "name"), null, true)
                )
            )
            .then(Commands.argument("path", StringArgumentType.string())
                .executes(it ->
                    export(it.getSource(), StringArgumentType.getString(it, "name"), StringArgumentType.getString(it, "path"), false)
                )
                .then(Commands.literal("zip")
                    .executes(it ->
                        export(it.getSource(), StringArgumentType.getString(it, "name"), StringArgumentType.getString(it, "path"), true)
                    )
                )
            )
        );

    private static int export(CommandSourceStack commandSource, String name, String pathArg, boolean zip) {
        new ModCommand.CmdExecuteThread(() -> {
            if (!QuickbackupmultiReforged.getDatabase().storageExists(name)) {
                commandSource.sendSystemMessage(Component.nullToEmpty(tr("quickbackupmulti.export.not_found", name)));
                return;
            }

            boolean singleplayer = commandSource.getServer().isSingleplayer();
            if (pathArg != null && !singleplayer) {
                commandSource.sendSystemMessage(Component.nullToEmpty(tr("quickbackupmulti.export.server_no_custom_path")));
                return;
            }

            Path storagePath = Path.of(QuickbackupmultiReforged.getModConfig().getStoragePath());
            Path defaultBase = storagePath.resolve("export");

            // Resolve the export destination. Path.of / createDirectories can fail on an unknown drive
            // letter, illegal characters, or an unwritable parent — surface these as a friendly message.
            Path folderTarget;
            Path zipTarget;
            try {
                if (pathArg != null) {
                    Path custom = Path.of(pathArg);
                    if (zip) {
                        zipTarget = pathArg.toLowerCase().endsWith(".zip") ? custom : custom.resolve(name + ".zip");
                        folderTarget = null;
                    } else {
                        folderTarget = custom;
                        zipTarget = null;
                    }
                } else {
                    folderTarget = zip ? null : defaultBase.resolve(name);
                    zipTarget = zip ? defaultBase.resolve(name + ".zip") : null;
                }
            } catch (InvalidPathException e) {
                commandSource.sendSystemMessage(Component.nullToEmpty(tr("quickbackupmulti.export.invalid_path", pathArg)));
                return;
            }

            commandSource.sendSystemMessage(Component.nullToEmpty(tr("quickbackupmulti.export.start", name)));

            try {
                Path finalPath;
                if (zip) {
                    Path tempDir = java.nio.file.Files.createTempDirectory("qbm-export-");
                    try {
                        if (!BackupManager.exportBackup(name, tempDir)) {
                            commandSource.sendSystemMessage(Component.nullToEmpty(tr("quickbackupmulti.export.fail", name)));
                            return;
                        }
                        commandSource.sendSystemMessage(Component.nullToEmpty(tr("quickbackupmulti.export.zipping")));
                        ZipUtils.zipDirectory(tempDir, zipTarget);
                    } finally {
                        FileUtils.deleteDirectory(tempDir.toFile());
                    }
                    finalPath = zipTarget;
                } else {
                    // Create the target dir up-front so a bad drive letter / unwritable parent surfaces as a
                    // FileSystemException here (exportBackup swallows it and would only report a generic failure).
                    java.nio.file.Files.createDirectories(folderTarget);
                    if (!BackupManager.exportBackup(name, folderTarget)) {
                        commandSource.sendSystemMessage(Component.nullToEmpty(tr("quickbackupmulti.export.fail", name)));
                        return;
                    }
                    finalPath = folderTarget;
                }
                commandSource.sendSystemMessage(Component.nullToEmpty(tr("quickbackupmulti.export.success", name, finalPath.toAbsolutePath().toString())));
            } catch (java.nio.file.FileSystemException e) {
                commandSource.sendSystemMessage(Component.nullToEmpty(tr("quickbackupmulti.export.invalid_path", pathArg)));
            } catch (Exception e) {
                ModCommand.getLogger().error("Export backup failed", e);
                commandSource.sendSystemMessage(Component.nullToEmpty(tr("quickbackupmulti.export.fail", e.toString())));
            }
        });
        return 1;
    }
}
