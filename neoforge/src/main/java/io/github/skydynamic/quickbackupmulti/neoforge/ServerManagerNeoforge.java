package io.github.skydynamic.quickbackupmulti.neoforge;

import io.github.skydynamic.quickbackupmulti.ServerManager;
import io.github.skydynamic.quickbackupmulti.neoforge.restart.NeoforgeRestart;
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
