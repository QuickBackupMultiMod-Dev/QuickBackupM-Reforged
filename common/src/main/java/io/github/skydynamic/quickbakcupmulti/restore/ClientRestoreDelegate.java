package io.github.skydynamic.quickbakcupmulti.restore;

import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import io.github.skydynamic.quickbakcupmulti.client.screen.RestoreScreen;
import io.github.skydynamic.quickbakcupmulti.translate.Translate;
import io.github.skydynamic.quickbakcupmulti.utils.BackupManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientRestoreDelegate {
    private final RestoreScreen screen = new RestoreScreen(this::cancel);
    private CompletableFuture<Void> restoreFuture = null;
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);

    protected final Minecraft minecraftClient = Minecraft.getInstance();
    String levelId = QuickbakcupmultiReforged.getModContainer().getLevelId();

    public void run() {
        long startTime = System.currentTimeMillis();
        minecraftClient.executeBlocking(() -> {
            minecraftClient.level.disconnect();
            minecraftClient.disconnect(screen);
        });

        restoreFuture = CompletableFuture.runAsync(() -> {
            screen.setState(Translate.tr("quickbackupmulti.restoring_backup.state.make_temp_backup"));
            screen.setProgress(0.05f);
            BackupManager.makeTempBackup();

            screen.setState(Translate.tr("quickbackupmulti.restoring_backup.state.delete_origin_save"));
            screen.setProgress(0.1f);
            deleteWorld();

            if (isCancelled.get()) {
                handleCancellation();
                return;
            }

            BackupManager.RestoreExtraRunnable extraRunnable = (totalProgress, currentProgress) -> {
                screen.setState(Translate.tr("quickbackupmulti.restoring_backup.state.restoring_backup"));
                screen.setProgress((float) currentProgress / totalProgress * 0.9f);
            };
            BackupManager.restoreBackup(QuickbakcupmultiReforged.getModContainer().getCurrentSelectionBackup(), extraRunnable);

            if (isCancelled.get()) {
                handleCancellation();
                return;
            }

            // Restore finish and rejoin the world
            QuickbakcupmultiReforged.getModContainer().setRestoringBackup(false);
            long endTime = System.currentTimeMillis();
            minecraftClient.execute(() -> {
                Component title = Component.nullToEmpty(Translate.tr("quickbackupmulti.toast.end_title"));
                Component desc = Component.nullToEmpty(Translate.tr("quickbackupmulti.toast.end_desc", (int) (endTime - startTime * 1000)));
                SystemToast.addOrUpdate(minecraftClient.getToastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, desc);
            });
            if (QuickbakcupmultiReforged.getModConfig().isClientAutoReJoinWorld()) {
                minecraftClient.execute(() -> minecraftClient.createWorldOpenFlows().openWorld(levelId,
                    () -> minecraftClient.setScreen(null)));
            } else {
                minecraftClient.execute(() -> minecraftClient.setScreen(null));
            }
        }, Executors.newSingleThreadExecutor());
    }

    private void handleCancellation() {
        try {
            screen.setState(Translate.tr("quickbackupmulti.restoring_backup.state.delete_origin_save"));
            deleteWorld();
            screen.setState(Translate.tr("quickbackupmulti.restoring_backup.state.restore_temp_backup"));
            BackupManager.restoreBackup("restore_temp");
            minecraftClient.execute(() -> {
                Component title = Component.nullToEmpty(Translate.tr("quickbackupmulti.toast.cancel_success"));
                Component desc = Component.nullToEmpty(Translate.tr("quickbackupmulti.toast.cancel_success.desc"));
                SystemToast.addOrUpdate(minecraftClient.getToastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, desc);
                minecraftClient.setScreen(null);
            });
        } catch (Exception e) {
            QuickbakcupmultiReforged.logger.error("Error during cancellation", e);
        }
    }

    public void cancel(Button button) {
        isCancelled.set(true);

        if (button != null) {
            screen.setState(Translate.tr("quickbackupmulti.restoring_backup.state.cancel"));
            button.active = false;
        }

        QuickbakcupmultiReforged.getModContainer().setRestoringBackup(false);
    }

    private void deleteWorld() {
        try(LevelStorageSource.LevelStorageAccess levelStorageSource = minecraftClient.getLevelSource().createAccess(levelId)) {
            levelStorageSource.deleteLevel();
        } catch (IOException e) {
            QuickbakcupmultiReforged.logger.error("Error during delete level", e);
        }
    }
}
