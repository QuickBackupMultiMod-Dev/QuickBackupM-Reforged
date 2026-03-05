package io.github.skydynamic.quickbackupmulti.command.settings;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.skydynamic.quickbackupmulti.utils.permission.PermissionManager;
import io.github.skydynamic.quickbackupmulti.utils.permission.PermissionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class SettingCommand {
    public static final LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal("setting")
        .requires(it -> PermissionManager.hasPermission(it, 4, PermissionType.ADMIN))
        .then(LangSettingCommand.cmd)
        .then(RestartModeSettingCommand.cmd)
        .then(AutoReJoinSettingCommand.cmd);
}
