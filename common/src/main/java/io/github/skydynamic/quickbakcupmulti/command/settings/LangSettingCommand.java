package io.github.skydynamic.quickbakcupmulti.command.settings;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import io.github.skydynamic.quickbakcupmulti.translate.Translate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class LangSettingCommand {
    public static final LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal("lang")
        .then(getCmdTree());

    private static LiteralArgumentBuilder<CommandSourceStack> getCmdTree() {
        LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal("set");
        for (String lang : Translate.supportLanguage) {
            cmd = cmd.then(Commands.literal(lang)
                .executes(it -> setLang(it.getSource(), lang)
                )
            );
        }
        return cmd;
    }

    private static int setLang(CommandSourceStack source, String lang) {
        Translate.handleResourceReload(lang);
        source.sendSystemMessage(Component.nullToEmpty(Translate.tr("quickbackupmulti.lang.set", lang)));
        QuickbakcupmultiReforged.getModConfig().setLang(lang);
        return 1;
    }
}
