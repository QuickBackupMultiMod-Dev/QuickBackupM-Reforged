package io.github.skydynamic.quickbackupmulti.neoforge.events;

import io.github.skydynamic.quickbackupmulti.QuickbackupmultiReforged;
import io.github.skydynamic.quickbackupmulti.ServerManager;
import io.github.skydynamic.quickbackupmulti.event.OnServerStoppedHandler;
import io.github.skydynamic.quickbackupmulti.neoforge.QuickbackupmultiReforgedNeoForge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@Mod.EventBusSubscriber(modid = QuickbackupmultiReforged.MOD_ID)
public class NeoForgeEvents {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        QuickbackupmultiReforgedNeoForge.getModContainer().setDispatcher(event.getDispatcher());
        QuickbackupmultiReforged.registerCommand();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        QuickbackupmultiReforged.setServerManager(new ServerManager(event.getServer()));

        // suck ModifiableBiomeInfo & ModifiableStructureInfo
        if (QuickbackupmultiReforged.getModContainer().isRestoringBackup()) {
            QuickbackupmultiReforged.getModContainer().setRestoringBackup(false);
        }
    }

    @OnlyIn(Dist.DEDICATED_SERVER)
    @SubscribeEvent
    public static void onDedicatedServerStopped(ServerStoppedEvent event) {
        // One restore path for both loaders. This used to special-case DEFAULT because that mode
        // relaunched inside the same JVM on Fabric but launched a detached one here, so the shared
        // handler's restart branch did not apply. Every mode now restarts the same way.
        OnServerStoppedHandler.handle();
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onIntegratedServerStopped(ServerStoppedEvent event) {
        OnServerStoppedHandler.handle();
    }
}
