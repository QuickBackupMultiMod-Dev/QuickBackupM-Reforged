package io.github.skydynamic.quickbakcupmulti.neoforge;

import io.github.skydynamic.quickbakcupmulti.ServerManager;
import io.github.skydynamic.quickbakcupmulti.neoforge.restart.NeoforgeRestart;
import net.minecraft.server.MinecraftServer;

public class ServerManagerNeoforge extends ServerManager {
    public ServerManagerNeoforge(MinecraftServer server) {
        super(server);
    }

    @Override
    public void startServer() {
        NeoforgeRestart.restartServer();
    }
}
