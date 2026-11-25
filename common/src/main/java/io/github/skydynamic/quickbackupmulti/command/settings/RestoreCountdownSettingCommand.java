package io.github.skydynamic.quickbackupmulti.command.settings;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.skydynamic.quickbackupmulti.QuickbackupmultiReforged;
import io.github.skydynamic.quickbackupmulti.command.ModCommand;
import io.github.skydynamic.quickbackupmulti.config.ModConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import static io.github.skydynamic.quickbackupmulti.translate.Translate.tr;

public class RestoreCountdownSettingCommand {
    public static final LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal("restore-countdown")
        .requires(ModCommand::serverOnly)
        .then(Commands.argument("seconds", IntegerArgumentType.integer(
                ModConfig.MIN_RESTORE_COUNTDOWN_SECONDS, ModConfig.MAX_RESTORE_COUNTDOWN_SECONDS))
            .executes(it -> set(it.getSource(), IntegerArgumentType.getInteger(it, "seconds")))
        );

    private static int set(CommandSourceStack commandSource, int seconds) {
        QuickbackupmultiReforged.getModConfig().setRestoreCountdownSeconds(seconds);
        commandSource.sendSystemMessage(
            Component.nullToEmpty(tr("quickbackupmulti.countdown.switch", QuickbackupmultiReforged.getModConfig().getRestoreCountdownSeconds()))
        );
        return 1;
    }
}
