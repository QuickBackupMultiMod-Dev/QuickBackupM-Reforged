package io.github.skydynamic.quickbackupmulti.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.skydynamic.increment.storage.lib.database.StorageInfo;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

import static io.github.skydynamic.quickbackupmulti.translate.Translate.tr;
import static io.github.skydynamic.quickbackupmulti.utils.BackupManager.getBackupsList;
import static io.github.skydynamic.quickbackupmulti.utils.ListBackupsUtils.search;

public class SearchCommand {
    public static final LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal("search")
        .then(Commands.argument("name", StringArgumentType.string())
            .executes(it ->
                searchSaveBackups(it.getSource(), StringArgumentType.getString(it, "name"))
            )
        );

    private static int searchSaveBackups(CommandSourceStack commandSource, String string) {
        List<String> backupsList = getBackupsList().stream().map(StorageInfo::getName).toList();
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
