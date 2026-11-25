package io.github.skydynamic.quickbackupmulti.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.skydynamic.increment.storage.lib.database.StorageInfo;
import io.github.skydynamic.quickbackupmulti.utils.BackupManager;
import io.github.skydynamic.quickbackupmulti.utils.permission.PermissionManager;
import io.github.skydynamic.quickbackupmulti.utils.permission.PermissionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

import java.util.List;

import static io.github.skydynamic.quickbackupmulti.translate.Translate.tr;

public class PruneCommand {
    public static final LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal("prune")
        .requires(it -> PermissionManager.hasPermission(it, Permissions.COMMANDS_GAMEMASTER, PermissionType.HELPER))
        .executes(it -> prune(it.getSource(), false))
        .then(Commands.literal("dryrun")
            .executes(it -> prune(it.getSource(), true))
        );

    private static int prune(CommandSourceStack commandSource, boolean dryRun) {
        new ModCommand.CmdExecuteThread(() -> {
            List<StorageInfo> expired = BackupManager.pruneRegularBackups(commandSource, dryRun);
            if (dryRun) {
                commandSource.sendSystemMessage(Component.literal(tr("quickbackupmulti.prune.dryrun", expired.size())));
                expired.stream()
                    .limit(10)
                    .forEach(backup -> commandSource.sendSystemMessage(Component.literal(" - " + backup.getName())));
            } else {
                commandSource.sendSystemMessage(Component.literal(tr("quickbackupmulti.prune.success", expired.size())));
            }
        });
        return 0;
    }
}
