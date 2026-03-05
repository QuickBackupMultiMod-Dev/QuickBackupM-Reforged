package io.github.skydynamic.quickbackupmulti.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.skydynamic.quickbackupmulti.QuickbackupmultiReforged;
import io.github.skydynamic.quickbackupmulti.utils.permission.PermissionManager;
import io.github.skydynamic.quickbackupmulti.utils.permission.PermissionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.NameAndId;

import java.util.Collection;

import static io.github.skydynamic.quickbackupmulti.translate.Translate.tr;

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

    private static int setPermission(CommandSourceStack commandSource, Collection<NameAndId> players, int level) {
        players.forEach(player -> {
            QuickbackupmultiReforged.getModContainer().getPermissionManager().setPermissionByPermissionLevelInt(level, player.name());
            commandSource.sendSystemMessage(
            Component.literal(
                "Set %s to %s".formatted(
                    player.name(),
                    PermissionType.getByLevelInt(level).name())
                )
            );
        });
        return 1;
    }

    private static int reloadPermission(CommandSourceStack commandSource) {
        QuickbackupmultiReforged.getModContainer().getPermissionManager().reloadPermission();
        commandSource.sendSystemMessage(Component.literal(tr("quickbackupmulti.permission.reload")));
        return 1;
    }
}
