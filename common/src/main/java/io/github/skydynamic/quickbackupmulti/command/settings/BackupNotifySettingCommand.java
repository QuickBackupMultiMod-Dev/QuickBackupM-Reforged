package io.github.skydynamic.quickbackupmulti.command.settings;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.skydynamic.quickbackupmulti.QuickbackupmultiReforged;
import io.github.skydynamic.quickbackupmulti.command.ModCommand;
import io.github.skydynamic.quickbackupmulti.config.ScheduleBackupConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import static io.github.skydynamic.quickbackupmulti.translate.Translate.tr;

public class BackupNotifySettingCommand {
    public static final LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal("backup-notify")
        .requires(ModCommand::serverOnly)
        .executes(it -> toggle(it.getSource()));

    private static int toggle(CommandSourceStack commandSource) {
        ScheduleBackupConfig scheduleBackupConfig = QuickbackupmultiReforged.getModConfig().getScheduleBackupConfig();
        scheduleBackupConfig.notifyPlayers = !scheduleBackupConfig.notifyPlayers;
        QuickbackupmultiReforged.getModConfig().save();
        commandSource.sendSystemMessage(
            Component.nullToEmpty(tr("quickbackupmulti.notify.switch", scheduleBackupConfig.notifyPlayers))
        );
        return 1;
    }
}
