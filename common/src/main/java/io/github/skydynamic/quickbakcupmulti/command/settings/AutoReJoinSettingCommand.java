package io.github.skydynamic.quickbakcupmulti.command.settings;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import io.github.skydynamic.quickbakcupmulti.command.ModCommand;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import static io.github.skydynamic.quickbakcupmulti.translate.Translate.tr;

public class AutoReJoinSettingCommand {
    public static final LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal("auto-rejoin")
        .requires(ModCommand::clientOnly)
        .executes(it -> execute(it.getSource()));

    private static int execute(CommandSourceStack source) {
        QuickbakcupmultiReforged.getModConfig().setClientAutoReJoinWorld(!QuickbakcupmultiReforged.getModConfig().isClientAutoReJoinWorld());
        source.sendSystemMessage(
            Component.nullToEmpty(tr("quickbackupmulti.rejoin.switch", QuickbakcupmultiReforged.getModConfig().isClientAutoReJoinWorld()))
        );
        return 0;
    }
}
