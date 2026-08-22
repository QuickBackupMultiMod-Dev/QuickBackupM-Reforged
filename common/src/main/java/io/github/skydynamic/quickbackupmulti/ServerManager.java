package io.github.skydynamic.quickbackupmulti;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.PacketProcessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConnectionListener;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.validation.ContentValidationException;

import java.io.IOException;
import java.nio.file.Path;
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
            this.server.storageSource = reopenStorageSource();
            this.server.playerDataStorage = this.server.storageSource.createPlayerStorage();
            this.server.packetProcessor = new PacketProcessor(this.server.getRunningThread());
            this.server.runServer();
        } catch (IOException e) {
            QuickbackupmultiReforged.logger.error("Failed to start the server", e);
        } catch (ContentValidationException e1) {
            QuickbackupmultiReforged.logger.error("Level data is corrupted", e1);
        }
    }

    /**
     * Re-acquire the level directory after a restore so the server can be started again in-process.
     *
     * <p>{@link LevelStorageSource#createDefault} takes the <em>saves</em> directory, not the level
     * directory: it appends the level id itself. Passing the level directory made
     * {@code validateAndCreateAccess} resolve {@code ./world} + {@code world}, and since acquiring
     * the directory lock creates the directory, every restore left behind an extra nested
     * {@code world/world} and the next one nested again.
     *
     * <p>{@code MinecraftServer#stopServer} closes the old storage source before this runs, so the
     * lock on the level directory is free to be taken again.
     */
    private LevelStorageSource.LevelStorageAccess reopenStorageSource() throws IOException, ContentValidationException {
        Path levelDirectory = this.server.storageSource.getLevelDirectory().path();
        Path savesDirectory = levelDirectory.getParent();
        if (savesDirectory == null) {
            // A bare relative level directory such as "world" has no parent; that is the working
            // directory, which is what LevelStorageSource would have resolved against anyway.
            savesDirectory = Path.of("");
        }
        return LevelStorageSource.createDefault(savesDirectory)
            .validateAndCreateAccess(this.server.storageSource.getLevelId());
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
