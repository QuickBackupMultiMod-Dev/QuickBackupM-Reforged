package io.github.skydynamic.quickbakcupmulti.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.skydynamic.quickbakcupmulti.DatabaseCache;
import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import io.github.skydynamic.quickbakcupmulti.utils.BackupManager;
import io.github.skydynamic.quickbakcupmulti.utils.permission.PermissionManager;
import io.github.skydynamic.quickbakcupmulti.utils.permission.PermissionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.text.SimpleDateFormat;

public class MakeCommand {
    static class makeRunnable implements Runnable {
        CommandSourceStack sourceStack;
        String name;
        String desc;

        makeRunnable(CommandSourceStack commandSource, String name, String desc) {
            this.sourceStack = commandSource;
            this.name = name;
            this.desc = desc;
        }

        @Override
        public void run() {
            long l = System.currentTimeMillis();
            QuickbakcupmultiReforged.logger.info("Make Backup thread started...");
            BackupManager.makeBackup(sourceStack, name, desc);
            if (QuickbakcupmultiReforged.getModConfig().isCacheDatabase()) {
                DatabaseCache.updateStorageInfoCaches();
            }
            QuickbakcupmultiReforged.logger.info("Make Backup thread close => {}ms", System.currentTimeMillis() - l);
        }
    }

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HHmmss");

    public static final LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal("make")
        .requires(it -> PermissionManager.hasPermission(it, 4, PermissionType.HELPER))
        .executes(it -> makeSaveBackup(it.getSource(), dateFormat.format(System.currentTimeMillis()), ""))
        .then(Commands.argument("name", StringArgumentType.string())
            .executes(it -> makeSaveBackup(it.getSource(), StringArgumentType.getString(it, "name"), ""))
            .then(Commands.argument("desc", StringArgumentType.string())
                .executes(it -> makeSaveBackup(
                    it.getSource(),
                    StringArgumentType.getString(it, "name"),
                    StringArgumentType.getString(it, "desc")
                )))
        );

    private static int makeSaveBackup(CommandSourceStack commandSource, String name, String desc) {
        new Thread(new makeRunnable(commandSource, name, desc)).start();
        return 1;
    }
}
