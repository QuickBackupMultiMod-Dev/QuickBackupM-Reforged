package io.github.skydynamic.quickbakcupmulti.restore;

import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import io.github.skydynamic.quickbakcupmulti.translate.Translate;
import io.github.skydynamic.quickbakcupmulti.utils.BackupManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class ClientRestoreDelegate {
    public void run() {
        Minecraft minecraftClient = Minecraft.getInstance();
        String levelId = QuickbakcupmultiReforged.getModContainer().getLevelId();
        minecraftClient.execute(() -> {
            minecraftClient.level.disconnect();
            minecraftClient.disconnect(new GenericMessageScreen(Component.nullToEmpty("Restore backup")));
            LevelStorageSource levelStorageSource = minecraftClient.getLevelSource();

            try (LevelStorageSource.LevelStorageAccess levelStorageAccess = levelStorageSource.createAccess(levelId)) {
                levelStorageAccess.deleteLevel();
            } catch (IOException e) {
                QuickbakcupmultiReforged.logger.error("", e);
            }

            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                BackupManager.restoreBackup(QuickbakcupmultiReforged.getModContainer().getCurrentSelectionBackup());

                // Restore finish and rejoin the world
                // TODO: show waiting restore screen exit button and lock the world util restore finish
                QuickbakcupmultiReforged.getModContainer().setRestoringBackup(false);
                minecraftClient.execute(() -> {
                    Component title = Component.nullToEmpty(Translate.tr("quickbackupmulti.toast.end_title"));
                    SystemToast.addOrUpdate(minecraftClient.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, Component.empty());
                });
                if (QuickbakcupmultiReforged.getModConfig().isClientAutoReJoinWorld()) {
                    minecraftClient.execute(() -> minecraftClient.createWorldOpenFlows().openWorld(levelId,
                        () -> minecraftClient.setScreen(null)));
                } else {
                    minecraftClient.execute(() -> minecraftClient.setScreen(null));
                }
            });
        });
    }
}
