package io.github.skydynamic.quickbakcupmulti.neoforge.events;

import io.github.skydynamic.quickbakcupmulti.neoforge.QuickbakcupmultiReforgedNeoForge;
import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import io.github.skydynamic.quickbakcupmulti.ServerManager;
import io.github.skydynamic.quickbakcupmulti.event.OnServerStopedHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@EventBusSubscriber(value = Dist.DEDICATED_SERVER, modid = QuickbakcupmultiReforged.MOD_ID)
public class NeoForgeEvents {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        QuickbakcupmultiReforgedNeoForge.getModContainer().setDispatcher(event.getDispatcher());
        QuickbakcupmultiReforged.registerCommand();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        QuickbakcupmultiReforged.setServerManager(new ServerManager(event.getServer()));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStartedEvent event) {
        OnServerStopedHandler.handle();
    }
}
