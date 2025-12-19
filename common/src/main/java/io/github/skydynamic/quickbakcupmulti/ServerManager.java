package io.github.skydynamic.quickbakcupmulti;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.PacketProcessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConnectionListener;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.validation.ContentValidationException;

import java.io.IOException;
import java.util.List;

public class ServerManager {
    private final MinecraftServer server;

    public ServerManager(MinecraftServer server) {
        this.server = server;
    }

    public void startServer() {
        try {
            this.server.running = true;
            this.server.stopped = false;
            this.server.connection = new ServerConnectionListener(this.server);
            LevelStorageSource levelStorageSource = LevelStorageSource.createDefault(
                this.server.storageSource.getLevelDirectory().path()
            );
            this.server.storageSource = levelStorageSource.validateAndCreateAccess(
                this.server.storageSource.getLevelId()
            );
            this.server.playerDataStorage = this.server.storageSource.createPlayerStorage();
            this.server.packetProcessor = new PacketProcessor(this.server.getRunningThread());
            this.server.runServer();
        } catch (IOException e) {
            QuickbakcupmultiReforged.logger.error("Failed to start the server", e);
        } catch (ContentValidationException e1) {
            QuickbakcupmultiReforged.logger.error("Level data is corrupted", e1);
        }
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
