package io.github.skydynamic.quickbackupmulti.command.settings;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.skydynamic.quickbackupmulti.QuickbackupmultiReforged;
import io.github.skydynamic.quickbackupmulti.command.ModCommand;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import static io.github.skydynamic.quickbackupmulti.translate.Translate.tr;

public class AutoReJoinSettingCommand {
    public static final LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal("auto-rejoin")
        .requires(ModCommand::clientOnly)
        .executes(it -> execute(it.getSource()));

    private static int execute(CommandSourceStack source) {
        QuickbackupmultiReforged.getModConfig().setClientAutoReJoinWorld(!QuickbackupmultiReforged.getModConfig().isClientAutoReJoinWorld());
        source.sendSystemMessage(
            Component.nullToEmpty(tr("quickbackupmulti.rejoin.switch", QuickbackupmultiReforged.getModConfig().isClientAutoReJoinWorld()))
        );
        return 0;
    }
}
