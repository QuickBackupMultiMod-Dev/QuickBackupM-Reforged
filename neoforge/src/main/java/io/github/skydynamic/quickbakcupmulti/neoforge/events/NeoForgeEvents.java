package io.github.skydynamic.quickbakcupmulti.neoforge.events;

import io.github.skydynamic.quickbakcupmulti.config.ModConfig;
import io.github.skydynamic.quickbakcupmulti.neoforge.QuickbakcupmultiReforgedNeoForge;
import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import io.github.skydynamic.quickbakcupmulti.event.OnServerStoppedHandler;
import io.github.skydynamic.quickbakcupmulti.neoforge.ServerManagerNeoforge;
import io.github.skydynamic.quickbakcupmulti.utils.BackupManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@EventBusSubscriber(value = Dist.DEDICATED_SERVER, modid = QuickbakcupmultiReforged.MOD_ID)
public class NeoForgeEvents {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        QuickbakcupmultiReforgedNeoForge.getModContainer().setDispatcher(event.getDispatcher());
        QuickbakcupmultiReforged.registerCommand();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        QuickbakcupmultiReforged.setServerManager(new ServerManagerNeoforge(event.getServer()));

        // suck ModifiableBiomeInfo & ModifiableStructureInfo
        if (QuickbakcupmultiReforged.getModContainer().isRestoringBackup()) {
            QuickbakcupmultiReforged.getModContainer().setRestoringBackup(false);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        if (QuickbakcupmultiReforged.getModContainer().isRestoringBackup()) {
            if (QuickbakcupmultiReforged.getModConfig().getAutoRestartMode() == ModConfig.AutoRestartMode.DEFAULT) {
                BackupManager.restoreBackup(QuickbakcupmultiReforged.getModContainer().getCurrentSelectionBackup());
                QuickbakcupmultiReforged.getServerManager().startServer();
            } else {
                OnServerStoppedHandler.handle();
            }
        }
    }
}
