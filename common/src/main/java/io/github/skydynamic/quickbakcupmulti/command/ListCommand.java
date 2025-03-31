package io.github.skydynamic.quickbakcupmulti.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.skydynamic.quickbakcupmulti.utils.ListBackupsUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.MutableComponent;

public class ListCommand {
    public static final LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal("list")
        .executes(it -> listSaveBackups(it.getSource(), 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .executes(it ->
                        listSaveBackups(it.getSource(), IntegerArgumentType.getInteger(it, "page")
                        )
                    )
                );

    private static int listSaveBackups(CommandSourceStack sourceStack, int page) {
        new ModCommand.CmdExecuteThread(() -> {
            MutableComponent listText = ListBackupsUtils.list(page);
            sourceStack.sendSuccess(() -> listText, false);
        });
        return 1;
    }
}
