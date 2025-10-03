package io.github.skydynamic.quickbakcupmulti;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class ServerManager {
    private final MinecraftServer server;

    public ServerManager(MinecraftServer server) {
        this.server = server;
    }

    public void stopServer() {
        this.server.halt(false);
    }

    public List<ServerPlayer> getPlayers() {
        return this.server.getPlayerList().getPlayers();
    }

    public CommandSourceStack getCommandSource() {
        return this.server.createCommandSourceStack();
    }
}
