package io.github.skydynamic.quickbakcupmulti.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import static io.github.skydynamic.quickbakcupmulti.utils.ListBackupsUtils.show;

public class ShowCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal("show")
        .then(Commands.argument("name", StringArgumentType.string())
            .executes(it ->
                showBackupDetail(it.getSource(), StringArgumentType.getString(it, "name"))
            )
        );

    private static int showBackupDetail(CommandSourceStack commandSource, String name) {
        commandSource.sendSystemMessage(show(name));
        return 1;
    }
}
