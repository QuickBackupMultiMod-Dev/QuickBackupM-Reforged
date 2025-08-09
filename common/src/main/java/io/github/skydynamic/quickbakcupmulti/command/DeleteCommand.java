package io.github.skydynamic.quickbakcupmulti.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.skydynamic.increment.storage.lib.database.StorageInfo;
import io.github.skydynamic.quickbakcupmulti.DatabaseCache;
import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import io.github.skydynamic.quickbakcupmulti.utils.BackupManager;
import io.github.skydynamic.quickbakcupmulti.utils.permission.PermissionManager;
import io.github.skydynamic.quickbakcupmulti.utils.permission.PermissionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import static io.github.skydynamic.quickbakcupmulti.translate.Translate.tr;

public class DeleteCommand {
    public static final LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal("delete")
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
                deleteBackup(it.getSource(), StringArgumentType.getString(it, "name"))
            )
        );

    private static int deleteBackup(CommandSourceStack commandSource, String name) {
        new ModCommand.CmdExecuteThread(() -> {
            if (BackupManager.deleteBackup(commandSource, name)) {
                commandSource.sendSystemMessage(Component.literal(tr("quickbackupmulti.delete.success", name)));
                if (QuickbakcupmultiReforged.getModConfig().isCacheDatabase()) {
                    DatabaseCache.updateStorageInfoCaches();
                }
            } else {
                commandSource.sendSystemMessage(Component.literal(tr("quickbackupmulti.delete.fail", name)));
            }
        });
        return 0;
    }
}
