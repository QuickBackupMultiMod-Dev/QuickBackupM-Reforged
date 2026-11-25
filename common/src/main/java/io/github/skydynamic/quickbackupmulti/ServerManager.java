package io.github.skydynamic.quickbackupmulti;

import io.github.skydynamic.quickbackupmulti.utils.ProcessRestart;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class ServerManager {
    private final MinecraftServer server;

    public ServerManager(MinecraftServer server) {
        this.server = server;
    }

    /**
     * Brings a server back up after a rollback.
     *
     * <p>This launches a replacement process and exits; it does not restart the server in place. See
     * {@link ProcessRestart} for why re-entering {@code runServer()} on this instance cannot work.
     */
    public void startServer() {
        ProcessRestart.restart();
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
