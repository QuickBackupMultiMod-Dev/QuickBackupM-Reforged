package io.github.skydynamic.quickbakcupmulti.fabric.events;

import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import io.github.skydynamic.quickbakcupmulti.ServerManager;
import io.github.skydynamic.quickbakcupmulti.fabric.QuickbackupmultiReforgedFabric;
import io.github.skydynamic.quickbakcupmulti.event.OnServerStoppedHandler;
import io.github.skydynamic.quickbakcupmulti.restart.RestoreMarker;
import io.github.skydynamic.quickbakcupmulti.utils.BackupManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

public class FabricEvents {
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(FabricEvents::onServerStarted);
        CommandRegistrationCallback.EVENT.register(
            (commandDispatcher, registryAccess, environment) -> {
                QuickbackupmultiReforgedFabric.getModContainer().setDispatcher(commandDispatcher);
                QuickbakcupmultiReforged.registerCommand();
            }
        );
        ServerLifecycleEvents.SERVER_STOPPED.register(FabricEvents::onServerStopped);
    }

    private static void onServerStarted(MinecraftServer server) {
        RestoreMarker.read().ifPresent(backup -> {
            BackupManager.restoreBackup(backup);
            QuickbakcupmultiReforged.getModContainer().setCurrentSelectionBackup(backup);
            RestoreMarker.delete();
        });
        QuickbakcupmultiReforged.setServerManager(new ServerManager(server));
        if (QuickbakcupmultiReforged.getModContainer().isRestoringBackup()) {
            QuickbakcupmultiReforged.getModContainer().setRestoringBackup(false);
        }
    }

    private static void onServerStopped(MinecraftServer server) {
        OnServerStoppedHandler.handle();
    }
}
