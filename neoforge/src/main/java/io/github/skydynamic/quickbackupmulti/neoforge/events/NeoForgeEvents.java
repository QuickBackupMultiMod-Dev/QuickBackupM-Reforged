package io.github.skydynamic.quickbackupmulti.neoforge.events;

import io.github.skydynamic.quickbackupmulti.QuickbackupmultiReforged;
import io.github.skydynamic.quickbackupmulti.config.ModConfig;
import io.github.skydynamic.quickbackupmulti.neoforge.QuickbackupmultiReforgedNeoForge;
import io.github.skydynamic.quickbackupmulti.event.OnServerStoppedHandler;
import io.github.skydynamic.quickbackupmulti.event.OnServerStoppingHandler;
import io.github.skydynamic.quickbackupmulti.neoforge.ServerManagerNeoforge;
import io.github.skydynamic.quickbackupmulti.schedule.ScheduleManager;
import io.github.skydynamic.quickbackupmulti.utils.BackupManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@EventBusSubscriber(modid = QuickbackupmultiReforged.MOD_ID)
public class NeoForgeEvents {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        QuickbackupmultiReforgedNeoForge.getModContainer().setDispatcher(event.getDispatcher());
        QuickbackupmultiReforged.registerCommand();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        QuickbackupmultiReforged.setServerManager(new ServerManagerNeoforge(event.getServer()));

        // suck ModifiableBiomeInfo & ModifiableStructureInfo
        if (QuickbackupmultiReforged.getModContainer().isRestoringBackup()) {
            QuickbackupmultiReforged.getModContainer().setRestoringBackup(false);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        OnServerStoppingHandler.handle();
    }

    @OnlyIn(Dist.DEDICATED_SERVER)
    @SubscribeEvent
    public static void onDedicatedServerStopped(ServerStoppedEvent event) {
        if (QuickbackupmultiReforged.getModContainer().isRestoringBackup()
            && QuickbackupmultiReforged.getModConfig().getAutoRestartMode() == ModConfig.AutoRestartMode.DEFAULT) {
            // NeoForge's DEFAULT restart launches a detached JVM instead of relaunching in place, so this
            // process is about to be replaced rather than reused and the shared handler's restart branch
            // does not apply. The schedules still have to be torn down: their Quartz threads are not
            // daemons, so leaving them behind would keep this JVM alive alongside its replacement.
            ScheduleManager.clearAllSchedule();
            BackupManager.restoreBackup(QuickbackupmultiReforged.getModContainer().getCurrentSelectionBackup());
            QuickbackupmultiReforged.getServerManager().startServer();
            return;
        }
        // Every other stop runs the shared handler, exactly as Fabric does. This used to be reached only
        // when a restore was pending, so a plain `stop` never cleared the schedules or closed the
        // database: the Quartz worker threads outlived the server and the JVM never exited.
        OnServerStoppedHandler.handle();
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onIntegratedServerStopped(ServerStoppedEvent event) {
        OnServerStoppedHandler.handle();
    }
}
