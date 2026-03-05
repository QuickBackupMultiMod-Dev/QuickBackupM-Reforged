package io.github.skydynamic.quickbackupmulti.fabric.events;

import io.github.skydynamic.quickbackupmulti.QuickbackupmultiReforged;
import io.github.skydynamic.quickbackupmulti.fabric.QuickbackupmultiReforgedFabric;
import io.github.skydynamic.quickbackupmulti.ServerManager;
import io.github.skydynamic.quickbackupmulti.event.OnServerStoppedHandler;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

public class FabricEvents {
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(FabricEvents::onServerStarted);
        CommandRegistrationCallback.EVENT.register(
            (commandDispatcher, registryAccess, environment) -> {
                QuickbackupmultiReforgedFabric.getModContainer().setDispatcher(commandDispatcher);
                QuickbackupmultiReforged.registerCommand();
            }
        );
        ServerLifecycleEvents.SERVER_STOPPED.register(FabricEvents::onServerStopped);
    }

    private static void onServerStarted(MinecraftServer server) {
        QuickbackupmultiReforged.setServerManager(new ServerManager(server));
    }

    private static void onServerStopped(MinecraftServer server) {
        OnServerStoppedHandler.handle();
    }
}
