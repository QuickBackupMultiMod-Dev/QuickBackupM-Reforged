package io.github.skydynamic.quickbakcupmulti;

import net.minecraft.server.MinecraftServer;

public class ServerManager {
    private final MinecraftServer server;

    public ServerManager(MinecraftServer server) {
        this.server = server;
    }

    public void startServer() {
        this.server.running = true;
        this.server.stopped = false;
        this.server.runServer();
    }

    public void stopServer() {
        this.server.halt(false);
    }
}
