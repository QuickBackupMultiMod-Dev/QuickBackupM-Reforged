package io.github.skydynamic.quickbakcupmulti.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import io.github.skydynamic.quickbakcupmulti.utils.permission.PermissionManager;
import io.github.skydynamic.quickbakcupmulti.utils.permission.PermissionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;

import java.util.Collection;

import static io.github.skydynamic.quickbakcupmulti.translate.Translate.tr;

public class PermissionCommand {
    public static final LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal("permission")
        .requires(it -> PermissionManager.hasPermission(it, 4, PermissionType.ADMIN))
        .then(Commands.literal("set")
            .then(Commands.argument("player", GameProfileArgument.gameProfile())
                .then(Commands.argument("level", IntegerArgumentType.integer(0, 2))
                    .executes(it -> setPermission(
                            it.getSource(),
                            GameProfileArgument.getGameProfiles(it, "player"),
                            IntegerArgumentType.getInteger(it, "level")
                    ))
                )
            )
        )
        .then(Commands.literal("reload")
            .executes(it -> reloadPermission(it.getSource()))
        );

    private static int setPermission(CommandSourceStack commandSource, Collection<GameProfile> players, int level) {
        players.forEach(player -> {
            QuickbakcupmultiReforged.getModContainer().getPermissionManager().setPermissionByPermissionLevelInt(level, player.getName());
            commandSource.sendSystemMessage(
            Component.literal(
                "Set %s to %s".formatted(
                    player.getName(),
                    PermissionType.getByLevelInt(level).name())
                )
            );
        });
        return 1;
    }

    private static int reloadPermission(CommandSourceStack commandSource) {
        QuickbakcupmultiReforged.getModContainer().getPermissionManager().reloadPermission();
        commandSource.sendSystemMessage(Component.literal(tr("quickbackupmulti.permission.reload")));
        return 1;
    }
}
