package io.github.skydynamic.quickbakcupmulti.restore;

import io.github.skydynamic.quickbakcupmulti.ModEnvType;
import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import io.github.skydynamic.quickbakcupmulti.command.RestoreCommand;
import io.github.skydynamic.quickbakcupmulti.restart.RestoreMarker;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.TimerTask;

public class RestoreTimer extends TimerTask {
    private final ModEnvType env;
    private final List<ServerPlayer> players;

    public RestoreTimer(ModEnvType env, List<ServerPlayer> players) {
        this.env = env;
        this.players = players;
    }

    @Override
    public void run() {
        if (env == ModEnvType.SERVER) {
            QuickbakcupmultiReforged.getServerManager()
                .getCommandSource()
                .getServer()
                .execute(() -> {
                    RestoreCommand.getRestoreDataMap().clear();
                    QuickbakcupmultiReforged.getModContainer().setRestoringBackup(true);
                    for (ServerPlayer player : players) {
                        player.connection.disconnect(Component.literal("Server restore backup"));
                    }
                    RestoreMarker.write(QuickbakcupmultiReforged.getModContainer().getCurrentSelectionBackup());
                    QuickbakcupmultiReforged.getServerManager().stopServer();
                });
        } else {
            RestoreCommand.getRestoreDataMap().clear();
            QuickbakcupmultiReforged.getModContainer().setRestoringBackup(true);
            new ClientRestoreDelegate().run();
        }
    }
}
