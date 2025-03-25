package io.github.skydynamic.quickbakcupmulti.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.skydynamic.quickbakcupmulti.utils.BackupManager;
import io.github.skydynamic.quickbakcupmulti.utils.permission.PermissionManager;
import io.github.skydynamic.quickbakcupmulti.utils.permission.PermissionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class DeleteCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal("delete")
        .requires(it -> PermissionManager.hasPermission(it, 2, PermissionType.HELPER))
        .then(Commands.argument("name", StringArgumentType.string())
            .executes(it ->
                deleteBackup(it.getSource(), StringArgumentType.getString(it, "name")
                )
            )
        );

    private static int deleteBackup(CommandSourceStack commandSource, String name) {
        new ModCommand.CmdExecuteThread(() -> {
            if (BackupManager.deleteBackup(commandSource, name)) {
                commandSource.sendSystemMessage(Component.literal("Delete Success"));
            } else {
                commandSource.sendSystemMessage(Component.literal("Delete Failed"));
            }
        });
        return 0;
    }
}
