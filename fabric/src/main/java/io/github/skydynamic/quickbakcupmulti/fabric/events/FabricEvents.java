package io.github.skydynamic.quickbakcupmulti.fabric.events;

import io.github.skydynamic.quickbakcupmulti.fabric.QuickbakcupmultiReforgedFabric;
import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import io.github.skydynamic.quickbakcupmulti.ServerManager;
import io.github.skydynamic.quickbakcupmulti.event.OnServerStopedHandler;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

public class FabricEvents {
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(FabricEvents::onServerStarted);
        CommandRegistrationCallback.EVENT.register(
            (commandDispatcher, registryAccess, environment) -> {
                QuickbakcupmultiReforgedFabric.getModContainer().setDispatcher(commandDispatcher);
                QuickbakcupmultiReforged.registerCommand();
            }
        );
        ServerLifecycleEvents.SERVER_STOPPED.register(FabricEvents::onServerStoped);
    }

    private static void onServerStarted(MinecraftServer server) {
        QuickbakcupmultiReforged.setServerManager(new ServerManager(server));
    }

    private static void onServerStoped(MinecraftServer server) {
        OnServerStopedHandler.handle();
    }
}
