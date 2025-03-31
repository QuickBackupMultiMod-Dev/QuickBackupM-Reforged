package io.github.skydynamic.quickbakcupmulti.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

import static io.github.skydynamic.quickbakcupmulti.translate.Translate.tr;
import static io.github.skydynamic.quickbakcupmulti.utils.BackupManager.getBackupsList;
import static io.github.skydynamic.quickbakcupmulti.utils.ListBackupsUtils.search;

public class SearchCommand {
    public static final LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal("search")
        .then(Commands.argument("name", StringArgumentType.string())
            .executes(it ->
                searchSaveBackups(it.getSource(), StringArgumentType.getString(it, "name"))
            )
        );

    private static int searchSaveBackups(CommandSourceStack commandSource, String string) {
        List<String> backupsList = getBackupsList();
        List<String> result = backupsList.stream()
            .filter(it -> StringUtils.containsIgnoreCase(it, string))
            .toList();
        if (result.isEmpty()) {
            commandSource.sendSystemMessage(Component.nullToEmpty(tr("quickbackupmulti.search.fail")));
        } else {
            commandSource.sendSystemMessage(search(result));
        }
        return 1;
    }
}
