package io.github.skydynamic.quickbakcupmulti.command.settings;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.skydynamic.quickbakcupmulti.utils.permission.PermissionManager;
import io.github.skydynamic.quickbakcupmulti.utils.permission.PermissionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.permissions.Permissions;

public class SettingCommand {
    public static final LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal("setting")
        .requires(it -> PermissionManager.hasPermission(it, Permissions.COMMANDS_OWNER, PermissionType.ADMIN))
        .then(LangSettingCommand.cmd)
        .then(RestartModeSettingCommand.cmd)
        .then(AutoReJoinSettingCommand.cmd);
}
