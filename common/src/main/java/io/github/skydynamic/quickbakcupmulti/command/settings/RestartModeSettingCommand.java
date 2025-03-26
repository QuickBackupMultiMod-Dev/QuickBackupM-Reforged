package io.github.skydynamic.quickbakcupmulti.command.settings;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import io.github.skydynamic.quickbakcupmulti.config.ModConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import static io.github.skydynamic.quickbakcupmulti.translate.Translate.tr;

public class RestartModeSettingCommand {
    public static final LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal("auto-restart-mode")
        .then(getCmdTree());

    public static LiteralArgumentBuilder<CommandSourceStack> getCmdTree() {
        LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal("set");
        for (ModConfig.AutoRestartMode mode : ModConfig.AutoRestartMode.values()) {
            cmd = cmd.then(Commands.literal(mode.name())
                .executes(it ->
                    setAutoRestartMode(it.getSource(), mode.toString())
                )
            );
        }
        return cmd;
    }

    private static int setAutoRestartMode(CommandSourceStack commandSource, String mode) {
        ModConfig.AutoRestartMode newMode = ModConfig.AutoRestartMode.valueOf(mode.toUpperCase());
        QuickbakcupmultiReforged.getModConfig().setAutoRestartMode(newMode);
        commandSource.sendSystemMessage(Component.nullToEmpty((tr("quickbackupmulti.restartmode.switch", newMode.name()))));
        return 1;
    }
}
