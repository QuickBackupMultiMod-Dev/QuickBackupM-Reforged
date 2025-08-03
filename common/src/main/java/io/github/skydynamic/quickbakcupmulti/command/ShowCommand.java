package io.github.skydynamic.quickbakcupmulti.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.skydynamic.increment.storage.lib.database.StorageInfo;
import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import static io.github.skydynamic.quickbakcupmulti.utils.ListBackupsUtils.show;

public class ShowCommand {
    public static final LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal("show")
        .then(Commands.argument("name", StringArgumentType.string())
            .suggests(((context, builder) -> {
                for (StorageInfo info : QuickbakcupmultiReforged.getDatabase().getAllStorageInfo()) {
                    if (info.getName().contains(builder.getRemaining())) {
                        builder.suggest(info.getName());
                    }
                }
                return builder.buildFuture();
            }))
            .executes(it ->
                showBackupDetail(it.getSource(), StringArgumentType.getString(it, "name"))
            )
        );

    private static int showBackupDetail(CommandSourceStack commandSource, String name) {
        commandSource.sendSystemMessage(show(name));
        return 1;
    }
}
