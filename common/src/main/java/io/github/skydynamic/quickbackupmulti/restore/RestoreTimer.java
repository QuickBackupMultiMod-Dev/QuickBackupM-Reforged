package io.github.skydynamic.quickbackupmulti.restore;

import io.github.skydynamic.quickbackupmulti.ModEnvType;
import io.github.skydynamic.quickbackupmulti.QuickbackupmultiReforged;
import io.github.skydynamic.quickbackupmulti.command.RestoreCommand;
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
        RestoreCommand.getRestoreDataMap().clear();
        QuickbackupmultiReforged.getModContainer().setRestoringBackup(true);
        if (env == ModEnvType.SERVER) {
            for (ServerPlayer player : players) {
                player.connection.disconnect(Component.literal("Server restore backup"));
            }
            QuickbackupmultiReforged.getServerManager().stopServer();
        } else {
            new ClientRestoreDelegate().run();
        }
    }
}
