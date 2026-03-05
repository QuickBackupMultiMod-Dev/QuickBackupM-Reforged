package io.github.skydynamic.quickbackupmulti.command.settings;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.skydynamic.quickbackupmulti.QuickbackupmultiReforged;
import io.github.skydynamic.quickbackupmulti.command.ModCommand;
import io.github.skydynamic.quickbackupmulti.config.ModConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import static io.github.skydynamic.quickbackupmulti.translate.Translate.tr;

public class RestartModeSettingCommand {
    public static final LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal("auto-restart-mode")
        .requires(ModCommand::serverOnly)
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
        QuickbackupmultiReforged.getModConfig().setAutoRestartMode(newMode);
        commandSource.sendSystemMessage(Component.nullToEmpty((tr("quickbackupmulti.restartmode.switch", newMode.name()))));
        return 1;
    }
}
